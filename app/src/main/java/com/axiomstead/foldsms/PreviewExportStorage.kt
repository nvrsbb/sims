package com.axiomstead.foldsms

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.room.withTransaction
import java.io.OutputStream
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun capturePreviewExport(
    database: ConversationMetadataDatabase,
    draftPreferences: SharedPreferences,
    deletionPreferences: SharedPreferences,
    settingsPreferences: SharedPreferences,
    capturedAt: Instant = Instant.now()
): PreviewExportSnapshot = withContext(Dispatchers.IO) {
    database.withTransaction {
        val metadata = database.metadata().getAll()
        val custom = database.customConversations().getConversations()
        // Read each preference file once, so deletion and undo are from the same snapshot.
        val drafts = draftPreferences.all.mapNotNull { (key, value) ->
            val id = key.takeIf { it.startsWith("thread_") }?.removePrefix("thread_")?.toIntOrNull()
            if (id != null && value is String) id to value else null
        }.toMap()
        val deletion = deletionPreferences.all
        fun ids(key: String) = (deletion[key] as? Set<*>)?.mapNotNull { (it as? String)?.toIntOrNull() }?.toSet().orEmpty()
        val theme = ThemeChoice.fromStored(settingsPreferences.all["theme"] as? String)
        PreviewExportSnapshot(capturedAt, database.openHelper.readableDatabase.version, exampleThreads, custom, metadata, drafts,
            SampleDeletionState(ids("deleted_v1"), ids("undo_v1")), theme)
    }
}

internal suspend fun capturePreviewExport(context: Context): PreviewExportSnapshot = capturePreviewExport(
    sampleMetadataDatabase(context),
    context.getSharedPreferences("sample_drafts", Context.MODE_PRIVATE),
    context.getSharedPreferences("sample_deletions", Context.MODE_PRIVATE),
    context.getSharedPreferences("preview_settings", Context.MODE_PRIVATE)
)

internal enum class PreviewExportResult { CANCELLED, SAVED }

internal suspend fun exportPreviewDocument(
    destination: Uri?,
    capture: suspend () -> PreviewExportSnapshot,
    openDocument: (Uri) -> OutputStream?
): PreviewExportResult = withContext(Dispatchers.IO) {
    if (destination == null) return@withContext PreviewExportResult.CANCELLED
    val snapshot = capture()
    writePreviewExport(openDocument(destination), snapshot)
    PreviewExportResult.SAVED
}
