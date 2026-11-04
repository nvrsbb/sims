package com.axiomstead.foldsms

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CustomSampleConversationsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun creationPersistsStableIdsAndRejectsDuplicateAddresses() = runBlocking {
        val name = "custom-sample-test-${UUID.randomUUID()}"
        fun open() = Room.databaseBuilder(context, ConversationMetadataDatabase::class.java, name).build()
        var database = open()
        try {
            val first = database.customConversations().create("Sample Person", "+1 202-555-0199", "Sample draft\nnext line", listOf("+1 202-555-0101"))
            assertEquals(-1, first.first.id)
            assertEquals("Sample draft\nnext line", first.second)
            val second = database.customConversations().create("Sample Person Two", "202-555-0198", "", emptyList())
            assertEquals(-2, second.first.id)
            val duplicate = runCatching { database.customConversations().create("Duplicate", "1 (202) 555-0199", "Must not replace", emptyList()) }
            assertTrue(duplicate.exceptionOrNull() is IllegalArgumentException)
            database.close()
            database = open()
            assertEquals(listOf(first.first, second.first), database.customConversations().observeConversations().first())
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun creatingWithInitialDraftPreservesItsExactTextAndDoesNotReplaceAnotherDraft() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
        val preferences = context.getSharedPreferences("custom-drafts-${UUID.randomUUID()}", Context.MODE_PRIVATE)
        try {
            val drafts = DraftStore(preferences)
            drafts.save(1, "Existing fabricated draft")
            drafts.save(-5, "Unattached fabricated draft")
            val store = CustomSampleConversationStore(database, drafts)
            val created = store.create("Sample Person", "+1 202-555-0199", "  Sample draft\nsecond line  ", emptyList())
            assertEquals(-6, created.id)
            assertEquals("Unattached fabricated draft", drafts.get(-5))
            assertEquals("  Sample draft\nsecond line  ", DraftStore(preferences).get(created.id))
            assertEquals("Existing fabricated draft", drafts.get(1))
        } finally {
            database.close()
            preferences.edit().clear().commit()
        }
    }

    @Test fun failedInitialDraftWriteRollsBackCreationWithoutOverwritingOtherDrafts() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
        val preferences = context.getSharedPreferences("custom-failure-${UUID.randomUUID()}", Context.MODE_PRIVATE)
        preferences.edit().putString("thread_1", "Existing fabricated draft").commit()
        val failingPreferences = object : SharedPreferences by preferences {
            override fun edit(): SharedPreferences.Editor {
                val edit = preferences.edit()
                return object : SharedPreferences.Editor by edit {
                    override fun commit(): Boolean = false
                }
            }
        }
        try {
            val store = CustomSampleConversationStore(database, DraftStore(failingPreferences))
            assertTrue(runCatching { store.create("Sample", "202-555-0199", "Unwritten draft", emptyList()) }.isFailure)
            assertTrue(database.customConversations().getConversations().isEmpty())
            assertEquals("Existing fabricated draft", preferences.getString("thread_1", null))
            assertFalse(preferences.contains("thread_-1"))
        } finally {
            database.close()
            preferences.edit().clear().commit()
        }
    }

    @Test fun concurrentFormattedDuplicatesProduceOneConversation() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
        try {
            val attempts = listOf("+1 202-555-0199", "1 (202) 555-0199").map { address ->
                async { runCatching { database.customConversations().create("Sample", address, "", emptyList()) } }
            }.awaitAll()
            assertEquals(1, attempts.count { it.isSuccess })
            assertEquals(1, database.customConversations().getConversations().size)
        } finally { database.close() }
    }

    @Test fun versionThreeMigrationRetainsExistingMetadataAndDrafts() = runBlocking {
        val name = "custom-sample-migration-${UUID.randomUUID()}"
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("CREATE TABLE IF NOT EXISTS conversation_metadata (threadId INTEGER NOT NULL, category TEXT, pinned INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(threadId))")
            old.execSQL("INSERT INTO conversation_metadata (threadId,category,pinned,archived) VALUES (2,'Unknown',1,1)")
            old.version = 3
        }
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        preferences.edit().putString("thread_2", "Fabricated existing\ndraft").commit()
        val database = Room.databaseBuilder(context, ConversationMetadataDatabase::class.java, name)
            .addMigrations(MIGRATION_3_4).build()
        try {
            val metadata = database.metadata().observeCategories().first().single()
            assertEquals("Unknown", metadata.category)
            assertTrue(metadata.pinned)
            assertTrue(metadata.archived)
            assertTrue(database.customConversations().getConversations().isEmpty())
            assertEquals("Fabricated existing\ndraft", preferences.getString("thread_2", null))
        } finally {
            database.close()
            context.deleteDatabase(name)
            preferences.edit().clear().commit()
        }
    }
}
