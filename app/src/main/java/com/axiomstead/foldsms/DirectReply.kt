package com.axiomstead.foldsms

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** What happened to a reply typed in a notification. */
internal enum class DirectReplyOutcome { SENT, SAVED_LONG, SAVED_DUPLICATE, DRAFT_WAITING, CANNOT_SEND, NOT_A_NUMBER, EMPTY, FAILED }

/** The short text shown on the notification afterwards. Never contains the message. */
internal fun directReplyMessage(outcome: DirectReplyOutcome): String = when (outcome) {
    DirectReplyOutcome.SENT -> "Reply sent"
    DirectReplyOutcome.SAVED_LONG -> "Saved as a draft: it needs more than one SMS part. Open FoldSMS to review and send."
    DirectReplyOutcome.SAVED_DUPLICATE -> "Saved as a draft: the same message was already sent. Open FoldSMS to review."
    DirectReplyOutcome.DRAFT_WAITING -> "You have an unsent message. Open FoldSMS to send or discard it, then reply."
    DirectReplyOutcome.CANNOT_SEND -> "Could not send. Check the SMS permission and SIM in FoldSMS."
    DirectReplyOutcome.NOT_A_NUMBER -> "This sender is not a phone number, so it cannot be replied to."
    DirectReplyOutcome.EMPTY -> "Nothing to send."
    DirectReplyOutcome.FAILED -> "Could not confirm the send. Check FoldSMS before trying again."
}

/**
 * A reply typed in a notification follows the same rules as one typed in the conversation: it goes out at once only when it is a
 * single SMS part and not a possible duplicate; otherwise it is kept as the draft for the owner to review in the app. It never
 * replaces text already waiting in the one shared draft, and it never resends.
 */
internal suspend fun handleDirectReply(services: SmsServices, address: String, text: String, preferredSim: Int): DirectReplyOutcome {
    if (text.isBlank()) return DirectReplyOutcome.EMPTY
    val recipient = try { smsAddress(address) } catch (_: IllegalArgumentException) { return DirectReplyOutcome.NOT_A_NUMBER }
    val access = services.platform.access()
    if (!services.consent.enabled() || !access.canSend) return DirectReplyOutcome.CANNOT_SEND
    if (services.drafts.read().body.isNotBlank()) return DirectReplyOutcome.DRAFT_WAITING
    val sims = try { services.platform.subscriptions() } catch (_: Exception) { return DirectReplyOutcome.CANNOT_SEND }
    val sim = sims.firstOrNull { it.id == preferredSim }?.id ?: sims.firstOrNull()?.id ?: return DirectReplyOutcome.CANNOT_SEND
    services.drafts.edit(address = recipient, body = text, subscriptionId = sim)
    return try {
        val confirmation = services.controller.confirm(services.drafts.read())
        val duplicate = services.journal.recentAttempts().any {
            it.recipient == confirmation.recipient && it.bodyDigest == smsBodyDigest(confirmation.draft.body) && it.stage in setOf("SUBMITTED", "PARTIAL")
        }
        when {
            confirmation.parts.size != 1 -> DirectReplyOutcome.SAVED_LONG
            needsSendReview(existingConversation = true, partCount = 1, duplicateRisk = duplicate) -> DirectReplyOutcome.SAVED_DUPLICATE
            else -> { services.controller.submit(confirmation); DirectReplyOutcome.SENT }
        }
    } catch (failure: kotlinx.coroutines.CancellationException) { throw failure }
    catch (_: Exception) { DirectReplyOutcome.FAILED }
}

internal const val REPLY_KEY = "reply_text"
internal const val DIRECT_REPLY_ACTION = "com.axiomstead.foldsms.DIRECT_REPLY"

/** The "Reply" button of a notification, or null for a sender that is not a phone number. */
internal fun replyAction(context: Context, address: String, subscriptionId: Int): NotificationCompat.Action? {
    if (runCatching { smsAddress(address) }.isFailure) return null
    val input = RemoteInput.Builder(REPLY_KEY).setLabel("Reply").build()
    val intent = Intent(context, DirectReplyReceiver::class.java).setAction(DIRECT_REPLY_ACTION)
        .setData(android.net.Uri.Builder().scheme("foldsms-reply").authority("conversation").appendPath(address).build())
        .putExtra("address", address).putExtra("sub", subscriptionId)
    // Mutable on purpose: the system fills in what the owner typed. The intent names this receiver, which is not exported.
    val pending = PendingIntent.getBroadcast(context, address.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    return NotificationCompat.Action.Builder(0, "Reply", pending).addRemoteInput(input)
        .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY).setShowsUserInterface(false).build()
}

class DirectReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DIRECT_REPLY_ACTION) return
        val address = intent.getStringExtra("address").orEmpty()
        val subscription = intent.getIntExtra("sub", -1)
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(REPLY_KEY)?.toString().orEmpty()
        val app = context.applicationContext
        val completion = goAsync()
        scope.launch {
            val outcome = try { handleDirectReply(smsServices(app), address, text, subscription) }
                catch (failure: Exception) { Diagnostics.record(app, "DIRECT_REPLY_ERROR", failure.javaClass.name); DirectReplyOutcome.FAILED }
            Diagnostics.record(app, "DIRECT_REPLY", outcome.name)
            if (outcome == DirectReplyOutcome.SENT && isDefaultSmsApp(app)) {
                try { DefaultAppProviderWrites(app).markThreadRead(Telephony.Threads.getOrCreateThreadId(app, address)) } catch (_: Exception) { }
            }
            // The notification must be replaced, or Android keeps showing the reply as still sending.
            val open = PendingIntent.getActivity(app, address.hashCode(),
                Intent(app, MainActivity::class.java).setAction(OPEN_CONVERSATION_ACTION)
                    .setData(android.net.Uri.Builder().scheme("foldsms").authority("conversation").appendPath(address).build())
                    .putExtra(EXTRA_OPEN_ADDRESS, address).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notice = NotificationCompat.Builder(app, "incoming_sms").setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(contactNameFor(app, address) ?: address).setContentText(directReplyMessage(outcome))
                .setOnlyAlertOnce(true).setAutoCancel(true).setContentIntent(open)
                .setTimeoutAfter(if (outcome == DirectReplyOutcome.SENT) 4_000L else 15_000L).build()
            try { app.getSystemService(NotificationManager::class.java)?.notify(address, 0, notice) } catch (_: SecurityException) { }
            completion.finish()
        }
    }
    private companion object { val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO) }
}
