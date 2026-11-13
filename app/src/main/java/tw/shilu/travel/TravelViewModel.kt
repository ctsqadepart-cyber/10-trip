package tw.shilu.travel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import tw.shilu.travel.data.*
import tw.shilu.travel.media.MediaStore

class TravelViewModel(application: Application): AndroidViewModel(application) {
    val repo=(application as TravelApplication).repository
    val media=MediaStore(application,repo)
    val state=repo.state.stateIn(viewModelScope,SharingStarted.Eagerly,TravelState())
    val screen=MutableStateFlow("home")
    val tripId=MutableStateFlow<String?>(null)
    val selectedId=MutableStateFlow<String?>(null)
    val expenseId=MutableStateFlow<String?>(null)
    val editor=MutableStateFlow<RecordEntity?>(null)
    val notice=MutableStateFlow("")
    val pending=MutableStateFlow(0)
    val recording=MutableStateFlow(false)
    val playingId=MutableStateFlow<String?>(null)
    val editorWriteFailed=MutableStateFlow(false)
    var foreground=false
    var recordingStartedAt=0L
    private var navigationGeneration=0
    private val queue=Channel<suspend ()->Unit>(Channel.UNLIMITED)
    init {
        media.onRecordingInterrupted={stopAudio("錄音被系統中斷或已達檔案大小上限")}
        viewModelScope.launch {for(task in queue){try{task()}catch(e:Exception){notice.value=e.message?:"操作失敗，請重試"}finally{pending.update{it-1}}}}
        work {withContext(Dispatchers.IO){repo.initialize()};val recovered=media.recoverInterrupted();if(recovered>0)notice.value="已檢查 $recovered 段中斷錄音；可播放的部分已保留，失敗項目請查看附件狀態。"}
    }
    fun work(task: suspend ()->Unit){pending.update{it+1};queue.trySend(task)}
    fun go(next: String){navigationGeneration++;if(recording.value)stopAudio("因切換畫面停止錄音");media.stopPlayback();playingId.value=null;screen.value=next}
    fun newRecord(trip: String?)=work {val r=withContext(Dispatchers.IO){repo.newRecord(trip)};selectedId.value=r.id;editor.value=r;editorWriteFailed.value=false;tripId.value=trip;go("editor")}
    fun openRecord(id:String,edit:Boolean=false)=work {val row=withContext(Dispatchers.IO){repo.dao.record(id)}?:return@work;selectedId.value=id;tripId.value=row.tripId;editor.value=row;editorWriteFailed.value=false;go(if(edit||row.draft)"editor" else "detail")}
    fun edit(transform:(RecordEntity)->RecordEntity){val next=editor.value?.let(transform)?:return;editor.value=next;work {try{withContext(Dispatchers.IO){repo.saveEditor(next)};editorWriteFailed.value=false}catch(e:Exception){editorWriteFailed.value=true;throw e}}}
    fun finishRecord() {val next=editor.value?:return;work{val saved=withContext(Dispatchers.IO){repo.saveEditor(next,true)};editor.value=saved;go("detail");notice.value="已儲存在手機・尚未開通雲端同步"}}
    fun createTrip(title:String,start:String,end:String,currency:String)=work {val id=withContext(Dispatchers.IO){repo.createTrip(title,start,end,currency)};tripId.value=id;go("trip")}
    fun deleteRecord(id:String)=work {withContext(Dispatchers.IO){repo.deleteRecord(id)};go(if(tripId.value==null)"diary" else "trip");notice.value="紀錄已刪除，手機相簿原圖未刪除。"}
    fun photos(uris:List<Uri>){val id=selectedId.value?:return;work{var failed=0;uris.forEachIndexed{i,uri->notice.value="正在保存照片 ${i+1}/${uris.size}";try{media.importPhoto(id,uri)}catch(e:Exception){failed++;notice.value=e.message?:"照片匯入失敗"}};if(failed==0)notice.value="照片已複製並儲存在手機" else notice.value="$failed 張照片匯入失敗，請重選；成功項目已保留。"}}
    fun startAudio(){val id=selectedId.value?:return;val generation=navigationGeneration;work{if(!foreground||generation!=navigationGeneration)return@work;media.startAudio(id){foreground&&generation==navigationGeneration};if(!foreground||generation!=navigationGeneration){media.stopAudio();return@work};recordingStartedAt=android.os.SystemClock.elapsedRealtime();recording.value=true;notice.value="錄音中・鎖屏或離開 App 會停止並保存"}}
    fun stopAudio(reason:String="已停止錄音"){if(!recording.value&&!media.isRecording())return;recording.value=false;val ended=media.endCapture()?:return;work{val valid=media.persistEndedAudio(ended);notice.value=if(valid)"$reason，已儲存在手機" else "$reason。錄音可能太短或被中斷，請檢查附件狀態。"}}
    fun play(row:AttachmentEntity){runCatching{if(playingId.value==row.id){media.stopPlayback();playingId.value=null}else{media.play(row){playingId.value=null};playingId.value=row.id}}.onFailure{notice.value="錄音無法播放：${it.message}"}}
    fun background(){foreground=false;navigationGeneration++;stopAudio("因鎖屏或離開 App 停止錄音");media.stopPlayback();playingId.value=null}
    override fun onCleared(){media.endCapture();media.stopPlayback();super.onCleared()}
}
