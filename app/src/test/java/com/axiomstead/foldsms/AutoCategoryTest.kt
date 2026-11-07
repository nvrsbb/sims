package com.axiomstead.foldsms

import org.junit.Assert.assertEquals
import org.junit.Test

/** All messages here are invented for the tests. */
class AutoCategoryTest {
    private fun message(id: Long, text: String, outgoing: Boolean = false) =
        DeviceMessage(DeviceMessageKind.SMS, id, "", text, "", id * 1000, outgoing, true, 1, 0, 0, false)
    private fun thread(address: String, vararg messages: DeviceMessage, contact: String? = null) =
        DeviceConversation("thread-1", 1, address, messages.toList(), contact)
    private fun category(address: String, vararg texts: String) = autoCategory(thread(address, *texts.mapIndexed { i, t -> message(i + 1L, t) }.toTypedArray()))

    @Test fun nameSendersAndShortCodesAreBusinessWhateverTheyWrite() {
        assertEquals("Business", category("EXAMPLEBANK", "Your verification code is 482913. Do not share it."))
        assertEquals("Business", category("PARCELCO", "Your parcel is out for delivery today"))
        assertEquals("Business", category("SHOPX", "Big sale: 50% off on all items"))
        assertEquals("Business", category("ACME", "Hello there"))
        assertEquals("Business", category("2256", "Thanks"))
        assertEquals("Business", category("+29290", "Your balance is low"))
    }

    @Test fun ordinaryMobileNumbersWritingNormallyArePersonal() {
        assertEquals("Personal", category("+63 917 555 0101", "See you tonight"))
        assertEquals("Personal", category("2025550101", "Are you coming later?"))
        assertEquals("Personal", category("09175550101", "Free ka ba mamaya? Paload naman, thanks")) // "free" and "load" alone are not enough
        assertEquals("Personal", category("09175550101", "Look at this https://example.com/video")) // a link alone is not enough
    }

    @Test fun commercialTextFromAMobileNumberIsBusiness() {
        assertEquals("Business", category("09175550102", "FREE data! Register now at https://promo.example.com/x"))
        assertEquals("Business", category("+63 917 555 0103", "Your verification code is 482913"))
        assertEquals("Business", category("09175550104", "Order update: parcel delivered. Track https://track.example.ph/a1"))
    }

    @Test fun aSavedContactIsPersonalEvenIfItSoundsCommercial() {
        assertEquals("Personal", autoCategory(thread("09175550105", message(1, "FREE data! Register now at https://promo.example.com/x"), contact = "Ana Cruz")))
        assertEquals("Personal", autoCategory(thread("GCASH", message(1, "Hello"), contact = "My shop")))
    }

    @Test fun aNumberYouHaveWrittenToStaysPersonalEvenIfItMentionsMoney() {
        val chat = thread("+63 917 555 0101", message(1, "Did you get the payment?", outgoing = true), message(2, "Yes, balance is fine https://pay.example.com/x"))
        assertEquals("Personal", autoCategory(chat))
    }

    @Test fun emptyThreadsAndBlankSendersAreHandled() {
        assertEquals("Personal", autoCategory(thread("2025550101")))
        assertEquals("Business", autoCategory(thread("")))
    }

    @Test fun onlyTheLastFiveIncomingMessagesAreRead() {
        val old = message(1, "Your verification code is 111111")
        val newer = (2L..7L).map { message(it, "Lunch at noon?") }
        assertEquals("Personal", autoCategory(thread("2025550101", old, *newer.toTypedArray())))
    }

    @Test fun theOwnersChoiceAlwaysWinsAndAutomaticReturnsWhenCleared() {
        val chat = thread("2025550101", message(1, "Hello"))
        assertEquals("Business", effectiveCategory(chat, DeviceConversationMetadata("thread-1", category = "Business")))
        assertEquals("Personal", effectiveCategory(thread("SHOPX", message(1, "Hi")), DeviceConversationMetadata("thread-1", category = "Personal")))
        assertEquals("Personal", effectiveCategory(chat, DeviceConversationMetadata("thread-1", pinned = true))) // no choice stored: automatic
        assertEquals("Personal", effectiveCategory(chat, null))
    }

    @Test fun valuesStoredByOlderVersionsReadAsBusinessExceptPersonal() {
        for (old in listOf("Finance", "Services", "Promotions", "OTP", "Unknown", "anything")) assertEquals("Business", normalizeCategory(old))
        assertEquals("Personal", normalizeCategory("Personal"))
        assertEquals("Business", effectiveCategory(thread("2025550101", message(1, "Hello")), DeviceConversationMetadata("thread-1", category = "Finance")))
    }
}
