package com.axiomstead.foldsms

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.saveable.listSaver
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

@Composable
private fun SearchAllButton(loading: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = !loading, modifier = Modifier.testTag("device-search-all")) {
        Text(if (loading) "Reading all messages…" else "Search all messages, including older ones")
    }
}

/** Multi-select over the conversation list. Category changes are local; read, unread and delete need the default SMS role. */
internal class InboxSelection(
    val active: Boolean, val keys: Set<String>, val canChangeMessages: Boolean,
    val onStart: () -> Unit, val onToggle: (String) -> Unit, val onSelectAll: () -> Unit, val onClose: () -> Unit,
    val onMarkRead: () -> Unit, val onMarkUnread: () -> Unit, val onDelete: () -> Unit, val onCategory: () -> Unit
)

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
    onLaunchRecipientHandled: () -> Unit = {},
    codeCopier: ((String) -> Unit)? = null,
    openAddress: String? = null,
    onOpenAddressHandled: () -> Unit = {}
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
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    var selectMode by rememberSaveable { mutableStateOf(false) }
    var selectedKeys by rememberSaveable(stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })) { mutableStateOf(emptySet<String>()) }
    var bulkDelete by rememberSaveable { mutableStateOf(false) }
    var dismissedCode by rememberSaveable { mutableStateOf<String?>(null) }
    var blockTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val actions = state.services.actions
    val arranged = arrangeDeviceConversations(state.conversations, metadata, showArchive, filter, unreadOnly, openKey)
    val openReal = state.conversations.firstOrNull { it.key == openKey }
    // A number with no history yet: an empty conversation that only exists on screen until the provider has a thread for it.
    val synthetic = pendingAddress?.takeIf { openReal == null }?.let {
        // A number with no thread yet still shows the contact's name.
        withContactNames(listOf(DeviceConversation("new-${conversationAddressKey(it)}", 0, it, emptyList())), contactNameMap(state.contacts)).single()
    }
    val open = openReal ?: synthetic
    // A conversation is always shown complete, even when its messages are older than the list's window.
    LaunchedEffect(openReal?.threadId) { openReal?.threadId?.let { state.loadThread(it) } }
    LaunchedEffect(pendingAddress, state.conversations) {
        val pending = pendingAddress ?: return@LaunchedEffect
        state.conversations.firstOrNull { sameRecipient(it.address, pending) }?.let { openKey = it.key; pendingAddress = null }
    }
    val startConversation = { number: String ->
        val existing = state.conversations.firstOrNull { sameRecipient(it.address, number) }
        if (existing != null) { openKey = existing.key; pendingAddress = null; showArchive = isArchived(existing, metadata[existing.key]) }
        else { openKey = null; pendingAddress = number }
    }
    // A number handed over by another app (sms:/smsto: link). Same validation as typing it; nothing is sent.
    // A notification asks for the conversation with one sender. It is opened once the list has it; if it never shows up (for
    // example it was deleted), the request is dropped and the list stays.
    LaunchedEffect(openAddress, state.conversations, state.hasLoaded) {
        val address = openAddress ?: return@LaunchedEffect
        val thread = state.conversations.firstOrNull { sameRecipient(it.address, address) }
        if (thread != null) {
            openKey = thread.key; pendingAddress = null
            showArchive = isArchived(thread, metadata[thread.key])
            onOpenAddressHandled()
        } else if (state.hasLoaded) onOpenAddressHandled()
    }
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
    val copyCode = codeCopier ?: { code -> copyCodeToClipboard(context, code) }
    // Copying a code is reading it: the conversation is marked read when that is possible.
    fun copyFrom(thread: DeviceConversation?, code: String, dismissKey: String? = null) {
        copyCode(code)
        if (dismissKey != null) dismissedCode = dismissKey
        if (thread != null && canManage && thread.unreadCount > 0) scope.launch {
            withContext(Dispatchers.IO) { actions?.markThreadRead(thread.threadId) }; state.refresh()
        }
    }
    val chosen = state.conversations.filter { it.key in selectedKeys }
    fun finishSelection(failed: Boolean) { selectMode = false; selectedKeys = emptySet(); bulkDelete = false; if (failed) state.error = "Some conversations could not be changed." }
    var bulkCategory by rememberSaveable { mutableStateOf(false) }
    val selection = InboxSelection(
        active = selectMode, keys = selectedKeys, canChangeMessages = canManage && actions != null,
        onStart = { selectMode = true },
        onToggle = { key -> selectMode = true; selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key },
        onSelectAll = { selectedKeys = searchDeviceConversations(arranged, search).map { it.key }.toSet() },
        onClose = { finishSelection(false) },
        onMarkRead = { scope.launch {
            val failed = withContext(Dispatchers.IO) { chosen.count { !actions!!.markThreadRead(it.threadId) } > 0 }
            finishSelection(failed); state.refresh()
        } },
        onMarkUnread = { scope.launch {
            val failed = withContext(Dispatchers.IO) { chosen.count { thread -> thread.latestIncoming?.let { actions!!.markUnread(it.kind, it.id) } != true } > 0 }
            finishSelection(failed); state.refresh()
        } },
        onDelete = { if (selectedKeys.isNotEmpty() && canManage) bulkDelete = true },
        onCategory = { if (selectedKeys.isNotEmpty()) bulkCategory = true }
    )
    if (bulkCategory) CategoryPicker(
        category = "", title = "Category for ${chosen.size} conversation${if (chosen.size == 1) "" else "s"}",
        onChoose = { value -> scope.launch { chosen.forEach { store.setCategory(it.key, value) } }; bulkCategory = false; finishSelection(false) },
        onReset = { scope.launch { chosen.forEach { store.setCategory(it.key, null) } }; bulkCategory = false; finishSelection(false) },
        onDismiss = { bulkCategory = false }
    )
    BackHandler(enabled = selection?.active == true) { finishSelection(false) }
    if (bulkDelete) AlertDialog(
        onDismissRequest = { bulkDelete = false },
        title = { Text(if (chosen.size == 1) "Delete this conversation?" else "Delete ${chosen.size} conversations?") },
        text = { Text("All their messages are removed from this phone. This cannot be undone.") },
        confirmButton = { TextButton(modifier = Modifier.testTag("selection-confirm-delete"), onClick = {
            scope.launch {
                val failed = withContext(Dispatchers.IO) { chosen.count { !actions!!.deleteThread(it.threadId) } > 0 }
                finishSelection(failed); state.refresh()
            }
        }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { bulkDelete = false }) { Text("Keep") } }
    )
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
            MenuAction(if (isArchived(thread, flags)) "Restore conversation" else "Archive conversation") {
                scope.launch { store.setArchived(thread.key, !isArchived(thread, flags), thread.last.date); openKey = null; pendingAddress = null }
            }
        ) + if (canManage) listOfNotNull(
            if (thread.unreadCount > 0) MenuAction("Mark as read") { scope.launch { withContext(Dispatchers.IO) { actions?.markThreadRead(thread.threadId) }; state.refresh() } } else null,
            MenuAction("Delete conversation") { deleteTarget = thread.key }
        ) else emptyList()
    } ?.let { base ->
        val thread = openReal
        val blocker = state.services.blocker
        if (thread == null || blocker == null || !state.canBlock || !isBlockable(thread.address)) base
        else base + if (isBlockedIn(state.blockedNumbers, thread.address)) MenuAction("Unblock this number") {
            scope.launch { withContext(Dispatchers.IO) { blocker.unblock(thread.address) }; state.refresh() }
        } else MenuAction("Block this number") { blockTarget = thread.address }
    }
    blockTarget?.let { address ->
        AlertDialog(
            onDismissRequest = { blockTarget = null }, title = { Text("Block this number?") },
            text = { Text("You will stop receiving texts and calls from $address. Messages already here stay. You can unblock it here or in Settings.") },
            confirmButton = { TextButton(modifier = Modifier.testTag("block-confirm"), onClick = {
                blockTarget = null
                scope.launch {
                    val done = withContext(Dispatchers.IO) { state.services.blocker?.block(address) == true }
                    if (!done) state.error = "Could not block this number."
                    state.refresh()
                }
            }) { Text("Block") } },
            dismissButton = { TextButton(onClick = { blockTarget = null }) { Text("Cancel") } }
        )
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
                onOpenSettings, onRequestPermissions, permissionsAsked, now, zone, onNewMessage = { newMessageDialog = true }, offer = offer,
                unreadOnly = unreadOnly, onToggleUnread = { unreadOnly = !unreadOnly }, selection = selection,
                dismissedCode = dismissedCode, onCopyCode = { thread, code, dismissKey -> copyFrom(thread, code, dismissKey) }
            )
        }
        if (wide) VerticalDivider()
        if (open != null) Box(Modifier.weight(1f).fillMaxHeight()) {
            DeviceConversationScreen(open, simLabel, loadImage, onBack = if (wide) null else ({ openKey = null; pendingAddress = null }), now = now, zone = zone,
                actions = conversationActions.orEmpty(), composer = { ReplyComposer(state, open) },
                onCopyCode = { code -> copyFrom(openReal, code) },
                blocked = openReal?.let { isBlockedIn(state.blockedNumbers, it.address) } == true)
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
    offer: DefaultOffer? = null,
    unreadOnly: Boolean = false,
    onToggleUnread: () -> Unit = {},
    selection: InboxSelection? = null,
    dismissedCode: String? = null,
    onCopyCode: (DeviceConversation?, String, String?) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Codes expire on the clock, not only when messages change.
    var clock by remember { mutableStateOf(now()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000); clock = now() } }
    val latest = latestOtp(state.conversations, clock).takeIf { it != null && "${it.dateMillis}:${it.code}" != dismissedCode }
    val selecting = selection?.active == true
    // Reaching the end of the list reads older messages; a search does not, it offers to read everything instead.
    LaunchedEffect(listState, state.exhausted, search) {
        if (!state.exhausted && search.isBlank()) snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }.collect { (last, total) -> if (total > 0 && last >= total - 3) state.loadMore() }
    }
    val conversations = searchDeviceConversations(arranged, search)
    val access = state.access
    Column(Modifier.fillMaxSize()) {
        if (selecting && selection != null) Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = selection.onClose, modifier = Modifier.testTag("selection-close")) { Icon(Icons.Default.Clear, contentDescription = "Stop selecting") }
            Text("${selection.keys.size} selected", Modifier.weight(1f).testTag("selection-count"), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = selection.onSelectAll, modifier = Modifier.testTag("selection-select-all")) { Text("Select all") }
        } else         Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = search,
                onValueChange = onSearch,
                placeholder = { Text(if (unreadOnly) "Search unread" else if (showArchive) "Search archived" else "Search messages") },
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
                MenuAction(if (unreadOnly) "Show all messages" else "Show unread only", onClick = onToggleUnread),
                MenuAction(if (showArchive) "Inbox" else "Archived", onClick = onToggleArchive)
            ) + listOfNotNull(selection?.let { MenuAction("Select messages", onClick = it.onStart) }) + MenuAction("Settings", onClick = onOpenSettings))
        }
        val chipCategories = listOf("All") + chatCategories
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).testTag("device-category-filters"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            chipCategories.forEach { c ->
                val label = c
                Surface(
                    onClick = { onFilter(c) },
                    modifier = Modifier.weight(label.length + 2f).testTag("device-category-filter-$c"),
                    shape = RoundedCornerShape(8.dp),
                    color = if (c == filter) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (c == filter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(label, Modifier.padding(horizontal = 2.dp, vertical = 7.dp), fontSize = 14.sp, maxLines = 1, softWrap = false,
                        overflow = TextOverflow.Clip, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = if (c == filter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (latest != null && access.canRead && !selecting) Surface(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).testTag("device-latest-code"),
            shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ) {
            Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Latest code · ${latest.sender}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(latest.code, Modifier.testTag("device-latest-code-value"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
                Button(onClick = {
                    onCopyCode(state.conversations.firstOrNull { it.key == latest.conversationKey }, latest.code, "${latest.dateMillis}:${latest.code}")
                }, modifier = Modifier.testTag("device-latest-code-copy")) { Text("Copy") }
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
                        unreadOnly -> "No unread conversations"
                        showArchive && filter == "All" -> "Archive is empty"
                        filter != "All" -> "No conversations in this category"
                        else -> "Everything is archived."
                    }, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                conversations.isEmpty() -> Column(Modifier.padding(20.dp)) {
                    Text("No conversations match", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!state.exhausted) SearchAllButton(state.loadingMore) { scope.launch { state.loadAll() } }
                }
                else -> LazyColumn(Modifier.fillMaxSize().testTag("device-inbox-list"), state = listState, contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(conversations, key = { it.key }) { conversation ->
                        DeviceConversationRow(conversation, formatConversationTime(conversation.last.date, now(), zone, Locale.getDefault()),
                            selected = conversation.key == selectedKey, category = effectiveCategory(conversation, metadata[conversation.key]),
                            pinned = metadata[conversation.key]?.pinned == true,
                            checked = if (selecting) conversation.key in selection!!.keys else null,
                            code = if (selecting) null else recentOtp(conversation, clock), onCopyCode = { code -> onCopyCode(conversation, code, null) },
                            onLongClick = selection?.let { s -> { s.onToggle(conversation.key) } },
                            onClick = { if (selecting) selection!!.onToggle(conversation.key) else onOpen(conversation.key) })
                        HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    if (!state.exhausted) item(key = "older") {
                        if (search.isNotBlank()) Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { SearchAllButton(state.loadingMore) { scope.launch { state.loadAll() } } }
                        else Text("Loading older messages…", Modifier.padding(20.dp).testTag("device-loading-older"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // Declared after the list so it is drawn, and hit-tested, on top of it.
            if (access.canSend && !selecting) FloatingActionButton(
                onClick = onNewMessage,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("device-new-message"),
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary
            ) { Icon(Icons.Default.Edit, contentDescription = "New message") }
        }
        if (selecting && selection != null) Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                val any = selection.keys.isNotEmpty()
                val change = any && selection.canChangeMessages
                TextButton(onClick = selection.onMarkRead, enabled = change, modifier = Modifier.testTag("selection-read")) { Text("Read") }
                TextButton(onClick = selection.onMarkUnread, enabled = change, modifier = Modifier.testTag("selection-unread")) { Text("Unread") }
                TextButton(onClick = selection.onCategory, enabled = any, modifier = Modifier.testTag("selection-category")) { Text("Category") }
                TextButton(onClick = selection.onDelete, enabled = change, modifier = Modifier.testTag("selection-delete")) { Text("Delete", color = if (change) MaterialTheme.colorScheme.error else Color.Unspecified) }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun DeviceConversationRow(
    conversation: DeviceConversation, time: String, selected: Boolean, category: String, pinned: Boolean, onClick: () -> Unit,
    checked: Boolean? = null, onLongClick: (() -> Unit)? = null,
    code: String? = null, onCopyCode: (String) -> Unit = {}
) {
    val last = conversation.last
    val unread = conversation.unreadCount
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("device-thread-${conversation.key}").padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (checked != null) Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp).testTag("device-check-${conversation.key}"))
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
                if (code != null) Text("Copy $code", Modifier.padding(start = 8.dp).clickable { onCopyCode(code) }.padding(vertical = 2.dp)
                    .testTag("device-copy-code-${conversation.key}"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
