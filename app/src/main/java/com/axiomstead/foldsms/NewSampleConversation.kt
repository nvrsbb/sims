package com.axiomstead.foldsms

/** No carrier lookup or sending. Formatting is ignored, country codes are explicit. */
fun normalizedSampleAddress(address: String): String? {
    val trimmed = address.trim()
    if (!trimmed.matches(Regex("\\+?[0-9 ().-]+"))) return null
    return trimmed.filter { it in '0'..'9' }.takeIf { it.length in 3..20 }
}

data class NewSampleConversationInput(val name: String, val address: String, val normalizedAddress: String, val draft: String)

fun validateNewSampleConversation(
    name: String,
    address: String,
    draft: String,
    existingAddresses: Collection<String>
): NewSampleConversationInput {
    val cleanName = name.trim()
    require(cleanName.isNotEmpty() && cleanName.length <= 80) { "Enter a sample name of 1–80 characters." }
    val normalized = normalizedSampleAddress(address)
    require(normalized != null) { "Enter a fabricated numeric address with 3–20 digits. Formatting and a leading + are allowed." }
    require(existingAddresses.none { normalizedSampleAddress(it) == normalized }) {
        "A sample conversation already uses this address. Open that conversation to keep its draft."
    }
    return NewSampleConversationInput(cleanName, address.trim(), normalized, draft)
}

/** Fixture IDs are positive. Custom IDs count down from -1 and are never reused. */
fun nextCustomConversationId(lowestExistingId: Int?): Int {
    require(lowestExistingId == null || lowestExistingId < 0)
    check(lowestExistingId != Int.MIN_VALUE) { "No local conversation IDs remain." }
    return (lowestExistingId ?: 0) - 1
}
