package com.axiomstead.foldsms

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The reply handler with a fake platform and an in-memory journal: nothing is sent and no notification is posted. */
@org.junit.runner.RunWith(RetryRunner::class)
class DirectReplyTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "direct-reply-${UUID.randomUUID()}"
    private val consentPrefs = context.getSharedPreferences("$prefix-consent", Context.MODE_PRIVATE)
    private val draftPrefs = context.getSharedPreferences("$prefix-draft", Context.MODE_PRIVATE)
    private val consent = SmsConsentStore(consentPrefs).also { it.setEnabled(true) }
    private val sms = FakeSmsPlatform(consent).also { it.sims = listOf(SmsSubscription(7, 0, "First"), SmsSubscription(8, 1, "Second")) }
    private val database = Room.inMemoryDatabaseBuilder(context, SmsSendDatabase::class.java).build()
    private val drafts = SmsDraftStore(draftPrefs)
    private val services = SmsServices(sms, consent, drafts, database.sends(), FakeMmsPlatform(consent))

    @After fun clean() { database.close(); listOf(consentPrefs, draftPrefs).forEach { it.edit().clear().commit() } }

    private fun reply(address: String = "2025550101", text: String = "On my way", sim: Int = 8) =
        runBlocking { handleDirectReply(services, address, text, sim) }

    @Test fun aShortReplyIsSentOnceOnTheChosenSimAndTheDraftClearsOnSuccess() {
        assertEquals(DirectReplyOutcome.SENT, reply())
        val request = sms.requests.single()
        assertEquals("2025550101", request.address); assertEquals(8, request.sim); assertEquals(listOf("On my way"), request.parts)
        runBlocking { services.controller.result(request.id, 0, SMS_NETWORK_SENT) }
        assertEquals("", drafts.read().body)
    }

    @Test fun anInactiveSimFallsBackToTheFirstActiveOne() {
        assertEquals(DirectReplyOutcome.SENT, reply(sim = 99))
        assertEquals(7, sms.requests.single().sim)
    }

    @Test fun aLongReplyIsKeptAsTheDraftAndNotSent() {
        val long = "x".repeat(150) // 70 characters per part in the fake platform
        assertEquals(DirectReplyOutcome.SAVED_LONG, reply(text = long))
        assertTrue(sms.requests.isEmpty())
        assertEquals(long, drafts.read().body); assertEquals("2025550101", drafts.read().address)
    }

    @Test fun textAlreadyWaitingInTheDraftIsNeverReplaced() {
        drafts.edit(address = "2025550999", body = "Unsent for someone else", subscriptionId = 7)
        assertEquals(DirectReplyOutcome.DRAFT_WAITING, reply())
        assertTrue(sms.requests.isEmpty())
        assertEquals("Unsent for someone else", drafts.read().body); assertEquals("2025550999", drafts.read().address)
    }

    @Test fun aBlankReplyOrANameSenderDoesNothing() {
        assertEquals(DirectReplyOutcome.EMPTY, reply(text = "   "))
        assertEquals(DirectReplyOutcome.NOT_A_NUMBER, reply(address = "EXAMPLEBANK"))
        assertTrue(sms.requests.isEmpty()); assertEquals("", drafts.read().body)
    }

    @Test fun withoutSendPermissionNothingIsSentOrSaved() {
        sms.gate = sms.gate.copy(sendGranted = false)
        assertEquals(DirectReplyOutcome.CANNOT_SEND, reply())
        assertTrue(sms.requests.isEmpty()); assertEquals("", drafts.read().body)
    }

    @Test fun theSameMessageAgainAfterAnUnconfirmedSendIsKeptForReviewNotSentTwice() {
        assertEquals(DirectReplyOutcome.SENT, reply(text = "Same text"))
        drafts.clearIfRevision(drafts.read().revision) // the owner cleared the draft while the first send was still unconfirmed
        assertEquals(DirectReplyOutcome.SAVED_DUPLICATE, reply(text = "Same text"))
        assertEquals(1, sms.requests.size)
        assertEquals("Same text", drafts.read().body)
    }

    @Test fun aSecondReplyWhileTheFirstIsUnconfirmedWaitsForTheDraft() {
        assertEquals(DirectReplyOutcome.SENT, reply(text = "First"))
        assertEquals(DirectReplyOutcome.DRAFT_WAITING, reply(text = "Second")) // the first draft is cleared only by its result
        assertEquals(1, sms.requests.size)
    }
}
