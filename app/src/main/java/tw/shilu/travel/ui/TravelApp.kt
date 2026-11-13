@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package tw.shilu.travel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.shilu.domain.Money
import tw.shilu.travel.TravelViewModel
import tw.shilu.travel.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val Light=lightColorScheme(primary=Color(0xFF365D46),onPrimary=Color.White,background=Color(0xFFFAF8F3),surface=Color(0xFFFAF8F3),surfaceVariant=Color(0xFFE9EFDF),onSurface=Color(0xFF26382E),onSurfaceVariant=Color(0xFF606B63))
private val Dark=darkColorScheme(primary=Color(0xFFBDD7AC),onPrimary=Color(0xFF20351E),background=Color(0xFF1E211E),surface=Color(0xFF1E211E),surfaceVariant=Color(0xFF354331),onSurface=Color(0xFFEDF1E7),onSurfaceVariant=Color(0xFFB4BFB0))

@Composable fun TravelApp(vm:TravelViewModel,pickPhotos:()->Unit,camera:()->Unit,audio:()->Unit,locate:()->Unit){
    val data by vm.state.collectAsStateWithLifecycle();val screen by vm.screen.collectAsStateWithLifecycle()
    val tripId by vm.tripId.collectAsStateWithLifecycle();val recordId by vm.selectedId.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle();val notice by vm.notice.collectAsStateWithLifecycle()
    val record=data.records.find{it.id==recordId};val trip=data.trips.find{it.id==tripId}
    MaterialTheme(colorScheme=if(isSystemInDarkTheme())Dark else Light,shapes=Shapes(medium=RoundedCornerShape(16.dp),large=RoundedCornerShape(20.dp))){
        fun back(){vm.go(when(screen){"editor","detail"->if(tripId==null)"diary" else "trip";"expense"->if(record?.draft==true)"editor" else "detail";"members"->"trip";"share"->"detail";else->"home"})}
        BackHandler(enabled=screen!="home"){back()}
        Scaffold(topBar={TopAppBar(title={Text(when(screen){"home"->"拾旅";"diary"->"日常紀錄";"trip"->trip?.title?:"旅程";"editor"->"記下這一刻";"detail"->"紀錄內容";"expense"->"費用與分帳";"newTrip"->"建立旅程";"members"->"旅程與成員";"share"->"確認加入並分享";else->"設定與資料"},maxLines=2)},navigationIcon={if(screen!="home")IconButton(onClick={back()}){Icon(Icons.Outlined.ArrowBack,"返回")}})},bottomBar={
            NavigationBar{listOf(Triple("home","旅程",Icons.Outlined.Luggage),Triple("diary","日常",Icons.Outlined.Book),Triple("new","新增",Icons.Outlined.AddCircleOutline),Triple("settings","設定",Icons.Outlined.Settings)).forEach{(id,label,icon)->NavigationBarItem(selected=screen==id,onClick={if(id=="new")vm.newRecord(if(screen=="trip")tripId else null)else vm.go(id)},icon={Icon(icon,label)},label={Text(label)})}}
        }){padding->
            Column(Modifier.fillMaxSize().padding(padding).imePadding()){
                if(pending>0)LinearProgressIndicator(Modifier.fillMaxWidth())
                if(notice.isNotBlank())Surface(color=MaterialTheme.colorScheme.surfaceVariant){Row(Modifier.fillMaxWidth().padding(start=16.dp),verticalAlignment=Alignment.CenterVertically){Text(notice,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall);IconButton(onClick={vm.notice.value=""}){Icon(Icons.Outlined.Close,"關閉提示")}}}
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                    when(screen){
                        "home"->{Text("日子，都值得記下。",style=MaterialTheme.typography.headlineMedium);Muted("把旅程與日常，慢慢收進來。")
                            MainButton("＋ 建立旅程"){vm.go("newTrip")}
                            if(data.trips.isEmpty())Info("還沒有旅程。先建立一趟旅程，或記錄旅程之外的小日子。")
                            data.trips.forEach{t->Panel{Text(t.title,style=MaterialTheme.typography.titleLarge);Muted("${t.startDate} — ${t.endDate}");val count=data.records.count{it.tripId==t.id&&!it.draft};val sum=data.expensesFor(t.id).sumOf{it.settledMinor};Text("$count 筆紀錄 · ${Money.format(sum,t.currency)} ${t.currency}");TextButton(onClick={vm.tripId.value=t.id;vm.go("trip")}){Text("進入旅程 →")}}}
                            val drafts=data.records.filter{it.draft};if(drafts.isNotEmpty()){Title("未完成的草稿");drafts.forEach{RecordCard(it,data,vm)}}
                            OutlinedButton(onClick={vm.go("diary")},modifier=Modifier.fillMaxWidth()){Text("查看私人日常紀錄")};MainButton("＋ 記下這一刻"){vm.newRecord(null)};Muted("已儲存在手機的內容不會自行上傳。")
                        }
                        "newTrip"->NewTrip(vm)
                        "diary"->{Info("私人・僅儲存在手機。加入旅程前會再次確認分享內容。");MainButton("＋ 新增日常紀錄"){vm.newRecord(null)};val rows=data.records.filter{it.tripId==null};if(rows.isEmpty())Muted("還沒有日常紀錄。");rows.forEach{RecordCard(it,data,vm)}}
                        "trip"->if(trip!=null)TripScreen(vm,data,trip)
                        "editor"->RecordEditor(vm,data,pickPhotos,camera,audio,locate)
                        "detail"->if(record!=null)RecordDetail(vm,data,record)
                        "expense"->if(record!=null)ExpenseEditor(vm,data,record)
                        "members"->if(trip!=null)MemberScreen(vm,data,trip)
                        "share"->if(record!=null)ShareScreen(vm,data,record)
                        "settings"->{Title("安心留在手邊");Info("目前為本機版本，不需要登入，沒有網路權限。資料庫與附件保存在 App 私有空間。")
                            Title("本機儲存");Muted("旅程 ${data.trips.size} 趟 · 紀錄 ${data.records.count{!it.draft}} 筆 · 草稿 ${data.records.count{it.draft}} 筆")
                            Muted("附件 ${data.attachments.size} 個 · 約 ${data.attachments.sumOf{it.sizeBytes}/1024/1024} MB（不含保留的刪除版本）")
                            Info("錄音會在鎖屏或離開 App 時停止並保存。若錄音異常中斷，重新開啟時檢查可恢復部分。")
                            Title("備份與匯出尚未開放");Muted("第二階段提供未加密完整備份、還原、費用表與圖文匯出。取得未加密備份的人可能讀取私人內容。")
                            Info("解除安裝或清除 App 資料會刪除本機內容。目前尚無備份功能，測試期間請勿把此版本當成唯一保存來源。")
                            Muted("0.1.0-local · 本機測試版 · 尚未開通同步、附近搜尋及離線地圖")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable internal fun Muted(text:String)=Text(text,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
@Composable internal fun Title(text:String)=Text(text,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Medium)
@Composable internal fun MainButton(text:String,enabled:Boolean=true,onClick:()->Unit)=Button(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(text)}
@Composable internal fun Info(text:String)=Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=RoundedCornerShape(14.dp)){Text(text,Modifier.padding(16.dp),style=MaterialTheme.typography.bodyMedium)}
@Composable internal fun Panel(content:@Composable ColumnScope.()->Unit)=OutlinedCard(modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)}
@Composable internal fun Field(label:String,value:String,onChange:(String)->Unit,modifier:Modifier=Modifier,singleLine:Boolean=true)=OutlinedTextField(value=value,onValueChange=onChange,label={Text(label)},singleLine=singleLine,modifier=modifier.fillMaxWidth())
@Composable internal fun Choice(label:String,value:String,values:List<String>,onChange:(String)->Unit){var open by remember{mutableStateOf(false)};Column{Muted(label);Box{OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth()){Text(value.ifBlank{"請選擇"})};DropdownMenu(expanded=open,onDismissRequest={open=false}){values.forEach{v->DropdownMenuItem(text={Text(v)},onClick={open=false;onChange(v)})}}}}}
@Composable internal fun Rating(value:Int?,onChange:(Int?)->Unit){Row(Modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically){(1..5).forEach{n->IconButton(onClick={onChange(n)}){Icon(if(n<=(value?:0))Icons.Outlined.Star else Icons.Outlined.StarBorder,"$n 顆星")}}};TextButton(onClick={onChange(null)}){Text(if(value==null)"未評分" else "清除評分（目前 $value / 5）")}}

@Composable private fun NewTrip(vm:TravelViewModel){
    var title by rememberSaveable{mutableStateOf("")};var start by rememberSaveable{mutableStateOf(LocalDate.now().toString())};var end by rememberSaveable{mutableStateOf(LocalDate.now().toString())};var currency by rememberSaveable{mutableStateOf("TWD")}
    Field("旅程名稱",title,{title=it});Field("開始日期 YYYY-MM-DD",start,{start=it});Field("結束日期 YYYY-MM-DD",end,{end=it});Field("結算幣別，例如 TWD、JPY、USD",currency,{currency=it.uppercase()});Muted("有支出後結算幣別固定，避免未經確認改變既有分帳。")
    MainButton("建立旅程"){vm.createTrip(title,start,end,currency)}
}
@Composable private fun RecordCard(r:RecordEntity,data:TravelState,vm:TravelViewModel){Panel{
    Muted("${r.occurredAt.replace('T',' ')} · ${r.zoneId}${if(r.draft)" · 草稿" else ""}")
    Text(r.title.ifBlank{if(r.draft)"未完成的紀錄" else "未命名紀錄"},style=MaterialTheme.typography.titleMedium)
    if(r.text.isNotBlank())Text(r.text,maxLines=3)
    if(r.place.isNotBlank())Muted(r.place)
    val attachments=data.attachments.filter{it.recordId==r.id&&!it.deleted&&it.state=="READY"}
    Muted("${attachments.count{it.kind=="PHOTO"}} 張照片 · ${attachments.count{it.kind=="AUDIO"}} 段錄音 · 已儲存在手機")
    TextButton(onClick={vm.openRecord(r.id)}){Text(if(r.draft)"繼續草稿 →" else "查看紀錄 →")}
}}

@Composable private fun TripScreen(vm:TravelViewModel,data:TravelState,trip:TripEntity){
    var tab by rememberSaveable(trip.id){mutableStateOf(0)};var date by rememberSaveable(trip.id){mutableStateOf(data.records.firstOrNull{it.tripId==trip.id}?.localDate?:trip.startDate)}
    Muted("${trip.startDate} — ${trip.endDate} · ${trip.currency} 結算")
    TextButton(onClick={vm.go("members")}){Text("成員、預設匯率與旅程設定 →")}
    TabRow(selectedTabIndex=tab){listOf("紀錄","地點","費用").forEachIndexed{i,t->Tab(selected=tab==i,onClick={tab=i},text={Text(t)})}}
    when(tab){
        0->{MainButton("＋ 新增旅程紀錄"){vm.newRecord(trip.id)}
            val dates=(data.records.filter{it.tripId==trip.id}.map{it.localDate}+listOf(trip.startDate)).distinct().sorted()
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){dates.forEach{d->FilterChip(selected=date==d,onClick={date=d},label={Text(d)})}}
            val rows=data.records.filter{it.tripId==trip.id&&it.localDate==date};if(rows.isEmpty())Muted("這一天還沒有紀錄。");rows.forEach{RecordCard(it,data,vm)}
        }
        1->{Info("第一階段先保存座標與手動地點名稱。附近搜尋與地圖底圖尚未開通。")
            data.records.filter{it.tripId==trip.id&&(it.place.isNotBlank()||it.latitude!=null)}.forEach{r->Panel{Title(r.place.ifBlank{"未命名地點"});Muted(if(r.latitude!=null)"${r.latitude}, ${r.longitude}" else "尚未填寫座標");Muted("${r.occurredAt} · ${r.locationSource?:"手動"}");TextButton(onClick={vm.openRecord(r.id)}){Text("查看紀錄")}}}
        }
        else->Costs(vm,data,trip)
    }
}

@Composable private fun Costs(vm:TravelViewModel,data:TravelState,trip:TripEntity){
    val expenses=data.expensesFor(trip.id);val total=expenses.fold(0L){a,e->Math.addExact(a,e.settledMinor)}
    Muted("整趟旅程支出 · ${trip.currency}");Text(Money.format(total,trip.currency),style=MaterialTheme.typography.headlineLarge)
    val members=data.membersFor(trip.id,vm.repo.actorId)
    Title("成員結算")
    val balances=runCatching{Money.balances(expenses.map{e->Money.LedgerExpense(e.settledMinor,e.payerId,data.shares.filter{it.expenseId==e.id}.associate{it.memberId to it.minor})})}.getOrNull()
    if(balances==null)Info("分帳資料仍在讀取，請稍候。") else {members.forEach{m->val b=balances[m.id]?:Money.Balance(0,0);Panel{Text(m.name,style=MaterialTheme.typography.titleMedium);Text("${if(b.net>=0)"應收" else "應付"} ${Money.format(kotlin.math.abs(b.net),trip.currency)} ${trip.currency}");Muted("代付 ${Money.format(b.paid,trip.currency)} · 應負擔 ${Money.format(b.burden,trip.currency)}")}};Muted("淨應收／應付合計 ${Money.format(balances.values.sumOf{it.net},trip.currency)} ${trip.currency} · 不含實際轉帳")}
    Title("分類統計");listOf("飲食","交通","住宿","購物","其他").forEach{c->Text("$c　${Money.format(expenses.filter{it.category==c}.sumOf{it.settledMinor},trip.currency)} ${trip.currency}")}
    Title("每日明細")
    expenses.groupBy{e->data.records.find{it.id==e.recordId}?.localDate?:""}.toSortedMap(reverseOrder()).forEach{(date,rows)->Title("$date · ${Money.format(rows.sumOf{it.settledMinor},trip.currency)} ${trip.currency}");rows.forEach{e->Panel{Text("${e.category} · ${e.label}");Text("${Money.format(e.originalMinor,e.originalCurrency)} ${e.originalCurrency} → ${Money.format(e.settledMinor,e.settlementCurrency)} ${e.settlementCurrency}");Muted("1 ${e.originalCurrency} = ${e.rate} ${e.settlementCurrency}");TextButton(onClick={vm.selectedId.value=e.recordId;vm.expenseId.value=e.id;vm.go("expense")}){Text("編輯這筆費用與匯率")}}}
    }
    if(expenses.isEmpty())Info("從旅程紀錄內新增費用，完成紀錄後會計入統計。")
}

@Composable private fun MemberScreen(vm:TravelViewModel,data:TravelState,trip:TripEntity){
    var name by rememberSaveable{mutableStateOf("")};var currency by rememberSaveable{mutableStateOf("JPY")};var rate by rememberSaveable{mutableStateOf("")}
    Info("本機成員不必註冊帳號。只有擁有者能管理成員；所有未來共同成員皆可編輯旅程內容。")
    data.membersFor(trip.id,vm.repo.actorId).forEach{m->Text("${m.name}　${if(m.id==trip.ownerId)"擁有者" else "本機成員"}")}
    Field("新增成員名稱",name,{name=it});MainButton("新增本機成員"){vm.work{withContext(Dispatchers.IO){vm.repo.addMember(trip.id,name)};name=""}}
    Title("新支出的預設匯率")
    Field("原始幣別，例如 JPY",currency,{currency=it.uppercase()});Field("1 $currency = 多少 ${trip.currency}",rate,{rate=it})
    MainButton("儲存預設，僅用於新支出"){vm.work{withContext(Dispatchers.IO){vm.repo.setRate(trip.id,currency,trip.currency,rate)};vm.notice.value="已保存新支出預設；既有支出與分帳沒有改變"}}
    data.rates.filter{it.scopeId==trip.id}.forEach{Muted("1 ${it.originalCurrency} = ${it.rate} ${it.settlementCurrency}")}
    Info("重算既有支出：請到費用頁逐筆編輯匯率，先確認新金額與分攤再保存。指定金額分攤必須重新對齊合計。")
    Muted("結算幣別：${trip.currency}。本機測試版不提供旅程刪除、擁有權移交與成員移除，避免破壞已記錄的分帳。")
}
