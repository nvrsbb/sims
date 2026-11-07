package com.axiomstead.foldsms

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class ConversationPinsTest {
    @get:Rule val migrations = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), ConversationMetadataDatabase::class.java
    )

    @Test fun migrationPreservesCategoryAndPinsSurviveReopenAndCategoryReset() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "pin-test-${UUID.randomUUID()}"
        migrations.createDatabase(name, 1).apply {
            execSQL("INSERT INTO conversation_metadata (threadId, category) VALUES (1, 'Unknown')")
            close()
        }
        migrations.runMigrationsAndValidate(name, 2, true, METADATA_MIGRATION_1_2).apply {
            query("SELECT category, pinned FROM conversation_metadata WHERE threadId = 1").use {
                assertTrue(it.moveToFirst())
                assertEquals("Unknown", it.getString(0))
                assertEquals(0, it.getInt(1))
            }
            close()
        }
        fun open() = Room.databaseBuilder(context, ConversationMetadataDatabase::class.java, name)
            .addMigrations(METADATA_MIGRATION_1_2, METADATA_MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
        var database = open()
        try {
            var store = ConversationMetadataStore(database.metadata())
            store.setPinned(1, true)
            store.setCategory(1, null)
            store.setCategory(2, "Services")
            store.setPinned(2, true)
            store.setPinned(2, false)
            database.close()
            database = open()
            store = ConversationMetadataStore(database.metadata())
            assertEquals(setOf(ConversationMetadata(1, null, true), ConversationMetadata(2, "Services", false)), store.categories.first().toSet())
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
