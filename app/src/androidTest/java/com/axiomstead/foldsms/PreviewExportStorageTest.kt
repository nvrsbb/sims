package com.axiomstead.foldsms

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PreviewExportStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun captureIncludesArchivedCustomAndUnattachedDraftsWithoutChangingStorage() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
        val prefix = "preview-export-${UUID.randomUUID()}"
        val drafts = context.getSharedPreferences("$prefix-drafts", Context.MODE_PRIVATE)
        val deletion = context.getSharedPreferences("$prefix-deletion", Context.MODE_PRIVATE)
        val settings = context.getSharedPreferences("$prefix-settings", Context.MODE_PRIVATE)
        try {
            val savedDraft = "  Fabricated saved draft\n₱ sample  "
            val created = CustomSampleConversationStore(database, DraftStore(drafts))
                .create("Sample export", "202-555-0199", savedDraft, emptyList())
            DraftStore(drafts).saveInitial(1, "Existing sample draft")
            DraftStore(drafts).saveInitial(-9, "Unattached sample draft")
            drafts.edit().putString("unrelated", "Do not export").commit()
            database.metadata().setArchived(created.id, true)
            database.metadata().setPinned(created.id, true)
            database.metadata().setCategory(2, "Unknown")
            assertTrue(SampleDeletionStore(deletion).delete(setOf(101, 202)))
            ThemeStore(settings).save(ThemeChoice.DARK)
            val draftBefore = drafts.all
            val deletionBefore = deletion.all
            val settingsBefore = settings.all
            val metadataBefore = database.metadata().getAll()
            val customBefore = database.customConversations().getConversations()
            val fixed = Instant.parse("2026-11-05T01:00:00Z")
            val snapshot = capturePreviewExport(database, drafts, deletion, settings, fixed)
            assertEquals(fixed, snapshot.capturedAt)
            assertEquals(4, snapshot.roomSchemaVersion)
            assertEquals(savedDraft, snapshot.drafts[created.id])
            assertEquals("Unattached sample draft", snapshot.drafts[-9])
            assertEquals(setOf(1, -1, -9), snapshot.drafts.keys)
            assertEquals(metadataBefore, snapshot.metadata)
            assertEquals(customBefore, snapshot.customConversations)
            assertEquals(SampleDeletionState(setOf(101, 202), setOf(101, 202)), snapshot.deletion)
            assertEquals(ThemeChoice.DARK, snapshot.theme)
            assertEquals(exampleThreads, snapshot.fixtures)
            assertEquals(draftBefore, drafts.all)
            assertEquals(deletionBefore, deletion.all)
            assertEquals(settingsBefore, settings.all)
            assertEquals(metadataBefore, database.metadata().getAll())
            assertEquals(customBefore, database.customConversations().getConversations())
        } finally {
            database.close()
            drafts.edit().clear().commit(); deletion.edit().clear().commit(); settings.edit().clear().commit()
        }
    }

    @Test fun pickerCancellationNeverCapturesOrOpensAFile() = runBlocking {
        val result = exportPreviewDocument(null, { error("Must not read data after cancellation") }, { error("Must not open cancelled destination") })
        assertEquals(PreviewExportResult.CANCELLED, result)
    }

    @Test fun documentSuccessAndProviderFailuresHaveDistinctResults() = runBlocking {
        val snapshot = PreviewExportSnapshot(Instant.EPOCH, 4, exampleThreads, emptyList(), emptyList(),
            mapOf(1 to "Fabricated draft"), SampleDeletionState(), ThemeChoice.DEVICE)
        val destination = Uri.parse("content://fabricated-preview/document")
        val stream = ByteArrayOutputStream()
        assertEquals(PreviewExportResult.SAVED, exportPreviewDocument(destination, { snapshot }, { stream }))
        assertEquals("foldsms-fabricated-preview", JSONObject(stream.toString("UTF-8")).getString("format"))
        assertTrue(runCatching { exportPreviewDocument(destination, { snapshot }, { null }) }.exceptionOrNull() is IOException)
        var opened = false
        assertTrue(runCatching { exportPreviewDocument(destination, { throw IOException("Fabricated capture failure") }, { opened = true; stream }) }.isFailure)
        assertFalse(opened)
    }
}
