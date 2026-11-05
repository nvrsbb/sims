package com.axiomstead.foldsms

import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal data class SmsConfirmation(val draft: SmsDraft, val recipient: String, val parts: List<String>)

internal class SmsSendController(
    private val platform: SmsPlatform, private val consent: SmsConsentStore,
    private val drafts: SmsDraftStore, private val journal: SmsSendDao
) {
    suspend fun confirm(draft: SmsDraft): SmsConfirmation = withContext(Dispatchers.IO) {
        check(platform.access().canSend) { "SMS sending access is unavailable. Keep a different messaging app as default and review permissions." }
        val recipient = validateSmsDraft(draft)
        check(platform.subscriptions().any { it.id == draft.subscriptionId }) { "Choose an active SIM; the previous SIM is unavailable." }
        val parts = platform.split(draft.body, draft.subscriptionId).toList()
        require(parts.size in 1..MAX_SMS_PARTS && parts.none { it.isEmpty() } && parts.joinToString("") == draft.body) {
            "This message cannot be sent intact within the 10-part limit. Shorten it without losing text."
        }
        SmsConfirmation(draft, recipient, parts)
    }

    /** Once the user confirms, finish the short handoff even if the UI is recreated. Never auto-retry. */
    suspend fun submit(confirmation: SmsConfirmation): String = withContext(NonCancellable + Dispatchers.IO) {
        val fresh = confirm(confirmation.draft)
        check(fresh == confirmation) { "SIM encoding changed. Review the message and part count again." }
        check(drafts.ensureDurable(confirmation.draft)) { "The draft changed or could not be saved. Review it before sending." }
        val operation = SmsSendAttempt(UUID.randomUUID().toString(), confirmation.draft.revision, confirmation.recipient,
            smsBodyDigest(confirmation.draft.body), confirmation.draft.subscriptionId, confirmation.parts.size, System.currentTimeMillis())
        journal.prepare(operation)
        try {
            check(platform.access().canSend && platform.subscriptions().any { it.id == operation.subscriptionId }) { "SMS access or selected SIM changed." }
            check(drafts.read() == confirmation.draft) { "The draft changed. Review it again." }
        } catch (failure: Exception) {
            journal.rejectPrepared(operation.id)
            throw failure
        }
        check(journal.beginDispatch(operation.id) == 1) { "This unsubmitted attempt was cancelled." }
        try {
            platform.dispatch(operation.id, confirmation.recipient, confirmation.parts, operation.subscriptionId)
        } catch (_: Exception) {
            if (journal.attempt(operation.id)?.stage in setOf("SENT", "FAILED", "PARTIAL")) return@withContext operation.id
            // The service might have accepted a request before throwing. Do not turn uncertainty into an automatic resend.
            throw IllegalStateException("Submission is unconfirmed. Check Google Messages before composing another attempt.")
        }
        operation.id
    }

    suspend fun result(id: String, part: Int, code: Int) = withContext(Dispatchers.IO) {
        val updated = journal.recordResult(id, part, code)
        if (updated?.stage == "SENT" && consent.enabled()) drafts.clearIfRevision(updated.draftRevision)
    }

    suspend fun reconcileCompletedDraft() = withContext(Dispatchers.IO) {
        if (consent.enabled()) {
            val draft = drafts.read()
            if (journal.forRevision(draft.revision).any { it.stage == "SENT" }) drafts.clearIfRevision(draft.revision)
        }
    }
}
