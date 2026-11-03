package com.axiomstead.foldsms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun PreviewSettings(theme: ThemeChoice, onThemeChange: (ThemeChoice) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Preview settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Text("Device follows Android’s light or dark appearance.")
                    Column(Modifier.selectableGroup()) {
                        ThemeChoice.entries.forEach { choice ->
                            Row(
                                Modifier.fillMaxWidth().selectable(selected = theme == choice, role = Role.RadioButton, onClick = { onThemeChange(choice) }).padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = theme == choice, onClick = null)
                                Spacer(Modifier.width(12.dp))
                                Text(choice.label)
                            }
                        }
                    }
                    HorizontalDivider()
                    Text("Privacy and storage", style = MaterialTheme.typography.titleMedium)
                    Text("Conversations and message histories are fabricated samples. Drafts and preview preferences stay in this app’s local storage. This preview has no internet permission, account, analytics, or cloud backup.")
                    Text("Clearing app data or uninstalling removes local drafts and preferences. Export and restore are not implemented.")
                    Text("Preview capabilities", style = MaterialTheme.typography.titleMedium)
                    Text("Available: browse fabricated conversations, filter samples, inspect and copy sample text, and save local drafts.")
                    Text("Sending is disabled. This preview cannot read, receive, or send SMS/MMS, access contacts, or provide message notifications. Sample sent labels do not represent real delivery.")
                    Text("Default messaging app", style = MaterialTheme.typography.titleMedium)
                    Text("Keep Google Messages as your default. A future SMS milestone needs explicit authorization and tested sending, receiving, MMS, notifications, and backup restoration before replacement. This screen does not request the default SMS role or permissions.")
                }
            }
        }
    }
}
