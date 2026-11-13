package tw.shilu.travel.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TravelDao {
    @Query("SELECT * FROM trips WHERE deleted=0 ORDER BY startDate DESC") fun trips(): Flow<List<TripEntity>>
    @Query("SELECT * FROM records WHERE deleted=0 ORDER BY occurredAt DESC") fun records(): Flow<List<RecordEntity>>
    @Query("SELECT * FROM attachments WHERE deleted=0 ORDER BY position") fun attachments(): Flow<List<AttachmentEntity>>
    @Query("SELECT * FROM members WHERE deleted=0") fun members(): Flow<List<MemberEntity>>
    @Query("SELECT * FROM trip_members") fun memberships(): Flow<List<TripMemberEntity>>
    @Query("SELECT * FROM dishes WHERE deleted=0") fun dishes(): Flow<List<DishEntity>>
    @Query("SELECT * FROM expenses WHERE deleted=0") fun expenses(): Flow<List<ExpenseEntity>>
    @Query("SELECT * FROM shares") fun shares(): Flow<List<ShareEntity>>
    @Query("SELECT * FROM rate_presets") fun rates(): Flow<List<RatePresetEntity>>
    @Query("SELECT * FROM outbox") fun outbox(): Flow<List<OutboxEntity>>
    @Query("SELECT * FROM records WHERE id=:id") suspend fun record(id: String): RecordEntity?
    @Query("SELECT * FROM trips WHERE id=:id") suspend fun trip(id: String): TripEntity?
    @Query("SELECT * FROM members WHERE id=:id") suspend fun member(id: String): MemberEntity?
    @Query("SELECT * FROM trip_members WHERE tripId=:id") suspend fun tripMembers(id: String): List<TripMemberEntity>
    @Query("SELECT * FROM attachments WHERE recordId=:id") suspend fun recordAttachments(id: String): List<AttachmentEntity>
    @Query("SELECT * FROM expenses WHERE recordId=:id") suspend fun recordExpenses(id: String): List<ExpenseEntity>
    @Query("SELECT * FROM dishes WHERE recordId=:id") suspend fun recordDishes(id: String): List<DishEntity>
    @Query("SELECT * FROM shares WHERE expenseId=:id") suspend fun expenseShares(id: String): List<ShareEntity>
    @Query("SELECT * FROM expenses WHERE id=:id") suspend fun expense(id: String): ExpenseEntity?
    @Query("SELECT * FROM expenses WHERE deleted=0") suspend fun allExpenses(): List<ExpenseEntity>
    @Query("SELECT * FROM attachments WHERE id=:id") suspend fun attachment(id: String): AttachmentEntity?
    @Query("SELECT * FROM attachments WHERE state='RECORDING' AND deleted=0") suspend fun interruptedAudio(): List<AttachmentEntity>
    @Query("SELECT * FROM rate_presets WHERE scopeId=:scope AND originalCurrency=:currency") suspend fun rate(scope: String, currency: String): RatePresetEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTrip(row: TripEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMember(row: MemberEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMembership(row: TripMemberEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRecord(row: RecordEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAttachment(row: AttachmentEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDish(row: DishEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putExpense(row: ExpenseEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putShares(rows: List<ShareEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRate(row: RatePresetEntity)
    @Insert suspend fun enqueue(row: OutboxEntity)
    @Query("DELETE FROM outbox WHERE aggregateId=:id AND status='LOCAL_ONLY'") suspend fun compactLocalOutbox(id: String)
    @Query("DELETE FROM shares WHERE expenseId=:id") suspend fun clearShares(id: String)
}

@Database(entities = [TripEntity::class, MemberEntity::class, TripMemberEntity::class, RecordEntity::class,
    AttachmentEntity::class, DishEntity::class, ExpenseEntity::class, ShareEntity::class,
    RatePresetEntity::class, OutboxEntity::class], version = 1, exportSchema = true)
abstract class TravelDatabase : RoomDatabase() { abstract fun dao(): TravelDao }
