package com.axiomstead.foldsms

private val otpWords = Regex("""\b(otp|one[- ]time (password|pin|code)|verification code|security code|login code|passcode|confirmation code|authentication code)\b""", RegexOption.IGNORE_CASE)
private val codeNearDigits = Regex("""\b(code|pin)\b.{0,25}\b\d{4,8}\b|\b\d{4,8}\b.{0,25}\b(is your|as your)\b.{0,20}\b(code|otp|pin)\b""", RegexOption.IGNORE_CASE)
private val financeWords = Regex("""\b(bank|bpi|bdo|metrobank|unionbank|landbank|security bank|rcbc|pnb|gcash|maya|paymaya|credit card|debit card|card ending|transaction|balance|payment received|payment of|paid|fund transfer|transferred|deposit|withdrawal|loan|installments?|amount due|statement|invoice|insurance|php|peso|usd)\b|[₱$]\s?\d""", RegexOption.IGNORE_CASE)
private val servicesWords = Regex("""\b(deliver(y|ed|ies)|parcel|package|order|booking|reservation|appointment|reminder|tracking|courier|rider|your bill|account number|subscription (has|will)|ticket|flight|schedule[d]?)\b""", RegexOption.IGNORE_CASE)
private val promoWords = Regex("""\b(promo|sale|discounts?|\d+% ?off|offer|voucher|deal|win|free|limited time|unli(mited)?|data|load|bonus|raffle|reward|cashback|register now|subscribe|exclusive|giveaway)\b""", RegexOption.IGNORE_CASE)

private val linkPattern = Regex("""https?://|www\\.|\\b[a-z0-9-]+\\.(com|ph|net|org|ly|me)/""", RegexOption.IGNORE_CASE)

/** The two categories the owner sees. Everything that is not a person is Business. */
internal const val CATEGORY_PERSONAL = "Personal"
internal const val CATEGORY_BUSINESS = "Business"
internal val chatCategories = listOf(CATEGORY_PERSONAL, CATEGORY_BUSINESS)

/** Older versions stored Finance, Services, Promotions, OTP or Unknown; all of those read as Business now. */
internal fun normalizeCategory(stored: String): String = if (stored == CATEGORY_PERSONAL) CATEGORY_PERSONAL else CATEGORY_BUSINESS

/**
 * A category for a conversation until the owner picks one by hand, strongest sign first:
 * a saved contact or a number you have written to is Personal; a name sender (GCASH) or a short code is Business; an
 * ordinary mobile number is Personal unless its recent messages look commercial (a one-time code, or a link plus
 * promotional, money or service wording), because a friend may well write "free" or "order" or send a link.
 */
internal fun autoCategory(conversation: DeviceConversation): String {
    if (conversation.contactName != null) return CATEGORY_PERSONAL
    val numericSender = runCatching { smsAddress(conversation.address) }.isSuccess
    if (!numericSender) return CATEGORY_BUSINESS
    if (conversation.messages.any { it.outgoing }) return CATEGORY_PERSONAL
    if (conversation.address.count { it.isDigit() } <= 6) return CATEGORY_BUSINESS // short codes belong to companies
    val incoming = conversation.messages.filter { !it.outgoing }.takeLast(5).joinToString("\n") { it.text.ifBlank { it.subject } }
    val score = (if (otpWords.containsMatchIn(incoming) || codeNearDigits.containsMatchIn(incoming)) 2 else 0) +
        (if (linkPattern.containsMatchIn(incoming)) 1 else 0) +
        listOf(financeWords, servicesWords, promoWords).count { it.containsMatchIn(incoming) }
    return if (score >= 2) CATEGORY_BUSINESS else CATEGORY_PERSONAL
}

/** The owner's choice if there is one (older values are mapped), otherwise the automatic category. */
internal fun effectiveCategory(conversation: DeviceConversation, metadata: DeviceConversationMetadata?): String =
    metadata?.category?.let(::normalizeCategory) ?: autoCategory(conversation)
