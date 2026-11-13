package tw.shilu.travel

import android.app.Application
import android.net.Uri
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tw.shilu.domain.Money
import tw.shilu.travel.data.*
import tw.shilu.travel.media.MediaStore
import java.io.File
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class RepositoryTest {
    private lateinit var db:TravelDatabase
    private lateinit var repo:TravelRepository
    private lateinit var context:Application
    private lateinit var databaseName:String
    @Before fun setup()=runBlocking(Dispatchers.IO){context=RuntimeEnvironment.getApplication();databaseName="test-${newId()}.db";open();repo.initialize()}
    private fun open(){db=Room.databaseBuilder(context,TravelDatabase::class.java,databaseName).build();repo=TravelRepository(db,"actor","device",context.filesDir)}
    @After fun close(){db.close();context.deleteDatabase(databaseName)}
    private suspend fun record(trip:String?=null)=repo.newRecord(trip).let{repo.saveEditor(it.copy(title="離線紀錄",text="重啟後仍保留"),true)}
    private suspend fun expense(r:RecordEntity,amount:Long=1000,currency:String="JPY",rate:String="0.215",participants:List<String> = listOf("actor")):ExpenseEntity {
        val settlement=r.tripId?.let{repo.dao.trip(it)?.currency}?:"TWD"
        val converted=Money.convert(amount,currency,settlement,rate)
        val e=ExpenseEntity(recordId=r.id,label="午餐",category="飲食",originalMinor=amount,originalCurrency=currency,rate=rate,settlementCurrency=settlement,settledMinor=converted,payerId="actor")
        repo.saveExpense(e,Money.equal(converted,participants),true);return e
    }
    @Test fun closeAndReopenRetainsRecordExpenseSharesAndOutbox()=runBlocking(Dispatchers.IO){
        val trip=repo.createTrip("京都","2026-11-07","2026-11-11","TWD");repo.addMember(trip,"小安")
        val r=record(trip);val ids=repo.dao.tripMembers(trip).map{it.memberId};val e=expense(r,participants=ids)
        db.close();open()
        assertEquals("離線紀錄",repo.dao.record(r.id)!!.title);assertEquals(21500L,repo.dao.expense(e.id)!!.settledMinor)
        assertEquals(21500L,repo.dao.expenseShares(e.id).sumOf{it.minor})
        val op=repo.dao.outbox().first().single{it.aggregateId==r.id};assertTrue(op.payload.contains(e.id));assertTrue(op.payload.contains("shares"));assertEquals("LOCAL_ONLY",op.status)
    }
    @Test fun diaryMoveRetainsIdentityAndIsAtomicOnInvalidRate()=runBlocking(Dispatchers.IO){
        val r=record();val e=expense(r);val trip=repo.createTrip("共同旅程","2026-11-07","2026-11-11","JPY")
        repo.moveDiary(r.id,trip,mapOf("actor" to "actor"),emptyMap())
        assertEquals(trip,repo.dao.record(r.id)!!.tripId);assertEquals(e.id,repo.dao.recordExpenses(r.id).single().id);assertEquals(1000L,repo.dao.expense(e.id)!!.settledMinor)
        assertTrue(runCatching{repo.moveDiary(r.id,trip,mapOf("actor" to "actor"),emptyMap())}.isFailure)
        assertEquals(1,repo.dao.recordExpenses(r.id).size)
        val other=record();val otherExpense=expense(other);val usd=repo.createTrip("美元旅程","2026-11-07","2026-11-11","USD")
        assertTrue(runCatching{repo.moveDiary(other.id,usd,mapOf("actor" to "actor"),mapOf("JPY" to "bad"))}.isFailure)
        assertNull(repo.dao.record(other.id)!!.tripId);assertEquals(21500L,repo.dao.expense(otherExpense.id)!!.settledMinor)
    }
    @Test fun ratePresetDoesNotRewriteExistingExpenseAndDeletionKeepsTombstone()=runBlocking(Dispatchers.IO){
        val trip=repo.createTrip("旅程","2026-11-07","2026-11-11","TWD");val r=record(trip);val e=expense(r)
        repo.setRate(trip,"JPY","TWD","0.3");assertEquals("0.215",repo.dao.expense(e.id)!!.rate);assertEquals(21500L,repo.dao.expense(e.id)!!.settledMinor)
        repo.deleteRecord(r.id);assertTrue(repo.dao.record(r.id)!!.deleted);assertTrue(repo.dao.records().first().none{it.id==r.id});assertTrue(repo.dao.outbox().first().single{it.aggregateId==r.id}.payload.contains("\"deleted\":true"))
    }
    @Test fun importedPhotoSurvivesSourceDeletionAndDatabaseReopen()=runBlocking(Dispatchers.IO){
        val r=record();val source=File(context.cacheDir,"source.png")
        source.writeBytes(Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aWQ0AAAAASUVORK5CYII="))
        val original=source.readBytes();MediaStore(context,repo).importPhoto(r.id,Uri.fromFile(source));source.delete()
        val row=repo.dao.recordAttachments(r.id).single();db.close();open()
        val restored=repo.dao.recordAttachments(r.id).single();assertEquals(row.id,restored.id);assertArrayEquals(original,repo.file(restored.relativePath).readBytes());assertEquals(restored.sha256,repo.digest(repo.file(restored.relativePath)))
        repo.updateAttachment(restored.id,deleted=true);assertTrue(repo.dao.recordAttachments(r.id).single().deleted);assertTrue(repo.file(restored.relativePath).exists())
    }
    @Test fun rejectForeignMembersAndExpenseMismatchWithoutPartialWrites()=runBlocking(Dispatchers.IO){
        val r=record();val e=ExpenseEntity(recordId=r.id,label="錯誤",originalMinor=100,originalCurrency="TWD",rate="1",settlementCurrency="TWD",settledMinor=100,payerId="intruder")
        assertTrue(runCatching{repo.saveExpense(e,mapOf("actor" to 100L),false)}.isFailure);assertNull(repo.dao.expense(e.id))
        assertTrue(runCatching{repo.saveExpense(e.copy(payerId="actor"),mapOf("actor" to 99L),false)}.isFailure);assertNull(repo.dao.expense(e.id))
        assertTrue(runCatching{repo.file("../elsewhere")}.isFailure)
    }
}
