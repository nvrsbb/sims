package com.axiomstead.foldsms

import androidx.room.*
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.io.IOException

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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(conversation: CustomSampleConversation)

    @Transaction
    open suspend fun create(name: String, address: String, draft: String, fixtureAddresses: List<String>, reservedLowestId: Int? = null): Pair<CustomSampleConversation, String> {
        val existing = getConversations()
        val input = validateNewSampleConversation(name, address, draft, fixtureAddresses + existing.map { it.address })
        val id = nextCustomConversationId(listOfNotNull(existing.minOfOrNull { it.id }, reservedLowestId).minOrNull())
        val conversation = CustomSampleConversation(id, input.name, input.address, input.normalizedAddress)
        insert(conversation)
        return conversation to input.draft
    }
}

/** Keep creation and initial draft together before making the new row visible. */
internal class CustomSampleConversationStore(
    private val database: ConversationMetadataDatabase,
    private val drafts: DraftStore
) {
    val conversations = database.customConversations().observeConversations()

    suspend fun create(name: String, address: String, draft: String, fixtureAddresses: List<String>): CustomSampleConversation =
        database.withTransaction {
            val (conversation, initialDraft) = database.customConversations().create(name, address, draft, fixtureAddresses, drafts.lowestCustomThreadId())
            if (!drafts.saveInitial(conversation.id, initialDraft)) throw IOException("Could not save the initial draft.")
            conversation
        }
}
