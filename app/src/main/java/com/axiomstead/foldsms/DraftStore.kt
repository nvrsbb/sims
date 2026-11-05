package com.axiomstead.foldsms

import android.content.Context
import android.content.SharedPreferences

/** Keeps the original preference file and keys so existing drafts remain readable. */
internal class DraftStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("sample_drafts", Context.MODE_PRIVATE))

    fun get(threadId: Int): String = preferences.getString("thread_$threadId", "") ?: ""

    fun customDrafts(): Map<Int, String> = preferences.all.mapNotNull { (key, value) ->
        val id = key.takeIf { it.startsWith("thread_") }?.removePrefix("thread_")?.toIntOrNull()
        val text = value as? String
        if (id != null && id < 0 && !text.isNullOrEmpty()) id to text else null
    }.toMap()

    fun lowestCustomThreadId(): Int? = preferences.all.keys.mapNotNull { key ->
        key.takeIf { it.startsWith("thread_") }?.removePrefix("thread_")?.toIntOrNull()?.takeIf { it < 0 }
    }.minOrNull()

    // Called on Room's transaction executor before committing a new custom conversation.
    // Ordinary typing continues to use apply() below.
    fun saveInitial(threadId: Int, text: String): Boolean {
        val edit = preferences.edit()
        if (text.isEmpty()) edit.remove("thread_$threadId") else edit.putString("thread_$threadId", text)
        return edit.commit()
    }

    fun save(threadId: Int, text: String) {
        val edit = preferences.edit()
        if (text.isEmpty()) edit.remove("thread_$threadId") else edit.putString("thread_$threadId", text)
        edit.apply()
    }
}
