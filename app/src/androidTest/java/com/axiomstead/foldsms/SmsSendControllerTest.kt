package com.axiomstead.foldsms

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class SmsSendControllerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "sms-controller-${UUID.randomUUID()}"
    private val draftPreferences = context.getSharedPreferences("$name-draft", Context.MODE_PRIVATE)
    private val consentPreferences = context.getSharedPreferences("$name-consent", Context.MODE_PRIVATE)
    private val consent = SmsConsentStore(consentPreferences).also { check(it.setEnabled(true)) }
    private val drafts = SmsDraftStore(draftPreferences)
    private val platform = FakeSmsPlatform(consent)
    private val database = Room.inMemoryDatabaseBuilder(context, SmsSendDatabase::class.java).build()
    private val journal = database.sends()
    private val controller = SmsSendController(platform, consent, drafts, journal)

    @After fun clean() { database.close(); draftPreferences.edit().clear().commit(); consentPreferences.edit().clear().commit() }
    private fun draft(body: String = "Fabricated SMS") = drafts.run { edit("+1 (202) 555-0199", body, 7); read() }

    @Test fun deniedAccessInactiveSimAndMultipartLimitNeverDispatch() = runBlocking {
        val saved = draft()
        platform.gate = platform.gate.copy(sendGranted = false)
        assertTrue(runCatching { controller.confirm(saved) }.isFailure)
        assertEquals(0, platform.splits)
        platform.gate = platform.gate.copy(sendGranted = true)
        platform.sims = emptyList()
        assertTrue(runCatching { controller.confirm(saved) }.isFailure)
        platform.sims = listOf(SmsSubscription(7, 0, "Fabricated SIM")); platform.partLength = 1
        assertTrue(runCatching { controller.confirm(draft("x".repeat(11))) }.isFailure)
        assertTrue(platform.requests.isEmpty())
        assertTrue(journal.forRevision(saved.revision).isEmpty())
    }

    @Test fun concurrentConfirmationsSubmitExactlyOneRequestForTheSameDraftRevision() = runBlocking {
        val saved = draft()
        val confirmed = controller.confirm(saved)
        val results = listOf(async { runCatching { controller.submit(confirmed) } }, async { runCatching { controller.submit(confirmed) } }).awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, platform.requests.size)
        assertEquals(7, platform.requests.single().sim)
        assertEquals("+12025550199", platform.requests.single().address)
        assertEquals(saved.body, platform.requests.single().parts.joinToString(""))
    }

    @Test fun multipartOutOfOrderAndDuplicateCallbacksPreserveNewerDraftEdits() = runBlocking {
        platform.partLength = 4
        val saved = draft("  Sample 👋\nSMS  ")
        val id = controller.submit(controller.confirm(saved))
        val count = journal.attempt(id)!!.partCount
        controller.result(id, count - 1, SMS_NETWORK_SENT)
        controller.result(id, count - 1, 2) // First terminal result wins.
        assertEquals(SMS_NETWORK_SENT, journal.parts(id).last().resultCode)
        drafts.edit(body = "Newer fabricated draft")
        for (part in 0 until count - 1) controller.result(id, part, SMS_NETWORK_SENT)
        assertEquals("SENT", journal.attempt(id)!!.stage)
        assertEquals("Newer fabricated draft", drafts.read().body)
        controller.reconcileCompletedDraft()
        assertEquals("Newer fabricated draft", drafts.read().body)
    }

    @Test fun allPartsMustSucceedBeforeAnUnchangedDraftClears() = runBlocking {
        platform.partLength = 4
        val saved = draft("Sample SMS")
        val id = controller.submit(controller.confirm(saved))
        val count = journal.attempt(id)!!.partCount
        for (part in 0 until count - 1) controller.result(id, part, SMS_NETWORK_SENT)
        assertEquals(saved.body, drafts.read().body)
        controller.result(id, count - 1, SMS_NETWORK_SENT)
        assertEquals("", drafts.read().body)
        assertEquals("", drafts.read().address)
        assertTrue(drafts.read().revision > saved.revision)
    }

    @Test fun partialFailureRetainsDraftAndUnknownSubmissionNeverAutoRetries() = runBlocking {
        platform.partLength = 4
        val saved = draft("Sample SMS")
        val id = controller.submit(controller.confirm(saved))
        for (part in 0 until journal.attempt(id)!!.partCount) controller.result(id, part, if (part == 0) 2 else SMS_NETWORK_SENT)
        assertEquals("PARTIAL", journal.attempt(id)!!.stage)
        assertEquals(saved, drafts.read())
        assertTrue(runCatching { controller.submit(controller.confirm(saved)) }.isFailure)
        assertEquals(1, platform.requests.size)
        drafts.edit(body = "New fabricated request")
        platform.onDispatch = { throw IllegalStateException("Fabricated unknown binder result") }
        val next = drafts.read()
        assertTrue(runCatching { controller.submit(controller.confirm(next)) }.isFailure)
        assertEquals("SUBMITTED", journal.forRevision(next.revision).single().stage)
        assertEquals(next, drafts.read())
        controller.reconcileCompletedDraft()
        assertEquals(2, platform.requests.size)
    }

    @Test fun draftEditsAndLostAccessAfterReviewBlockSubmission() = runBlocking {
        val saved = draft()
        val confirmed = controller.confirm(saved)
        drafts.edit(body = "New text after review")
        assertTrue(runCatching { controller.submit(confirmed) }.isFailure)
        assertTrue(platform.requests.isEmpty())
        val latest = controller.confirm(drafts.read())
        platform.gate = platform.gate.copy(hasDefaultApp = false)
        assertTrue(runCatching { controller.submit(latest) }.isFailure)
        assertTrue(platform.requests.isEmpty())
    }

    @Test fun unsubmittedCancellationAndUnknownCallbacksDoNotClearDrafts() = runBlocking {
        val saved = draft()
        val operation = SmsSendAttempt(UUID.randomUUID().toString(), saved.revision, "+12025550199", smsBodyDigest(saved.body), 7, 1, 0)
        journal.prepare(operation)
        assertEquals(1, journal.rejectPrepared(operation.id))
        assertEquals(0, journal.beginDispatch(operation.id))
        controller.result(operation.id, 0, SMS_NETWORK_SENT)
        controller.result(UUID.randomUUID().toString(), 0, SMS_NETWORK_SENT)
        assertEquals(saved, drafts.read())
        assertTrue(platform.requests.isEmpty())
    }

    @Test fun explicitRetryPreservesTextAndLateOldCallbackCannotClearIt() = runBlocking {
        val saved = draft()
        val first = controller.submit(controller.confirm(saved))
        val next = drafts.newAttemptIfRevision(saved.revision)!!
        assertEquals(saved.body, next.body)
        assertTrue(next.revision > saved.revision)
        assertNull(drafts.newAttemptIfRevision(saved.revision))
        controller.result(first, 0, SMS_NETWORK_SENT)
        assertEquals(next, drafts.read())
        controller.submit(controller.confirm(next))
        assertEquals(2, platform.requests.size)
    }

    @Test fun completedJournalReconcilesAfterConsentReturnsWithoutResending() = runBlocking {
        val saved = draft()
        val id = controller.submit(controller.confirm(saved))
        consent.setEnabled(false)
        controller.result(id, 0, SMS_NETWORK_SENT)
        assertEquals(saved, drafts.read())
        consent.setEnabled(true)
        SmsSendController(platform, consent, SmsDraftStore(draftPreferences), journal).reconcileCompletedDraft()
        assertEquals("", drafts.read().body)
        assertEquals(1, platform.requests.size)
    }

    @Test fun journalReopenRetainsPartialResultsAndReconcilesWithoutResending() = runBlocking {
        val saved = draft()
        val file = "sms-journal-${UUID.randomUUID()}.db"
        val id = UUID.randomUUID().toString()
        try {
            val first = Room.databaseBuilder(context, SmsSendDatabase::class.java, file).build()
            try {
                first.sends().prepare(SmsSendAttempt(id, saved.revision, "+12025550199", smsBodyDigest(saved.body), 7, 2, 0))
                first.sends().beginDispatch(id)
                first.sends().recordResult(id, 1, SMS_NETWORK_SENT)
            } finally { first.close() }
            val reopened = Room.databaseBuilder(context, SmsSendDatabase::class.java, file).build()
            try {
                assertEquals("SUBMITTED", reopened.sends().attempt(id)!!.stage)
                assertEquals(SMS_NETWORK_SENT, reopened.sends().parts(id)[1].resultCode)
                val resumed = SmsSendController(platform, consent, SmsDraftStore(draftPreferences), reopened.sends())
                resumed.reconcileCompletedDraft()
                assertEquals(saved, drafts.read())
                resumed.result(id, 0, SMS_NETWORK_SENT)
                assertEquals("", drafts.read().body)
                assertTrue(platform.requests.isEmpty())
            } finally { reopened.close() }
        } finally { context.deleteDatabase(file) }
    }

    @Test fun failedConsentCommitIsFailClosedForThisProcess() {
        val failing = object : SharedPreferences by consentPreferences {
            override fun edit(): SharedPreferences.Editor {
                val editor = consentPreferences.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { editor.putBoolean(key, value) }
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { editor.putString(key, value) }
                    override fun commit(): Boolean { editor.apply(); return false }
                }
            }
        }
        val store = SmsConsentStore(failing)
        assertFalse(store.setEnabled(false)); assertFalse(store.enabled())
        assertFalse(store.setEnabled(true)); assertFalse(store.enabled())
    }
}
