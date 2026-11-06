package com.axiomstead.foldsms

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun SampleMessage.detailSender(conversationSender: String): String =
    if (direction == MessageDirection.OUTGOING) "You" else conversationSender

internal val SampleMessage.fullTimestamp: String
    get() = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss 'Asia/Manila (UTC+08:00)'", Locale.ENGLISH)
        .withZone(ZoneId.of("Asia/Manila")).format(timestamp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessageDetailsSheet(message: SampleMessage, sender: String, onDismiss: () -> Unit, onCopy: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Message details", style = MaterialTheme.typography.headlineSmall)
            Text(message.body, style = MaterialTheme.typography.bodyLarge)
            HorizontalDivider()
            Text("Sender: ${message.detailSender(sender)}")
            Text("Time: ${message.fullTimestamp}")
            Text("Direction: ${if (message.direction == MessageDirection.OUTGOING) "Outgoing" else "Incoming"}")
            Text("Fabricated sample · No real delivery or SIM information", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    // The seam lets tests check what is copied without reading the system clipboard, which Android only allows for the focused window.
                    if (onCopy != null) onCopy(message.body) else {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Sample message", message.body))
                    }
                }) { Text("Copy text") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}
