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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
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
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The main tab. Real device conversations when the owner has opted in; otherwise (or by choice) the fabricated sample
 * inbox. Opt-in, permissions and consent stay with the existing stores; nothing is read until consent is on.
 */
@Composable
internal fun ChatsTab(
    state: DeviceMessagesState,
    wide: Boolean,
    onConversationOpen: (Boolean) -> Unit,
    samples: @Composable (onUseDeviceMessages: () -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()
    var showSamples by rememberSaveable { mutableStateOf(false) }
    var enablePrompt by rememberSaveable { mutableStateOf(false) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        scope.launch { state.refresh() }
    }
    if (enablePrompt) AlertDialog(
        onDismissRequest = { enablePrompt = false },
        title = { Text("Show your real messages?") },
        text = { Text("FoldSMS will read your device SMS and MMS, including MMS images, and can send chargeable SMS through the SIM you choose, only after you confirm each message. Android will ask for SMS, MMS and Phone/SIM permissions. Google Messages stays your default app for MMS download, storage and notifications. Nothing is sent automatically.") },
        confirmButton = { TextButton(onClick = {
            enablePrompt = false
            if (state.services.consent.setEnabled(true)) {
                state.access = state.services.platform.access()
                showSamples = false
                permissionRequest.launch(smsPermissions)
            } else state.error = "Could not save SMS consent. No SMS access was enabled."
        }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { enablePrompt = false }) { Text("Cancel") } }
    )
    if (state.access.enabled && !showSamples) DeviceInboxHost(
        state = state, wide = wide, onConversationOpen = onConversationOpen,
        onShowSamples = { showSamples = true },
        onRequestPermissions = { permissionRequest.launch(smsPermissions) }
    ) else samples {
        if (state.access.enabled) showSamples = false else enablePrompt = true
    }
}

/** List plus conversation: side by side when wide, one at a time on a phone-width screen. */
@Composable
internal fun DeviceInboxHost(
    state: DeviceMessagesState,
    wide: Boolean,
    onConversationOpen: (Boolean) -> Unit,
    onShowSamples: () -> Unit,
    onRequestPermissions: () -> Unit,
    now: () -> Instant = { Instant.now() },
    zone: ZoneId = ZoneId.systemDefault()
) {
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    val open = state.conversations.firstOrNull { it.key == openKey }
    val fullScreen = !wide && open != null
    BackHandler(enabled = fullScreen) { openKey = null }
    SideEffect { onConversationOpen(fullScreen) }
    DisposableEffect(Unit) { onDispose { onConversationOpen(false) } }
    val simLabel = { message: DeviceMessage ->
        if (state.subscriptions.size > 1) state.subscriptions.firstOrNull { it.id == message.subscriptionId }?.let { "SIM ${it.slot + 1}" } else null
    }
    val loadImage: suspend (Long) -> ImageBitmap? = { id -> withContext(Dispatchers.IO) { state.services.mms?.image(id)?.asImageBitmap() } }
    Row(Modifier.fillMaxSize()) {
        if (wide || !fullScreen) Box(Modifier.weight(1f).fillMaxHeight()) {
            DeviceInboxScreen(state, openKey, { openKey = it }, onShowSamples, onRequestPermissions, now, zone)
        }
        if (wide) VerticalDivider()
        if (open != null) Box(Modifier.weight(1f).fillMaxHeight()) {
            DeviceConversationScreen(open, simLabel, loadImage, onBack = if (wide) null else ({ openKey = null }), now = now, zone = zone,
                composer = { ReplyComposer(state, open) })
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
    selectedKey: String?,
    onOpen: (String) -> Unit,
    onShowSamples: () -> Unit,
    onRequestPermissions: () -> Unit,
    now: () -> Instant = { Instant.now() },
    zone: ZoneId = ZoneId.systemDefault()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var search by rememberSaveable { mutableStateOf("") }
    val conversations = searchDeviceConversations(state.conversations, search)
    val access = state.access
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("FoldSMS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Messages", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
            OverflowMenu("device-inbox-actions", "inbox actions", listOf(
                MenuAction("Refresh messages", !state.refreshing) { scope.launch { state.refresh() } },
                MenuAction("Request permissions", onClick = onRequestPermissions),
                MenuAction("Android permissions") {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                },
                MenuAction("Sample data", onClick = onShowSamples),
                MenuAction("Turn off real messages") {
                    val saved = state.services.consent.setEnabled(false)
                    state.access = state.services.platform.access()
                    state.messages = emptyList(); state.mmsMessages = emptyList(); state.subscriptions = emptyList()
                    if (!saved) state.error = "Disabled for this session, but Android could not save the preference. Revoke SMS permissions in Android settings before restarting."
                }
            ))
        }
        TextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search messages") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, contentDescription = "Clear search") } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("device-inbox-search"),
            singleLine = true,
            shape = RoundedCornerShape(50),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
            )
        )
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            if (!access.messagingSupported) Text("This device has no supported SMS service.", color = MaterialTheme.colorScheme.error)
            if (access.messagingSupported && !access.externalDefault) Text("Choose Google Messages or another capable default messaging app in Android settings. FoldSMS never takes over as default.", color = MaterialTheme.colorScheme.error)
            if (access.messagingSupported && access.externalDefault && !access.canRead) {
                Text("Reading messages needs SMS read and receive permission. Android or the installer may refuse restricted permissions; no bypass is attempted.")
                TextButton(onClick = onRequestPermissions, modifier = Modifier.testTag("device-request-permissions")) { Text("Request permissions") }
            }
            if (access.canRead && !state.mmsCanRead) Text("Photos and other MMS need MMS receive permission, which Android or the installer may refuse.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (access.canRead && !access.canSend) Text("Sending needs SMS and Phone/SIM permissions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("device-inbox-error")) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !access.canRead -> Unit
                !state.hasLoaded -> Text("Loading messages…", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.conversations.isEmpty() -> Text("No messages found. Refresh after receiving one.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                conversations.isEmpty() -> Text("No conversations match", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyColumn(Modifier.fillMaxSize().testTag("device-inbox-list"), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(conversations, key = { it.key }) { conversation ->
                        DeviceConversationRow(conversation, formatConversationTime(conversation.last.date, now(), zone, Locale.getDefault()),
                            selected = conversation.key == selectedKey, onClick = { onOpen(conversation.key) })
                        HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun DeviceConversationRow(conversation: DeviceConversation, time: String, selected: Boolean, onClick: () -> Unit) {
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
                if (last.failed) Text("Failed", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                if (unread > 0) Box(
                    Modifier.padding(start = 8.dp).defaultMinSize(minWidth = 22.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                        .testTag("device-unread-${conversation.key}"),
                    contentAlignment = Alignment.Center
                ) { Text(if (unread > 99) "99+" else unread.toString(), Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary) }
            }
        }
    }
}
