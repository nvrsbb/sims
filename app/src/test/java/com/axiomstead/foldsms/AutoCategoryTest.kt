package com.axiomstead.foldsms

import org.junit.Assert.assertEquals
import org.junit.Test

/** All messages here are invented for the tests. */
class AutoCategoryTest {
    private fun message(id: Long, text: String, outgoing: Boolean = false) =
        DeviceMessage(DeviceMessageKind.SMS, id, "", text, "", id * 1000, outgoing, true, 1, 0, 0, false)
    private fun thread(address: String, vararg messages: DeviceMessage) = DeviceConversation("thread-1", 1, address, messages.toList())
    private fun category(address: String, vararg texts: String) = autoCategory(thread(address, *texts.mapIndexed { i, t -> message(i + 1L, t) }.toTypedArray()))

    @Test fun oneTimeCodesAreOtpEvenFromABank() {
        assertEquals("OTP", category("EXAMPLEBANK", "Your verification code is 482913. Do not share it."))
        assertEquals("OTP", category("ACME", "Use 123456 as your login code"))
        assertEquals("OTP", category("+63 917 555 0101", "OTP: 7391"))
        assertEquals("OTP", category("ACME", "Your code is 55123, valid for 5 minutes"))
    }

    @Test fun moneyMessagesAreFinance() {
        assertEquals("Finance", category("SAMPLEBANK", "Card ending 1234 was charged ₱500.00"))
        assertEquals("Finance", category("PAYAPP", "Payment of PHP 250 received. Balance updated."))
        assertEquals("Finance", category("ACME", "Your statement is ready. Amount due 1,200"))
    }

    @Test fun deliveriesAndNoticesAreServices() {
        assertEquals("Services", category("PARCELCO", "Your parcel is out for delivery today"))
        assertEquals("Services", category("CLINIC", "Reminder: appointment tomorrow at 9"))
    }

    @Test fun offersAreOnlyPromotionsWhenNothingStrongerMatches() {
        assertEquals("Promotions", category("TELCOX", "Get UNLI data and FREE calls this week only"))
        assertEquals("Promotions", category("SHOPX", "Big sale: 50% off on all items"))
    }

    @Test fun peopleAreAPersonalConversation() {
        assertEquals("Personal", category("+63 917 555 0101", "See you tonight"))
        assertEquals("Personal", category("2025550101", "Are you coming later?"))
    }

    @Test fun aNumberYouHaveWrittenToStaysPersonalEvenIfItMentionsMoney() {
        val chat = thread("+63 917 555 0101", message(1, "Did you get the payment?", outgoing = true), message(2, "Yes, balance is fine"))
        assertEquals("Personal", autoCategory(chat))
    }

    @Test fun unmatchedNamesAreServicesAndEmptyThreadsAreHandled() {
        assertEquals("Services", category("ACME", "Hello there"))
        assertEquals("Personal", autoCategory(thread("2025550101")))
        assertEquals("Services", autoCategory(thread("")))
    }

    @Test fun onlyTheLastFiveIncomingMessagesAreRead() {
        val old = message(1, "Your verification code is 111111")
        val newer = (2L..7L).map { message(it, "Lunch at noon?") }
        assertEquals("Personal", autoCategory(thread("2025550101", old, *newer.toTypedArray())))
    }

    @Test fun theOwnersChoiceAlwaysWinsAndAutomaticReturnsWhenCleared() {
        val chat = thread("2025550101", message(1, "Hello"))
        assertEquals("Finance", effectiveCategory(chat, DeviceConversationMetadata("thread-1", category = "Finance")))
        assertEquals("Personal", effectiveCategory(chat, DeviceConversationMetadata("thread-1", pinned = true)))
        assertEquals("Personal", effectiveCategory(chat, null))
    }

    @Test fun chipLabelsAreShortButCategoryNamesAreUnchanged() {
        assertEquals("Promos", categoryChipLabel("Promotions"))
        assertEquals("OTP", categoryChipLabel("OTP"))
    }
}
