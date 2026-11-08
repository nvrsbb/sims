package com.axiomstead.foldsms

/** A fixed contact list for tests; the phone's real contacts are never read. */
internal class FakeContactsList(var allowed: Boolean, val entries: List<ContactEntry>) : ContactsPlatform {
    override fun canRead() = allowed
    override fun all() = if (allowed) entries else emptyList()
}
