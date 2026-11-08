package com.axiomstead.foldsms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/** One phone number of one contact. A contact with several numbers gives several entries. */
internal data class ContactEntry(val name: String, val number: String, val contactId: Long = 0, val lookupKey: String = "")

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
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
        return context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC")?.use { cursor ->
            buildList {
                while (size < 10_000 && cursor.moveToNext()) {
                    val name = cursor.getString(0).orEmpty(); val number = cursor.getString(1).orEmpty()
                    if (name.isNotBlank() && number.isNotBlank()) add(ContactEntry(name, number, cursor.getLong(2), cursor.getString(3).orEmpty()))
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

/** One person with all their numbers, for the Contacts screen. */
internal data class ContactGroup(val name: String, val contactId: Long, val lookupKey: String, val numbers: List<String>) {
    val initial: String get() = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#"
}

/** Entries of one contact (same id, or the same name when there is no id) become one person; the list is sorted by name. */
internal fun groupContacts(entries: List<ContactEntry>): List<ContactGroup> =
    entries.groupBy { if (it.contactId > 0) "id-${it.contactId}" else "name-${it.name.trim().lowercase()}" }.values.map { group ->
        val first = group.first()
        ContactGroup(first.name.trim(), first.contactId, first.lookupKey, group.map { it.number.trim() }.distinctBy { conversationAddressKey(it) })
    }.sortedWith(compareBy({ it.name.lowercase() }, { it.contactId }))

/** People whose name contains the text or one of whose numbers contains its digits (three or more); blank keeps everyone. */
internal fun filterGroups(groups: List<ContactGroup>, query: String): List<ContactGroup> {
    val needle = query.trim()
    if (needle.isEmpty()) return groups
    val digits = needle.filter { it.isDigit() }
    return groups.filter { group ->
        group.name.contains(needle, ignoreCase = true) || (digits.length >= 3 && group.numbers.any { it.filter(Char::isDigit).contains(digits) })
    }
}

/** The system's new-contact form. Saving is done in that form, so no write permission is needed here. */
internal fun newContactIntent(): android.content.Intent =
    android.content.Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)

/** Offers to create a contact with this number or add it to an existing one, in the system's own screens. */
internal fun addToContactsIntent(number: String): android.content.Intent =
    android.content.Intent(android.content.Intent.ACTION_INSERT_OR_EDIT).setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
        .putExtra(ContactsContract.Intents.Insert.PHONE, number)

/** The system's contact editor for an existing contact, or null when the contact has no usable id. */
internal fun editContactIntent(contactId: Long, lookupKey: String): android.content.Intent? {
    if (contactId <= 0 || lookupKey.isBlank()) return null
    return android.content.Intent(android.content.Intent.ACTION_EDIT, ContactsContract.Contacts.getLookupUri(contactId, lookupKey))
}

/** The edit intent for the contact that holds this number, from the already loaded list. */
internal fun editIntentForNumber(entries: List<ContactEntry>, address: String): android.content.Intent? {
    val key = conversationAddressKey(address)
    val entry = entries.firstOrNull { conversationAddressKey(it.number) == key } ?: return null
    return editContactIntent(entry.contactId, entry.lookupKey)
}
