package com.axiomstead.foldsms

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.provider.Telephony
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val smsPermissions = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS,
    Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE)

@Composable
internal fun SmsCompanionRoute(onClose: () -> Unit, supplied: SmsServices? = null) {
    val context = LocalContext.current
    val services = remember(context, supplied) { supplied ?: smsServices(context) }
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    var access by remember { mutableStateOf(services.platform.access()) }
    var subscriptions by remember { mutableStateOf(emptyList<SmsSubscription>()) }
    var messages by remember { mutableStateOf(emptyList<DeviceSms>()) }
    var error by remember { mutableStateOf<String?>(null) }
    val refreshLock = remember { kotlinx.coroutines.sync.Mutex() }
    var refreshing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var enablePrompt by rememberSaveable { mutableStateOf(false) }
    var clearPrompt by rememberSaveable { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<SmsConfirmation?>(null) }
    var detail by remember { mutableStateOf<DeviceSms?>(null) }
    var replyAddress by remember { mutableStateOf<String?>(null) }
    var retryDraft by remember { mutableStateOf<SmsDraft?>(null) }
    val draftFlow = remember(services) { services.drafts.changes }
    val attemptFlow = remember(services) { services.journal.observeRecent() }
    val draft by draftFlow.collectAsState(initial = services.drafts.read())
    val attempts by attemptFlow.collectAsState(initial = emptyList())

    suspend fun refresh() {
        refreshLock.lock()
        try {
        refreshing = true
        try {
            val next = services.platform.access()
            access = next
            if (!next.canRead) messages = emptyList()
            if (!next.canSend) subscriptions = emptyList()
            withContext(Dispatchers.IO) {
                val rows = if (next.canRead) services.platform.recent() else emptyList()
                val sims = if (next.canSend) services.platform.subscriptions() else emptyList()
                withContext(Dispatchers.Main) {
                    val current = services.platform.access()
                    access = current
                    messages = if (current.canRead) rows else emptyList()
                    subscriptions = if (current.canSend) sims else emptyList()
                }
            }
            services.controller.reconcileCompletedDraft()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            messages = emptyList(); subscriptions = emptyList()
            access = services.platform.access()
            error = "SMS access changed or the provider could not be read. Review permissions and refresh."
        } finally { refreshing = false }
        } finally { refreshLock.unlock() }
    }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        scope.launch { refresh() }
    }
    DisposableEffect(owner, services) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) scope.launch { refresh() } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(services, access.canRead, access.canSend, access.enabled) {
        services.platform.changes().collect { refresh() }
    }

    fun reply(address: String) {
        if (draft.body.isNotBlank() && draft.address != address) replyAddress = address
        else services.drafts.edit(address = address)
        detail = null
    }
    if (enablePrompt) AlertDialog(
        onDismissRequest = { enablePrompt = false }, title = { Text("Enable SMS companion?") },
        text = { Text("This mode can read device SMS and send chargeable SMS through your chosen SIM. Android will ask for SMS and Phone/SIM permissions. Google Messages stays default for MMS, message storage and notifications. This is not yet a replacement. No messages are sent automatically.", Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = {
            enablePrompt = false
            if (services.consent.setEnabled(true)) {
                access = services.platform.access()
                permissionRequest.launch(smsPermissions)
            } else error = "Could not save SMS consent. No SMS access was enabled."
        }) { Text("Enable and request access") } },
        dismissButton = { TextButton(onClick = { enablePrompt = false }) { Text("Cancel") } }
    )
    if (clearPrompt) AlertDialog(
        onDismissRequest = { clearPrompt = false }, title = { Text("Clear this SMS draft?") },
        text = { Text("This clears only the SMS composer. It does not cancel a submitted message or change sample drafts.") },
        confirmButton = { TextButton(onClick = { services.drafts.clearIfRevision(draft.revision); clearPrompt = false }) { Text("Clear SMS draft") } },
        dismissButton = { TextButton(onClick = { clearPrompt = false }) { Text("Keep draft") } }
    )
    replyAddress?.let { address -> AlertDialog(
        onDismissRequest = { replyAddress = null }, title = { Text("Change SMS recipient?") },
        text = { Text("Keep the current draft text and address it to $address?") },
        confirmButton = { TextButton(onClick = { services.drafts.edit(address = address); replyAddress = null }) { Text("Change recipient") } },
        dismissButton = { TextButton(onClick = { replyAddress = null }) { Text("Cancel") } }
    ) }
    retryDraft?.let { previous -> AlertDialog(
        onDismissRequest = { retryDraft = null }, title = { Text("Review another send attempt?") },
        text = { Text("Check Google Messages first. An earlier attempt may already have sent some or all parts and cannot be cancelled. Another attempt can duplicate messages and charges. Your current draft text will be kept.") },
        confirmButton = { TextButton(onClick = {
            retryDraft = null; busy = true; error = null
            scope.launch {
                try {
                    val next = withContext(Dispatchers.IO) { services.drafts.newAttemptIfRevision(previous.revision) }
                        ?: throw IllegalStateException("The draft changed or could not be saved. Review it again.")
                    confirmation = services.controller.confirm(next)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message ?: "Could not prepare another attempt." }
                finally { busy = false }
            }
        }) { Text("Review with duplicate risk") } },
        dismissButton = { TextButton(onClick = { retryDraft = null }) { Text("Cancel") } }
    ) }
    detail?.let { sms -> AlertDialog(
        onDismissRequest = { detail = null }, title = { Text(sms.address.ifEmpty { "Unknown sender" }) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(DateFormat.getDateTimeInstance().format(Date(sms.date)))
            Text(sms.body.ifEmpty { "Empty SMS" })
            Text("Device SMS · viewing here does not mark it read in the default messaging app.")
        } },
        confirmButton = { TextButton(enabled = runCatching { smsAddress(sms.address) }.isSuccess, onClick = { reply(sms.address) }) { Text("Reply") } },
        dismissButton = { TextButton(onClick = { detail = null }) { Text("Close") } }
    ) }
    confirmation?.let { send ->
        val duplicateRisk = attempts.any { it.attempt.recipient == send.recipient && it.attempt.bodyDigest == smsBodyDigest(send.draft.body)
            && it.attempt.stage in setOf("SUBMITTED", "PARTIAL") }
        AlertDialog(onDismissRequest = { if (!busy) confirmation = null }, title = { Text("Send real SMS?") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("To: ${send.recipient}")
                Text(subscriptions.firstOrNull { it.id == send.draft.subscriptionId }?.let { "SIM ${it.slot + 1} · ${it.label}" } ?: "Selected SIM is no longer active")
                Text("${send.parts.size} SMS part(s). Carrier charges may apply. Submission cannot be cancelled.")
                if (duplicateRisk) Text("A matching earlier attempt is unconfirmed or partially sent. Check Google Messages first; sending again can duplicate messages and charges.", color = MaterialTheme.colorScheme.error)
                Text(send.draft.body)
            } },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true; confirmation = null; error = null
                scope.launch {
                    try { services.controller.submit(send) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "Submission could not be confirmed. Check Google Messages." }
                    finally { busy = false }
                }
            }) { Text("Send ${send.parts.size} part(s)") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirmation = null }) { Text("Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SMS companion", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onClose) { Text("Back to preview") }
        }
        Text("Google Messages stays default. MMS and notifications remain with your default app.", style = MaterialTheme.typography.labelMedium)
        if (!access.enabled) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text("Opt-in device SMS access", style = MaterialTheme.typography.titleMedium)
                Text("Read recent device SMS and send through an explicitly chosen SIM. Sample conversations remain separate and their sending stays disabled. SMS permission availability depends on Android and the installer. This mode does not request the default SMS role.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { enablePrompt = true }) { Text("Enable SMS companion") }
            }
            return@Column
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(enabled = !busy, onClick = {
                val saved = services.consent.setEnabled(false)
                access = services.platform.access(); messages = emptyList(); subscriptions = emptyList(); confirmation = null
                if (!saved) error = "Disabled for this session, but Android could not save the preference. Revoke SMS permissions in Android settings before restarting."
            }) { Text("Disable SMS access") }
            TextButton(onClick = { permissionRequest.launch(smsPermissions) }) { Text("Request permissions") }
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Android permissions") }
            TextButton(enabled = !refreshing, onClick = { scope.launch { refresh() } }) { Text("Refresh SMS") }
        }
        if (!access.messagingSupported) Text("This device has no supported SMS service.", color = MaterialTheme.colorScheme.error)
        if (!access.externalDefault) Text("Choose Google Messages or another capable default messaging app in Android settings before using this companion.", color = MaterialTheme.colorScheme.error)
        if (!access.canRead) Text("Receiving/display needs SMS read and receive access. Android or the installer may refuse restricted permissions; no bypass is attempted.")
        if (!access.canSend) Text("Sending needs SMS and Phone/SIM permissions, and an external default messaging app.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("device-sms-list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Recent device SMS · latest 200", style = MaterialTheme.typography.titleMedium) }
            if (access.canRead && messages.isEmpty()) item { Text("No device SMS found. Refresh after receiving a message.") }
            if (attempts.isNotEmpty()) item { Text("Recent send attempts", style = MaterialTheme.typography.titleMedium) }
            items(attempts.take(3), key = { "attempt-${it.attempt.id}" }) { operation ->
                Column(Modifier.fillMaxWidth().testTag("sms-attempt-${operation.attempt.id}")) {
                    Text(operation.attempt.recipient)
                    Text(smsAttemptLabel(operation), style = MaterialTheme.typography.bodySmall)
                    if (operation.attempt.draftRevision == draft.revision && operation.attempt.stage in setOf("SUBMITTED", "FAILED", "PARTIAL"))
                        TextButton(enabled = access.canSend && !busy, onClick = { retryDraft = draft }) { Text("Review another attempt") }
                    if (operation.attempt.stage == "PREPARED") TextButton(enabled = !busy, onClick = {
                        scope.launch { services.journal.rejectPrepared(operation.attempt.id) }
                    }) { Text("Cancel unsubmitted attempt") }
                }
            }
            items(messages, key = { "sms-${it.id}" }) { sms ->
                Card(onClick = { detail = sms }, modifier = Modifier.fillMaxWidth().testTag("device-sms-${sms.id}")) {
                    Column(Modifier.padding(12.dp)) {
                        Text(sms.address.ifEmpty { "Unknown sender" }, style = MaterialTheme.typography.titleSmall)
                        Text(sms.body.ifEmpty { "Empty SMS" }, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(when (sms.type) {
                            Telephony.Sms.MESSAGE_TYPE_INBOX -> if (sms.read) "Received" else "Received · unread in default app"
                            Telephony.Sms.MESSAGE_TYPE_SENT -> "Sent (provider)"
                            Telephony.Sms.MESSAGE_TYPE_DRAFT -> "Unsent provider draft"
                            Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.MESSAGE_TYPE_QUEUED -> "Queued (provider)"
                            Telephony.Sms.MESSAGE_TYPE_FAILED -> "Failed (provider)"
                            else -> "Provider message"
                        } + " · " + DateFormat.getDateTimeInstance().format(Date(sms.date)), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

        }
        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
            Text("Real SMS composer", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(draft.address, { services.drafts.edit(address = it) }, label = { Text("SMS number") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            OutlinedTextField(draft.body, { services.drafts.edit(body = it) }, label = { Text("SMS message") },
                modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 3)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                subscriptions.forEach { sim -> FilterChip(selected = sim.id == draft.subscriptionId,
                    onClick = { services.drafts.edit(subscriptionId = sim.id) }, label = { Text("SIM ${sim.slot + 1} · ${sim.label}") }) }
            }
            if (subscriptions.isEmpty()) Text("No active SIM available for sending.", style = MaterialTheme.typography.labelSmall)
            Row {
                Button(enabled = access.canSend && !busy && attempts.none { it.attempt.draftRevision == draft.revision && it.attempt.stage != "REJECTED" } && draft.body.isNotBlank() && subscriptions.any { it.id == draft.subscriptionId }, onClick = {
                    busy = true; error = null
                    scope.launch {
                        try { confirmation = services.controller.confirm(draft) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { error = failure.message ?: "Review the number, message and SIM." }
                        finally { busy = false }
                    }
                }) { Text("Review SMS") }
                TextButton(enabled = !busy && (draft.address.isNotEmpty() || draft.body.isNotEmpty()), onClick = { clearPrompt = true }) { Text("Clear SMS draft") }
            }
            Text("Drafts clear only after every part is accepted by the network and no newer edits exist. No automatic retries or delivery guarantees.", style = MaterialTheme.typography.labelSmall)
        }
    }
}
