package com.axiomstead.foldsms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The contacts of the phone: search, open a person, message a number, edit in the system editor, add a new contact. Nothing is
 * stored by FoldSMS; the list comes from the phone's own contacts, so changes made here or in any other app show everywhere.
 */
@Composable
internal fun ContactsScreen(
    groups: List<ContactGroup>, canRead: Boolean,
    onMessage: (String) -> Unit, onCall: (String) -> Unit = {}, onEdit: (ContactGroup) -> Unit, onAdd: () -> Unit, onAllow: () -> Unit, onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = remember(groups, query) { filterGroups(groups, query) }
    val open = groups.firstOrNull { "${it.contactId}:${it.name}" == openId }
    Dialog(onDismissRequest = { if (open != null) openId = null else onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (open != null) openId = null else onDismiss() }, modifier = Modifier.testTag("contacts-back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(open?.name ?: "Contacts", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (open == null && canRead) IconButton(onClick = onAdd, modifier = Modifier.testTag("contacts-add")) { Icon(Icons.Default.Add, contentDescription = "Add contact") }
                }
                when {
                    !canRead -> Column(Modifier.padding(20.dp)) {
                        Text("Allow contacts to see and search your contacts here.")
                        Button(onClick = onAllow, modifier = Modifier.testTag("contacts-allow")) { Text("Allow contacts") }
                    }
                    open != null -> Column(Modifier.padding(horizontal = 16.dp)) {
                        open.numbers.forEach { number ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(number, Modifier.weight(1f).testTag("contacts-number"))
                                if (dialableNumber(number) != null) TextButton(onClick = { onCall(number) }, modifier = Modifier.testTag("contacts-call")) { Text("Call") }
                                TextButton(onClick = { onMessage(number) }, modifier = Modifier.testTag("contacts-message")) { Text("Message") }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                        if (open.lookupKey.isNotBlank()) TextButton(onClick = { onEdit(open) }, modifier = Modifier.testTag("contacts-edit")) { Text("Edit contact") }
                    }
                    else -> Column {
                        TextField(
                            value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("Search contacts") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, contentDescription = "Clear search") } },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("contacts-search"), shape = RoundedCornerShape(50),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                        when {
                            groups.isEmpty() -> Text("No contacts yet.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            shown.isEmpty() -> Text("No contacts match.", Modifier.padding(20.dp).testTag("contacts-none"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else -> LazyColumn(Modifier.fillMaxSize().testTag("contacts-list")) {
                                items(shown, key = { "${it.contactId}:${it.name}" }) { group ->
                                    Row(
                                        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { openId = "${group.contactId}:${group.name}" }
                                            .padding(horizontal = 16.dp, vertical = 8.dp).testTag("contacts-row-${group.name}"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ConversationAvatar(group.name, group.name.hashCode(), 40.dp)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(group.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(group.numbers.first() + if (group.numbers.size > 1) " +${group.numbers.size - 1}" else "",
                                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
