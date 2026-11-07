package com.axiomstead.foldsms

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings

/** Whether to ask the owner to make FoldSMS the default messaging app. Asked once; "Not now" is remembered. */
internal fun shouldOfferDefault(access: SmsAccess, dismissed: Boolean, roleAvailable: Boolean): Boolean =
    access.enabled && access.messagingSupported && roleAvailable && !access.isDefaultApp && !dismissed

internal class DefaultPromptStore(private val preferences: SharedPreferences) {
    fun dismissed(): Boolean = preferences.getBoolean("default_prompt_dismissed", false)
    fun dismiss() { preferences.edit().putBoolean("default_prompt_dismissed", true).apply() }
}

/** The system's own role dialog; Android shows what changes and the owner confirms there. Null if the role cannot be requested. */
internal fun defaultRoleRequest(context: Context): Intent? {
    val roles = context.getSystemService(RoleManager::class.java) ?: return null
    return if (roles.isRoleAvailable(RoleManager.ROLE_SMS) && !roles.isRoleHeld(RoleManager.ROLE_SMS)) roles.createRequestRoleIntent(RoleManager.ROLE_SMS) else null
}

internal fun roleAvailable(context: Context): Boolean =
    context.getSystemService(RoleManager::class.java)?.isRoleAvailable(RoleManager.ROLE_SMS) == true

/** Android's own screen for choosing the default SMS app: the way back to Google Messages. */
internal fun defaultAppsSettings() = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)

/** The inbox banner asking the owner to choose FoldSMS as the default messaging app. */
internal class DefaultOffer(val onMakeDefault: () -> Unit, val onNotNow: () -> Unit)
