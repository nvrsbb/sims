package com.axiomstead.foldsms

import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/** Versioned preview data only; not a Telephony or carrier backup. */
internal data class PreviewExportSnapshot(
    val capturedAt: Instant,
    val roomSchemaVersion: Int,
    val fixtures: List<SampleConversation>,
    val customConversations: List<CustomSampleConversation>,
    val metadata: List<ConversationMetadata>,
    val drafts: Map<Int, String>,
    val deletion: SampleDeletionState,
    val theme: ThemeChoice
)

internal fun encodePreviewExport(snapshot: PreviewExportSnapshot): String {
    fun ids(values: Set<Int>) = JSONArray(values.sorted())
    return JSONObject().apply {
        put("format", "foldsms-fabricated-preview")
        put("formatVersion", 1)
        put("applicationId", "com.axiomstead.foldsms")
        put("roomSchemaVersion", snapshot.roomSchemaVersion)
        put("capturedAt", snapshot.capturedAt.toString())
        put("fixtureConversations", JSONArray().apply {
            snapshot.fixtures.sortedBy { it.id }.forEach { conversation ->
                put(JSONObject().apply {
                    put("id", conversation.id); put("name", conversation.sender)
                    put("address", conversation.address); put("defaultCategory", conversation.category)
                    put("messages", JSONArray().apply {
                        conversation.history.forEach { message -> put(JSONObject().apply {
                            put("id", message.id); put("body", message.body)
                            put("timestamp", message.timestamp.toString()); put("direction", message.direction.name)
                        }) }
                    })
                })
            }
        })
        put("customConversations", JSONArray().apply {
            snapshot.customConversations.sortedBy { it.id }.forEach { conversation -> put(JSONObject().apply {
                put("id", conversation.id); put("name", conversation.name)
                put("address", conversation.address); put("normalizedAddress", conversation.normalizedAddress)
            }) }
        })
        put("metadata", JSONArray().apply {
            snapshot.metadata.sortedBy { it.threadId }.forEach { metadata -> put(JSONObject().apply {
                put("threadId", metadata.threadId); put("category", metadata.category ?: JSONObject.NULL)
                put("pinned", metadata.pinned); put("archived", metadata.archived)
            }) }
        })
        put("drafts", JSONArray().apply {
            snapshot.drafts.toSortedMap().forEach { (id, text) -> put(JSONObject().apply {
                put("threadId", id); put("text", text)
            }) }
        })
        put("sampleDeletion", JSONObject().apply {
            put("deletedMessageIds", ids(snapshot.deletion.deletedIds))
            put("undoMessageIds", ids(snapshot.deletion.undoIds))
        })
        put("theme", snapshot.theme.name)
    }.toString(2).let { json ->
        // JSON escapes retain even isolated UTF-16 code units; UTF-8 encoding would replace them.
        buildString {
            json.forEach { ch ->
                if (ch.code in 0xD800..0xDFFF) append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                else append(ch)
            }
        }
    }
}

/** Success requires both the write and provider close to complete. Source data is immutable. */
internal fun writePreviewExport(output: OutputStream?, snapshot: PreviewExportSnapshot) {
    val document = output ?: throw IOException("No writable document returned")
    document.use { it.write(encodePreviewExport(snapshot).toByteArray(Charsets.UTF_8)); it.flush() }
}
