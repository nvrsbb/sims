package com.axiomstead.foldsms

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** v1 uses a new preference file; legacy drafts and Room schema remain untouched. */
internal class SampleDeletionStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("sample_deletions", Context.MODE_PRIVATE))

    private fun readIds(key: String) = preferences.getStringSet(key, emptySet()).orEmpty()
        .mapNotNull(String::toIntOrNull).toSet()

    private val mutableState = MutableStateFlow(SampleDeletionState(readIds("deleted_v1"), readIds("undo_v1")))
    val state = mutableState.asStateFlow()
    private val mutex = Mutex()

    suspend fun delete(ids: Set<Int>): Boolean = update { it.delete(ids) }
    suspend fun undo(): Boolean = update(SampleDeletionState::undo)

    private suspend fun update(transform: (SampleDeletionState) -> SampleDeletionState): Boolean =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val next = transform(mutableState.value)
                if (next == mutableState.value) return@withLock true
                val saved = preferences.edit()
                    .putStringSet("deleted_v1", next.deletedIds.map(Int::toString).toSet())
                    .putStringSet("undo_v1", next.undoIds.map(Int::toString).toSet()).commit()
                if (saved) mutableState.value = next
                saved
            }
        }
}
