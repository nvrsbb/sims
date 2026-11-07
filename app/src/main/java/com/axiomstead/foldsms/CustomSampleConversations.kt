package com.axiomstead.foldsms

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Legacy table from the removed sample inbox. The app no longer reads or writes it; the definition stays so existing
 * installs keep their data and the database schema and migrations are unchanged.
 */
@Entity(tableName = "custom_sample_conversations", indices = [Index(value = ["normalizedAddress"], unique = true)])
data class CustomSampleConversation(
    @PrimaryKey val id: Int,
    val name: String,
    val address: String,
    val normalizedAddress: String
)

@Dao
abstract class CustomSampleConversationDao {
    @Query("SELECT * FROM custom_sample_conversations ORDER BY id DESC")
    abstract fun observeConversations(): Flow<List<CustomSampleConversation>>

    @Query("SELECT * FROM custom_sample_conversations ORDER BY id DESC")
    abstract suspend fun getConversations(): List<CustomSampleConversation>
}
