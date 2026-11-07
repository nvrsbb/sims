package com.axiomstead.foldsms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/** One phone number of one contact. A contact with several numbers gives several entries. */
internal data class ContactEntry(val name: String, val number: String)

internal interface ContactsPlatform {
    fun canRead(): Boolean
    fun all(): List<ContactEntry>
}

/** Number key (see [conversationAddressKey]) to contact name. If two contacts share a number, the first by name wins. */
internal fun contactNameMap(entries: List<ContactEntry>): Map<String, String> {
    val names = LinkedHashMap<String, String>()
    entries.sortedBy { it.name.lowercase() }.forEach { entry ->
        val key = conversationAddressKey(entry.number)
        if (key.isNotEmpty() && entry.name.isNotBlank()) names.putIfAbsent(key, entry.name.trim())
    }
    return names
}

/** Contacts whose name contains the text, or whose number contains its digits (three or more); best matches first. */
internal fun searchContacts(entries: List<ContactEntry>, query: String, limit: Int = 6): List<ContactEntry> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    val digits = needle.filter { it.isDigit() }
    return entries.filter { it.name.contains(needle, ignoreCase = true) ||
            (digits.length >= 3 && it.number.filter(Char::isDigit).contains(digits)) }
        .sortedWith(compareByDescending<ContactEntry> { it.name.startsWith(needle, ignoreCase = true) }.thenBy { it.name.lowercase() }.thenBy { it.number })
        .distinctBy { it.name.lowercase() to conversationAddressKey(it.number) }
        .take(limit)
}

/** Conversations with their contact name filled in; the title, avatar and search then use it. */
internal fun withContactNames(conversations: List<DeviceConversation>, names: Map<String, String>): List<DeviceConversation> =
    if (names.isEmpty()) conversations
    else conversations.map { conversation -> names[conversationAddressKey(conversation.address)]?.let { conversation.copy(contactName = it) } ?: conversation }

internal class AndroidContactsPlatform(private val context: Context) : ContactsPlatform {
    override fun canRead() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    override fun all(): List<ContactEntry> {
        if (!canRead()) return emptyList()
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        return context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC")?.use { cursor ->
            buildList {
                while (size < 10_000 && cursor.moveToNext()) {
                    val name = cursor.getString(0).orEmpty(); val number = cursor.getString(1).orEmpty()
                    if (name.isNotBlank() && number.isNotBlank()) add(ContactEntry(name, number))
                }
            }
        } ?: emptyList()
    }
}

/** The contact name for one number, for notifications. Null without permission or a match. */
internal fun contactNameFor(context: Context, address: String): String? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
    if (address.none { it.isDigit() } || address.any { it.isLetter() }) return null
    val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
    return try {
        context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: Exception) { null }
}
