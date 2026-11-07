package com.axiomstead.foldsms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun CategoryPicker(
    category: String,
    onChoose: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    resetLabel: String = "Reset to sample default"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conversation category") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                conversationCategories.forEach { value ->
                    TextButton(onClick = { onChoose(value) }) {
                        Text(if (value == category) "$value (current)" else value)
                    }
                }
                TextButton(onClick = onReset) { Text(resetLabel) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
