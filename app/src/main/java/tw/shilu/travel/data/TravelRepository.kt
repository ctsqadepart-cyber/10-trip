package tw.shilu.travel.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import tw.shilu.domain.Money
import java.io.File
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class TravelState(val trips: List<TripEntity> = emptyList(), val records: List<RecordEntity> = emptyList(),
    val attachments: List<AttachmentEntity> = emptyList(), val members: List<MemberEntity> = emptyList(),
    val memberships: List<TripMemberEntity> = emptyList(), val dishes: List<DishEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(), val shares: List<ShareEntity> = emptyList(),
    val rates: List<RatePresetEntity> = emptyList(), val operations: List<OutboxEntity> = emptyList()) {
    fun membersFor(tripId: String?, actorId: String) = members.filter { m -> if (tripId == null) m.id == actorId else memberships.any { it.tripId == tripId && it.memberId == m.id } }
    fun expensesFor(tripId: String?) = expenses.filter { e -> records.any { it.id == e.recordId && it.tripId == tripId && !it.draft } }
    fun settlement(tripId: String?) = trips.find { it.id == tripId }?.currency ?: "TWD"
}

class TravelRepository(val db: TravelDatabase, val actorId: String, val deviceId: String, val filesDir: File) {
    val dao = db.dao()
    private val content = combine(dao.trips(), dao.records(), dao.attachments(), dao.members(), dao.memberships()) { t, r, a, m, tm -> TravelState(t, r, a, m, tm) }
    private val accounting = combine(dao.dishes(), dao.expenses(), dao.shares(), dao.rates(), dao.outbox()) { d, e, s, r, o -> TravelState(dishes=d, expenses=e, shares=s, rates=r, operations=o) }
    val state: Flow<TravelState> = combine(content, accounting) { c, a -> c.copy(dishes=a.dishes, expenses=a.expenses, shares=a.shares, rates=a.rates, operations=a.operations) }
    suspend fun initialize() { if (dao.member(actorId) == null) dao.putMember(MemberEntity(actorId, "我")) }

