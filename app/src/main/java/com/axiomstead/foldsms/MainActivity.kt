package com.axiomstead.foldsms

import kotlinx.coroutines.launch
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.isSystemInDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            val themeStore = remember(context) { ThemeStore(context.applicationContext) }
            PreviewApp(themeStore)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreviewApp(
    themeStore: ThemeStore,
    draftStore: DraftStore? = null,
    metadataStore: ConversationMetadataStore? = null,
    customStore: CustomSampleConversationStore? = null,
    wide: Boolean = LocalConfiguration.current.screenWidthDp >= 600,
    deletionStore: SampleDeletionStore? = null,
    smsServices: SmsServices? = null,
    deviceMetadataStore: DeviceMetadataStore? = null
) {
    var theme by remember { mutableStateOf(themeStore.get()) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val dark = theme.usesDarkColors(isSystemInDarkTheme())
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFFF2A26A), onPrimary = Color(0xFF112022),
        secondary = Color(0xFF6DC3B8), background = Color(0xFF051214),
        surface = Color(0xFF112022), surfaceVariant = Color(0xFF1B292C),
        onSurface = Color(0xFFD9ECEF), onSurfaceVariant = Color(0xFF9DAFB2),
        outline = Color(0xFF314043), outlineVariant = Color(0xFF253336)
    ) else lightColorScheme(
        primary = Color(0xFFAC5700), onPrimary = Color(0xFFFDF8EF),
        secondary = Color(0xFF00877C), background = Color(0xFFF4ECE0),
        surface = Color(0xFFFDF8EF), surfaceVariant = Color(0xFFEBDCC9),
        onSurface = Color(0xFF211C15), onSurfaceVariant = Color(0xFF756A58),
        outline = Color(0xFFD6C8B4), outlineVariant = Color(0xFFE2D3BF)
    )
    val activity = androidx.compose.ui.platform.LocalContext.current as? ComponentActivity
    DisposableEffect(activity, dark) {
        // Edge-to-edge with status/navigation icons that follow the app theme, not only the device theme.
        val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        onDispose {}
    }
    var conversationOpen by remember { mutableStateOf(false) }
    val deviceState = rememberDeviceMessagesState(smsServices)
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                // Like Telegram, the tab bar belongs to lists; it hides inside a phone-width conversation and while typing.
                if (!keyboardVisible && !conversationOpen) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = true, onClick = {},
                        icon = { Icon(Icons.Default.Email, contentDescription = null) }, label = { Text("Chats") },
                        modifier = Modifier.testTag("preview-chats")
                    )
                    NavigationBarItem(
                        selected = false, onClick = { showSettings = true },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) }, label = { Text("Settings") },
                        modifier = Modifier.testTag("preview-settings")
                    )
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                ChatsTab(deviceState, wide = wide, onConversationOpen = { conversationOpen = it }, deviceMetadata = deviceMetadataStore) { useDeviceMessages ->
                    FoldSmsApp(
                        store = draftStore, wide = wide, metadataStore = metadataStore, customStore = customStore,
                        onOpenSettings = { showSettings = true }, deletionStore = deletionStore,
                        onConversationOpen = { conversationOpen = it }, onUseDeviceMessages = useDeviceMessages
                    )
                }
            }
        }
        if (showSettings) PreviewSettings(
            theme = theme,
            onThemeChange = { theme = it; themeStore.save(it) },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
internal fun FoldSmsApp(
    store: DraftStore? = null,
    wide: Boolean = LocalConfiguration.current.screenWidthDp >= 600,
    metadataStore: ConversationMetadataStore? = null,
    deletionStore: SampleDeletionStore? = null,
    customStore: CustomSampleConversationStore? = null,
    onOpenSettings: () -> Unit = {},
    onConversationOpen: (Boolean) -> Unit = {},
    onUseDeviceMessages: (() -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val draftStore = store ?: remember(context) { DraftStore(context.applicationContext) }
    val drafts = remember(draftStore) { mutableStateMapOf<Int, String>().apply {
        exampleThreads.forEach { put(it.id, draftStore.get(it.id)) }
    } }
    val metadata = remember(context, metadataStore) { metadataStore ?: ConversationMetadataStore.get(context) }
    val custom = remember(context, customStore, draftStore) {
        customStore ?: CustomSampleConversationStore(sampleMetadataDatabase(context), draftStore)
    }
    val customRows by custom.conversations.collectAsState<List<CustomSampleConversation>, List<CustomSampleConversation>?>(initial = null)
    LaunchedEffect(customRows, draftStore) {
        customRows.orEmpty().forEach { row ->
            if (!drafts.containsKey(row.id)) drafts[row.id] = draftStore.get(row.id)
        }
    }
    val overrides by metadata.categories.collectAsState(initial = emptyList())
    val allThreads = exampleThreads + customRows.orEmpty().map { row ->
        SampleConversation(row.id, row.name, "Unknown", emptyList(), row.address)
    }
    val deletions = remember(context, deletionStore) { deletionStore ?: SampleDeletionStore(context.applicationContext) }
    val deletionState by deletions.state.collectAsState()
    val threads = deletionState.visible(allThreads).map { thread ->
        thread.copy(category = overrides.firstOrNull { it.threadId == thread.id }?.category ?: thread.category)
    }
    val scope = rememberCoroutineScope()
    var deletionBusy by remember { mutableStateOf(false) }
    var deletionError by remember { mutableStateOf(false) }
    fun deleteMessages(ids: Set<Int>) {
        deletionBusy = true
        scope.launch {
            try { if (!deletions.delete(ids)) deletionError = true }
            finally { deletionBusy = false }
        }
    }
    fun undoDeletion() {
        deletionBusy = true
        scope.launch {
            try { if (!deletions.undo()) deletionError = true }
            finally { deletionBusy = false }
        }
    }
    var pendingDeleteIds by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var pendingDeleteScope by rememberSaveable { mutableStateOf("") }
    fun requestDelete(ids: Set<Int>, scopeLabel: String) {
        pendingDeleteIds = ids.toList()
        pendingDeleteScope = scopeLabel
    }
    var recoveryId by rememberSaveable { mutableStateOf<Int?>(null) }
    var showRecovery by rememberSaveable { mutableStateOf(false) }
    var recoveryRevision by remember { mutableIntStateOf(0) }
    val recoverable = remember(customRows, draftStore, recoveryRevision) {
        if (customRows == null) emptyMap() else draftStore.customDrafts().filterKeys { id -> customRows.orEmpty().none { it.id == id } }
    }
    if (showRecovery) AlertDialog(
        onDismissRequest = { showRecovery = false },
        title = { Text("Saved drafts to recover") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("These drafts were saved before their sample conversations finished creating. Choose one to recover. Nothing is sent or removed.")
                recoverable.toSortedMap(reverseOrder()).entries.forEachIndexed { index, (id, text) ->
                    TextButton(modifier = Modifier.testTag("recover-draft-$id"), onClick = { recoveryId = id; showRecovery = false }) {
                        Column { Text("Saved draft ${index + 1}"); Text(text.take(120)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showRecovery = false }) { Text("Close") } }
    )
    var showNewSample by rememberSaveable { mutableStateOf(false) }
    var creatingSample by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    var showArchive by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("All") }
    var search by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableIntStateOf(1) }
    var showConversation by rememberSaveable { mutableStateOf(false) }
    val inboxState = rememberLazyListState()
    val categoryState = rememberLazyListState()
    val conversationStates = rememberSaveableStateHolder()
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    BackHandler(enabled = !wide && showConversation && !keyboardVisible) { showConversation = false }
    val conversationFullScreen = !wide && showConversation
    SideEffect { onConversationOpen(conversationFullScreen) }
    DisposableEffect(Unit) { onDispose { onConversationOpen(false) } }
    val pinnedIds = overrides.filter { it.pinned }.map { it.threadId }.toSet()
    val archivedIds = overrides.filter { it.archived }.map { it.threadId }.toSet()
    val available = archiveConversations(threads, archivedIds, showArchive)
    val activeVisible = (selected in archivedIds) == showArchive
    val filtered = searchConversations(orderedConversations(available, pinnedIds), search, filter)
    val active = threads.firstOrNull { it.id == selected }
    LaunchedEffect(customRows, recoveryId, creatingSample) {
        if (customRows != null && !creatingSample && recoveryId?.let { it !in recoverable } == true) recoveryId = null
    }
    if (showNewSample || recoveryId?.let { it in recoverable } == true) key(recoveryId) { NewSampleConversationDialog(
        saving = creatingSample,
        error = createError,
        recoveryDraft = recoveryId?.let { recoverable[it] },
        onDismiss = { showNewSample = false; recoveryId = null; createError = null },
        onCreate = { name, address, initialDraft ->
            creatingSample = true
            createError = null
            scope.launch {
                try {
                    val created = recoveryId?.let { custom.recover(it, name, address, exampleThreads.map { thread -> thread.address }) }
                        ?: custom.create(name, address, initialDraft, exampleThreads.map { it.address })
                    drafts[created.id] = draftStore.get(created.id)
                    selected = created.id
                    showConversation = true
                    showArchive = false
                    filter = "All"
                    search = ""
                    showNewSample = false
                    recoveryId = null
                } catch (failure: IllegalArgumentException) {
                    createError = failure.message
                } catch (_: android.database.sqlite.SQLiteConstraintException) {
                    createError = "A sample conversation already uses this address. Open that conversation to keep its draft."
                } catch (_: java.io.IOException) {
                    createError = "The sample conversation could not be saved. Your existing drafts are unchanged."
                } finally {
                    recoveryRevision++
                    creatingSample = false
                }
            }
        }
    ) }
    if (active == null) {
        Surface(Modifier.fillMaxSize()) { Text("Loading local sample conversation…", Modifier.padding(20.dp)) }
        return
    }
    var selecting by rememberSaveable(selected) { mutableStateOf(false) }
    var selectedMessages by rememberSaveable(selected) { mutableStateOf(emptyList<Int>()) }
    BackHandler(enabled = (wide || showConversation) && selecting && !deletionBusy) {
        selecting = false; selectedMessages = emptyList(); pendingDeleteIds = emptyList()
    }
    if (pendingDeleteIds.isNotEmpty()) SampleDeleteConfirmation(
        count = pendingDeleteIds.size, scope = pendingDeleteScope,
        onDismiss = { pendingDeleteIds = emptyList() },
        onConfirm = {
            deleteMessages(pendingDeleteIds.toSet())
            pendingDeleteIds = emptyList(); selecting = false; selectedMessages = emptyList()
        }
    )
    if (deletionError) AlertDialog(
        onDismissRequest = { deletionError = false },
        title = { Text("Could not save message changes") },
        text = { Text("Please try again. Drafts have not been changed.") },
        confirmButton = { TextButton(onClick = { deletionError = false }) { Text("Close") } }
    )
    var detailMessageId by rememberSaveable(selected) { mutableStateOf<Int?>(null) }
    active.history.takeIf { activeVisible }?.firstOrNull { it.id == detailMessageId }?.let { message ->
        MessageDetailsSheet(message, active.sender, onDismiss = { detailMessageId = null })
    }
    if (showCategories) CategoryPicker(
        category = active.category,
        onChoose = { category ->
            scope.launch { metadata.setCategory(active.id, category) }
            showCategories = false
        },
        onReset = {
            scope.launch { metadata.setCategory(active.id, null) }
            showCategories = false
        },
        onDismiss = { showCategories = false }
    )
    fun closeConversation() { showConversation = false; selecting = false; selectedMessages = emptyList() }
    Surface(Modifier.fillMaxSize().imePadding(), color = MaterialTheme.colorScheme.background) {
        Row {
            if (wide || !showConversation) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("FoldSMS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(if (showArchive) "Sample archive" else "Sample inbox", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        }
                        OverflowMenu("inbox-actions", "inbox actions", listOf(
                            MenuAction(if (showArchive) "Inbox" else "Archive") {
                                showArchive = !showArchive; showConversation = false; selecting = false; selectedMessages = emptyList(); detailMessageId = null
                            },
                            MenuAction("Settings", onClick = onOpenSettings)
                        ) + listOfNotNull(onUseDeviceMessages?.let { MenuAction("Real messages", onClick = it) }) + listOf(
                            MenuAction("Delete all messages in app", sampleMessageIds(threads).isNotEmpty() && !deletionBusy) {
                                requestDelete(sampleMessageIds(threads), "across the entire app, including conversations outside this search or category")
                            }
                        ))
                    }
                    if (recoverable.isNotEmpty()) TextButton(onClick = { showRecovery = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Recover saved drafts (${recoverable.size})") }
                    if (deletionState.undoIds.isNotEmpty()) TextButton(
                        onClick = { undoDeletion() }, enabled = !deletionBusy,
                        modifier = Modifier.padding(horizontal = 8.dp).testTag("undo-inbox")
                    ) { Text("Undo last deletion (${deletionState.undoIds.size})") }
                    TextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Search sample messages") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, contentDescription = "Clear search") } },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("inbox-search"),
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    androidx.compose.foundation.lazy.LazyRow(state = categoryState, modifier = Modifier.testTag("category-filters"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("All") + conversationCategories) { c ->
                            FilterChip(modifier = Modifier.testTag("category-filter-$c"), selected = c == filter, onClick = { filter = c }, label = { Text(c) })
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (filtered.isEmpty()) {
                            Text(if (showArchive && available.isEmpty()) "Archive is empty" else "No sample conversations match",
                                Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LazyColumn(state = inboxState, modifier = Modifier.fillMaxSize().testTag("inbox-list"), contentPadding = PaddingValues(bottom = 88.dp)) {
                            items(filtered, key = { it.conversation.id }) { result ->
                                val thread = result.conversation
                                val draft = drafts[thread.id].orEmpty()
                                Row(
                                    Modifier.fillMaxWidth()
                                        .background(if (thread.id == selected && wide) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                        .clickable { selected = thread.id; showConversation = true }
                                        .testTag("thread_${thread.id}")
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ConversationAvatar(thread.sender, thread.id, 52.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(thread.sender, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                                            if (thread.time.isNotEmpty()) Text(thread.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.weight(1f)) {
                                                if (draft.isNotEmpty()) Text(
                                                    buildAnnotatedString {
                                                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) { append("Draft: ") }
                                                        append(draft.lineSequence().first())
                                                    },
                                                    modifier = Modifier.testTag("draft_indicator_${thread.id}"), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                                                ) else Text(result.snippet.ifEmpty { "No sample messages" }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (thread.id in pinnedIds) Text("Pinned", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Text(thread.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                    }
                                }
                                HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                        FloatingActionButton(
                            onClick = { showNewSample = true; createError = null },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("new-sample"),
                            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary
                        ) { Icon(Icons.Default.Edit, contentDescription = "New sample") }
                    }
                }
            }
            if ((wide || showConversation) && activeVisible) {
                if (wide) VerticalDivider()
                Column(Modifier.weight(1f).fillMaxHeight().background(chatBackground())) {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (!wide) IconButton(onClick = { closeConversation() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            } else Spacer(Modifier.width(12.dp))
                            ConversationAvatar(active.sender, active.id, 40.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(active.sender, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                                Text("Sample conversation · ${active.category}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                            }
                            if (!selecting) OverflowMenu("conversation-actions", "conversation actions", listOf(
                                MenuAction("Change category") { showCategories = true },
                                MenuAction("Select messages", active.history.isNotEmpty() && !deletionBusy) { selecting = true },
                                MenuAction(if (active.id in pinnedIds) "Unpin conversation" else "Pin conversation") {
                                    scope.launch { metadata.setPinned(active.id, active.id !in pinnedIds) }
                                },
                                MenuAction(if (active.id in archivedIds) "Restore conversation" else "Archive conversation") {
                                    scope.launch {
                                        metadata.setArchived(active.id, active.id !in archivedIds)
                                        closeConversation()
                                        detailMessageId = null
                                    }
                                },
                                MenuAction("Delete all in conversation", active.history.isNotEmpty() && !deletionBusy) {
                                    requestDelete(sampleMessageIds(threads, active.id), "in the entire ${active.sender} conversation")
                                },
                                MenuAction("Settings", onClick = onOpenSettings)
                            ))
                        }
                    }
                    if (selecting) MessageSelectionActions(
                        selectedMessages.size, deletionBusy,
                        onCancel = { selecting = false; selectedMessages = emptyList() },
                        onDelete = { requestDelete(selectedMessages.toSet(), "selected in ${active.sender}") }
                    )
                    if (deletionState.undoIds.isNotEmpty()) TextButton(
                        enabled = !deletionBusy,
                        onClick = { undoDeletion() }, modifier = Modifier.padding(horizontal = 8.dp).testTag("undo-conversation")
                    ) { Text("Undo last deletion (${deletionState.undoIds.size})") }
                    Surface(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp), shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)) {
                        Text(if (active.id < 0) "Local sample · Unsent draft only" else "Sample history · 2 Nov 2026 · Manila time",
                            Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    conversationStates.SaveableStateProvider(active.id) {
                        val historyState = rememberLazyListState()
                        LazyColumn(
                            state = historyState,
                            modifier = Modifier.weight(1f).fillMaxWidth().testTag("conversation-history-${active.id}"),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
                        ) {
                            if (active.history.isEmpty()) item {
                                Text("No sample messages. Your draft is still available.", modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("empty-history"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            items(active.history, key = { it.id }) { message ->
                                val outgoing = message.direction == MessageDirection.OUTGOING
                                Box(Modifier.fillMaxWidth(), contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
                                    Box(Modifier.fillMaxWidth(0.82f), contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
                                        Surface(
                                            onClick = {
                                                if (selecting) selectedMessages = if (message.id in selectedMessages)
                                                    selectedMessages - message.id else selectedMessages + message.id
                                                else detailMessageId = message.id
                                            },
                                            enabled = !deletionBusy,
                                            modifier = Modifier.testTag("message-${message.id}").semantics {
                                                if (selecting) stateDescription = if (message.id in selectedMessages) "Selected" else "Not selected"
                                            },
                                            shape = bubbleShape(outgoing),
                                            color = if (outgoing) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.surface)
                                                else MaterialTheme.colorScheme.surface
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (selecting) Checkbox(
                                                    checked = message.id in selectedMessages,
                                                    onCheckedChange = null,
                                                    modifier = Modifier.padding(start = 8.dp).testTag("message-selected-${message.id}")
                                                )
                                                Column(Modifier.padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 6.dp), horizontalAlignment = Alignment.End) {
                                                    Text(message.body, color = MaterialTheme.colorScheme.onSurface)
                                                    Text(
                                                        if (outgoing) "${message.time} · Sample sent" else message.time,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    key(selected) {
                        DraftComposer(
                            draft = drafts[selected].orEmpty(),
                            onDraftChange = { updated ->
                                draftStore.save(selected, updated)
                                drafts[selected] = updated
                            }
                        )
                    }
                }
            } else if (wide) {
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxHeight().background(chatBackground()), contentAlignment = Alignment.Center) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)) {
                        Text("Choose a sample conversation", Modifier.padding(horizontal = 14.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
