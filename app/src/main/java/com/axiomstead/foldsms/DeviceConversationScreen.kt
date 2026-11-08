package com.axiomstead.foldsms

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Read-only view of one real conversation: SMS and MMS bubbles oldest first, date separators, MMS images and a SIM
 * label when more than one SIM is active. Opens at the latest message and follows new ones only while at the bottom.
 */
@Composable
internal fun DeviceConversationScreen(
    conversation: DeviceConversation,
    simLabel: (DeviceMessage) -> String?,
    loadImage: suspend (Long) -> ImageBitmap?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: List<MenuAction> = emptyList(),
    composer: @Composable () -> Unit = {},
    onCopyCode: (String) -> Unit = {},
    blocked: Boolean = false,
    now: () -> Instant = { Instant.now() },
    zone: ZoneId = ZoneId.systemDefault(),
    // While the keyboard is open the list is much shorter; keep the newest message in view so the reply you are writing
    // never scrolls out of sight. A parameter so tests can drive it without a real keyboard.
    pinToLatest: Boolean = WindowInsets.ime.getBottom(LocalDensity.current) > 0
) {
    val locale = Locale.getDefault()
    val items = remember(conversation, zone, locale) { chatItems(conversation.messages, now(), zone, locale) }
    val listState = rememberLazyListState()
    var positioned by rememberSaveable(conversation.key) { mutableStateOf(false) }
    var following by rememberSaveable(conversation.key) { mutableStateOf(true) }
    var imagePart by remember { mutableStateOf<DeviceMmsPart?>(null) }
    var searching by rememberSaveable(conversation.key) { mutableStateOf(false) }
    var query by rememberSaveable(conversation.key) { mutableStateOf("") }
    var matchIndex by remember(conversation.key, query) { mutableIntStateOf(-1) }
    val matches = remember(conversation.messages, query) { threadMatches(conversation.messages, query) }
    // Opens on the newest match; the arrows walk back to older ones.
    LaunchedEffect(query, matches.size) { matchIndex = if (matches.isEmpty()) -1 else matches.lastIndex }
    val currentKey = matches.getOrNull(matchIndex)
    LaunchedEffect(currentKey) {
        currentKey?.let { key -> val at = items.indexOfFirst { it.key == key }; if (at >= 0) listState.scrollToItem(at) }
    }
    BackHandler(enabled = searching) { searching = false; query = "" }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(searching) { if (searching) searchFocus.requestFocus() }
    val newestOutgoingKey = remember(items) { items.lastOrNull { it is ChatItem.Bubble && it.message.outgoing }?.key }
    LaunchedEffect(conversation.key) {
        if (!positioned && items.isNotEmpty()) listState.scrollToItem(items.lastIndex)
        positioned = true
    }
    LaunchedEffect(listState) {
        snapshotFlow { val info = listState.layoutInfo; (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - 2 }
            .collect { following = it }
    }
    LaunchedEffect(items.size) { if (positioned && following && !searching && items.isNotEmpty()) listState.scrollToItem(items.lastIndex) }
    val itemCount by rememberUpdatedState(items.size)
    LaunchedEffect(listState, pinToLatest, searching) {
        // The viewport shrinks over several frames as the keyboard slides up; follow it until it settles.
        if (pinToLatest && !searching) snapshotFlow { listState.layoutInfo.viewportSize.height }.collect { if (itemCount > 0) listState.scrollToItem(itemCount - 1) }
    }
    Column(modifier.fillMaxSize().background(chatBackground()).imePadding()) {
        if (searching) Surface(color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { searching = false; query = "" }, modifier = Modifier.testTag("conversation-search-close")) { Icon(Icons.Default.Clear, contentDescription = "Close search") }
                TextField(
                    value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("Search this conversation") },
                    modifier = Modifier.weight(1f).focusRequester(searchFocus).testTag("conversation-search-field"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                    )
                )
                Text(
                    when { query.isBlank() -> ""; matches.isEmpty() -> "No matches"; else -> "${matchIndex + 1}/${matches.size}" },
                    Modifier.testTag("conversation-search-count"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = { if (matchIndex > 0) matchIndex-- }, enabled = matchIndex > 0, modifier = Modifier.testTag("conversation-search-older")) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Older match")
                }
                IconButton(onClick = { if (matchIndex < matches.lastIndex) matchIndex++ }, enabled = matchIndex in 0 until matches.lastIndex, modifier = Modifier.testTag("conversation-search-newer")) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Newer match")
                }
            }
        } else         Surface(color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.testTag("device-conversation-back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                } else Spacer(Modifier.width(12.dp))
                ConversationAvatar(conversation.title, conversation.key.hashCode(), 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(conversation.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("device-conversation-title").semantics { heading() })
                    val mmsCount = conversation.messages.count { it.kind == DeviceMessageKind.MMS }
                    Text(listOfNotNull(conversation.contactName?.let { conversation.address.ifBlank { null } }, if (mmsCount > 0) "SMS and MMS" else "SMS", if (blocked) "Blocked" else null).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = { searching = true }, modifier = Modifier.testTag("conversation-search")) { Icon(Icons.Default.Search, contentDescription = "Search in conversation") }
                if (actions.isNotEmpty()) OverflowMenu("device-conversation-actions", "conversation actions", actions)
            }
        }
        // The weight sits on a plain Box so the list can never take the whole height and push the composer off screen.
        Box(Modifier.weight(1f).fillMaxWidth()) { SelectionContainer(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState, modifier = Modifier.fillMaxSize().testTag("device-conversation-list"),
                verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
            ) {
                if (items.isEmpty()) item(key = "empty") {
                    Text("New message to ${conversation.title}. Nothing is sent until you review and confirm.",
                        Modifier.fillMaxWidth().padding(16.dp).testTag("device-new-conversation-hint"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(items, key = { it.key }) { item ->
                    when (item) {
                        is ChatItem.Separator -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)) {
                                Text(item.label, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        is ChatItem.Bubble -> MessageBubble(item.message, simLabel(item.message), zone, locale, loadImage, onCopyCode, now(),
                            query = if (searching) query else "", current = item.key == currentKey,
                            status = outgoingStatusLabel(item.message, item.key == newestOutgoingKey)) { imagePart = it }
                    }
                }
            }
        } }
        composer()
    }
    imagePart?.let { part -> ImageDialog(part, loadImage) { imagePart = null } }
}

@Composable
private fun MessageBubble(
    message: DeviceMessage, sim: String?, zone: ZoneId, locale: Locale,
    loadImage: suspend (Long) -> ImageBitmap?, onCopyCode: (String) -> Unit, now: java.time.Instant,
    query: String, current: Boolean, status: String?, onOpenImage: (DeviceMmsPart) -> Unit
) {
    val outgoing = message.outgoing
    Box(Modifier.fillMaxWidth().testTag("device-message-${message.key}"), contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            Modifier.widthIn(max = 520.dp).fillMaxWidth(0.84f).wrapContentWidth(if (outgoing) Alignment.End else Alignment.Start)
                .semantics { stateDescription = if (outgoing) "Sent by you" else "Received" },
            shape = bubbleShape(outgoing),
            color = if (outgoing) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.surface)
                else MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 6.dp), horizontalAlignment = Alignment.End) {
                val parts = message.mmsParts
                if (message.subject.isNotBlank()) Text(message.subject, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth())
                parts.filter { it.image }.forEach { part -> MmsImage(part, loadImage) { onOpenImage(part) } }
                parts.filter { it.attachment && !it.image }.forEach { part ->
                    Text("${part.mimeType} · ${part.name.ifEmpty { "attachment" }} · open in Google Messages",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                }
                if (message.text.isNotBlank()) {
                    val ranges = highlightRanges(message.text, query)
                    val shade = MaterialTheme.colorScheme.primary.copy(alpha = if (current) 0.5f else 0.22f)
                    if (ranges.isEmpty()) Text(message.text, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth())
                    else Text(buildAnnotatedString {
                        var at = 0
                        ranges.forEach { range ->
                            append(message.text.substring(at, range.first))
                            withStyle(SpanStyle(background = shade)) { append(message.text.substring(range.first, range.last + 1)) }
                            at = range.last + 1
                        }
                        append(message.text.substring(at))
                    }, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth())
                }
                val code = if (!outgoing && codeStillUseful(message.date, now)) extractOtp(message.text) else null
                if (code != null) TextButton(onClick = { onCopyCode(code) }, modifier = Modifier.testTag("device-bubble-copy-${message.key}")) { Text("Copy code $code") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    status?.let { Text("$it · ", Modifier.testTag("device-status-${message.key}"), style = MaterialTheme.typography.labelSmall,
                        color = if (message.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                    sim?.let { Text("$it · ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(formatMessageTime(message.date, zone, locale), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MmsImage(part: DeviceMmsPart, loadImage: suspend (Long) -> ImageBitmap?, onOpen: () -> Unit) {
    // null = loading, Result.failure/absent bitmap = unavailable. Decoding is bounded by the platform (1200 px).
    var loaded by remember(part.id) { mutableStateOf<Pair<Boolean, ImageBitmap?>>(false to null) }
    LaunchedEffect(part.id) { loaded = true to loadOrNull(loadImage, part.id) }
    val bitmap = loaded.second
    if (bitmap != null) Image(
        bitmap = bitmap, contentDescription = "MMS image", contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clickable(onClick = onOpen).testTag("device-image-${part.id}")
    ) else Text(if (loaded.first) "Image unavailable · open it in Google Messages" else "Loading image…",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().testTag("device-image-${part.id}"))
}

@Composable
private fun ImageDialog(part: DeviceMmsPart, loadImage: suspend (Long) -> ImageBitmap?, onDismiss: () -> Unit) {
    var loaded by remember(part.id) { mutableStateOf<Pair<Boolean, ImageBitmap?>>(false to null) }
    LaunchedEffect(part.id) { loaded = true to loadOrNull(loadImage, part.id) }
    val bitmap = loaded.second
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(part.name.ifEmpty { "MMS image" }) },
        text = { bitmap?.let { Image(it, "MMS image", Modifier.fillMaxWidth().heightIn(max = 500.dp), contentScale = ContentScale.Fit) } ?: Text(if (loaded.first) "Image unavailable. Open it in Google Messages." else "Loading image…") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close image") } }
    )
}

/** A bad or missing image must never crash the conversation; cancellation still propagates. */
private suspend fun loadOrNull(load: suspend (Long) -> ImageBitmap?, id: Long): ImageBitmap? =
    try { load(id) } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { null }
