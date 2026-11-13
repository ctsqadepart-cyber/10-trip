package tw.shilu.travel

import android.app.Application
import androidx.room.Room
import tw.shilu.travel.data.*

class TravelApplication : Application() {
    lateinit var repository: TravelRepository
        private set
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("identity", MODE_PRIVATE)
        val actor = prefs.getString("actor", null) ?: newId().also { prefs.edit().putString("actor", it).commit() }
        val device = prefs.getString("device", null) ?: newId().also { prefs.edit().putString("device", it).commit() }
        val db = Room.databaseBuilder(this, TravelDatabase::class.java, "shilu.db").build()
        repository = TravelRepository(db, actor, device, filesDir)
    }
}
