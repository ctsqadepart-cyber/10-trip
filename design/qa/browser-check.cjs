const fs = require('node:fs');
const path = require('node:path');
const cp = require('node:child_process');
const assert = require('node:assert/strict');
const workspace = path.resolve(__dirname, '..');
const source = process.argv[2];
const browserPath = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const output = path.join(workspace, 'previews');
fs.mkdirSync(output, {recursive:true});
const html = fs.readFileSync(source, 'utf8');
const chrome = cp.spawn(browserPath, ['--headless=new','--disable-gpu','--no-sandbox','--no-first-run','--disable-background-networking','--disable-extensions','--disable-component-update','--disable-sync','--remote-debugging-pipe',`--user-data-dir=${path.join(workspace, 'qa', 'browser-profile')}`], {stdio:['ignore','ignore','pipe','pipe','pipe'],windowsHide:true});
let seq=0, buffer='', sessionId;
const pending=new Map(), errors=[], results=[];
chrome.stdio[4].on('data',chunk=>{buffer+=chunk.toString();let i;while((i=buffer.indexOf('\0'))>=0){const raw=buffer.slice(0,i);buffer=buffer.slice(i+1);if(!raw)continue;const data=JSON.parse(raw);if(data.id&&pending.has(data.id)){const {resolve,reject,timer}=pending.get(data.id);clearTimeout(timer);pending.delete(data.id);data.error?reject(Error(JSON.stringify(data.error))):resolve(data.result);}if(data.method==='Runtime.exceptionThrown')errors.push(data.params.exceptionDetails.text+': '+(data.params.exceptionDetails.exception?.description||''));}});
chrome.on('error',error=>{for(const p of pending.values())p.reject(error);});
function send(method,params={},session){return new Promise((resolve,reject)=>{const id=++seq;const timer=setTimeout(()=>{pending.delete(id);reject(Error('CDP timeout: '+method));},15000);pending.set(id,{resolve,reject,timer});chrome.stdio[3].write(JSON.stringify({id,method,params,...(session?{sessionId:session}:{})})+'\0');});}
async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true},sessionId);if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description||r.exceptionDetails.text);return r.result.value;}
const click=action=>evaluate(`(()=>{const e=document.querySelector('[data-action="${action}"]');if(!e)throw Error('Missing action: ${action}');e.click();})()`);
const text=()=>evaluate("document.getElementById('travel-design').innerText");
async function screenshot(name){const metrics=await send('Page.getLayoutMetrics',{},sessionId);const r=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true,clip:{x:0,y:0,width:metrics.cssContentSize.width,height:metrics.cssContentSize.height,scale:1}},sessionId);fs.writeFileSync(path.join(output,name+'.png'),Buffer.from(r.data,'base64'));}
async function field(id,value){await evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(id)});e.value=${JSON.stringify(value)};e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));})()`);}
async function noOverflow(label){const r=await evaluate(`(()=>{const root=document.getElementById('travel-design');const right=root.getBoundingClientRect().right;return {root:root.clientWidth,scroll:root.scrollWidth,overflow:[...root.querySelectorAll('*')].filter(e=>e.getBoundingClientRect().width>0&&e.getBoundingClientRect().right>right+2).map(e=>e.tagName+'.'+e.className).slice(0,8)};})()`);assert.ok(r.scroll<=r.root+2, label+JSON.stringify(r));assert.equal(r.overflow.length,0,label+JSON.stringify(r));}
(async()=>{
 try{
  const target=await send('Target.createTarget',{url:'about:blank'});
  sessionId=(await send('Target.attachToTarget',{targetId:target.targetId,flatten:true})).sessionId;
  await send('Runtime.enable',{},sessionId);await send('Page.enable',{},sessionId);
  await send('Emulation.setDeviceMetricsOverride',{width:430,height:1000,deviceScaleFactor:1,mobile:false},sessionId);
  await send('Page.setDocumentContent',{frameId:(await send('Page.getFrameTree',{},sessionId)).frameTree.frame.id,html:'<!doctype html><html><head><meta charset="utf-8"><style>body{margin:0;padding:12px;color-scheme:light;background:#eee;}#travel-design .td-stage{padding:10px 0}</style></head><body>'+html+'</body></html>'},sessionId);
  assert.ok((await text()).includes('日子，都值得記下'));
  await noOverflow('home 430');await screenshot('home');results.push('首頁渲染與430px版面');
  await click('trip');await click('trip-detail');await click('photos');
  await click('down-1');assert.equal(await evaluate("[...document.querySelectorAll('[data-caption]')].map(e=>e.dataset.caption).join(',')"),'2,1');
  await click('remove-photo-1');assert.equal(await evaluate("document.querySelectorAll('[data-caption]').length"),1);results.push('照片排序與移除');
  await click('detail');await click('audio');await click('record');await click('interrupt');assert.ok((await text()).includes('因離開 App 停止錄音'));assert.equal(await evaluate("document.querySelectorAll('[data-audio]').length"),3);results.push('錄音中斷狀態示範');
  await click('detail');await click('expense');assert.equal(await evaluate("document.querySelector('#td-converted').textContent"),'215.00 TWD',await text());assert.ok((await text()).includes('71.67'));assert.equal(await evaluate("getComputedStyle(document.querySelector('#td-custom')).display"),'none');
  await screenshot('expense');
  await field('#td-amount','1');assert.equal(await evaluate("document.querySelector('#td-converted').textContent"),'0.22 TWD');
  await field('#td-amount','1000');await field('#td-split-mode','custom');
  await field('[data-custom="0"]','70.00');assert.equal(await evaluate("document.querySelector('#td-expense-save').disabled"),true);
  await field('[data-custom="0"]','71.67');assert.equal(await evaluate("document.querySelector('#td-expense-save').disabled"),false);
  await evaluate("document.querySelector('[data-participant="+'"2"'+"]').click()");assert.equal(await evaluate("document.querySelector('#td-expense-save').disabled"),true);
  await evaluate("document.querySelector('[data-participant="+'"2"'+"]').click()");
  await evaluate("document.querySelector('#td-expense-form').requestSubmit()");assert.ok((await text()).includes('淨應收／應付合計 0.00'));results.push('精確換算、四捨五入、平均與指定分攤驗證');
  await screenshot('costs');
  await click('daily');await click('diary-detail');await click('share');assert.equal(await evaluate("document.querySelector('[data-action=\"confirm-share\"]').disabled"),true);
  await screenshot('share');await evaluate("document.querySelector('#td-share-consent').click()");await click('confirm-share');await click('tab-costs');assert.ok((await text()).includes('335.00'));assert.ok((await text()).includes('淨應收／應付合計 0.00'));await click('daily');assert.ok((await text()).includes('日常紀錄已加入旅程'));results.push('分享確認、移入旅程與費用只加入一次');
  await click('settings');await click('backup');assert.ok((await text()).includes('未加密備份'));results.push('未加密備份警示畫面');
  await click('home');await click('new');await field('input[name="title"]','測試草稿');await click('edit-photos');await click('back-editor');assert.equal(await evaluate("document.querySelector('input[name=title]').value"),'測試草稿');results.push('編輯畫面切換保留原型草稿');
  await screenshot('editor');
  await evaluate("document.querySelector('#td-editor').requestSubmit()");assert.ok((await text()).includes('測試草稿'));await click('daily');assert.ok((await text()).includes('測試草稿'));results.push('新增私人日常與清單顯示');
  await click('home');await click('new');assert.equal(await evaluate("document.querySelector('input[name=title]').value"),'');await evaluate("document.querySelector('#td-editor').requestSubmit()");assert.ok((await text()).includes('請先加入文字'));results.push('空白紀錄阻擋與新增表單空值');
  for(const width of [320,736]){
   await send('Emulation.setDeviceMetricsOverride',{width,height:1200,deviceScaleFactor:1,mobile:false},sessionId);
   for(const route of [[],['trip'],['trip','tab-records','trip-detail'],['trip','tab-records','trip-detail','photos'],['trip','tab-records','trip-detail','audio'],['trip','tab-records','trip-detail','expense'],['daily'],['settings'],['trip','members']]){await click('home');for(const action of route)await click(action);await noOverflow((route.at(-1)||'home')+' '+width);}
  }
  results.push('320px與736px主要畫面無水平溢出');
  await send('Emulation.setEmulatedMedia',{features:[{name:'prefers-color-scheme',value:'dark'}]},sessionId);await click('home');await screenshot('home-dark');
  assert.equal(errors.length,0,JSON.stringify(errors));results.push('瀏覽器無未處理JavaScript錯誤');
  fs.writeFileSync(path.join(output,'prototype-checks.json'),JSON.stringify({status:'passed',executed:results,notExecuted:['Android建置安裝','手機離線持久保存','真實相機麥克風定位','備份還原與匯出','登入同步衝突及伺服器權限','真實地圖下載'],consoleErrors:errors},null,2));
  console.log(JSON.stringify({status:'passed',checks:results,screenshots:output},null,2));
 }finally{try{await send('Browser.close');}catch{}chrome.kill();}
})().catch(e=>{console.error(e.stack);process.exitCode=1;});
