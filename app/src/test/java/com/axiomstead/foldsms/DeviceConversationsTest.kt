package com.axiomstead.foldsms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceConversationsTest {
    private fun sms(id: Long, thread: Long, address: String, body: String, date: Long, type: Int = 1, read: Boolean = true) =
        DeviceSms(id, thread, address, body, date, type, 1, read)
    private fun mms(id: Long, thread: Long, address: String, date: Long, box: Int = 1, text: String = "", subject: String = "",
                    parts: List<DeviceMmsPart> = emptyList(), read: Boolean = true) =
        DeviceMms(id, thread, address, subject, date, box,
            read, listOf(DeviceMmsPart(1, "text/plain", text, "")) + parts)

    @Test fun groupsSmsAndMmsByThreadNewestConversationFirst() {
        val result = groupDeviceConversations(
            listOf(sms(1, 10, "2025550101", "first", 1000), sms(2, 20, "BANK", "alert", 5000), sms(3, 10, "2025550101", "later", 3000, type = 2)),
            listOf(mms(1, 10, "2025550101", 2000, text = "pic"))
        )
        assertEquals(listOf("thread-20", "thread-10"), result.map { it.key })
        assertEquals(listOf("first", "pic", "later"), result.last().messages.map { it.text })
        assertEquals(listOf("sms-1", "mms-1", "sms-3"), result.last().messages.map { it.key })
    }

    @Test fun smsAndMmsWithTheSameProviderIdStayDistinct() {
        val result = groupDeviceConversations(listOf(sms(5, 1, "111", "a", 10)), listOf(mms(5, 1, "111", 20, text = "b")))
        assertEquals(1, result.size)
        assertEquals(setOf("sms-5", "mms-5"), result.single().messages.map { it.key }.toSet())
    }

    @Test fun identicalTimestampsKeepADeterministicOrder() {
        val rows = listOf(sms(9, 1, "111", "nine", 100), sms(3, 1, "111", "three", 100), sms(7, 1, "111", "seven", 100))
        val a = groupDeviceConversations(rows, emptyList()).single().messages.map { it.id }
        val b = groupDeviceConversations(rows.reversed(), emptyList()).single().messages.map { it.id }
        assertEquals(listOf(3L, 7L, 9L), a)
        assertEquals(a, b)
    }

    @Test fun missingThreadIdsFallBackToNormalizedAddress() {
        val result = groupDeviceConversations(
            listOf(sms(1, 0, "+1 (202) 555-0101", "a", 10), sms(2, -1, "12025550101", "b", 20), sms(3, 0, "SMART", "c", 30), sms(4, 0, "smart", "d", 40)),
            emptyList()
        )
        assertEquals(2, result.size)
        assertEquals(listOf("a", "b"), result.first { it.key.contains("2025550101") }.messages.map { it.text })
        assertEquals(2, result.first { it.key == "address-smart" }.messages.size)
    }

    @Test fun providerDraftsAreSkipped() {
        val result = groupDeviceConversations(listOf(sms(1, 1, "111", "draft", 10, type = 3), sms(2, 1, "111", "real", 5)), listOf(mms(1, 1, "111", 50, box = 3, text = "mms draft")))
        assertEquals(listOf("real"), result.single().messages.map { it.text })
    }

    @Test fun directionFailureAndUnreadCountFollowProviderTypes() {
        val result = groupDeviceConversations(
            listOf(sms(1, 1, "111", "in-unread", 10, read = false), sms(2, 1, "111", "in-read", 20), sms(3, 1, "111", "out", 30, type = 2), sms(4, 1, "111", "bad", 40, type = 5)),
            listOf(mms(1, 1, "111", 50, box = 1, text = "mms-unread", read = false), mms(2, 1, "111", 60, box = 2, text = "mms-out", read = false))
        ).single()
        assertEquals(2, result.unreadCount) // outgoing rows never count, even when the provider flags them unread
        assertEquals(listOf(false, false, true, true, false, true), result.messages.map { it.outgoing })
        assertTrue(result.messages.first { it.id == 4L }.failed)
    }

    @Test fun previewFallsBackFromTextToSubjectToAttachments() {
        val image = DeviceMmsPart(2, "image/jpeg", "", "a.jpg")
        val audio = DeviceMmsPart(3, "audio/amr", "", "a.amr")
        assertEquals("hello", mms(1, 1, "1", 1, text = " hello ").toMessage().preview)
        assertEquals("Subject", mms(1, 1, "1", 1, subject = "Subject", parts = listOf(image)).toMessage().preview)
        assertEquals("Photo", mms(1, 1, "1", 1, parts = listOf(image)).toMessage().preview)
        assertEquals("2 photos", mms(1, 1, "1", 1, parts = listOf(image, image)).toMessage().preview)
        assertEquals("Attachment", mms(1, 1, "1", 1, parts = listOf(audio)).toMessage().preview)
        assertEquals("Message", mms(1, 1, "1", 1).toMessage().preview)
    }

    @Test fun conversationTitleUsesTheLatestKnownAddress() {
        val result = groupDeviceConversations(listOf(sms(1, 1, "", "a", 10), sms(2, 1, "BANK", "b", 20)), emptyList()).single()
        assertEquals("BANK", result.title)
        assertEquals("Unknown sender", groupDeviceConversations(listOf(sms(1, 1, "", "a", 10)), emptyList()).single().title)
    }

    @Test fun searchMatchesTitleDigitsTextAndSubjectAndKeepsOrder() {
        val all = groupDeviceConversations(
            listOf(sms(1, 1, "+63 917 555 0101", "lunch tomorrow", 30), sms(2, 2, "BANK", "Balance alert", 20)),
            listOf(mms(1, 3, "2025550199", 10, subject = "Vacation photos"))
        )
        assertEquals(3, searchDeviceConversations(all, "  ").size)
        assertEquals(listOf("thread-2"), searchDeviceConversations(all, "bank").map { it.key })
        assertEquals(listOf("thread-1"), searchDeviceConversations(all, "917 555").map { it.key })
        assertEquals(listOf("thread-1"), searchDeviceConversations(all, "lunch").map { it.key })
        assertEquals(listOf("thread-3"), searchDeviceConversations(all, "vacation").map { it.key })
        assertEquals(listOf("thread-3"), searchDeviceConversations(all, "555 0199").map { it.key })
        assertTrue(searchDeviceConversations(all, "zzz").isEmpty())
    }

    @Test fun identicalMessagesWithLargeProviderIdsStayDistinct() {
        // Ported from the removed Device tab test: same body and time must never collapse, and ids beyond Int must survive.
        val result = groupDeviceConversations(
            listOf(sms(3_000_000_001L, 1, "2025550199", "Same text", 1234), sms(3_000_000_002L, 1, "2025550199", "Same text", 1234)),
            listOf(mms(9_000_000_000L, 1, "2025550199", 1234, text = "Same text"), mms(9_000_000_001L, 1, "2025550199", 1234, text = "Same text"))
        ).single()
        assertEquals(4, result.messages.size)
        assertEquals(listOf("sms-3000000001", "sms-3000000002", "mms-9000000000", "mms-9000000001"), result.messages.map { it.key })
    }
}
