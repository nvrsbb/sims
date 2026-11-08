package com.axiomstead.foldsms

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager

/** A saved carrier code, for example "Load balance" and "*123#". Only the code is stored, never a reply. */
internal data class UssdCode(val label: String, val code: String)

/** The answer to one request: the carrier's text, or why there is none. The text is shown on screen only; it is never stored or logged. */
internal sealed interface UssdResult {
    data class Reply(val text: String) : UssdResult
    data class Failed(val reason: String) : UssdResult
}

internal interface UssdPlatform {
    /** Sends one request on the chosen SIM (null: the phone's default SIM) and reports once. Menus that need more replies are not followed. */
    fun run(code: String, subscriptionId: Int?, onResult: (UssdResult) -> Unit)
}

/** Starts with * or #, ends with #, and holds only digits, * and #. Anything else is refused before it reaches the carrier. */
internal fun isValidUssd(code: String): Boolean = code.length in 3..40 && (code.first() == '*' || code.first() == '#') && code.last() == '#' &&
    code.all { it.isDigit() || it == '*' || it == '#' }

internal val defaultUssdCodes = listOf(UssdCode("Load balance", "*123#"))

private const val FIELD = '\t'
private const val ROW = '\n'

internal fun encodeUssdCodes(codes: List<UssdCode>): String = codes.joinToString(ROW.toString()) { "${cleanLabel(it.label)}$FIELD${it.code}" }
internal fun cleanLabel(text: String) = text.filterNot { it == FIELD || it == ROW || it == '\r' }.trim().take(40)

/** Reads what [encodeUssdCodes] wrote; bad rows are dropped, never trusted. */
internal fun decodeUssdCodes(stored: String): List<UssdCode> = stored.split(ROW).mapNotNull { row ->
    val parts = row.split(FIELD)
    if (parts.size != 2 || parts[0].isBlank() || !isValidUssd(parts[1])) null else UssdCode(parts[0], parts[1])
}

internal class UssdCodeStore(private val prefs: SharedPreferences) {
    /** Until the owner changes the list, the load balance code is there; an emptied list stays empty. */
    fun all(): List<UssdCode> = prefs.getString(KEY, null)?.let { decodeUssdCodes(it) } ?: defaultUssdCodes
    /** Adds, or replaces the same code; returns false when the code is not valid or the name is blank. */
    fun save(code: UssdCode): Boolean {
        val label = cleanLabel(code.label)
        if (label.isBlank() || !isValidUssd(code.code)) return false
        write(all().filterNot { it.code == code.code } + UssdCode(label, code.code))
        return true
    }
    fun remove(code: String) = write(all().filterNot { it.code == code })
    private fun write(codes: List<UssdCode>) { prefs.edit().putString(KEY, encodeUssdCodes(codes)).apply() }
    private companion object { const val KEY = "codes_v1" }
}

internal class AndroidUssdPlatform(private val context: Context) : UssdPlatform {
    @SuppressLint("MissingPermission") // the caller asks for the Call permission first
    override fun run(code: String, subscriptionId: Int?, onResult: (UssdResult) -> Unit) {
        if (!isValidUssd(code)) { onResult(UssdResult.Failed("That code is not valid.")); return }
        val base = context.getSystemService(TelephonyManager::class.java)
        val manager = if (subscriptionId != null) base.createForSubscriptionId(subscriptionId) else base
        val callback = object : TelephonyManager.UssdResponseCallback() {
            override fun onReceiveUssdResponse(telephonyManager: TelephonyManager, request: String, response: CharSequence) =
                onResult(UssdResult.Reply(response.toString()))
            override fun onReceiveUssdResponseFailed(telephonyManager: TelephonyManager, request: String, failureCode: Int) =
                onResult(UssdResult.Failed(failureText(failureCode)))
        }
        try { manager.sendUssdRequest(code, callback, Handler(Looper.getMainLooper())) }
        catch (_: SecurityException) { onResult(UssdResult.Failed("Allow phone calls to check your balance.")) }
        catch (_: RuntimeException) { onResult(UssdResult.Failed("The request could not be started.")) }
    }

    private fun failureText(code: Int) = when (code) {
        TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL -> "The network could not take the request. Check your signal and try again."
        else -> "No answer from the network. Try again, or dial the code in the phone app."
    }
}
