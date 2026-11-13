package tw.shilu.travel.ui

import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tw.shilu.domain.Money
import tw.shilu.travel.TravelViewModel
import tw.shilu.travel.data.*
import java.math.BigDecimal

@Composable internal fun RecordEditor(vm:TravelViewModel,data:TravelState,pickPhotos:()->Unit,camera:()->Unit,audio:()->Unit,locate:()->Unit){
    val r by vm.editor.collectAsStateWithLifecycle();val record=r?:return
    val pending by vm.pending.collectAsStateWithLifecycle();val recording by vm.recording.collectAsStateWithLifecycle()
    val failed by vm.editorWriteFailed.collectAsStateWithLifecycle()
    var elapsed by remember{mutableStateOf(0L)}
    LaunchedEffect(recording){while(recording){elapsed=(android.os.SystemClock.elapsedRealtime()-vm.recordingStartedAt)/1000;kotlinx.coroutines.delay(500)}}
    var time by rememberSaveable(record.id){mutableStateOf(record.occurredAt)};var zone by rememberSaveable(record.id){mutableStateOf(record.zoneId)}
    var lat by rememberSaveable(record.id){mutableStateOf(record.latitude?.toString()?:"")};var lon by rememberSaveable(record.id){mutableStateOf(record.longitude?.toString()?:"")}
    var mealOpen by rememberSaveable(record.id){mutableStateOf(record.store.isNotBlank())}
    Info(if(pending>0)"正在保存修改到手機，請稍候。" else if(failed)"部分修改尚未保存，請修正上方錯誤並重試。" else if(record.draft)"草稿已保存在手機。完成紀錄後才納入旅程與費用統計。" else "修改會自動保存在手機。完成後返回紀錄內容。")
    Field("標題（選填）",record.title,{s->vm.edit{it.copy(title=s)}})
    Field("文字（選填）",record.text,{s->vm.edit{it.copy(text=s)}},singleLine=false,modifier=Modifier.heightIn(min=140.dp))
    Field("時間 YYYY-MM-DDTHH:MM",time,{time=it});Field("時區，例如 Asia/Taipei、Asia/Tokyo",zone,{zone=it})
    TextButton(onClick={vm.edit{it.copy(occurredAt=time,zoneId=zone)}}){Text("套用時間與時區")}
    Title("地點（選填）");Field("地點名稱，可手動修正",record.place,{s->vm.edit{it.copy(place=s)}})
    OutlinedButton(onClick=locate,modifier=Modifier.fillMaxWidth()){Text("取得一次目前位置")}
    if(record.latitude!=null){Muted("已保存：${record.latitude}, ${record.longitude}");Muted("${record.locationSource?:"手動"} · 精確度 ${record.accuracy?.toInt()?.toString()?:"未知"} 公尺")}
    Field("手動緯度 −90～90",lat,{lat=it});Field("手動經度 −180～180",lon,{lon=it})
    TextButton(onClick={runCatching{require(lat.isBlank()==lon.isBlank()){ "請成對填寫經緯度" };val a=lat.takeIf{it.isNotBlank()}?.toDouble();val b=lon.takeIf{it.isNotBlank()}?.toDouble();require(a==null||(a.isFinite()&&a in -90.0..90.0&&b!!.isFinite()&&b in -180.0..180.0)){"經緯度超出有效範圍"};vm.edit{it.copy(latitude=a,longitude=b,accuracy=null,locationCapturedAt=if(a==null)null else now(),locationSource=if(a==null)null else "MANUAL")}}.onFailure{vm.notice.value=it.message?:"座標格式錯誤"}}){Text("套用手動座標（清空可移除）")}
    Title("照片與錄音")
    OutlinedButton(onClick=camera,modifier=Modifier.fillMaxWidth()){Text("即時拍照")};OutlinedButton(onClick=pickPhotos,modifier=Modifier.fillMaxWidth()){Text("選取手機照片（可多選）")}
    Button(onClick={if(recording)vm.stopAudio()else audio()},modifier=Modifier.fillMaxWidth()){Text(if(recording)"● 錄音中・停止並保存" else "開始錄音")}
    if(recording)Text("錄音中 ${(elapsed/60).toString().padStart(2,'0')}:${(elapsed%60).toString().padStart(2,'0')}")
    Muted("鎖屏或離開 App 會停止錄音。照片匯入後使用 App 副本，不影響相簿原圖。")
    Attachments(vm,data,record.id,true)
    TextButton(onClick={mealOpen=!mealOpen}){Text(if(mealOpen)"收起飲食與餐點" else "＋ 飲食與餐點（選填）")}
    if(mealOpen){Field("店名",record.store,{s->vm.edit{it.copy(store=s)}});Muted("整餐評分（可不評分）");Rating(record.rating){s->vm.edit{it.copy(rating=s)}};Field("整餐心得",record.mealNotes,{s->vm.edit{it.copy(mealNotes=s)}},singleLine=false);Dishes(vm,data,record,true)}
    Title("相關費用")
    data.expenses.filter{it.recordId==record.id}.forEach{e->Text("${e.label} · ${Money.format(e.originalMinor,e.originalCurrency)} ${e.originalCurrency}");TextButton(onClick={vm.expenseId.value=e.id;vm.go("expense")}){Text("編輯金額與分帳")}}
    OutlinedButton(onClick={vm.expenseId.value=null;vm.go("expense")},modifier=Modifier.fillMaxWidth()){Text("＋ 新增費用")}
    if(record.mealExpenseId==null&&record.store.isNotBlank())TextButton(onClick={vm.expenseId.value=null;vm.go("expense")}){Text("記錄整餐實付費用（不自動加總餐點）")}
    MainButton("完成並保存紀錄",pending==0&&!recording){runCatching{java.time.LocalDateTime.parse(time);java.time.ZoneId.of(zone);vm.edit{it.copy(occurredAt=time,zoneId=zone)};vm.finishRecord()}.onFailure{vm.notice.value="時間或時區格式錯誤，請修正後保存"}}
}

