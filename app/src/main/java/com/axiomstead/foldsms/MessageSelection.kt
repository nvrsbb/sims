package com.axiomstead.foldsms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Shown in place of the conversation menu while messages are being selected. */
@Composable
internal fun MessageSelectionActions(selectedCount: Int, busy: Boolean, onCancel: () -> Unit, onDelete: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
            TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel selection") }
            TextButton(onClick = onDelete, enabled = selectedCount > 0 && !busy,
                modifier = Modifier.testTag("delete-selected")) { Text("Delete selected ($selectedCount)") }
        }
    }
}

@Composable
internal fun SampleDeleteConfirmation(count: Int, scope: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete sample messages?") },
        text = { Text("Delete $count sample messages $scope? Drafts stay saved. You can undo the latest deletion, including after reopening the app. A new deletion replaces that undo.") },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep messages") } },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm-delete")) { Text("Delete") } }
    )
}
