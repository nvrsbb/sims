package com.axiomstead.foldsms

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Save a value, not a MutableState reference that cancellation cleanup can still change.
private val writingCheckpointSaver = Saver<MutableState<Boolean>, Boolean>(
    save = { it.value }, restore = { mutableStateOf(it) }
)

@Composable
internal fun PreviewExportSection(
    onBusyChange: (Boolean) -> Unit,
    exportDocument: (suspend (Uri?) -> PreviewExportResult)? = null
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var writing by rememberSaveable(saver = writingCheckpointSaver) { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    val interrupted = remember { writing }
    LaunchedEffect(Unit) {
        if (interrupted) {
            writing = false
            status = "The previous export was interrupted. Check the selected file and try again. Your local preview data is unchanged."
        }
    }
    fun setBusy(value: Boolean) { busy = value; onBusyChange(value) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        setBusy(true)
        writing = uri != null
        scope.launch {
            try {
                status = if (uri == null) null else "Exporting preview data…"
                val result = if (exportDocument != null) exportDocument(uri) else exportPreviewDocument(uri, { capturePreviewExport(context) }) {
                    context.contentResolver.openOutputStream(it, "wt")
                }
                status = if (result == PreviewExportResult.SAVED) "Preview file exported. Import and restore are not available yet." else "Export cancelled. No preview data changed."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                status = "Export did not finish. The selected file may be incomplete. Your local preview data is unchanged; please try again."
            } finally { writing = false; setBusy(false) }
        }
    }
    // A document picker survives recreation, but a cancelled writing coroutine does not.
    DisposableEffect(Unit) { onDispose { onBusyChange(false) } }
    PreviewExportControl(busy, status) {
        setBusy(true)
        status = null
        try { picker.launch("foldsms-preview.json") }
        catch (_: ActivityNotFoundException) {
            setBusy(false)
            status = "Android’s file chooser is unavailable. No preview data changed."
        }
    }
}

@Composable
internal fun PreviewExportControl(busy: Boolean, status: String?, onChooseFile: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    TextButton(enabled = !busy, onClick = { confirm = true }) { Text("Export preview data") }
    status?.let { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Export preview data?") },
        text = { Text("The unencrypted file includes drafts, custom sample conversations, categories, pins/archive, appearance, and all fabricated histories including hidden/deleted samples and undo state. Choose where Android saves it. This is not a real SMS/MMS backup. Import and restore are not available yet.", Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { confirm = false; onChooseFile() }) { Text("Choose file") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
    )
}
