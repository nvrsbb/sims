package com.axiomstead.foldsms

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Every message here is invented. */
class OtpCodeTest {
    @Test fun commonOneTimeCodeMessagesYieldTheCode() {
        assertEquals("482913", extractOtp("Your verification code is 482913. Do not share it."))
        assertEquals("123456", extractOtp("Use 123456 as your login code"))
        assertEquals("7391", extractOtp("OTP: 7391"))
        assertEquals("55123", extractOtp("Your code is 55123, valid for 5 minutes"))
        assertEquals("904512", extractOtp("[ACME] 904512 is your one-time password. Valid for 3 minutes."))
        assertEquals("246810", extractOtp("Ang iyong kodigo ay 246810. Huwag ibigay sa iba."))
        assertEquals("1234", extractOtp("Your PIN is 1234"))
    }

    @Test fun codesWrittenInTwoGroupsAreJoined() {
        assertEquals("123456", extractOtp("Your code is 123 456"))
        assertEquals("123456", extractOtp("Verification code: 123-456"))
    }

    @Test fun messagesThatAreNotCodesYieldNothing() {
        assertNull(extractOtp("See you at 5 tonight"))
        assertNull(extractOtp("Your order 12345678 has shipped")) // no code wording
        assertNull(extractOtp("Verification code is ready, check the app")) // no digits
    }

    @Test fun amountsPercentagesAndLongNumbersAreNotCodes() {
        assertNull(extractOtp("Your PIN transaction of PHP 1500 was approved"))
        assertNull(extractOtp("Security alert: payment of ₱2500.00 received"))
        assertNull(extractOtp("Confirm your 5000 points"))
        assertNull(extractOtp("Your login code is 123456789012")) // too long
        assertNull(extractOtp("Call 09175550101 to confirm"))
    }

    @Test fun theCandidateAfterTheIntentWordWinsOverAnEarlierNumber() {
        assertEquals("667788", extractOtp("Ref 99887766. Your verification code is 667788"))
    }

    @Test fun veryLongTextIsIgnored() {
        assertNull(extractOtp("verification code 123456 " + "x".repeat(700)))
    }

    private fun message(id: Long, text: String, date: Long, outgoing: Boolean = false) =
        DeviceMessage(DeviceMessageKind.SMS, id, "A", text, "", date, outgoing, false, -1, 0, 0, false)
    private fun thread(key: String, vararg messages: DeviceMessage) = DeviceConversation(key, 1, key, messages.toList())
    private val now = Instant.ofEpochMilli(10_000_000)

    @Test fun theLatestCodeIsTheNewestReceivedWithinTheWindowAcrossConversations() {
        val older = thread("a", message(1, "Your code is 111111", 10_000_000 - 8 * 60_000))
        val newer = thread("b", message(2, "Lunch?", 10_000_000 - 1_000), message(3, "Your code is 222222", 10_000_000 - 2 * 60_000))
        val hit = latestOtp(listOf(older, newer), now)!!
        assertEquals("222222", hit.code); assertEquals("b", hit.conversationKey)
    }

    @Test fun oldCodesAndCodesWeSentAreNotLatest() {
        assertNull(latestOtp(listOf(thread("a", message(1, "Your code is 111111", 10_000_000 - 11 * 60_000))), now))
        assertNull(latestOtp(listOf(thread("a", message(1, "Your code is 111111", 10_000_000 - 1_000, outgoing = true))), now))
        assertNull(latestOtp(emptyList(), now))
    }

    @Test fun aRowShowsACodeOnlyForARecentNewestReceivedMessage() {
        val fresh = thread("a", message(1, "Your code is 111111", 10_000_000 - 5 * 60_000))
        assertEquals("111111", recentOtp(fresh, now))
        assertNull(recentOtp(thread("a", message(1, "Your code is 111111", 10_000_000 - 40 * 60_000)), now))
        assertNull(recentOtp(thread("a", message(1, "Hello", 10_000_000 - 1_000)), now))
    }
}
