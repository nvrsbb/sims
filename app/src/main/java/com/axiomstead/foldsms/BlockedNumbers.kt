package com.axiomstead.foldsms

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract

/**
 * The phone's own block list (the same one the Phone app uses). Only the default SMS app or dialer may change it. Android
 * itself drops texts and calls from a blocked number before they reach any app, so FoldSMS only manages the list.
 */
internal interface NumberBlocker {
    fun canBlock(): Boolean
    fun blocked(): List<String>
    fun block(address: String): Boolean
    fun unblock(address: String): Boolean
}

/** Only plain phone numbers can be blocked: a name sender (BANK) is not a number the system can match. */
internal fun isBlockable(address: String): Boolean = runCatching { smsAddress(address) }.isSuccess

internal fun isBlockedIn(blocked: Collection<String>, address: String): Boolean {
    val key = conversationAddressKey(address)
    return key.isNotEmpty() && blocked.any { conversationAddressKey(it) == key }
}

internal class AndroidNumberBlocker(private val context: Context) : NumberBlocker {
    override fun canBlock(): Boolean = try { isDefaultSmsApp(context) && BlockedNumberContract.canCurrentUserBlockNumbers(context) } catch (_: Exception) { false }

    override fun blocked(): List<String> {
        if (!canBlock()) return emptyList()
        return try {
            context.contentResolver.query(BlockedNumberContract.BlockedNumbers.CONTENT_URI, arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
                null, null, null)?.use { cursor -> buildList { while (cursor.moveToNext()) cursor.getString(0)?.takeIf { it.isNotBlank() }?.let(::add) } }.orEmpty()
        } catch (_: Exception) { emptyList() }
    }

    override fun block(address: String): Boolean {
        if (!canBlock() || !isBlockable(address)) return false
        return try {
            if (BlockedNumberContract.isBlocked(context, address)) true
            else context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                ContentValues().apply { put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, smsAddress(address)) }) != null
        } catch (failure: Exception) { Diagnostics.record(context, "BLOCK_FAILED", failure.javaClass.name); false }
    }

    override fun unblock(address: String): Boolean {
        if (!canBlock()) return false
        return try { BlockedNumberContract.unblock(context, address); true } catch (failure: Exception) { Diagnostics.record(context, "UNBLOCK_FAILED", failure.javaClass.name); false }
    }
}
