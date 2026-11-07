package com.axiomstead.foldsms

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@org.junit.runner.RunWith(RetryRunner::class)
class DeviceMetadataStorageTest {
    @get:Rule val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ConversationMetadataDatabase::class.java)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val allMigrations = arrayOf(METADATA_MIGRATION_1_2, METADATA_MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

    @Test fun versionFourToFiveKeepsEverythingAndAddsAnEmptyDeviceTable() = runBlocking {
        val name = "device-meta-migration-${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        preferences.edit().putString("thread_2", "Fabricated existing\ndraft").commit()
        migrations.createDatabase(name, 4).apply {
            execSQL("INSERT INTO conversation_metadata (threadId, category, pinned, archived) VALUES (2, 'Finance', 1, 1)")
            execSQL("INSERT INTO custom_sample_conversations (id, name, address, normalizedAddress) VALUES (-1, 'Fabricated', '2025550100', '2025550100')")
            close()
        }
        migrations.runMigrationsAndValidate(name, 5, true, MIGRATION_4_5).close()
        val database = Room.databaseBuilder(context, ConversationMetadataDatabase::class.java, name).addMigrations(*allMigrations).build()
        try {
            assertEquals(listOf(ConversationMetadata(2, "Finance", true, true)), database.metadata().observeCategories().first())
            assertEquals(listOf(-1), database.customConversations().getConversations().map { it.id })
            assertTrue(database.deviceMetadata().getAll().isEmpty())
            assertEquals("Fabricated existing\ndraft", preferences.getString("thread_2", null))
        } finally { database.close(); context.deleteDatabase(name); preferences.edit().clear().commit() }
    }

    @Test fun versionFiveToSixKeepsDeviceFlagsAndReadsOldArchivesAsNoDate() = runBlocking {
        val name = "device-meta-migration-6-${UUID.randomUUID()}"
        migrations.createDatabase(name, 5).apply {
            execSQL("INSERT INTO device_conversation_metadata (conversationKey, category, pinned, archived) VALUES ('thread-1', 'Finance', 1, 1)")
            execSQL("INSERT INTO device_conversation_metadata (conversationKey, category, pinned, archived) VALUES ('thread-2', NULL, 0, 0)")
            close()
        }
        migrations.runMigrationsAndValidate(name, 6, true, MIGRATION_5_6).close()
        val database = Room.databaseBuilder(context, ConversationMetadataDatabase::class.java, name).addMigrations(*allMigrations).build()
        try {
            assertEquals(listOf(
                DeviceConversationMetadata("thread-1", "Finance", pinned = true, archived = true, archivedAtDate = 0),
                DeviceConversationMetadata("thread-2", null, pinned = false, archived = false, archivedAtDate = 0)
            ), database.deviceMetadata().getAll())
        } finally { database.close(); context.deleteDatabase(name) }
    }

    @Test fun theArchiveDateIsStoredAndClearedOnRestore() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
        try {
            val store = DeviceMetadataStore(database.deviceMetadata())
            store.setArchived("thread-9", true, atDate = 5_000)
            assertEquals(5_000L, store.all.first().getValue("thread-9").archivedAtDate)
            store.setArchived("thread-9", false, atDate = 9_000)
            assertEquals(DeviceConversationMetadata("thread-9", null, false, false, 0), store.all.first().getValue("thread-9"))
        } finally { database.close() }
    }

    @Test fun flagsPersistAcrossReopenAndNeverCollideWithSampleThreadIds() = runBlocking {
        val name = "device-meta-reopen-${UUID.randomUUID()}"
        fun open() = Room.databaseBuilder(context, ConversationMetadataDatabase::class.java, name).addMigrations(*allMigrations).build()
        var database = open()
        try {
            var device = DeviceMetadataStore(database.deviceMetadata())
            val samples = ConversationMetadataStore(database.metadata())
            samples.setPinned(12, true)
            device.setCategory("thread-12", "OTP")
            device.setPinned("thread-12", true)
            device.setArchived("address-2025550101", true)
            device.setCategory("thread-12", "Finance") // later writes keep the other flags
            database.close()
            database = open()
            device = DeviceMetadataStore(database.deviceMetadata())
            assertEquals(mapOf(
                "thread-12" to DeviceConversationMetadata("thread-12", "Finance", pinned = true, archived = false),
                "address-2025550101" to DeviceConversationMetadata("address-2025550101", null, pinned = false, archived = true)
            ), device.all.first())
            assertEquals(listOf(ConversationMetadata(12, null, true, false)), ConversationMetadataStore(database.metadata()).categories.first())
            device.setCategory("thread-12", null)
            assertEquals(null, device.all.first().getValue("thread-12").category)
        } finally { database.close(); context.deleteDatabase(name) }
    }

    @Test fun unknownCategoriesAreRejected() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
        try {
            val store = DeviceMetadataStore(database.deviceMetadata())
            assertThrows(IllegalArgumentException::class.java) { runBlocking { store.setCategory("thread-1", "Spam") } }
            assertTrue(store.all.first().isEmpty())
        } finally { database.close() }
    }

    @Test fun previewExportStillContainsOnlySampleData() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
        try {
            DeviceMetadataStore(database.deviceMetadata()).setPinned("thread-99", true)
            assertTrue(database.metadata().getAll().isEmpty()) // the export reads this table, never the device one
        } finally { database.close() }
    }
}
