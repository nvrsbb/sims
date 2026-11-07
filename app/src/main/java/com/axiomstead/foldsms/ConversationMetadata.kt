package com.axiomstead.foldsms

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Every value ever stored, so old data stays readable. The app now offers only Personal and Business (see chatCategories). */
val conversationCategories = listOf("Personal", "Business", "Finance", "Services", "Promotions", "OTP", "Unknown")

@Entity(tableName = "conversation_metadata")
data class ConversationMetadata(
    @PrimaryKey val threadId: Int,
    val category: String? = null,
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false
)

@Dao
interface ConversationMetadataDao {
    @Query("SELECT * FROM conversation_metadata")
    fun observeCategories(): Flow<List<ConversationMetadata>>

    @Query("SELECT * FROM conversation_metadata ORDER BY threadId")
    suspend fun getAll(): List<ConversationMetadata>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(value: ConversationMetadata)

    @Query("UPDATE conversation_metadata SET category = :category WHERE threadId = :threadId")
    suspend fun updateCategory(threadId: Int, category: String?)

    @Query("UPDATE conversation_metadata SET pinned = :pinned WHERE threadId = :threadId")
    suspend fun updatePinned(threadId: Int, pinned: Boolean)

    @Query("UPDATE conversation_metadata SET archived = :archived WHERE threadId = :threadId")
    suspend fun updateArchived(threadId: Int, archived: Boolean)

    @Transaction
    suspend fun setArchived(threadId: Int, archived: Boolean) {
        insertIfAbsent(ConversationMetadata(threadId))
        updateArchived(threadId, archived)
    }

    @Transaction
    suspend fun setCategory(threadId: Int, category: String?) {
        insertIfAbsent(ConversationMetadata(threadId))
        updateCategory(threadId, category)
    }

    @Transaction
    suspend fun setPinned(threadId: Int, pinned: Boolean) {
        insertIfAbsent(ConversationMetadata(threadId))
        updatePinned(threadId, pinned)
    }

}

// Legacy sample_drafts preferences stay untouched.
// Future schema changes must supply explicit migrations; never use destructive fallback.
@Database(entities = [ConversationMetadata::class, CustomSampleConversation::class, DeviceConversationMetadata::class], version = 6, exportSchema = true)
abstract class ConversationMetadataDatabase : RoomDatabase() {
    abstract fun metadata(): ConversationMetadataDao
    abstract fun customConversations(): CustomSampleConversationDao
    abstract fun deviceMetadata(): DeviceConversationMetadataDao
}

class ConversationMetadataStore(private val dao: ConversationMetadataDao) {
    val categories = dao.observeCategories()

    suspend fun setCategory(threadId: Int, category: String?) {
        require(category == null || category in conversationCategories)
        dao.setCategory(threadId, category)
    }

    suspend fun setPinned(threadId: Int, pinned: Boolean) = dao.setPinned(threadId, pinned)

    suspend fun setArchived(threadId: Int, archived: Boolean) = dao.setArchived(threadId, archived)

    companion object {
        @Volatile private var instance: ConversationMetadataStore? = null

        fun get(context: Context): ConversationMetadataStore = instance ?: synchronized(this) {
            instance ?: ConversationMetadataStore(
                sampleMetadataDatabase(context).metadata()
            ).also { instance = it }
        }
    }
}

val METADATA_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Nullable category means resetting an override can retain other metadata.
        db.execSQL("CREATE TABLE conversation_metadata_new (threadId INTEGER NOT NULL, category TEXT, pinned INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(threadId))")
        db.execSQL("INSERT INTO conversation_metadata_new (threadId, category, pinned) SELECT threadId, category, 0 FROM conversation_metadata")
        db.execSQL("DROP TABLE conversation_metadata")
        db.execSQL("ALTER TABLE conversation_metadata_new RENAME TO conversation_metadata")
    }
}

val METADATA_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE conversation_metadata ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS custom_sample_conversations (id INTEGER NOT NULL, name TEXT NOT NULL, address TEXT NOT NULL, normalizedAddress TEXT NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_custom_sample_conversations_normalizedAddress ON custom_sample_conversations (normalizedAddress)")
    }
}

private object SampleMetadataDatabaseProvider {
    @Volatile var instance: ConversationMetadataDatabase? = null
}

internal fun sampleMetadataDatabase(context: Context): ConversationMetadataDatabase =
    SampleMetadataDatabaseProvider.instance ?: synchronized(SampleMetadataDatabaseProvider) {
        SampleMetadataDatabaseProvider.instance ?: Room.databaseBuilder(context.applicationContext,
            ConversationMetadataDatabase::class.java, "sample_metadata.db")
            .addMigrations(METADATA_MIGRATION_1_2, METADATA_MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build().also { SampleMetadataDatabaseProvider.instance = it }
    }