@Composable internal fun RecordDetail(vm:TravelViewModel,data:TravelState,r:RecordEntity){
    var confirmDelete by remember{mutableStateOf(false)}
    Title(r.title.ifBlank{"未命名紀錄"});Muted("${r.occurredAt.replace('T',' ')} · ${r.zoneId}")
    Info("已儲存在手機・尚未開通同步${if(r.tripId==null)"・私人日常" else ""}")
    MainButton("編輯紀錄"){vm.openRecord(r.id,true)}
    if(r.text.isNotBlank())Text(r.text)
    if(r.place.isNotBlank())Text("地點：${r.place}")
    if(r.latitude!=null)Muted("座標 ${r.latitude}, ${r.longitude} · ${r.locationSource?:"手動"}")
    Attachments(vm,data,r.id,false)
    if(r.store.isNotBlank()||r.mealNotes.isNotBlank()||r.rating!=null){Title("飲食紀錄");Text(r.store);Muted(if(r.rating==null)"整餐未評分" else "整餐 ${r.rating} / 5 星");Text(r.mealNotes)}
    Dishes(vm,data,r,false)
    val expenses=data.expenses.filter{it.recordId==r.id};if(expenses.isNotEmpty()){Title("關聯費用");expenses.forEach{e->Panel{Text("${e.category} · ${e.label}${if(r.mealExpenseId==e.id)"（整餐費用）" else ""}");Text("${Money.format(e.originalMinor,e.originalCurrency)} ${e.originalCurrency} → ${Money.format(e.settledMinor,e.settlementCurrency)} ${e.settlementCurrency}");Muted("1 ${e.originalCurrency} = ${e.rate} ${e.settlementCurrency}");TextButton(onClick={vm.expenseId.value=e.id;vm.go("expense")}){Text("查看與編輯分帳")}}}}
    if(r.tripId==null)MainButton("加入指定旅程"){vm.go("share")}
    TextButton(onClick={confirmDelete=true}){Text("刪除紀錄",color=MaterialTheme.colorScheme.error)}
    if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},title={Text("刪除這筆紀錄？")},text={Text("此紀錄、${data.attachments.count{it.recordId==r.id}} 個附件與 ${expenses.size} 筆關聯支出將從紀錄及統計移除。相簿原圖不會刪除；App 保留刪除標記與版本供未來衝突處理。")},confirmButton={TextButton(onClick={confirmDelete=false;vm.deleteRecord(r.id)}){Text("確認刪除")}},dismissButton={TextButton(onClick={confirmDelete=false}){Text("取消")}})
}

@Composable private fun LocalPhoto(vm:TravelViewModel,row:AttachmentEntity){
    val bitmap by produceState<android.graphics.Bitmap?>(null,row.id,row.sha256){value=withContext(Dispatchers.IO){runCatching{val f=vm.repo.file(row.relativePath);val b=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(f.path,b);var sample=1;while(b.outWidth/sample>1280||b.outHeight/sample>1280)sample*=2;val decoded=BitmapFactory.decodeFile(f.path,BitmapFactory.Options().apply{inSampleSize=sample})?:return@runCatching null;val orientation=runCatching{ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)}.getOrDefault(1);val matrix=Matrix();when(orientation){2->matrix.setScale(-1f,1f);3->matrix.setRotate(180f);4->matrix.setScale(1f,-1f);5->{matrix.setRotate(90f);matrix.postScale(-1f,1f)};6->matrix.setRotate(90f);7->{matrix.setRotate(270f);matrix.postScale(-1f,1f)};8->matrix.setRotate(270f)};if(matrix.isIdentity)decoded else android.graphics.Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true)}.getOrNull()}}
    if(bitmap!=null)Image(bitmap!!.asImageBitmap(),contentDescription=row.name.ifBlank{"紀錄照片"},modifier=Modifier.fillMaxWidth().heightIn(max=300.dp),contentScale=ContentScale.Fit)else Muted("照片讀取中，或檔案已損壞／不受支援。")
}

