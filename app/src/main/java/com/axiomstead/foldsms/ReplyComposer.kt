package com.axiomstead.foldsms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reply box for a real conversation. It uses the single shared SMS draft and the send journal: revision-bound draft, one
 * attempt per revision, no automatic resend, the draft clears only after every part is accepted. Tapping Send goes straight
 * out for a one-part reply in an existing conversation; a new recipient, several parts or a possible duplicate first show the
 * review dialog with number, text, SIM and part count (see [needsSendReview]).
 */
@Composable
internal fun ReplyComposer(state: DeviceMessagesState, conversation: DeviceConversation, modifier: Modifier = Modifier) {
    val services = state.services
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val draft by remember(services) { services.drafts.changes }.collectAsState(initial = services.drafts.read())
    // What the box shows lives here, so typing never waits for the draft flow (a stale value made letters land out of order).
    // The draft replaces it only when something else changed the draft: sending, discarding, restoring.
    var field by remember(conversation.address) { mutableStateOf(composerText(conversation.address, draft).let { TextFieldValue(it, TextRange(it.length)) }) }
    var ownRevision by remember(conversation.address) { mutableLongStateOf(draft.revision) }
    LaunchedEffect(draft.revision) {
        if (draft.revision > ownRevision) {
            ownRevision = draft.revision
            val shown = composerText(conversation.address, draft)
            if (shown != field.text) field = TextFieldValue(shown, TextRange(shown.length))
        }
    }
    val attempts by remember(services) { services.journal.observeRecent() }.collectAsState(initial = emptyList())
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<SmsConfirmation?>(null) }
    var retryFrom by remember { mutableStateOf<SmsDraft?>(null) }
    var discardPrompt by remember { mutableStateOf(false) }

    val status = replyState(conversation.address, draft, state.access.canSend, state.subscriptions)
    val sim = preferredSubscription(conversation, draft, state.subscriptions)
    val current = attempts.firstOrNull { it.attempt.draftRevision == draft.revision && it.attempt.stage != "REJECTED" }

    fun duplicateRiskFor(send: SmsConfirmation) = attempts.any { it.attempt.recipient == send.recipient && it.attempt.bodyDigest == smsBodyDigest(send.draft.body)
        && it.attempt.stage in setOf("SUBMITTED", "PARTIAL") }

    confirmation?.let { send ->
        val duplicateRisk = duplicateRiskFor(send)
        AlertDialog(onDismissRequest = { if (!busy) confirmation = null }, title = { Text("Send real SMS?") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("To: ${send.recipient}")
                Text(state.subscriptions.firstOrNull { it.id == send.draft.subscriptionId }?.let { "SIM ${it.slot + 1} · ${it.label}" } ?: "Selected SIM is no longer active")
                Text("${send.parts.size} SMS part(s). Carrier charges may apply. Submission cannot be cancelled.")
                if (duplicateRisk) Text("A matching earlier attempt is unconfirmed or partially sent. Check Google Messages first; sending again can duplicate messages and charges.", color = MaterialTheme.colorScheme.error)
                Text(send.draft.body)
            } },
            confirmButton = { TextButton(enabled = !busy, modifier = Modifier.testTag("reply-confirm-send"), onClick = {
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
    retryFrom?.let { previous -> AlertDialog(
        onDismissRequest = { retryFrom = null }, title = { Text("Review another send attempt?") },
        text = { Text("Check Google Messages first. An earlier attempt may already have sent some or all parts and cannot be cancelled. Another attempt can duplicate messages and charges. Your current draft text will be kept.") },
        confirmButton = { TextButton(onClick = {
            retryFrom = null; busy = true; error = null
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
        dismissButton = { TextButton(onClick = { retryFrom = null }) { Text("Cancel") } }
    ) }
    if (discardPrompt) AlertDialog(
        onDismissRequest = { discardPrompt = false }, title = { Text("Discard that SMS draft?") },
        text = { Text("This clears the unsent SMS draft. It does not cancel a message that was already submitted.") },
        confirmButton = { TextButton(modifier = Modifier.testTag("reply-confirm-discard"), onClick = { services.drafts.clearIfRevision(draft.revision); discardPrompt = false }) { Text("Discard draft") } },
        dismissButton = { TextButton(onClick = { discardPrompt = false }) { Text("Keep draft") } }
    )

    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 6.dp)) {
            when (status) {
                ReplyState.NotAPhoneNumber -> Note("You can't reply to this sender because it is not a phone number.", "reply-note")
                ReplyState.NoSendPermission -> Note("Allow SMS and phone access in Android settings to send.", "reply-note")
                ReplyState.NoActiveSim -> Note("No active SIM to send with.", "reply-note")
                is ReplyState.OtherDraftWaiting -> {
                    Note(if (status.address.isBlank()) "An unsent SMS draft with no recipient is waiting. Send or discard it before writing here."
                        else "An unsent SMS to ${status.address} is waiting. Send or discard it before writing here.", "reply-note")
                    TextButton(onClick = { discardPrompt = true }, modifier = Modifier.testTag("reply-discard-other")) { Text("Discard that draft") }
                }
                ReplyState.Ready -> {
                    if (state.subscriptions.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.subscriptions.forEach { option ->
                            FilterChip(selected = option.id == sim?.id, modifier = Modifier.testTag("reply-sim-${option.id}"),
                                onClick = { services.drafts.edit(address = conversation.address, subscriptionId = option.id) },
                                label = { Text("SIM ${option.slot + 1} · ${option.label}") })
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        TextField(
                            value = field,
                            onValueChange = { typed ->
                                val before = field.text
                                val result = if (TypingPrefs.tidy(context)) tidyWhileTyping(before, typed.text, typed.selection.start, typed.selection.end)
                                    else Tidied(typed.text, typed.selection.start, typed.selection.end)
                                // The keyboard's own value (with its composing word) is kept unless the text was really tidied.
                                field = if (result.text == typed.text) typed else TextFieldValue(result.text, TextRange(result.start, result.end))
                                services.drafts.edit(address = conversation.address, body = result.text, subscriptionId = sim?.id)
                                ownRevision = services.drafts.read().revision
                            },
                            placeholder = { Text("SMS message") },
                            modifier = Modifier.weight(1f).testTag("reply-composer"),
                            minLines = 1, maxLines = 5, shape = RoundedCornerShape(24.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                        IconButton(
                            modifier = Modifier.testTag("reply-send"),
                            enabled = !busy && current == null && draft.body.isNotBlank() && sim != null,
                            onClick = {
                                busy = true; error = null
                                scope.launch {
                                    try {
                                        if (TypingPrefs.tidy(context)) tidyPunctuation(services.drafts.read().body).text.let { tidy -> services.drafts.edit(body = tidy) }
                                        val send = services.controller.confirm(services.drafts.read())
                                        if (needsSendReview(conversation.messages.isNotEmpty(), send.parts.size, duplicateRiskFor(send))) confirmation = send
                                        else services.controller.submit(send)
                                    }
                                    catch (cancelled: CancellationException) { throw cancelled }
                                    catch (failure: Exception) { error = failure.message ?: "Could not send the message." }
                                    finally { busy = false }
                                }
                            }
                        ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send SMS") }
                    }
                    current?.let { operation ->
                        Text(smsAttemptLabel(operation), Modifier.testTag("reply-status"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row {
                            if (operation.attempt.stage in setOf("SUBMITTED", "FAILED", "PARTIAL"))
                                TextButton(enabled = !busy, onClick = { retryFrom = draft }, modifier = Modifier.testTag("reply-another-attempt")) { Text("Review another attempt") }
                            if (operation.attempt.stage == "PREPARED") TextButton(enabled = !busy, onClick = {
                                scope.launch { services.journal.rejectPrepared(operation.attempt.id) }
                            }) { Text("Cancel unsubmitted attempt") }
                        }
                    }
                    Text("Real SMS · carrier charges may apply", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("reply-error")) }
        }
    }
}

@Composable
private fun Note(text: String, tag: String) =
    Text(text, Modifier.padding(horizontal = 6.dp, vertical = 4.dp).testTag(tag), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
