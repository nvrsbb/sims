package com.axiomstead.foldsms

private val otpWords = Regex("""\b(otp|one[- ]time (password|pin|code)|verification code|security code|login code|passcode|confirmation code|authentication code)\b""", RegexOption.IGNORE_CASE)
private val codeNearDigits = Regex("""\b(code|pin)\b.{0,25}\b\d{4,8}\b|\b\d{4,8}\b.{0,25}\b(is your|as your)\b.{0,20}\b(code|otp|pin)\b""", RegexOption.IGNORE_CASE)
private val financeWords = Regex("""\b(bank|bpi|bdo|metrobank|unionbank|landbank|security bank|rcbc|pnb|gcash|maya|paymaya|credit card|debit card|card ending|transaction|balance|payment received|payment of|paid|fund transfer|transferred|deposit|withdrawal|loan|installments?|amount due|statement|invoice|insurance|php|peso|usd)\b|[₱$]\s?\d""", RegexOption.IGNORE_CASE)
private val servicesWords = Regex("""\b(deliver(y|ed|ies)|parcel|package|order|booking|reservation|appointment|reminder|tracking|courier|rider|your bill|account number|subscription (has|will)|ticket|flight|schedule[d]?)\b""", RegexOption.IGNORE_CASE)
private val promoWords = Regex("""\b(promo|sale|discounts?|\d+% ?off|offer|voucher|deal|win|free|limited time|unli(mited)?|data|load|bonus|raffle|reward|cashback|register now|subscribe|exclusive|giveaway)\b""", RegexOption.IGNORE_CASE)

/**
 * A category for a conversation from its sender and recent incoming text, used until the owner picks one by hand.
 * Order matters: one-time codes first, then money, then service notices, then promotions; a phone number you have
 * written to is Personal; any other unmatched number is Personal and any unmatched name is Services.
 */
internal fun autoCategory(conversation: DeviceConversation): String {
    val incoming = conversation.messages.filter { !it.outgoing }.takeLast(5).joinToString("\n") { it.text.ifBlank { it.subject } }
    val numericSender = runCatching { smsAddress(conversation.address) }.isSuccess
    val repliedTo = conversation.messages.any { it.outgoing }
    if (numericSender && repliedTo) return "Personal"
    if (otpWords.containsMatchIn(incoming) || codeNearDigits.containsMatchIn(incoming)) return "OTP"
    val sender = if (numericSender) "" else conversation.address
    val text = "$sender\n$incoming"
    if (financeWords.containsMatchIn(text)) return "Finance"
    if (servicesWords.containsMatchIn(text)) return "Services"
    if (promoWords.containsMatchIn(text)) return "Promotions"
    return if (numericSender) "Personal" else "Services"
}

/** The owner's choice if there is one, otherwise the automatic category. */
internal fun effectiveCategory(conversation: DeviceConversation, metadata: DeviceConversationMetadata?): String =
    metadata?.category ?: autoCategory(conversation)

/** Short label for the one-line chip row; the stored category name is unchanged. */
internal fun categoryChipLabel(category: String): String = if (category == "Promotions") "Promos" else category
