package tw.shilu.travel

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import tw.shilu.travel.data.now
import tw.shilu.travel.ui.TravelApp

class MainActivity: ComponentActivity() {
    private val vm:TravelViewModel by viewModels()
    private var locationListener:LocationListener?=null
    private val handler=Handler(Looper.getMainLooper())
    private var requestedRecord:String?=null
    private val capturePrefs by lazy {getSharedPreferences("pending_camera",MODE_PRIVATE)}
    private val photoPicker=registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){vm.photos(it)}
    private val camera=registerForActivityResult(ActivityResultContracts.TakePicture()){success->
        val id=capturePrefs.getString("id",null);capturePrefs.edit().clear().commit()
        if(id!=null)vm.work{vm.media.finishCamera(id,success);vm.notice.value=if(success)"照片已儲存在手機" else "已取消拍照，其他內容仍保留"}
    }
    private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){result->
        when(pendingAction){"camera"->if(result[Manifest.permission.CAMERA]==true)takePhoto()else vm.notice.value="相機未授權，仍可選取本機照片或保存其他內容";"audio"->if(result[Manifest.permission.RECORD_AUDIO]==true)vm.startAudio()else vm.notice.value="麥克風未授權，仍可保存其他內容";"location"->if(result.values.any{it})locate()else vm.notice.value="定位未授權，可手動填寫地點，或稍後補填"};pendingAction=null
    }
    private var pendingAction:String?=null
    private val screenOff=object:BroadcastReceiver(){override fun onReceive(context:Context?,intent:Intent?){vm.background()}}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);pendingAction=savedInstanceState?.getString("permission_action");requestedRecord=savedInstanceState?.getString("location_record");ContextCompat.registerReceiver(this,screenOff,IntentFilter(Intent.ACTION_SCREEN_OFF),ContextCompat.RECEIVER_NOT_EXPORTED);setContent{TravelApp(vm,::pickPhotos,::requestCamera,::requestAudio,::requestLocation)}}
    override fun onSaveInstanceState(outState:Bundle){outState.putString("permission_action",pendingAction);outState.putString("location_record",requestedRecord);super.onSaveInstanceState(outState)}
    private fun pickPhotos(){runCatching{photoPicker.launch(arrayOf("image/*"))}.onFailure{vm.notice.value="找不到照片選取器，其他功能仍可使用"}}
    private fun allowed(p:String)=ContextCompat.checkSelfPermission(this,p)==PackageManager.PERMISSION_GRANTED
    private fun requestCamera(){if(allowed(Manifest.permission.CAMERA))takePhoto()else{pendingAction="camera";permissions.launch(arrayOf(Manifest.permission.CAMERA))}}
    private fun takePhoto(){val record=vm.selectedId.value?:return;vm.work{val row=vm.media.prepareCamera(record);capturePrefs.edit().putString("id",row.id).commit();val uri=FileProvider.getUriForFile(this,"$packageName.files",vm.repo.file(row.relativePath));runCatching{camera.launch(uri)}.onFailure{vm.media.finishCamera(row.id,false);vm.notice.value="找不到相機，仍可選取手機照片"}}}
    private fun requestAudio(){if(allowed(Manifest.permission.RECORD_AUDIO))vm.startAudio()else{pendingAction="audio";permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO))}}
    private fun requestLocation(){requestedRecord=vm.selectedId.value;if(allowed(Manifest.permission.ACCESS_COARSE_LOCATION)||allowed(Manifest.permission.ACCESS_FINE_LOCATION))locate()else{pendingAction="location";permissions.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))}}
    @Suppress("MissingPermission")
    private fun locate(){
        cancelLocation();val id=requestedRecord?:return;val manager=getSystemService(LOCATION_SERVICE) as LocationManager
        val fine=allowed(Manifest.permission.ACCESS_FINE_LOCATION)
        val provider=if(fine&&manager.isProviderEnabled(LocationManager.GPS_PROVIDER))LocationManager.GPS_PROVIDER else if(manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))LocationManager.NETWORK_PROVIDER else null
        if(provider==null){vm.notice.value="定位未開啟或不可用，仍可保存其他內容";return}
        val listener=object:LocationListener{override fun onLocationChanged(location:Location){cancelLocation();if(vm.editor.value?.id==id)vm.edit{it.copy(latitude=location.latitude,longitude=location.longitude,accuracy=location.accuracy,locationCapturedAt=now(),locationSource="DEVICE")};vm.notice.value="已取得本次位置，精確度約 ${location.accuracy.toInt()} 公尺"}}
        locationListener=listener
        runCatching{manager.requestSingleUpdate(provider,listener,Looper.getMainLooper());vm.notice.value="正在取得一次位置，最多等候 15 秒";handler.postDelayed({if(locationListener===listener){cancelLocation();vm.notice.value="定位逾時，其他內容可保存；稍後可重試或手動填寫"}},15000)}.onFailure{cancelLocation();vm.notice.value="無法定位，仍可保存其他內容"}
    }
    private fun cancelLocation(){locationListener?.let{runCatching{(getSystemService(LOCATION_SERVICE)as LocationManager).removeUpdates(it)}};locationListener=null;handler.removeCallbacksAndMessages(null)}
    override fun onStart(){super.onStart();vm.foreground=true}
    override fun onResume(){super.onResume();vm.foreground=true}
    override fun onWindowFocusChanged(hasFocus:Boolean){super.onWindowFocusChanged(hasFocus);if(!hasFocus)vm.stopAudio("因來電、系統視窗或切換操作停止錄音")}
    override fun onStop(){vm.background();cancelLocation();super.onStop()}
    override fun onDestroy(){runCatching{unregisterReceiver(screenOff)};cancelLocation();super.onDestroy()}
}
