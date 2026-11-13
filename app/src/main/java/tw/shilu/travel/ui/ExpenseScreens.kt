package tw.shilu.travel.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tw.shilu.domain.Money
import tw.shilu.travel.TravelViewModel
import tw.shilu.travel.data.*

@Composable internal fun ExpenseEditor(vm:TravelViewModel,data:TravelState,r:RecordEntity){
    val expenseId by vm.expenseId.collectAsStateWithLifecycle();val original=data.expenses.find{it.id==expenseId}
    val currency=data.settlement(r.tripId);val members=data.membersFor(r.tripId,vm.repo.actorId)
    val oldShares=data.shares.filter{it.expenseId==expenseId}
    var label by rememberSaveable(expenseId){mutableStateOf(original?.label?:if(r.store.isNotBlank())r.store else "")}
    var category by rememberSaveable(expenseId){mutableStateOf(original?.category?:if(r.store.isNotBlank())"飲食" else "其他")}
    var source by rememberSaveable(expenseId){mutableStateOf(original?.originalCurrency?:currency)}
    var amount by rememberSaveable(expenseId){mutableStateOf(original?.let{Money.format(it.originalMinor,it.originalCurrency)}?:"")}
    var rate by rememberSaveable(expenseId){mutableStateOf(original?.rate?:"1")}
    var payer by rememberSaveable(expenseId){mutableStateOf(original?.payerId?:vm.repo.actorId)}
    var selected by rememberSaveable(expenseId){mutableStateOf(if(original==null)members.map{it.id} else oldShares.map{it.memberId})}
    var splitMode by rememberSaveable(expenseId){mutableStateOf(original?.splitMode?:"EQUAL")}
    var custom by rememberSaveable(expenseId){mutableStateOf(oldShares.associate{it.memberId to Money.format(it.minor,currency)})}
    var linked by rememberSaveable(expenseId){mutableStateOf(r.mealExpenseId==expenseId&&expenseId!=null)}
    var deletion by remember{mutableStateOf(false)}
    val pending by vm.pending.collectAsStateWithLifecycle()
    val calculated=runCatching{val minor=Money.parse(amount,source);require(minor>0){"費用必須大於零"};val converted=Money.convert(minor,source,currency,if(source==currency)"1" else rate);val splits=if(splitMode=="EQUAL")Money.equal(converted,selected)else Money.custom(converted,selected.associateWith{Money.parse(custom[it]?:"",currency)});Triple(minor,converted,splits)}
    Info("結算幣別 $currency。每筆費用保留原幣金額與本次採用匯率。${if(r.draft)"完成紀錄前屬草稿，不納入正式統計。" else ""}")
    Field("費用名稱",label,{label=it});Choice("支出類別",category,listOf("飲食","交通","住宿","購物","其他")){category=it}
    Field("原始金額",amount,{amount=it});Field("原始幣別，例如 JPY、TWD、USD",source,{source=it.uppercase();rate=if(source==currency)"1" else data.rates.find{p->p.scopeId==(r.tripId?:"diary")&&p.originalCurrency==source}?.rate?:""})
    if(source!=currency)Field("1 $source = 多少 $currency",rate,{rate=it})else Muted("1 $source = 1 $currency")
    calculated.getOrNull()?.let{(_,total,_)->Title("換算 ${Money.format(total,currency)} $currency")}
    val payerLabel=members.find{it.id==payer}?.name?:"請選擇付款人"
    var payerMenu by remember{mutableStateOf(false)}
    Muted("付款人");Box{OutlinedButton(onClick={payerMenu=true},modifier=Modifier.fillMaxWidth()){Text(payerLabel)};DropdownMenu(expanded=payerMenu,onDismissRequest={payerMenu=false}){members.forEach{m->DropdownMenuItem(text={Text("${m.name} · ${m.id.takeLast(4)}")},onClick={payer=m.id;payerMenu=false})}}}
    Title("參與分攤")
    members.forEach{m->Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=m.id in selected,onCheckedChange={checked->selected=if(checked)(selected+m.id).distinct()else selected-m.id});Text("${m.name} · ${m.id.takeLast(4)}")}}
    Choice("分攤方式",if(splitMode=="EQUAL")"平均分攤" else "指定每人金額",listOf("平均分攤","指定每人金額")){splitMode=if(it=="平均分攤")"EQUAL" else "CUSTOM"}
    if(splitMode=="CUSTOM")members.filter{it.id in selected}.forEach{m->Field("${m.name} 應負擔（$currency）",custom[m.id]?:"",{custom=custom+(m.id to it)})}
    calculated.getOrNull()?.let{(_,total,splits)->Panel{Title("分攤預覽");members.filter{it.id in splits}.forEach{m->Text("${m.name}　${Money.format(splits.getValue(m.id),currency)} $currency")};Muted("合計 ${Money.format(total,currency)} $currency。平均分攤尾差依固定成員識別順序分配。")}}
    calculated.exceptionOrNull()?.let{Text(it.message?:"請確認金額、匯率與分攤資料",color=MaterialTheme.colorScheme.error)}
    if(r.mealExpenseId==null||r.mealExpenseId==expenseId)Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=linked,onCheckedChange={linked=it});Text("連結為這筆紀錄的整餐費用（不重複計算）")}
    Info(if(original==null)"預設匯率只帶入新支出，不會改變既有支出。" else "本次修改匯率只重新計算這筆支出。請確認換算後金額及每人分攤；其他支出不受影響。")
    MainButton("確認金額與分攤，保存費用",calculated.isSuccess&&pending==0&&label.isNotBlank()){
        vm.work{val (minor,settled,shares)=calculated.getOrThrow();val e=ExpenseEntity(id=original?.id?:newId(),recordId=r.id,label=label.trim(),category=category,originalMinor=minor,originalCurrency=source,rate=if(source==currency)"1" else rate,settlementCurrency=currency,settledMinor=settled,payerId=payer,splitMode=splitMode);withContext(Dispatchers.IO){vm.repo.saveExpense(e,shares,linked)};vm.editor.value=withContext(Dispatchers.IO){vm.repo.dao.record(r.id)};vm.go(if(r.draft)"editor" else "detail");vm.notice.value="費用與分帳已儲存在手機"}
    }
    if(original!=null)TextButton(onClick={deletion=true}){Text("刪除此費用",color=MaterialTheme.colorScheme.error)}
    if(deletion)AlertDialog(onDismissRequest={deletion=false},title={Text("刪除此費用與分帳？")},text={Text("會從每日、旅程及成員結算統計移除，不會刪除整筆紀錄。")},confirmButton={TextButton(onClick={deletion=false;vm.work{withContext(Dispatchers.IO){vm.repo.deleteExpense(original!!.id)};vm.editor.value=withContext(Dispatchers.IO){vm.repo.dao.record(r.id)};vm.go(if(r.draft)"editor" else "detail")}}){Text("確認刪除")}},dismissButton={TextButton(onClick={deletion=false}){Text("取消")}})
}

