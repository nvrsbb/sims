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
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false
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

    @Query("UPDATE device_conversation_metadata SET archived = :archived WHERE conversationKey = :key")
    suspend fun updateArchived(key: String, archived: Boolean)

    @Transaction
    suspend fun setCategory(key: String, category: String?) { insertIfAbsent(DeviceConversationMetadata(key)); updateCategory(key, category) }

    @Transaction
    suspend fun setPinned(key: String, pinned: Boolean) { insertIfAbsent(DeviceConversationMetadata(key)); updatePinned(key, pinned) }

    @Transaction
    suspend fun setArchived(key: String, archived: Boolean) { insertIfAbsent(DeviceConversationMetadata(key)); updateArchived(key, archived) }
}

class DeviceMetadataStore(private val dao: DeviceConversationMetadataDao) {
    val all: Flow<Map<String, DeviceConversationMetadata>> = dao.observeAll().map { rows -> rows.associateBy { it.conversationKey } }

    suspend fun setCategory(key: String, category: String?) {
        require(category == null || category in conversationCategories)
        dao.setCategory(key, category)
    }
    suspend fun setPinned(key: String, pinned: Boolean) = dao.setPinned(key, pinned)
    suspend fun setArchived(key: String, archived: Boolean) = dao.setArchived(key, archived)

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

/**
 * Archive view or inbox view, optional category filter ("All" keeps everything), pinned first, then newest, then key.
 * New messages never move an archived conversation back automatically.
 */
internal fun arrangeDeviceConversations(
    conversations: List<DeviceConversation>, metadata: Map<String, DeviceConversationMetadata>, showArchive: Boolean, filter: String
): List<DeviceConversation> = conversations
    .filter { (metadata[it.key]?.archived == true) == showArchive }
    .filter { filter == "All" || effectiveCategory(it, metadata[it.key]) == filter }
    .sortedWith(compareByDescending<DeviceConversation> { metadata[it.key]?.pinned == true }.thenByDescending { it.last.date }.thenBy { it.key })
