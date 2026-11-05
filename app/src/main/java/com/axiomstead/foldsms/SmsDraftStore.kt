package com.axiomstead.foldsms

import android.content.SharedPreferences
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Revision binds callbacks to the submitted draft, so newer edits cannot be cleared. */
internal class SmsDraftStore(private val preferences: SharedPreferences) {
    private fun readUnlocked(): SmsDraft = SmsDraft(preferences.getLong("revision", 0),
        preferences.getString("address", "").orEmpty(), preferences.getString("body", "").orEmpty(),
        preferences.getInt("subscription", -1))
    fun read(): SmsDraft = synchronized(lock) { readUnlocked() }
    fun edit(address: String? = null, body: String? = null, subscriptionId: Int? = null) = synchronized(lock) {
        val current = readUnlocked()
        val next = current.copy(address = address ?: current.address, body = body ?: current.body,
            subscriptionId = subscriptionId ?: current.subscriptionId)
        if (next != current) {
            check(current.revision < Long.MAX_VALUE)
            preferences.edit().putLong("revision", current.revision + 1).putString("address", next.address)
                .putString("body", next.body).putInt("subscription", next.subscriptionId).apply()
        }
    }
    fun ensureDurable(expected: SmsDraft): Boolean = synchronized(lock) {
        if (readUnlocked() != expected) return@synchronized false
        preferences.edit().putString("handoff_nonce", UUID.randomUUID().toString()).putLong("revision", expected.revision).putString("address", expected.address)
            .putString("body", expected.body).putInt("subscription", expected.subscriptionId).commit()
    }
    fun newAttemptIfRevision(revision: Long): SmsDraft? = synchronized(lock) {
        val current = readUnlocked()
        if (current.revision != revision || revision == Long.MAX_VALUE) return@synchronized null
        if (!preferences.edit().putLong("revision", revision + 1).commit()) return@synchronized null
        readUnlocked()
    }
    fun clearIfRevision(revision: Long): Boolean = synchronized(lock) {
        val current = readUnlocked()
        if (current.revision != revision || revision == Long.MAX_VALUE) return@synchronized false
        preferences.edit().putLong("revision", revision + 1).putString("address", "").putString("body", "").commit()
    }
    val changes get() = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(read())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
    companion object { private val lock = Any() }
}