    suspend fun createTrip(title: String, start: String, end: String, currency: String): String = db.withTransaction {
        require(title.isNotBlank()) { "請填寫旅程名稱" }
        require(!LocalDate.parse(end).isBefore(LocalDate.parse(start))) { "結束日期不能早於開始日期" }
        Money.scale(currency)
        val trip = TripEntity(title=title.trim(), startDate=start, endDate=end, currency=currency, ownerId=actorId)
        dao.putTrip(trip); dao.putMembership(TripMemberEntity(trip.id, actorId))
        enqueueTrip(trip); trip.id
    }
    suspend fun addMember(tripId: String, name: String) = db.withTransaction {
        require(name.isNotBlank()) { "請填寫成員名稱" }
        val trip = requireNotNull(dao.trip(tripId)); require(trip.ownerId == actorId)
        val member = MemberEntity(name=name.trim()); dao.putMember(member); dao.putMembership(TripMemberEntity(tripId, member.id))
        val updated = trip.copy(revision=trip.revision+1, modifiedAt=now()); dao.putTrip(updated); enqueueTrip(updated)
    }
    suspend fun newRecord(tripId: String?): RecordEntity = db.withTransaction {
        if(tripId!=null) require(dao.trip(tripId)?.deleted == false)
        RecordEntity(tripId=tripId, ownerId=actorId, deviceId=deviceId).also { dao.putRecord(it) }
    }
    suspend fun saveEditor(value: RecordEntity, finish: Boolean = false): RecordEntity = db.withTransaction {
        val old = requireNotNull(dao.record(value.id)); require(!old.deleted)
        val date = LocalDateTime.parse(value.occurredAt); ZoneId.of(value.zoneId)
        require((value.latitude == null) == (value.longitude == null)) { "請成對填寫經緯度" }
        require(value.latitude == null || (value.latitude.isFinite() && value.latitude in -90.0..90.0 && value.longitude!!.isFinite() && value.longitude in -180.0..180.0)) { "經緯度超出有效範圍" }
        require(value.rating == null || value.rating in 1..5)
        if(finish) require(value.title.isNotBlank() || value.text.isNotBlank() || value.place.isNotBlank() || value.latitude != null || value.store.isNotBlank() || dao.recordAttachments(value.id).any { !it.deleted && it.state == "READY" } || dao.recordExpenses(value.id).any { !it.deleted } || dao.recordDishes(value.id).any { !it.deleted }) { "請至少加入文字、地點、照片、錄音、餐點或費用" }
        val next = value.copy(tripId=old.tripId, ownerId=old.ownerId, mealExpenseId=old.mealExpenseId,
            localDate=date.toLocalDate().toString(), draft=if(finish) false else old.draft, revision=old.revision+1,
            serverRevision=old.serverRevision, modifiedBy=actorId, deviceId=deviceId, modifiedAt=now())
        dao.putRecord(next); if(!next.draft) enqueueRecord(next); next
    }
    private suspend fun touch(id: String) {
        val old = requireNotNull(dao.record(id)); require(!old.deleted)
        val next=old.copy(revision=old.revision+1, modifiedAt=now(), modifiedBy=actorId, deviceId=deviceId)
        dao.putRecord(next); if(!next.draft) enqueueRecord(next)
    }
    suspend fun deleteRecord(id: String) = db.withTransaction {
        val old=requireNotNull(dao.record(id)); val deleted=old.copy(deleted=true, revision=old.revision+1, modifiedAt=now())
        dao.putRecord(deleted); enqueueRecord(deleted) // retain referenced files and children for future delete/edit conflict handling
    }
    suspend fun attach(row: AttachmentEntity) = db.withTransaction { require(dao.record(row.recordId)?.deleted==false); dao.putAttachment(row); touch(row.recordId) }
    suspend fun updateAttachment(id: String, name: String? = null, deleted: Boolean = false) = db.withTransaction {
        val old=requireNotNull(dao.attachment(id)); dao.putAttachment(old.copy(name=name?:old.name, deleted=deleted, revision=old.revision+1)); touch(old.recordId)
    }
    suspend fun reorderAttachment(id: String, direction: Int) = db.withTransaction {
        val old=requireNotNull(dao.attachment(id)); val rows=dao.recordAttachments(old.recordId).filter { !it.deleted && it.kind == old.kind }.sortedWith(compareBy<AttachmentEntity>{it.position}.thenBy{it.id}).toMutableList()
        val index=rows.indexOfFirst { it.id==id }; val other=index+direction
        if(index>=0 && other in rows.indices) { java.util.Collections.swap(rows,index,other); rows.forEachIndexed { i,a -> dao.putAttachment(a.copy(position=i,revision=a.revision+1)) }; touch(old.recordId) }
    }
    suspend fun saveDish(row: DishEntity) = db.withTransaction {
        require(row.name.isNotBlank() && BigDecimal(row.quantity)>BigDecimal.ZERO && row.unitMinor>=0) { "請填寫餐點名稱、有效數量與金額" }
        require(row.rating==null || row.rating in 1..5); dao.putDish(row.copy(revision=row.revision+1)); touch(row.recordId)
    }
    suspend fun saveExpense(row: ExpenseEntity, shares: Map<String, Long>, mealLink: Boolean) = db.withTransaction {
        val record=requireNotNull(dao.record(row.recordId)); require(!record.deleted)
        val settlement=record.tripId?.let { dao.trip(it)?.currency }?:"TWD"
        require(row.settlementCurrency==settlement) { "結算幣別與旅程不符" }
        require(row.originalMinor>0 && row.settledMinor==Money.convert(row.originalMinor,row.originalCurrency,settlement,row.rate))
        Money.custom(row.settledMinor,shares)
        val allowed=if(record.tripId==null) setOf(actorId) else dao.tripMembers(record.tripId).map { it.memberId }.toSet()
        require(row.payerId in allowed && shares.keys.all { it in allowed }) { "付款與分攤成員必須屬於此旅程" }
        require(row.label.isNotBlank()) { "請填寫費用名稱" }
        val old=dao.expense(row.id); require(old==null || old.recordId==row.recordId)
        require(runCatching { dao.allExpenses().filter { it.id!=row.id }.fold(row.settledMinor) { sum,e -> Math.addExact(sum,e.settledMinor) } }.isSuccess) { "累計金額過大，超出此版本可處理的範圍" }
        dao.putExpense(row.copy(revision=(old?.revision?:0)+1)); dao.clearShares(row.id)
        dao.putShares(shares.map { ShareEntity(row.id,it.key,it.value) })
        if(mealLink) { require(record.mealExpenseId==null || record.mealExpenseId==row.id) { "此餐已連結另一筆支出，請編輯原支出" }; dao.putRecord(record.copy(mealExpenseId=row.id)) }
        else if(record.mealExpenseId==row.id) dao.putRecord(record.copy(mealExpenseId=null))
        touch(row.recordId)
    }
    suspend fun deleteExpense(id: String) = db.withTransaction {
        val old=requireNotNull(dao.expense(id)); dao.putExpense(old.copy(deleted=true,revision=old.revision+1))
        val r=requireNotNull(dao.record(old.recordId)); if(r.mealExpenseId==id)dao.putRecord(r.copy(mealExpenseId=null)); touch(old.recordId)
    }
    suspend fun setRate(scope: String, original: String, settlement: String, value: String) {
        Money.rate(value); Money.scale(original); Money.scale(settlement)
        require(original!=settlement || BigDecimal(value).compareTo(BigDecimal.ONE)==0)
        dao.putRate(RatePresetEntity(scope,original,settlement,value)) // immutable expense snapshots are unaffected
    }
    /** Preview and consent are handled by UI. Move uses the same record and expense IDs in one transaction. */
    suspend fun moveDiary(id: String, tripId: String, mapping: Map<String,String>, rates: Map<String,String>) = db.withTransaction {
        val record=requireNotNull(dao.record(id)); require(record.tripId==null && !record.deleted) { "這筆紀錄已加入旅程，請重新整理" }
        val trip=requireNotNull(dao.trip(tripId)); require(!trip.deleted)
        val allowed=dao.tripMembers(tripId).map { it.memberId }.toSet()
        val expenses=dao.recordExpenses(id).filter { !it.deleted }
        for(e in expenses) {
            val originalShares=dao.expenseShares(e.id)
            fun mapped(old: String)=mapping[old]?.also { require(it in allowed) }?:error("請先對應所有分帳成員")
            val payer=mapped(e.payerId)
            val newRate=if(e.originalCurrency==trip.currency) "1" else rates[e.originalCurrency]?:error("缺少 ${e.originalCurrency} 匯率")
            val converted=Money.convert(e.originalMinor,e.originalCurrency,trip.currency,newRate)
            val remapped=originalShares.groupBy { mapped(it.memberId) }.mapValues { (_,v)->v.fold(0L){a,b->Math.addExact(a,b.minor)} }
            val split=if(e.splitMode=="EQUAL"||remapped.size==1) Money.equal(converted,remapped.keys.toList()) else { require(e.settlementCurrency==trip.currency && e.settledMinor==converted) { "指定金額支出需逐筆確認目標幣別的分攤金額" }; Money.custom(converted,remapped) }
            dao.putExpense(e.copy(rate=newRate,settlementCurrency=trip.currency,settledMinor=converted,payerId=payer,revision=e.revision+1))
            dao.clearShares(e.id); dao.putShares(split.map { ShareEntity(e.id,it.key,it.value) })
        }
        dao.putRecord(record.copy(tripId=tripId)); touch(id)
    }
    fun file(relative: String): File = File(filesDir,relative).canonicalFile.also { require(it.path.startsWith(File(filesDir,"media").canonicalPath+File.separator)) }
    fun digest(file: File): String { val md=MessageDigest.getInstance("SHA-256"); file.inputStream().use { input -> val buffer=ByteArray(64*1024); while(true){val n=input.read(buffer);if(n<0)break;md.update(buffer,0,n)} }; return md.digest().joinToString("") { "%02x".format(it) } }
    private suspend fun enqueueTrip(trip: TripEntity) {
        val payload=JSONObject().put("schemaVersion",1).put("id",trip.id).put("title",trip.title).put("startDate",trip.startDate).put("endDate",trip.endDate).put("currency",trip.currency).put("ownerId",trip.ownerId).put("revision",trip.revision).put("deleted",trip.deleted).put("members",JSONArray(dao.tripMembers(trip.id).map { tm -> JSONObject().put("id",tm.memberId).put("name",dao.member(tm.memberId)?.name) }))
        dao.compactLocalOutbox(trip.id)
        dao.enqueue(OutboxEntity(aggregateId=trip.id,kind="TRIP",localRevision=trip.revision,baseServerRevision=trip.serverRevision,payload=payload.toString()))
    }
    private suspend fun enqueueRecord(r: RecordEntity) {
        val p=JSONObject().put("schemaVersion",1).put("id",r.id).put("tripId",r.tripId?:JSONObject.NULL).put("ownerId",r.ownerId).put("occurredAt",r.occurredAt).put("zoneId",r.zoneId).put("localDate",r.localDate).put("title",r.title).put("text",r.text).put("place",r.place).put("latitude",r.latitude?:JSONObject.NULL).put("longitude",r.longitude?:JSONObject.NULL).put("accuracy",r.accuracy?:JSONObject.NULL).put("locationCapturedAt",r.locationCapturedAt?:JSONObject.NULL).put("locationSource",r.locationSource?:JSONObject.NULL).put("store",r.store).put("rating",r.rating?:JSONObject.NULL).put("mealNotes",r.mealNotes).put("mealExpenseId",r.mealExpenseId?:JSONObject.NULL).put("revision",r.revision).put("modifiedBy",r.modifiedBy).put("deviceId",r.deviceId).put("deleted",r.deleted)
        p.put("attachments",JSONArray(dao.recordAttachments(r.id).map { a -> JSONObject().put("id",a.id).put("kind",a.kind).put("path",a.relativePath).put("mime",a.mime).put("name",a.name).put("position",a.position).put("durationMs",a.durationMs?:JSONObject.NULL).put("size",a.sizeBytes).put("sha256",a.sha256).put("state",a.state).put("revision",a.revision).put("deleted",a.deleted) }))
        p.put("dishes",JSONArray(dao.recordDishes(r.id).map { d -> JSONObject().put("id",d.id).put("name",d.name).put("quantity",d.quantity).put("unitMinor",d.unitMinor).put("currency",d.currency).put("rating",d.rating?:JSONObject.NULL).put("notes",d.notes).put("revision",d.revision).put("deleted",d.deleted) }))
        p.put("expenses",JSONArray(dao.recordExpenses(r.id).map { e -> JSONObject().put("id",e.id).put("label",e.label).put("category",e.category).put("originalMinor",e.originalMinor).put("originalCurrency",e.originalCurrency).put("settlementCurrency",e.settlementCurrency).put("rate",e.rate).put("settledMinor",e.settledMinor).put("payerId",e.payerId).put("splitMode",e.splitMode).put("roundingPolicy",e.roundingPolicy).put("revision",e.revision).put("deleted",e.deleted).put("shares",JSONArray(dao.expenseShares(e.id).map { s -> JSONObject().put("memberId",s.memberId).put("minor",s.minor) })) }))
        dao.compactLocalOutbox(r.id)
        dao.enqueue(OutboxEntity(aggregateId=r.id,kind="RECORD",localRevision=r.revision,baseServerRevision=r.serverRevision,payload=p.toString()))
    }
}
