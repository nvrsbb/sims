package com.axiomstead.foldsms

import android.content.Context
import android.content.SharedPreferences

internal class ThemeStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("preview_settings", Context.MODE_PRIVATE))

    fun get(): ThemeChoice = ThemeChoice.fromStored(preferences.getString("theme", null))

    fun save(theme: ThemeChoice) {
        preferences.edit().putString("theme", theme.name).apply()
    }
}