@Composable private fun Attachments(vm:TravelViewModel,data:TravelState,recordId:String,editable:Boolean){
    val playing by vm.playingId.collectAsStateWithLifecycle()
    var removeId by remember{mutableStateOf<String?>(null)}
    val rows=data.attachments.filter{it.recordId==recordId}
    if(rows.isNotEmpty())Title("照片與錄音附件")
    rows.forEach{row->key(row.id){Panel{
        if(row.kind=="PHOTO"&&row.state=="READY")LocalPhoto(vm,row)
        if(row.kind=="AUDIO")Muted("錄音 ${((row.durationMs?:0)/1000)/60}:${(((row.durationMs?:0)/1000)%60).toString().padStart(2,'0')}")
        if(row.state!="READY")Info(when(row.state){"RECORDING"->"錄音尚未完成，若曾被中斷請重新啟動檢查";"CAPTURING"->"拍照尚未完成，請重試或移除此項";else->"附件保存失敗，其他內容仍保留。可刪除此項後重試。"})
        var name by rememberSaveable(row.id){mutableStateOf(row.name)}
        if(editable){Field(if(row.kind=="PHOTO")"照片說明" else "錄音名稱",name,{name=it});TextButton(onClick={vm.work{withContext(Dispatchers.IO){vm.repo.updateAttachment(row.id,name)}}}){Text("保存${if(row.kind=="PHOTO")"說明" else "名稱"}")}}else if(row.name.isNotBlank())Text(row.name)
        if(row.kind=="AUDIO"&&row.state=="READY")OutlinedButton(onClick={vm.play(row)}){Text(if(playing==row.id)"停止播放" else "播放錄音")}
        if(editable){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){if(row.kind=="PHOTO"){TextButton(onClick={vm.work{withContext(Dispatchers.IO){vm.repo.reorderAttachment(row.id,-1)}}}){Text("上移")};TextButton(onClick={vm.work{withContext(Dispatchers.IO){vm.repo.reorderAttachment(row.id,1)}}}){Text("下移")}};TextButton(onClick={removeId=row.id}){Text("移除",color=MaterialTheme.colorScheme.error)}}}
    }}}
    if(removeId!=null)AlertDialog(onDismissRequest={removeId=null},title={Text("移除此附件？")},text={Text("只移除此紀錄內的附件，不會刪除手機相簿原圖。")},confirmButton={TextButton(onClick={val id=removeId!!;removeId=null;vm.media.stopPlayback();vm.playingId.value=null;vm.work{withContext(Dispatchers.IO){vm.repo.updateAttachment(id,deleted=true)}}}){Text("確認移除")}},dismissButton={TextButton(onClick={removeId=null}){Text("取消")}})
}

@Composable private fun Dishes(vm:TravelViewModel,data:TravelState,r:RecordEntity,editable:Boolean){
    var edit by remember{mutableStateOf<DishEntity?>(null)};var adding by remember{mutableStateOf(false)}
    val dishes=data.dishes.filter{it.recordId==r.id}
    dishes.forEach{d->Panel{Text("${d.name} × ${d.quantity}");Muted("單價 ${Money.format(d.unitMinor,d.currency)} ${d.currency} · ${d.rating?.let{"$it / 5 星"}?:"未評分"}");if(d.notes.isNotBlank())Text(d.notes);if(editable)Row{TextButton(onClick={edit=d;adding=true}){Text("編輯餐點")};TextButton(onClick={vm.work{withContext(Dispatchers.IO){vm.repo.saveDish(d.copy(deleted=true))}}}){Text("刪除餐點")}}}}
    if(editable)OutlinedButton(onClick={edit=null;adding=true},modifier=Modifier.fillMaxWidth()){Text("＋ 新增餐點")}
    if(adding){val original=edit;var name by rememberSaveable(original?.id){mutableStateOf(original?.name?:"")};var quantity by rememberSaveable(original?.id){mutableStateOf(original?.quantity?:"1")};var amount by rememberSaveable(original?.id){mutableStateOf(original?.let{Money.format(it.unitMinor,it.currency)}?:"")};var currency by rememberSaveable(original?.id){mutableStateOf(original?.currency?:data.settlement(r.tripId))};var rating by rememberSaveable(original?.id){mutableStateOf(original?.rating)};var notes by rememberSaveable(original?.id){mutableStateOf(original?.notes?:"")}
        AlertDialog(onDismissRequest={adding=false},title={Text("餐點明細")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Field("餐點名稱",name,{name=it});Field("數量",quantity,{quantity=it});Field("單價",amount,{amount=it});Field("幣別",currency,{currency=it.uppercase()});Rating(rating){rating=it};Field("餐點心得",notes,{notes=it})}},confirmButton={TextButton(onClick={vm.work{val minor=Money.parse(amount,currency);require(BigDecimal(quantity)>BigDecimal.ZERO);withContext(Dispatchers.IO){vm.repo.saveDish(DishEntity(id=original?.id?:newId(),recordId=r.id,name=name,quantity=quantity,unitMinor=minor,currency=currency,rating=rating,notes=notes,revision=original?.revision?:0))};adding=false}}){Text("保存餐點")}},dismissButton={TextButton(onClick={adding=false}){Text("取消")}})
    }
}
