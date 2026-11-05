package com.axiomstead.foldsms

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sms_attempts", indices = [Index("draftRevision")])
internal data class SmsSendAttempt(
    @PrimaryKey val id: String, val draftRevision: Long, val recipient: String, val bodyDigest: String,
    val subscriptionId: Int, val partCount: Int, val createdAt: Long, val stage: String = "PREPARED"
)

@Entity(tableName = "sms_parts", primaryKeys = ["operationId", "partIndex"],
    foreignKeys = [ForeignKey(entity = SmsSendAttempt::class, parentColumns = ["id"],
        childColumns = ["operationId"], onDelete = ForeignKey.CASCADE)])
internal data class SmsSendPart(val operationId: String, val partIndex: Int, val resultCode: Int? = null)

internal data class SmsAttemptWithParts(
    @Embedded val attempt: SmsSendAttempt,
    @Relation(parentColumn = "id", entityColumn = "operationId") val parts: List<SmsSendPart>
)

@Dao
internal abstract class SmsSendDao {
    @Insert protected abstract suspend fun insertAttempt(value: SmsSendAttempt)
    @Insert protected abstract suspend fun insertParts(values: List<SmsSendPart>)
    @Query("SELECT * FROM sms_attempts WHERE draftRevision = :revision")
    abstract suspend fun forRevision(revision: Long): List<SmsSendAttempt>
    @Query("SELECT * FROM sms_attempts WHERE id = :id") abstract suspend fun attempt(id: String): SmsSendAttempt?
    @Query("SELECT * FROM sms_parts WHERE operationId = :id ORDER BY partIndex") abstract suspend fun parts(id: String): List<SmsSendPart>
    @Transaction @Query("SELECT * FROM sms_attempts ORDER BY createdAt DESC, id DESC LIMIT 30")
    abstract fun observeRecent(): Flow<List<SmsAttemptWithParts>>
    @Query("UPDATE sms_attempts SET stage = 'SUBMITTED' WHERE id = :id AND stage = 'PREPARED'")
    abstract suspend fun beginDispatch(id: String): Int
    @Query("UPDATE sms_attempts SET stage = 'REJECTED' WHERE id = :id AND stage = 'PREPARED'")
    abstract suspend fun rejectPrepared(id: String): Int
    @Query("UPDATE sms_parts SET resultCode = :result WHERE operationId = :id AND partIndex = :part AND resultCode IS NULL")
    protected abstract suspend fun updatePart(id: String, part: Int, result: Int): Int
    @Query("UPDATE sms_attempts SET stage = :stage WHERE id = :id")
    protected abstract suspend fun updateStage(id: String, stage: String)

    @Transaction
    open suspend fun prepare(value: SmsSendAttempt) {
        require(value.partCount in 1..MAX_SMS_PARTS)
        check(forRevision(value.draftRevision).none { it.stage != "REJECTED" }) {
            "This draft already has a send attempt. Check its result before composing another message."
        }
        insertAttempt(value)
        insertParts((0 until value.partCount).map { SmsSendPart(value.id, it) })
    }

    @Transaction
    open suspend fun recordResult(id: String, part: Int, result: Int): SmsSendAttempt? {
        val operation = attempt(id) ?: return null
        if (operation.stage != "SUBMITTED" || part !in 0 until operation.partCount) return null
        if (updatePart(id, part, result) == 0) return null
        val stage = smsPartStatus(parts(id).map { it.resultCode })
        if (stage != "SUBMITTED") updateStage(id, stage)
        return operation.copy(stage = stage)
    }
}

/** Separate journal, no copied message bodies and no migration of preview storage. Never auto-resend. */
@Database(entities = [SmsSendAttempt::class, SmsSendPart::class], version = 1, exportSchema = true)
internal abstract class SmsSendDatabase : RoomDatabase() { abstract fun sends(): SmsSendDao }

internal object SmsJournalProvider {
    @Volatile private var instance: SmsSendDatabase? = null
    fun get(context: Context): SmsSendDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(context.applicationContext, SmsSendDatabase::class.java, "sms_send_journal.db")
            .build().also { instance = it }
    }
}