@Composable internal fun ShareScreen(vm:TravelViewModel,data:TravelState,r:RecordEntity){
    var target by rememberSaveable(r.id){mutableStateOf(data.trips.firstOrNull()?.id?:"")}
    var consent by rememberSaveable(r.id,target){mutableStateOf(false)}
    var rates by rememberSaveable(r.id,target){mutableStateOf(mapOf<String,String>())}
    val trip=data.trips.find{it.id==target};val expenses=data.expenses.filter{it.recordId==r.id}
    val pending by vm.pending.collectAsStateWithLifecycle()
    Info("日常紀錄預設私人。確認後整筆內容、地點、照片、錄音與關聯費用會加入旅程。未來共同旅程須另按同步才上傳。")
    if(data.trips.isEmpty()){Muted("請先建立一趟旅程，再加入此紀錄。");MainButton("建立旅程"){vm.go("newTrip")};return}
    Title("選擇目標旅程")
    data.trips.forEach{t->Row(verticalAlignment=Alignment.CenterVertically){RadioButton(selected=target==t.id,onClick={target=t.id});Text("${t.title} · ${t.currency}")}}
    if(trip==null)return
    Title("將分享給旅程成員")
    Text(data.membersFor(target,vm.repo.actorId).joinToString("、"){it.name})
    Muted("此版本為本機成員名單，沒有真正上傳或對外分享。")
    Title("內容預覽");Text(r.title.ifBlank{"未命名紀錄"});Text(r.text);Muted("時間 ${r.occurredAt} · ${r.zoneId}");Muted("地點 ${r.place.ifBlank{"未填寫"}}${r.latitude?.let{" · $it, ${r.longitude}"}?:""}")
    val attachments=data.attachments.filter{it.recordId==r.id}
    Muted("照片 ${attachments.count{it.kind=="PHOTO"}} 張 · 錄音 ${attachments.count{it.kind=="AUDIO"}} 段 · 費用 ${expenses.size} 筆")
    attachments.forEach{Muted("${if(it.kind=="PHOTO")"照片" else "錄音"}：${it.name.ifBlank{"未命名"}} · ${it.state}")}
    if(r.store.isNotBlank())Text("店名：${r.store}");if(r.rating!=null)Text("整餐評分：${r.rating} / 5");if(r.mealNotes.isNotBlank())Text(r.mealNotes)
    data.dishes.filter{it.recordId==r.id}.forEach{Text("${it.name} × ${it.quantity} · 單價 ${Money.format(it.unitMinor,it.currency)} ${it.currency}");if(it.notes.isNotBlank())Muted(it.notes)}
    Info("成員對應：日常的「我」→ 旅程的「我」。沿用同一個本機成員識別，不依同名合併。")
    expenses.map{it.originalCurrency}.distinct().filter{it!=trip.currency}.forEach{c->Field("1 $c = 多少 ${trip.currency}",rates[c]?:"",{rates=rates+(c to it)})}
    val preview=runCatching{expenses.associate{e->e.id to Money.convert(e.originalMinor,e.originalCurrency,trip.currency,if(e.originalCurrency==trip.currency)"1" else rates[e.originalCurrency]?:"")}}
    expenses.forEach{e->Panel{Text("${e.category} · ${e.label}");Muted("原幣 ${Money.format(e.originalMinor,e.originalCurrency)} ${e.originalCurrency}");preview.getOrNull()?.get(e.id)?.let{Text("加入後 ${Money.format(it,trip.currency)} ${trip.currency}");Muted("付款人：我 · 我應負擔 ${Money.format(it,trip.currency)} ${trip.currency}")}}}
    preview.getOrNull()?.let{amounts->val old=data.expensesFor(target).sumOf{it.settledMinor};Info("旅程總支出：${Money.format(old,trip.currency)} → ${Money.format(Math.addExact(old,amounts.values.sum()),trip.currency)} ${trip.currency}。原紀錄與費用識別碼不變，不另建副本。")}
    if(preview.isFailure)Text("請填寫有效匯率，才可預覽費用。",color=MaterialTheme.colorScheme.error)
    Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=consent,onCheckedChange={consent=it});Text("我確認整筆內容、附件與費用可以加入旅程，供未來旅程成員查看及編輯。")}
    MainButton("確認加入旅程",consent&&preview.isSuccess&&pending==0){vm.work{withContext(Dispatchers.IO){vm.repo.moveDiary(r.id,target,mapOf(vm.repo.actorId to vm.repo.actorId),rates)};vm.tripId.value=target;vm.go("trip");vm.notice.value="已加入旅程，關聯費用只計算一次。尚未上傳。"}}
}
