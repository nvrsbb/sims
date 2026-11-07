package com.axiomstead.foldsms

import java.security.MessageDigest

internal const val SMS_NETWORK_SENT = -1 // Activity.RESULT_OK from SmsManager's sent callback.
internal const val MAX_SMS_PARTS = 10
internal const val MAX_SMS_BODY_LENGTH = 10_000

internal data class SmsAccess(
    val enabled: Boolean, val messagingSupported: Boolean, val hasDefaultApp: Boolean,
    val readGranted: Boolean, val receiveGranted: Boolean, val sendGranted: Boolean, val phoneGranted: Boolean,
    /** FoldSMS itself holds the default SMS role. [hasDefaultApp] is true when any app, including FoldSMS, does. */
    val isDefaultApp: Boolean = false
) {
    val canRead get() = enabled && messagingSupported && hasDefaultApp && readGranted && receiveGranted
    val canSend get() = enabled && messagingSupported && hasDefaultApp && sendGranted && phoneGranted
}

internal data class SmsSubscription(val id: Int, val slot: Int, val label: String)
internal data class DeviceSms(val id: Long, val threadId: Long, val address: String, val body: String,
    val date: Long, val type: Int, val subscriptionId: Int, val read: Boolean)
internal data class SmsDraft(val revision: Long = 0, val address: String = "", val body: String = "", val subscriptionId: Int = -1)

internal fun smsAddress(input: String): String {
    require(input.all { it in '0'..'9' || it == '+' || it == '-' || it == '(' || it == ')' || it.isWhitespace() }) {
        "Enter an SMS number, including the country code when needed."
    }
    val number = input.filterNot { it.isWhitespace() || it in "-()" }
    require(number.matches(Regex("\\+?[0-9]{3,15}"))) { "Enter a valid SMS number (3–15 digits)." }
    return number
}

internal fun validateSmsDraft(draft: SmsDraft): String {
    val address = smsAddress(draft.address)
    require(draft.body.isNotBlank()) { "Write a message first." }
    require(draft.body.length <= MAX_SMS_BODY_LENGTH) { "This SMS mode supports up to 10,000 text characters." }
    require(draft.subscriptionId >= 0) { "Choose an active SIM." }
    return address
}

internal fun smsBodyDigest(body: String): String = MessageDigest.getInstance("SHA-256")
    .digest(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

internal fun smsPartStatus(results: List<Int?>): String = when {
    results.isEmpty() || results.any { it == null } -> "SUBMITTED"
    results.all { it == SMS_NETWORK_SENT } -> "SENT"
    results.any { it == SMS_NETWORK_SENT } -> "PARTIAL"
    else -> "FAILED"
}

internal fun smsAttemptLabel(attempt: SmsAttemptWithParts): String {
    val results = attempt.parts.map { it.resultCode }
    val sent = results.count { it == SMS_NETWORK_SENT }
    val failed = results.count { it != null && it != SMS_NETWORK_SENT }
    return when (attempt.attempt.stage) {
        "PREPARED", "REJECTED" -> "Not submitted to the SMS service"
        "SENT" -> "Sent to network · delivery not confirmed"
        "FAILED" -> "Send failed · draft retained"
        "PARTIAL" -> "Partially sent · check Google Messages before sending again"
        else -> if (failed > 0) "$sent parts accepted, $failed failed · other results unconfirmed"
            else "Submitted · $sent/${attempt.attempt.partCount} parts accepted · awaiting results"
    }
}
