package com.axiomstead.foldsms

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** List plus conversation: side by side when wide, one at a time on a phone-width screen. */
@Composable
internal fun DeviceInboxHost(
    state: DeviceMessagesState,
    wide: Boolean,
    metadataStore: DeviceMetadataStore?,
    onOpenSettings: () -> Unit,
    onRequestPermissions: () -> Unit,
    permissionsAsked: Boolean = false,
    now: () -> Instant = { Instant.now() },
    zone: ZoneId = ZoneId.systemDefault(),
    onMakeDefault: (() -> Unit)? = null,
    launchRecipient: String? = null,
    onLaunchRecipientHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val store = metadataStore ?: remember(context) { DeviceMetadataStore.get(context) }
    val scope = rememberCoroutineScope()
    val metadata by store.all.collectAsState(initial = emptyMap())
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    // Held here, not in the list screen: the list leaves composition while a phone-width conversation is open.
    var search by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    var showArchive by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("All") }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    var newMessageDialog by rememberSaveable { mutableStateOf(false) }
    var pendingAddress by rememberSaveable { mutableStateOf<String?>(null) }
    val prompt = state.services.defaultPrompt
    var promptDismissed by rememberSaveable { mutableStateOf(prompt?.dismissed() ?: false) }
    val offer = if (onMakeDefault != null && shouldOfferDefault(state.access, promptDismissed, roleAvailable = true))
        DefaultOffer(onMakeDefault) { promptDismissed = true; prompt?.dismiss() } else null
    var deleteTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val actions = state.services.actions
    val arranged = arrangeDeviceConversations(state.conversations, metadata, showArchive, filter)
    val openReal = state.conversations.firstOrNull { it.key == openKey }
    // A number with no history yet: an empty conversation that only exists on screen until the provider has a thread for it.
    val synthetic = pendingAddress?.takeIf { openReal == null }?.let { DeviceConversation("new-${conversationAddressKey(it)}", 0, it, emptyList()) }
    val open = openReal ?: synthetic
    LaunchedEffect(pendingAddress, state.conversations) {
        val pending = pendingAddress ?: return@LaunchedEffect
        state.conversations.firstOrNull { sameRecipient(it.address, pending) }?.let { openKey = it.key; pendingAddress = null }
    }
    val startConversation = { number: String ->
        val existing = state.conversations.firstOrNull { sameRecipient(it.address, number) }
        if (existing != null) { openKey = existing.key; pendingAddress = null; showArchive = (metadata[existing.key]?.archived == true) }
        else { openKey = null; pendingAddress = number }
    }
    // A number handed over by another app (sms:/smsto: link). Same validation as typing it; nothing is sent.
    LaunchedEffect(launchRecipient, state.access.enabled) {
        val number = launchRecipient ?: return@LaunchedEffect
        runCatching { smsAddress(number) }.onSuccess { startConversation(it) }
        onLaunchRecipientHandled()
    }
    if (newMessageDialog) NewMessageDialog(
        onDismiss = { newMessageDialog = false },
        onStart = { number -> newMessageDialog = false; startConversation(number) },
        contacts = state.contacts, contactsCanRead = state.contactsCanRead || state.services.contacts == null,
        onAllowContacts = onRequestPermissions
    )
    val canManage = state.access.isDefaultApp && actions?.canModify() == true
    LaunchedEffect(openReal?.key, openReal?.unreadCount, canManage) {
        val thread = openReal ?: return@LaunchedEffect
        if (canManage && thread.unreadCount > 0) { withContext(Dispatchers.IO) { actions?.markThreadRead(thread.threadId) }; state.refresh() }
    }
    deleteTarget?.let { key ->
        val thread = state.conversations.firstOrNull { it.key == key }
        AlertDialog(
            onDismissRequest = { deleteTarget = null }, title = { Text("Delete this conversation?") },
            text = { Text("All ${thread?.messages?.size ?: 0} messages with ${thread?.title.orEmpty()} are removed from this phone. This cannot be undone.") },
            confirmButton = { TextButton(modifier = Modifier.testTag("conversation-confirm-delete"), onClick = {
                deleteTarget = null
                if (thread != null) scope.launch {
                    val done = withContext(Dispatchers.IO) { actions?.deleteThread(thread.threadId) == true }
                    if (done) { openKey = null; pendingAddress = null; state.error = null } else state.error = "Could not delete the conversation."
                    state.refresh()
                }
            }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Keep") } }
        )
    }
    if (showCategories && open != null) CategoryPicker(
        category = effectiveCategory(open, metadata[open.key]),
        onChoose = { category -> scope.launch { store.setCategory(open.key, category) }; showCategories = false },
        onReset = { scope.launch { store.setCategory(open.key, null) }; showCategories = false },
        onDismiss = { showCategories = false },
        resetLabel = "Automatic"
    )
    val conversationActions = openReal?.let { thread ->
        val flags = metadata[thread.key]
        listOf(
            MenuAction("Change category") { showCategories = true },
            MenuAction(if (flags?.pinned == true) "Unpin conversation" else "Pin conversation") {
                scope.launch { store.setPinned(thread.key, flags?.pinned != true) }
            },
            MenuAction(if (flags?.archived == true) "Restore conversation" else "Archive conversation") {
                scope.launch { store.setArchived(thread.key, flags?.archived != true); openKey = null; pendingAddress = null }
            }
        ) + if (canManage) listOfNotNull(
            if (thread.unreadCount > 0) MenuAction("Mark as read") { scope.launch { withContext(Dispatchers.IO) { actions?.markThreadRead(thread.threadId) }; state.refresh() } } else null,
            MenuAction("Delete conversation") { deleteTarget = thread.key }
        ) else emptyList()
    }
    val fullScreen = !wide && open != null
    BackHandler(enabled = fullScreen) { openKey = null; pendingAddress = null }
    val simLabel = { message: DeviceMessage ->
        if (state.subscriptions.size > 1) state.subscriptions.firstOrNull { it.id == message.subscriptionId }?.let { "SIM ${it.slot + 1}" } else null
    }
    val loadImage: suspend (Long) -> ImageBitmap? = { id -> withContext(Dispatchers.IO) { state.services.mms?.image(id)?.asImageBitmap() } }
    Row(Modifier.fillMaxSize()) {
        if (wide || !fullScreen) Box(Modifier.weight(1f).fillMaxHeight()) {
            DeviceInboxScreen(
                state, arranged, metadata, open?.key, { openKey = it; pendingAddress = null }, search, { search = it }, listState,
                showArchive, { showArchive = !showArchive; openKey = null; pendingAddress = null }, filter, { filter = it },
                onOpenSettings, onRequestPermissions, permissionsAsked, now, zone, onNewMessage = { newMessageDialog = true }, offer = offer
            )
        }
        if (wide) VerticalDivider()
        if (open != null) Box(Modifier.weight(1f).fillMaxHeight()) {
            DeviceConversationScreen(open, simLabel, loadImage, onBack = if (wide) null else ({ openKey = null; pendingAddress = null }), now = now, zone = zone,
                actions = conversationActions.orEmpty(), composer = { ReplyComposer(state, open) })
        } else if (wide) Box(Modifier.weight(1f).fillMaxHeight().background(chatBackground()), contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)) {
                Text("Choose a conversation", Modifier.padding(horizontal = 14.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun DeviceInboxScreen(
    state: DeviceMessagesState,
    arranged: List<DeviceConversation>,
    metadata: Map<String, DeviceConversationMetadata>,
    selectedKey: String?,
    onOpen: (String) -> Unit,
    search: String,
    onSearch: (String) -> Unit,
    listState: LazyListState,
    showArchive: Boolean,
    onToggleArchive: () -> Unit,
    filter: String,
    onFilter: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onRequestPermissions: () -> Unit,
    permissionsAsked: Boolean,
    now: () -> Instant = { Instant.now() },
    zone: ZoneId = ZoneId.systemDefault(),
    onNewMessage: () -> Unit = {},
    offer: DefaultOffer? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val conversations = searchDeviceConversations(arranged, search)
    val access = state.access
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = search,
                onValueChange = onSearch,
                placeholder = { Text(if (showArchive) "Search archived" else "Search messages") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { onSearch("") }) { Icon(Icons.Default.Clear, contentDescription = "Clear search") } },
                modifier = Modifier.weight(1f).testTag("device-inbox-search"),
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                )
            )
            OverflowMenu("device-inbox-actions", "menu", listOf(
                MenuAction(if (showArchive) "Inbox" else "Archived", onClick = onToggleArchive),
                MenuAction("Settings", onClick = onOpenSettings)
            ))
        }
        // One row, no scrolling: each chip gets space in proportion to its label, and "Unknown" only appears if something has it.
        val chipCategories = listOf("All") + (conversationCategories - "Unknown") +
            (if (arranged.any { effectiveCategory(it, metadata[it.key]) == "Unknown" } || filter == "Unknown") listOf("Unknown") else emptyList())
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).testTag("device-category-filters"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            chipCategories.forEach { c ->
                val label = categoryChipLabel(c)
                Surface(
                    onClick = { onFilter(c) },
                    modifier = Modifier.weight(label.length + 2f).testTag("device-category-filter-$c"),
                    shape = RoundedCornerShape(8.dp),
                    color = if (c == filter) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (c == filter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(label, Modifier.padding(horizontal = 2.dp, vertical = 7.dp), fontSize = 12.sp, maxLines = 1, softWrap = false,
                        overflow = TextOverflow.Clip, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = if (c == filter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            if (!access.messagingSupported) Text("This device has no SMS service.", color = MaterialTheme.colorScheme.error)
            if (access.messagingSupported && !access.hasDefaultApp) Text("Choose a default SMS app in Android settings.", color = MaterialTheme.colorScheme.error)
            if (access.messagingSupported && access.hasDefaultApp && !access.canRead) {
                Text("Allow SMS access to see your messages.")
                Row {
                    Button(onClick = onRequestPermissions, modifier = Modifier.testTag("device-request-permissions")) { Text("Allow") }
                    if (permissionsAsked) TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    }, modifier = Modifier.testTag("device-open-app-settings")) { Text("Open settings") }
                }
            }
            if (access.canRead && offer != null) {
                Text("Make FoldSMS your default messaging app to receive and manage your messages here.")
                Row {
                    Button(onClick = offer.onMakeDefault, modifier = Modifier.testTag("device-make-default")) { Text("Make default") }
                    TextButton(onClick = offer.onNotNow, modifier = Modifier.testTag("device-default-not-now")) { Text("Not now") }
                }
            }
            if (access.canRead && !state.mmsCanRead) Text("Allow MMS access to see pictures.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (access.canRead && !access.canSend) Text("Allow SMS and phone access to send.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("device-inbox-error")) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !access.canRead -> Unit
                !state.hasLoaded -> Text("Loading messages…", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.conversations.isEmpty() -> Text("No messages yet.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                arranged.isEmpty() -> Text(
                    when {
                        showArchive && filter == "All" -> "Archive is empty"
                        filter != "All" -> "No conversations in this category"
                        else -> "Everything is archived."
                    }, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                conversations.isEmpty() -> Text("No conversations match", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyColumn(Modifier.fillMaxSize().testTag("device-inbox-list"), state = listState, contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(conversations, key = { it.key }) { conversation ->
                        DeviceConversationRow(conversation, formatConversationTime(conversation.last.date, now(), zone, Locale.getDefault()),
                            selected = conversation.key == selectedKey, category = effectiveCategory(conversation, metadata[conversation.key]),
                            pinned = metadata[conversation.key]?.pinned == true, onClick = { onOpen(conversation.key) })
                        HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            // Declared after the list so it is drawn, and hit-tested, on top of it.
            if (access.canSend) FloatingActionButton(
                onClick = onNewMessage,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("device-new-message"),
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary
            ) { Icon(Icons.Default.Edit, contentDescription = "New message") }
        }
    }
}

@Composable
internal fun DeviceConversationRow(conversation: DeviceConversation, time: String, selected: Boolean, category: String, pinned: Boolean, onClick: () -> Unit) {
    val last = conversation.last
    val unread = conversation.unreadCount
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .testTag("device-thread-${conversation.key}").padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConversationAvatar(conversation.title, conversation.key.hashCode(), 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(conversation.title, fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                Text(time, style = MaterialTheme.typography.labelSmall,
                    color = if (unread > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val prefix = if (last.outgoing) "You: " else ""
                Text(prefix + last.preview.lineSequence().first(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (pinned) Text("Pinned", Modifier.padding(start = 6.dp).testTag("device-pinned-${conversation.key}"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                if (last.failed) Text("Failed", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                if (unread > 0) Box(
                    Modifier.padding(start = 8.dp).defaultMinSize(minWidth = 22.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                        .testTag("device-unread-${conversation.key}"),
                    contentAlignment = Alignment.Center
                ) { Text(if (unread > 99) "99+" else unread.toString(), Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary) }
            }
            Text(category, Modifier.testTag("device-category-${conversation.key}"), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * Asks for the person to message: a number, or a name to find among the contacts. The number is validated like any SMS
 * recipient. Contacts are optional; without permission only typing a number works, with an offer to allow contacts.
 */
@Composable
internal fun NewMessageDialog(
    onDismiss: () -> Unit, onStart: (String) -> Unit,
    contacts: List<ContactEntry> = emptyList(), contactsCanRead: Boolean = true, onAllowContacts: (() -> Unit)? = null
) {
    var number by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val matches = remember(contacts, number) { searchContacts(contacts, number, limit = 5) }
    fun start(value: String) { try { onStart(smsAddress(value)) } catch (invalid: IllegalArgumentException) { error = invalid.message } }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("New message") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Type a number" + (if (contactsCanRead) " or a contact's name" else "") + ". You will review the number, text and SIM before anything is sent.")
            OutlinedTextField(
                value = number, onValueChange = { number = it; error = null }, label = { Text(if (contactsCanRead) "Name or number" else "Phone number") }, singleLine = true,
                isError = error != null, modifier = Modifier.fillMaxWidth().testTag("new-message-number"),
                supportingText = { error?.let { Text(it, Modifier.testTag("new-message-error")) } }
            )
            matches.forEachIndexed { index, entry ->
                Column(Modifier.fillMaxWidth().clickable { start(entry.number) }.padding(vertical = 8.dp).testTag("new-message-contact-$index")) {
                    Text(entry.name, fontWeight = FontWeight.SemiBold)
                    Text(entry.number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!contactsCanRead && onAllowContacts != null) TextButton(onClick = onAllowContacts, modifier = Modifier.testTag("new-message-allow-contacts")) { Text("Allow contacts to search by name") }
        } },
        confirmButton = { TextButton(modifier = Modifier.testTag("new-message-continue"), onClick = { start(number) }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
