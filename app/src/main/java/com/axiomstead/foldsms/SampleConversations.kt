package com.axiomstead.foldsms

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class MessageDirection { INCOMING, OUTGOING }

data class SampleMessage(
    val id: Int,
    val body: String,
    val timestamp: Instant,
    val direction: MessageDirection
) {
    val time: String get() = messageTime.format(timestamp)
}

private val messageTime = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Manila"))

data class SampleConversation(
    val id: Int,
    val sender: String,
    val category: String,
    val messages: List<SampleMessage>
) {
    val history: List<SampleMessage> get() = messages.sortedWith(compareBy({ it.timestamp }, { it.id }))
    val preview: String get() = history.lastOrNull()?.body.orEmpty()
    val time: String get() = history.lastOrNull()?.time.orEmpty()
}

// Fixed, fabricated fixtures. IDs 1–5 remain stable because existing drafts use them.
val exampleThreads = listOf(
    SampleConversation(1, "Alex", "Personal", listOf(
        message(101, "Are we still meeting later?", "01:30", MessageDirection.INCOMING),
        message(102, "Yes, after work works for me.", "01:35", MessageDirection.OUTGOING),
        message(103, "See you this evening!", "02:42", MessageDirection.INCOMING)
    )),
    SampleConversation(2, "Bank Alert", "Finance", listOf(
        message(201, "Sample account alert: card payment received.", "00:05", MessageDirection.INCOMING),
        message(202, "Sample transaction: ₱1,250", "01:18", MessageDirection.INCOMING)
    )),
    SampleConversation(3, "Parcel Updates", "Services", listOf(
        message(301, "Your sample order has been packed.", "00:10", MessageDirection.INCOMING),
        message(302, "Sample package is on the way", "01:00", MessageDirection.INCOMING)
    )),
    SampleConversation(4, "Promo Center", "Promotions", listOf(
        message(401, "Sample member offer: selected items this week.", "00:15", MessageDirection.INCOMING),
        message(402, "Sample weekend offer", "00:45", MessageDirection.INCOMING)
    )),
    SampleConversation(5, "Verification", "OTP", listOf(
        message(501, "This is a fabricated verification conversation.", "00:20", MessageDirection.INCOMING),
        message(502, "Your sample code is 123456", "00:30", MessageDirection.INCOMING)
    ))
)

private fun message(id: Int, body: String, utcTime: String, direction: MessageDirection) =
    SampleMessage(id, body, Instant.parse("2026-11-02T${utcTime}:00Z"), direction)
