package com.axiomstead.foldsms

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

val conversationCategories = listOf("Personal", "Finance", "Services", "Promotions", "OTP", "Unknown")

@Entity(tableName = "conversation_metadata")
data class ConversationMetadata(
    @PrimaryKey val threadId: Int,
    val category: String
)

@Dao
interface ConversationMetadataDao {
    @Query("SELECT * FROM conversation_metadata")
    fun observeCategories(): Flow<List<ConversationMetadata>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setCategory(value: ConversationMetadata)

    @Query("DELETE FROM conversation_metadata WHERE threadId = :threadId")
    suspend fun resetCategory(threadId: Int)
}

// Version 1 introduces metadata only. Legacy sample_drafts preferences stay untouched.
// Future schema changes must supply explicit migrations; never use destructive fallback.
@Database(entities = [ConversationMetadata::class], version = 1, exportSchema = true)
abstract class ConversationMetadataDatabase : RoomDatabase() {
    abstract fun metadata(): ConversationMetadataDao
}

class ConversationMetadataStore(private val dao: ConversationMetadataDao) {
    val categories = dao.observeCategories()

    suspend fun setCategory(threadId: Int, category: String?) {
        require(category == null || category in conversationCategories)
        if (category == null) dao.resetCategory(threadId)
        else dao.setCategory(ConversationMetadata(threadId, category))
    }

    companion object {
        @Volatile private var instance: ConversationMetadataStore? = null

        fun get(context: Context): ConversationMetadataStore = instance ?: synchronized(this) {
            instance ?: ConversationMetadataStore(
                Room.databaseBuilder(context.applicationContext, ConversationMetadataDatabase::class.java,
                    "sample_metadata.db").build().metadata()
            ).also { instance = it }
        }
    }
}
