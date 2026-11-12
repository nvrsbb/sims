package com.axiomstead.foldsms

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Only what the owner can actually change: appearance, Android permissions, and turning access off. */
@Composable
internal fun AppSettings(
    theme: ThemeChoice, onThemeChange: (ThemeChoice) -> Unit, onTurnOff: () -> Unit, onDismiss: () -> Unit,
    isDefaultApp: Boolean = false, onMakeDefault: (() -> Unit)? = null, onExport: (() -> Unit)? = null,
    blockedNumbers: List<String> = emptyList(), onUnblock: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val version = remember(context) { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("settings-done")) { Text("Done") }
                }
                Spacer(Modifier.height(8.dp))
                // Everything between the title and the version scrolls, so no setting is out of reach on the cover screen or at large fonts.
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text("Appearance", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
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
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Typing", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                var tidy by remember { mutableStateOf(TypingPrefs.tidy(context)) }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = tidy, role = Role.Switch, onClick = { tidy = !tidy; TypingPrefs.setTidy(context, tidy) }).testTag("settings-tidy"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Tidy punctuation")
                        Text("Adds a space after , . ! ? and capitalizes after a full stop. Web addresses and numbers are left alone.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = tidy, onCheckedChange = null)
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Default messaging app", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                if (isDefaultApp) {
                    Text("FoldSMS is your default app.", Modifier.padding(vertical = 4.dp).testTag("settings-default-status"))
                    TextButton(onClick = { context.startActivity(defaultAppsSettings()) }, modifier = Modifier.testTag("settings-switch-back")) { Text("Choose another app") }
                } else if (onMakeDefault != null) {
                    TextButton(onClick = onMakeDefault, modifier = Modifier.testTag("settings-make-default")) { Text("Make FoldSMS default") }
                }
                if (onUnblock != null && blockedNumbers.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Blocked numbers", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    blockedNumbers.forEach { number ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(number, Modifier.weight(1f).testTag("settings-blocked-$number"))
                            TextButton(onClick = { onUnblock(number) }, modifier = Modifier.testTag("settings-unblock-$number")) { Text("Unblock") }
                        }
                    }
                }
                if (onExport != null) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Backup", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onExport, modifier = Modifier.testTag("settings-export")) { Text("Export messages to a file") }
                }
                var entries by remember { mutableIntStateOf(Diagnostics.entryCount(context)) }
                if (entries > 0) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Troubleshooting", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    Text("$entries entries about failures. They contain no message text, numbers or names.", Modifier.padding(vertical = 4.dp).testTag("settings-diagnostics-count"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        TextButton(onClick = {
                            val report = "FoldSMS $version, Android ${android.os.Build.VERSION.SDK_INT}, ${android.os.Build.MODEL}\n" + Diagnostics.read(context)
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), "Share troubleshooting log"))
                        }, modifier = Modifier.testTag("settings-diagnostics-share")) { Text("Share") }
                        TextButton(onClick = { Diagnostics.clear(context); entries = 0 }, modifier = Modifier.testTag("settings-diagnostics-clear")) { Text("Clear") }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }, modifier = Modifier.testTag("settings-permissions")) { Text("Android permissions") }
                TextButton(onClick = onTurnOff, modifier = Modifier.testTag("settings-turn-off")) { Text("Turn off access") }
                }
                if (version.isNotEmpty()) Text("FoldSMS $version", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
