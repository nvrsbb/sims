package com.axiomstead.foldsms

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Only what the owner can actually change: appearance, Android permissions, and turning access off. */
@Composable
internal fun AppSettings(theme: ThemeChoice, onThemeChange: (ThemeChoice) -> Unit, onTurnOff: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version = remember(context) { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("settings-done")) { Text("Done") }
                }
                Spacer(Modifier.height(8.dp))
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
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
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }, modifier = Modifier.testTag("settings-permissions")) { Text("Android permissions") }
                TextButton(onClick = onTurnOff, modifier = Modifier.testTag("settings-turn-off")) { Text("Turn off access") }
                Spacer(Modifier.weight(1f))
                if (version.isNotEmpty()) Text("FoldSMS $version", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
