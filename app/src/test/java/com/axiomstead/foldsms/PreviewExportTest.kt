package com.axiomstead.foldsms

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PreviewExportTest {
    private val now = Instant.parse("2026-11-05T01:02:03Z")
    private val exact = "  Fabricated \"draft\"\n\tUnicode ₱ 👋 \\ \u0000  "
    private fun snapshot() = PreviewExportSnapshot(
        now, 4, exampleThreads,
        listOf(CustomSampleConversation(-2, "Sample Person", "+1 202-555-0199", "12025550199")),
        listOf(ConversationMetadata(-2, null, pinned = true, archived = true)),
        linkedMapOf(1 to exact, -9 to "Unattached sample draft", -2 to "", 2 to "Other draft"),
        SampleDeletionState(setOf(202, 101), setOf(101)), ThemeChoice.DARK
    )

    @Test fun jsonPreservesExactDraftsHiddenSamplesAndAllDurableMetadata() {
        val json = JSONObject(encodePreviewExport(snapshot()))
        assertEquals("foldsms-fabricated-preview", json.getString("format"))
        assertEquals(1, json.getInt("formatVersion"))
        assertEquals(4, json.getInt("roomSchemaVersion"))
        assertEquals("com.axiomstead.foldsms", json.getString("applicationId"))
        assertEquals(now.toString(), json.getString("capturedAt"))
        val drafts = json.getJSONArray("drafts")
        val texts = (0 until drafts.length()).associate { i ->
            drafts.getJSONObject(i).let { it.getInt("threadId") to it.getString("text") }
        }
        assertEquals(snapshot().drafts, texts)
        assertEquals(exact, texts[1])
        val metadata = json.getJSONArray("metadata").getJSONObject(0)
        assertTrue(metadata.isNull("category"))
        assertTrue(metadata.getBoolean("pinned")); assertTrue(metadata.getBoolean("archived"))
        assertEquals(-2, json.getJSONArray("customConversations").getJSONObject(0).getInt("id"))
        assertEquals("12025550199", json.getJSONArray("customConversations").getJSONObject(0).getString("normalizedAddress"))
        val messages = json.getJSONArray("fixtureConversations").getJSONObject(0).getJSONArray("messages")
        assertEquals(101, messages.getJSONObject(0).getInt("id")) // Hidden samples remain recoverable in the export.
        assertEquals(exampleThreads.first().history.size, messages.length())
        assertEquals(exampleThreads.first().history.first().timestamp.toString(), messages.getJSONObject(0).getString("timestamp"))
        assertEquals("INCOMING", messages.getJSONObject(0).getString("direction"))
        assertEquals("[101,202]", json.getJSONObject("sampleDeletion").getJSONArray("deletedMessageIds").toString())
        assertEquals("[101]", json.getJSONObject("sampleDeletion").getJSONArray("undoMessageIds").toString())
        assertEquals("DARK", json.getString("theme"))
    }

    @Test fun equivalentSnapshotsProduceStableOutputRegardlessOfStorageIterationOrder() {
        val first = snapshot()
        val second = first.copy(fixtures = first.fixtures.reversed().map { it.copy(messages = it.messages.reversed()) },
            drafts = first.drafts.toList().reversed().toMap(), deletion = SampleDeletionState(linkedSetOf(101, 202), setOf(101)))
        assertEquals(encodePreviewExport(first), encodePreviewExport(second))
    }

    @Test fun successfulWriteIsUtf8AndClosesTheDocument() {
        var closed = false
        val stream = object : ByteArrayOutputStream() { override fun close() { closed = true; super.close() } }
        writePreviewExport(stream, snapshot())
        assertTrue(closed)
        assertEquals(encodePreviewExport(snapshot()), stream.toString("UTF-8"))
    }

    @Test fun utf8ExportPreservesAnUnpairedUtf16CodeUnit() {
        val text = "Fabricated \uD800 draft"
        val stream = ByteArrayOutputStream()
        writePreviewExport(stream, snapshot().copy(drafts = mapOf(1 to text)))
        val decoded = JSONObject(stream.toString("UTF-8")).getJSONArray("drafts").getJSONObject(0).getString("text")
        assertEquals(text.toList(), decoded.toList())
    }

    @Test fun unavailableDocumentWriteFailureAndCloseFailureNeverReturnSuccess() {
        assertTrue(runCatching { writePreviewExport(null, snapshot()) }.exceptionOrNull() is IOException)
        var closed = false
        val failed = object : OutputStream() {
            override fun write(value: Int) { throw IOException("Fabricated write failure") }
            override fun close() { closed = true }
        }
        assertTrue(runCatching { writePreviewExport(failed, snapshot()) }.exceptionOrNull() is IOException)
        assertTrue(closed)
        val failedClose = object : ByteArrayOutputStream() { override fun close() { throw IOException("Fabricated close failure") } }
        assertTrue(runCatching { writePreviewExport(failedClose, snapshot()) }.exceptionOrNull() is IOException)
    }
}
