package com.axiomstead.foldsms

import java.time.Instant

/** A one-time code found in a received message. Only the code and where it came from are kept; nothing is logged. */
internal data class OtpHit(val code: String, val conversationKey: String, val sender: String, val dateMillis: Long)

private val otpIntent = Regex(
    """\b(otp|one[- ]?time|verification|verify|login|log[- ]?in|sign[- ]?in|security|authentication|authorization|passcode|pass code|pin|code|kodigo|confirm(ation)?|2fa|mfa|tac)\b""",
    RegexOption.IGNORE_CASE
)
// A group of 4-8 digits, optionally written as 123 456 or 123-456 (two groups of three or four), not glued to other digits or letters.
private val codeCandidate = Regex("""(?<![\w.,₱$@/-])(\d{3,4}[ -]\d{3,4}|\d{4,8})(?![\w@/])(?![.,]\d)""")
private val moneyBefore = Regex("""(₱|\$|php|usd|php\.|amount|balance|bal|total|paid|payment|of)\s*$""", RegexOption.IGNORE_CASE)
private val moneyOrUnitAfter = Regex("""^\s*(%|pesos?|php|usd|pts|points|mb|gb|kb|minutes|mins|hrs|hours|days)""", RegexOption.IGNORE_CASE)

/**
 * The one-time code in [text], or null. The message must talk about a code (OTP, verification, PIN, "code", ...), and the
 * number must look like one: 4 to 8 digits (or two groups such as 123 456), not an amount, percentage or longer number.
 * The candidate closest after the intent word wins, else the first one.
 */
internal fun extractOtp(text: String): String? {
    if (text.length > 600 || !otpIntent.containsMatchIn(text)) return null
    val intentAt = otpIntent.find(text)!!.range.first
    val candidates = codeCandidate.findAll(text).filter { match ->
        val before = text.substring(maxOf(0, match.range.first - 14), match.range.first)
        val after = text.substring(match.range.last + 1, minOf(text.length, match.range.last + 12))
        !moneyBefore.containsMatchIn(before) && !moneyOrUnitAfter.containsMatchIn(after)
    }.toList()
    val best = candidates.firstOrNull { it.range.first >= intentAt } ?: candidates.firstOrNull() ?: return null
    return best.value.filter { it.isDigit() }.takeIf { it.length in 4..8 }
}

/**
 * The newest received code within [windowMillis] of [now], across all conversations, or null. Used for the "latest code"
 * card, so an old code never lingers there.
 */
internal fun latestOtp(conversations: List<DeviceConversation>, now: Instant, windowMillis: Long = 10 * 60_000L): OtpHit? {
    val earliest = now.toEpochMilli() - windowMillis
    var best: OtpHit? = null
    conversations.forEach { conversation ->
        conversation.messages.asReversed().firstOrNull { !it.outgoing && it.date >= earliest && it.date <= now.toEpochMilli() + 60_000 && extractOtp(it.text) != null }
            ?.let { message ->
                if (best == null || message.date > best!!.dateMillis) best = OtpHit(extractOtp(message.text)!!, conversation.key, conversation.title, message.date)
            }
    }
    return best
}

/** The code of a conversation's newest received message, if that message has one and is recent enough to matter. */
internal fun recentOtp(conversation: DeviceConversation, now: Instant, windowMillis: Long = 30 * 60_000L): String? {
    val last = conversation.messages.lastOrNull { !it.outgoing } ?: return null
    return if (now.toEpochMilli() - last.date <= windowMillis) extractOtp(last.text) else null
}
