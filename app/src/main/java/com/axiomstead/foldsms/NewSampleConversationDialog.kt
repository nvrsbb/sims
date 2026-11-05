package com.axiomstead.foldsms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun NewSampleConversationDialog(
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    recoveryDraft: String? = null,
    onCreate: (String, String, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var draft by rememberSaveable { mutableStateOf(recoveryDraft.orEmpty()) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (recoveryDraft == null) "New sample conversation" else "Recover saved draft") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Use a fabricated name and address only. This creates a local preview with an unsent draft. It does not contact anyone or create sent messages.")
                OutlinedTextField(name, { name = it }, label = { Text("Sample name") }, singleLine = true, enabled = !saving)
                OutlinedTextField(address, { address = it }, label = { Text("Fabricated address") }, singleLine = true, enabled = !saving,
                    supportingText = { Text("Include any country code. Formatting is ignored when checking for duplicates.") })
                OutlinedTextField(draft, { draft = it }, label = { Text("Initial draft (optional)") }, minLines = 2, maxLines = 5, enabled = !saving, readOnly = recoveryDraft != null)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = !saving, onClick = { onCreate(name, address, draft) }) { Text(if (saving) "Saving…" else if (recoveryDraft == null) "Create sample" else "Recover draft") } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } }
    )
}
