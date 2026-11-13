package tw.shilu.travel.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.ZonedDateTime
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
fun now(): String = java.time.Instant.now().toString()

@Entity(tableName = "trips")
data class TripEntity(@PrimaryKey val id: String = newId(), val title: String, val startDate: String, val endDate: String,
    val currency: String = "TWD", val ownerId: String, val revision: Long = 1, val serverRevision: String? = null,
    val deleted: Boolean = false, val modifiedAt: String = now())

@Entity(tableName = "members")
data class MemberEntity(@PrimaryKey val id: String = newId(), val name: String, val accountId: String? = null,
    val revision: Long = 1, val deleted: Boolean = false)

@Entity(tableName = "trip_members", primaryKeys = ["tripId", "memberId"])
data class TripMemberEntity(val tripId: String, val memberId: String)

@Entity(tableName = "records", indices = [Index("tripId"), Index("localDate")])
data class RecordEntity(@PrimaryKey val id: String = newId(), val tripId: String? = null, val ownerId: String,
    val occurredAt: String = ZonedDateTime.now().toLocalDateTime().withSecond(0).withNano(0).toString(),
    val zoneId: String = java.time.ZoneId.systemDefault().id,
    val localDate: String = occurredAt.take(10), val title: String = "", val text: String = "", val place: String = "",
    val latitude: Double? = null, val longitude: Double? = null, val accuracy: Float? = null,
    val locationCapturedAt: String? = null, val locationSource: String? = null,
    val store: String = "", val rating: Int? = null, val mealNotes: String = "", val mealExpenseId: String? = null,
    val draft: Boolean = true, val revision: Long = 1, val serverRevision: String? = null,
    val modifiedBy: String = ownerId, val deviceId: String, val modifiedAt: String = now(), val deleted: Boolean = false)

@Entity(tableName = "attachments", indices = [Index("recordId")])
data class AttachmentEntity(@PrimaryKey val id: String = newId(), val recordId: String, val kind: String,
    val relativePath: String, val mime: String, val name: String = "", val position: Int = 0,
    val durationMs: Long? = null, val sizeBytes: Long = 0, val sha256: String = "",
    val state: String = "READY", val revision: Long = 1, val deleted: Boolean = false)

@Entity(tableName = "dishes", indices = [Index("recordId")])
data class DishEntity(@PrimaryKey val id: String = newId(), val recordId: String, val name: String,
    val quantity: String = "1", val unitMinor: Long = 0, val currency: String = "TWD", val rating: Int? = null,
    val notes: String = "", val revision: Long = 1, val deleted: Boolean = false)

@Entity(tableName = "expenses", indices = [Index("recordId")])
data class ExpenseEntity(@PrimaryKey val id: String = newId(), val recordId: String, val label: String,
    val category: String = "其他", val originalMinor: Long, val originalCurrency: String,
    val rate: String, val settlementCurrency: String, val settledMinor: Long, val payerId: String,
    val splitMode: String = "EQUAL", val roundingPolicy: Int = 1, val revision: Long = 1, val deleted: Boolean = false)

@Entity(tableName = "shares", primaryKeys = ["expenseId", "memberId"])
data class ShareEntity(val expenseId: String, val memberId: String, val minor: Long)

@Entity(tableName = "rate_presets", primaryKeys = ["scopeId", "originalCurrency"])
data class RatePresetEntity(val scopeId: String, val originalCurrency: String, val settlementCurrency: String, val rate: String)

@Entity(tableName = "outbox", indices = [Index("aggregateId")])
data class OutboxEntity(@PrimaryKey val operationId: String = newId(), val aggregateId: String, val kind: String,
    val localRevision: Long, val baseServerRevision: String?, val payload: String, val createdAt: String = now(),
    val status: String = "LOCAL_ONLY")
