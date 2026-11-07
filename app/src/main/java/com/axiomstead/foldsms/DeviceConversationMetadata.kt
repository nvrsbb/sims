package com.axiomstead.foldsms

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Local category, pin and archive flags for real conversations, keyed by the conversation key ("thread-12", "address-…").
 * A separate table keeps these keys from ever colliding with sample thread ids, and it is excluded from preview export.
 * Nothing here is written to the SMS provider or to Google Messages.
 */
@Entity(tableName = "device_conversation_metadata")
data class DeviceConversationMetadata(
    @PrimaryKey val conversationKey: String,
    val category: String? = null,
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    /** Date of the newest message when it was archived; 0 for archives made before this was recorded. See [isArchived]. */
    @ColumnInfo(defaultValue = "0") val archivedAtDate: Long = 0
)

@Dao
interface DeviceConversationMetadataDao {
    @Query("SELECT * FROM device_conversation_metadata")
    fun observeAll(): Flow<List<DeviceConversationMetadata>>

    @Query("SELECT * FROM device_conversation_metadata ORDER BY conversationKey")
    suspend fun getAll(): List<DeviceConversationMetadata>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(value: DeviceConversationMetadata)

    @Query("UPDATE device_conversation_metadata SET category = :category WHERE conversationKey = :key")
    suspend fun updateCategory(key: String, category: String?)

    @Query("UPDATE device_conversation_metadata SET pinned = :pinned WHERE conversationKey = :key")
    suspend fun updatePinned(key: String, pinned: Boolean)

    @Query("UPDATE device_conversation_metadata SET archived = :archived, archivedAtDate = :atDate WHERE conversationKey = :key")
    suspend fun updateArchived(key: String, archived: Boolean, atDate: Long)

    @Transaction
    suspend fun setCategory(key: String, category: String?) { insertIfAbsent(DeviceConversationMetadata(key)); updateCategory(key, category) }

    @Transaction
    suspend fun setPinned(key: String, pinned: Boolean) { insertIfAbsent(DeviceConversationMetadata(key)); updatePinned(key, pinned) }

    @Transaction
    suspend fun setArchived(key: String, archived: Boolean, atDate: Long) { insertIfAbsent(DeviceConversationMetadata(key)); updateArchived(key, archived, atDate) }
}

class DeviceMetadataStore(private val dao: DeviceConversationMetadataDao) {
    val all: Flow<Map<String, DeviceConversationMetadata>> = dao.observeAll().map { rows -> rows.associateBy { it.conversationKey } }

    suspend fun setCategory(key: String, category: String?) {
        require(category == null || category in conversationCategories)
        dao.setCategory(key, category)
    }
    suspend fun setPinned(key: String, pinned: Boolean) = dao.setPinned(key, pinned)
    /** [atDate] is the date of the conversation's newest message now; a later received message brings it back to the inbox. */
    suspend fun setArchived(key: String, archived: Boolean, atDate: Long = 0) = dao.setArchived(key, archived, if (archived) atDate else 0)

    companion object {
        @Volatile private var instance: DeviceMetadataStore? = null
        fun get(context: Context): DeviceMetadataStore = instance ?: synchronized(this) {
            instance ?: DeviceMetadataStore(sampleMetadataDatabase(context).deviceMetadata()).also { instance = it }
        }
    }
}

/** Additive only: a new table, so existing metadata, custom conversations and drafts are untouched. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `device_conversation_metadata` (`conversationKey` TEXT NOT NULL, `category` TEXT, `pinned` INTEGER NOT NULL DEFAULT 0, `archived` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`conversationKey`))")
    }
}

/** Additive only: one new column, existing rows keep their values and read as "archived before dates were recorded". */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `device_conversation_metadata` ADD COLUMN `archivedAtDate` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Archived means archived until a message is received after the archive date. Archives made before dates were recorded
 * (archivedAtDate 0) stay archived until the owner restores them.
 */
internal fun isArchived(conversation: DeviceConversation, metadata: DeviceConversationMetadata?): Boolean {
    if (metadata?.archived != true) return false
    val receivedLast = conversation.latestIncoming?.date ?: 0
    return !(metadata.archivedAtDate > 0 && receivedLast > metadata.archivedAtDate)
}

/**
 * Archive view or inbox view, optional category filter ("All" keeps everything), pinned first, then newest, then key.
 * A message received after a conversation was archived brings it back to the inbox (see [isArchived]).
 */
internal fun arrangeDeviceConversations(
    conversations: List<DeviceConversation>, metadata: Map<String, DeviceConversationMetadata>, showArchive: Boolean, filter: String,
    unreadOnly: Boolean = false, keepKey: String? = null
): List<DeviceConversation> = conversations
    .filter { isArchived(it, metadata[it.key]) == showArchive }
    // The open conversation stays listed even after reading it, so the list does not shift under the reader.
    .filter { !unreadOnly || it.unreadCount > 0 || it.key == keepKey }
    .filter { filter == "All" || effectiveCategory(it, metadata[it.key]) == filter }
    .sortedWith(compareByDescending<DeviceConversation> { metadata[it.key]?.pinned == true }.thenByDescending { it.last.date }.thenBy { it.key })
