package com.axiomstead.foldsms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun PreviewSettings(theme: ThemeChoice, onThemeChange: (ThemeChoice) -> Unit, onDismiss: () -> Unit, onOpenSms: (() -> Unit)? = null) {
    var exporting by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = { if (!exporting) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Preview settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    TextButton(enabled = !exporting, onClick = onDismiss) { Text("Done") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Text("Device follows Android’s light or dark appearance.")
                    Column(Modifier.selectableGroup()) {
                        ThemeChoice.entries.forEach { choice ->
                            Row(
                                Modifier.fillMaxWidth().selectable(enabled = !exporting, selected = theme == choice, role = Role.RadioButton, onClick = { onThemeChange(choice) }).padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(enabled = !exporting, selected = theme == choice, onClick = null)
                                Spacer(Modifier.width(12.dp))
                                Text(choice.label)
                            }
                        }
                    }
                    HorizontalDivider()
                    Text("Privacy and storage", style = MaterialTheme.typography.titleMedium)
                    Text("Conversations and message histories are fabricated samples. Drafts and preview preferences stay in this app’s local storage. This preview has no internet permission, account, analytics, or automatic cloud backup.")
                    Text("Clearing app data or uninstalling removes local drafts and preferences. Preview export is available below; importing and restoring are not implemented.")
                    PreviewExportSection(onBusyChange = { exporting = it })
                    Text("Preview capabilities", style = MaterialTheme.typography.titleMedium)
                    Text("Available: browse fabricated conversations, filter samples, inspect and copy sample text, and save local drafts.")
                    Text("Sending from sample conversations is disabled. Optional SMS companion mode can read device SMS and send through your selected SIM after explicit access and permissions. It does not handle MMS, contacts or notifications. Sample sent labels do not represent real delivery.")
                    Text("Device SMS", style = MaterialTheme.typography.titleMedium)
                    Text("SMS companion data is separate from sample drafts. Preview export excludes device SMS, the SMS composer and its send journal.")
                    onOpenSms?.let { open -> TextButton(enabled = !exporting, onClick = open) { Text("Open SMS companion") } }
                    Text("Default messaging app", style = MaterialTheme.typography.titleMedium)
                    Text("Keep Google Messages as default for provider storage, MMS and notifications. FoldSMS cannot yet replace it; MMS, backup/restore and carrier checks remain. SMS access is requested only inside the opt-in companion screen, never on preview launch.")
                }
            }
        }
    }
}
