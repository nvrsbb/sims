package com.axiomstead.foldsms

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The export flow: choose what to include, pick a file, watch progress, see the result. Read-only; changes nothing in the provider. */
@Stable
internal class ExportFlow(
    private val state: DeviceMessagesState, private val launch: (String) -> Unit,
    private val run: (Uri, Boolean) -> Unit, private val cancelRequested: AtomicBoolean
) {
    var choosing by mutableStateOf(false)
    var status by mutableStateOf<String?>(null)
    var running by mutableStateOf(false)
    internal var withAttachments = false

    fun start() { choosing = true }
    fun choose(includeAttachments: Boolean) { withAttachments = includeAttachments; choosing = false; launch(backupFileName(Instant.now())) }
    fun picked(uri: Uri) = run(uri, withAttachments)
    fun cancel() { cancelRequested.set(true) }
    fun dismiss() { status = null }

    @Composable
    fun Dialogs() {
        if (choosing) AlertDialog(
            onDismissRequest = { choosing = false }, title = { Text("Export messages") },
            text = { Column {
                Text("Saves your SMS and MMS to a file you choose, in the layout used by SMS Backup & Restore. Nothing is changed or deleted. Pictures make the file much larger. Keep the app open until it finishes.")
                TextButton(onClick = { choose(false) }, modifier = Modifier.testTag("export-text-only")) { Text("Messages only") }
                TextButton(onClick = { choose(true) }, modifier = Modifier.testTag("export-with-attachments")) { Text("With pictures and attachments") }
            } },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text("Cancel") } }
        )
        status?.let { message ->
            AlertDialog(
                onDismissRequest = { if (!running) dismiss() }, title = { Text(if (running) "Exporting…" else "Export") },
                text = { Text(message, Modifier.testTag("export-status")) },
                confirmButton = {
                    if (running) TextButton(onClick = { cancel() }, modifier = Modifier.testTag("export-cancel")) { Text("Cancel") }
                    else TextButton(onClick = { dismiss() }, modifier = Modifier.testTag("export-done")) { Text("Done") }
                }
            )
        }
    }
}

@Composable
/** [fixedTarget] replaces the system file picker; tests use it to write to a file they can read back. */
internal fun rememberExportFlow(state: DeviceMessagesState, fixedTarget: (() -> Uri)? = null): ExportFlow {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cancelRequested = remember { AtomicBoolean(false) }
    lateinit var flow: ExportFlow
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/xml")) { uri -> if (uri != null) flow.picked(uri) }
    flow = remember(state) {
        ExportFlow(state, launch = { name -> if (fixedTarget != null) flow.picked(fixedTarget()) else create.launch(name) }, cancelRequested = cancelRequested, run = { uri, attachments ->
            cancelRequested.set(false)
            flow.running = true; flow.status = "Reading messages…"
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        val sms = state.services.platform.recent(Int.MAX_VALUE)
                        val mms = state.services.mms?.takeIf { it.canRead() }
                        val mmsRows = mms?.recent(Int.MAX_VALUE).orEmpty()
                        val names = contactNameMap(state.contacts)
                        val out = context.contentResolver.openOutputStream(uri, "w") ?: error("Could not open the file")
                        out.buffered(64 * 1024).use { stream ->
                            writeBackup(stream, sms, mmsRows, attachments, partData = { id, o -> mms?.partData(id, o) ?: false },
                                nameFor = { names[conversationAddressKey(it)] }, now = Instant.now(),
                                onProgress = { done, total -> flow.status = "Writing $done of $total" }, cancelled = { cancelRequested.get() })
                        }
                    }
                    if (cancelRequested.get()) {
                        withContext(Dispatchers.IO) { try { DocumentsContract.deleteDocument(context.contentResolver, uri) } catch (_: Exception) { } }
                        flow.status = "Cancelled. The incomplete file was removed."
                    } else {
                        flow.status = "Saved ${result.sms} SMS and ${result.mms} MMS." +
                            (if (attachments) " ${result.attachments} attachments included" + (if (result.skippedAttachments > 0) ", ${result.skippedAttachments} could not be read." else ".") else "")
                        Diagnostics.record(context, "EXPORT_DONE", "sms=${result.sms} mms=${result.mms} attachments=${result.attachments} skipped=${result.skippedAttachments}")
                    }
                } catch (failure: Exception) {
                    Diagnostics.record(context, "EXPORT_FAILED", failure.javaClass.name)
                    withContext(Dispatchers.IO) { try { DocumentsContract.deleteDocument(context.contentResolver, uri) } catch (_: Exception) { } }
                    flow.status = "The export failed and the incomplete file was removed. Nothing was changed."
                } finally { flow.running = false }
            }
        })
    }
    return flow
}
