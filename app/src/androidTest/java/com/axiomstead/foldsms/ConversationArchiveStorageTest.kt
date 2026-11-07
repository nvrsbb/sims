package com.axiomstead.foldsms

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class ConversationArchiveStorageTest {
    @get:Rule val migrations = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), ConversationMetadataDatabase::class.java
    )

    @Test fun archiveMigrationAndReopeningPreserveCategoryPinAndDraft() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "archive-test-${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        preferences.edit().putString("thread_1", "Unsent fabricated draft").commit()
        migrations.createDatabase(name, 2).apply {
            execSQL("INSERT INTO conversation_metadata (threadId, category, pinned) VALUES (1, 'Unknown', 1)")
            close()
        }
        migrations.runMigrationsAndValidate(name, 3, true, METADATA_MIGRATION_2_3).close()
        fun open() = Room.databaseBuilder(context, ConversationMetadataDatabase::class.java, name)
            .addMigrations(METADATA_MIGRATION_1_2, METADATA_MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
        var database = open()
        try {
            var store = ConversationMetadataStore(database.metadata())
            assertEquals(listOf(ConversationMetadata(1, "Unknown", true, false)), store.categories.first())
            store.setArchived(1, true)
            database.close()
            database = open()
            store = ConversationMetadataStore(database.metadata())
            assertEquals(listOf(ConversationMetadata(1, "Unknown", true, true)), store.categories.first())
            store.setArchived(1, false)
            assertEquals(listOf(ConversationMetadata(1, "Unknown", true, false)), store.categories.first())
            assertEquals("Unsent fabricated draft", preferences.getString("thread_1", null))
        } finally {
            database.close()
            context.deleteDatabase(name)
            preferences.edit().clear().commit()
        }
    }
}
