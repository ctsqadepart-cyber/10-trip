package tw.shilu.travel.media

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tw.shilu.travel.data.*
import java.io.File
import java.io.IOException

/** Copies original bytes into app-private durable storage; never deletes source URIs. */
class MediaStore(private val context: Context, private val repo: TravelRepository) {
    private var recorder: MediaRecorder? = null
    private var recording: AttachmentEntity? = null
    private var player: MediaPlayer? = null
    var onRecordingInterrupted:(()->Unit)?=null
    private val maxFileBytes=100L*1024*1024
    suspend fun importPhoto(recordId: String, uri: Uri) = withContext(Dispatchers.IO) {
        val id=newId();val relative="media/$id.photo";val target=repo.file(relative)
        target.parentFile!!.mkdirs(); val temp=File(target.path+".part")
        try {
            require(target.parentFile!!.usableSpace>maxFileBytes+10*1024*1024) { "手機剩餘空間不足，請先清出至少 110 MB" }
            context.contentResolver.openInputStream(uri).use { input -> requireNotNull(input) { "無法讀取照片，請確認照片已下載到手機" }; temp.outputStream().use { out -> val buf=ByteArray(64*1024);var size=0L;while(true){val n=input.read(buf);if(n<0)break;size+=n;require(size<=maxFileBytes) { "單張照片上限為 100 MB" };out.write(buf,0,n)};out.fd.sync() } }
            validateImage(temp)
            check(temp.renameTo(target)) { "無法完成照片保存" }
            val order=repo.dao.recordAttachments(recordId).count { !it.deleted && it.kind=="PHOTO" }
            repo.attach(AttachmentEntity(id=id,recordId=recordId,kind="PHOTO",relativePath=relative,mime=context.contentResolver.getType(uri)?:"image/jpeg",position=order,sizeBytes=target.length(),sha256=repo.digest(target)))
        } catch(e: Exception){temp.delete();target.delete();throw e}
    }
    suspend fun prepareCamera(recordId: String): AttachmentEntity = withContext(Dispatchers.IO) {
        val id=newId(); val relative="media/$id.jpg"; val file=repo.file(relative);file.parentFile!!.mkdirs()
        require(file.parentFile!!.usableSpace>20*1024*1024) { "手機剩餘空間不足" };file.createNewFile()
        AttachmentEntity(id=id,recordId=recordId,kind="PHOTO",relativePath=relative,mime="image/jpeg",position=repo.dao.recordAttachments(recordId).count{!it.deleted&&it.kind=="PHOTO"},state="CAPTURING").also { repo.attach(it) }
    }
    suspend fun finishCamera(id: String, success: Boolean) = withContext(Dispatchers.IO) {
        val row=repo.dao.attachment(id)?:return@withContext
        val file=repo.file(row.relativePath)
        if(!success){repo.updateAttachment(id,deleted=true);return@withContext}
        try {validateImage(file);repo.attach(row.copy(state="READY",sizeBytes=file.length(),sha256=repo.digest(file),revision=row.revision+1))}
        catch(e: Exception){repo.attach(row.copy(state="FAILED",revision=row.revision+1));throw e}
    }
    @Suppress("DEPRECATION")
    suspend fun startAudio(recordId: String, canStart:()->Boolean = {true}) {
        check(recorder==null) { "已有一段錄音正在進行" }
        val id=newId();val relative="media/$id.m4a";val file=repo.file(relative);file.parentFile!!.mkdirs()
        require(file.parentFile!!.usableSpace>20*1024*1024) { "手機剩餘空間不足" }
        val row=AttachmentEntity(id=id,recordId=recordId,kind="AUDIO",relativePath=relative,mime="audio/mp4",name="語音筆記 ${java.time.LocalTime.now().withNano(0)}",position=repo.dao.recordAttachments(recordId).count{!it.deleted&&it.kind=="AUDIO"},state="RECORDING")
        withContext(Dispatchers.IO) {repo.attach(row)}
        val r=if(Build.VERSION.SDK_INT>=31) MediaRecorder(context) else MediaRecorder()
        try {check(canStart()){ "已離開錄音畫面，本次未開始錄音" };r.setAudioSource(MediaRecorder.AudioSource.MIC);r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);r.setAudioEncodingBitRate(96000);r.setAudioSamplingRate(44100);r.setMaxFileSize(maxFileBytes);r.setOnErrorListener{_,_,_->onRecordingInterrupted?.invoke()};r.setOnInfoListener{_,what,_->if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED)onRecordingInterrupted?.invoke()};r.setOutputFile(file.absolutePath);r.prepare();r.start();recorder=r;recording=row}
        catch(e: Exception){runCatching{r.release()};withContext(Dispatchers.IO){repo.attach(row.copy(state="FAILED"))};throw e}
    }
    suspend fun stopAudio(): Boolean {
        val ended=endCapture()?:return false
        return persistEndedAudio(ended)
    }
    // Release microphone synchronously on lifecycle interruption, even if a database/photo job is queued.
    fun endCapture():Pair<AttachmentEntity,Boolean>? {
        val row=recording?:return null;val r=recorder;recording=null;recorder=null
        val stopped=runCatching{r?.stop()}.isSuccess;runCatching{r?.release()}
        return row to stopped
    }
    suspend fun persistEndedAudio(ended:Pair<AttachmentEntity,Boolean>):Boolean=withContext(Dispatchers.IO){finalizeAudio(ended.first,ended.second)}
    private suspend fun finalizeAudio(row: AttachmentEntity, stopped: Boolean): Boolean {
        val file=repo.file(row.relativePath)
        val duration=runCatching {val m=MediaMetadataRetriever();try{m.setDataSource(file.absolutePath);m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:0}finally{m.release()}}.getOrDefault(0)
        val valid=file.exists()&&file.length()>0&&duration>0
        repo.attach(row.copy(state=if(valid) "READY" else "FAILED",durationMs=duration,sizeBytes=file.length(),sha256=if(valid)repo.digest(file) else "",revision=row.revision+1))
        return valid && stopped
    }
    suspend fun recoverInterrupted(): Int = withContext(Dispatchers.IO) {
        var count=0
        repo.dao.interruptedAudio().forEach { row -> runCatching{finalizeAudio(row,false)};count++ }
        count
    }
    fun isRecording()=recorder!=null
    fun stopPlayback(){runCatching{player?.stop()};runCatching{player?.release()};player=null}
    fun play(row: AttachmentEntity,onComplete:()->Unit){stopPlayback();val p=MediaPlayer();try{p.setDataSource(repo.file(row.relativePath).absolutePath);p.prepare();p.setOnCompletionListener{stopPlayback();onComplete()};p.setOnErrorListener{_,_,_->stopPlayback();onComplete();true};p.start();player=p}catch(e:Exception){p.release();throw e}}
    private fun validateImage(file: File){val options=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,options);require(options.outWidth>0&&options.outHeight>0) { "照片無法讀取或格式不受此手機支援" }}
}
