package com.axiomstead.foldsms

import android.content.Context
import android.content.SharedPreferences

/** Keeps the original preference file and keys so existing drafts remain readable. */
internal class DraftStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("sample_drafts", Context.MODE_PRIVATE))

    fun get(threadId: Int): String = preferences.getString("thread_$threadId", "") ?: ""

    fun save(threadId: Int, text: String) {
        val edit = preferences.edit()
        if (text.isEmpty()) edit.remove("thread_$threadId") else edit.putString("thread_$threadId", text)
        edit.apply()
    }
}
