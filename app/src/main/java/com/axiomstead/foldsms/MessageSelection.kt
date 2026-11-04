package com.axiomstead.foldsms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
internal fun MessageSelectionActions(
    selecting: Boolean,
    selectedCount: Int,
    hasMessages: Boolean,
    busy: Boolean,
    onCategory: () -> Unit,
    onSelect: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        if (selecting) {
            TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel selection") }
            TextButton(onClick = onDelete, enabled = selectedCount > 0 && !busy,
                modifier = Modifier.testTag("delete-selected")) { Text("Delete selected ($selectedCount)") }
        } else {
            TextButton(onClick = onCategory) { Text("Change category") }
            TextButton(onClick = onSelect, enabled = hasMessages && !busy) { Text("Select messages") }
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
