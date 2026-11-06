package com.axiomstead.foldsms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Telegram-style pill composer. Sample sending stays disabled; text is only saved as a local draft. */
@Composable
internal fun DraftComposer(draft: String, onDraftChange: (String) -> Unit) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 4.dp)) {
            TextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = { Text("Draft message") },
                modifier = Modifier.fillMaxWidth().testTag("draft-composer"),
                minLines = 1,
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                )
            )
            Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Saved on this device · Sending disabled", Modifier.weight(1f).padding(start = 12.dp),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (draft.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Clear draft") }
            }
        }
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
