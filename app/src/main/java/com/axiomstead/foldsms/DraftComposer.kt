package com.axiomstead.foldsms

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DraftComposer(draft: String, onDraftChange: (String) -> Unit) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = draft,
        onValueChange = onDraftChange,
        label = { Text("Draft message") },
        placeholder = { Text("Write a draft…") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 5,
        shape = RoundedCornerShape(14.dp),
        supportingText = { Text("Saved on this device · Sending disabled", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    )
    if (draft.isNotEmpty()) {
        TextButton(onClick = { confirmClear = true }) { Text("Clear draft") }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear this draft?") },
            text = { Text("Only this conversation's draft will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onDraftChange(""); confirmClear = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep draft") } }
        )
    }
}
