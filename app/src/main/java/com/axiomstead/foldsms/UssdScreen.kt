package com.axiomstead.foldsms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Balance and carrier codes: pick a saved code, run it on a SIM, read the one answer. Codes are the owner's own and editable; none are
 * guessed. A reply is shown here only and is gone when the screen closes.
 */
@Composable
internal fun UssdScreen(
    codes: List<UssdCode>, sims: List<SmsSubscription>,
    onRun: (code: String, subscriptionId: Int?, onResult: (UssdResult) -> Unit) -> Unit,
    onSave: (UssdCode) -> Boolean, onRemove: (String) -> Unit, onDismiss: () -> Unit
) {
    var simId by rememberSaveable { mutableStateOf<Int?>(null) }
    var waiting by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<Pair<String, UssdResult>?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    val chosenSim = simId ?: sims.singleOrNull()?.id
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("ussd-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    Text("Balance and codes", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { adding = true }, modifier = Modifier.testTag("ussd-add")) { Icon(Icons.Default.Add, contentDescription = "Add code") }
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    Text("Sends the code to your carrier and shows its answer here. Your carrier may charge for some codes.", style = MaterialTheme.typography.bodyMedium)
                    if (sims.size > 1) {
                        Spacer(Modifier.height(12.dp))
                        Text("SIM", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            sims.forEach { sim ->
                                FilterChip(selected = chosenSim == sim.id, onClick = { simId = sim.id }, modifier = Modifier.testTag("ussd-sim-${sim.id}"),
                                    label = { Text(sim.label.ifBlank { "SIM ${sim.slot + 1}" }) })
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (codes.isEmpty()) Text("No codes yet. Tap + to add one.", Modifier.padding(vertical = 8.dp))
                    codes.forEach { entry ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(entry.label); Text(entry.code, style = MaterialTheme.typography.bodySmall) }
                            TextButton(onClick = { removing = entry.code }, modifier = Modifier.testTag("ussd-remove")) { Text("Remove") }
                            Button(
                                enabled = waiting == null && (sims.size <= 1 || chosenSim != null), modifier = Modifier.testTag("ussd-run"),
                                onClick = {
                                    waiting = entry.code; result = null
                                    onRun(entry.code, chosenSim) { outcome -> waiting = null; result = entry.label to outcome }
                                }
                            ) { Text(if (waiting == entry.code) "Waiting…" else "Check") }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    if (sims.size > 1 && chosenSim == null) Text("Choose a SIM first.", Modifier.padding(top = 8.dp))
                    result?.let { (label, outcome) ->
                        Spacer(Modifier.height(16.dp))
                        Card(Modifier.fillMaxWidth().testTag("ussd-result")) {
                            Column(Modifier.padding(16.dp)) {
                                Text(label, style = MaterialTheme.typography.titleSmall)
                                Text(when (outcome) { is UssdResult.Reply -> outcome.text; is UssdResult.Failed -> outcome.reason })
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding) {
        var label by rememberSaveable { mutableStateOf("") }
        var code by rememberSaveable { mutableStateOf("") }
        val valid = label.isNotBlank() && isValidUssd(code)
        AlertDialog(
            onDismissRequest = { adding = false }, title = { Text("Add a code") },
            text = {
                Column {
                    OutlinedTextField(label, { label = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.testTag("ussd-new-label"))
                    OutlinedTextField(code, { code = it.filter { c -> c.isDigit() || c == '*' || c == '#' } }, label = { Text("Code, like *123#") },
                        singleLine = true, modifier = Modifier.testTag("ussd-new-code"))
                }
            },
            confirmButton = { TextButton(enabled = valid, onClick = { if (onSave(UssdCode(label, code))) adding = false }, modifier = Modifier.testTag("ussd-new-save")) { Text("Save") } },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } }
        )
    }
    removing?.let { target ->
        AlertDialog(
            onDismissRequest = { removing = null }, title = { Text("Remove this code?") }, text = { Text(target) },
            confirmButton = { TextButton(onClick = { onRemove(target); removing = null }, modifier = Modifier.testTag("ussd-remove-confirm")) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep") } }
        )
    }
}
