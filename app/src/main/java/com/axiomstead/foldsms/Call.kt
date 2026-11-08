package com.axiomstead.foldsms

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * The number as the phone should dial it: digits, a leading +, and the dial characters * # only.
 * Returns null for anything else (sender names, short text) so no call button is offered. Nothing is filtered by
 * value: emergency numbers (112, 911, ...) pass exactly like any other number.
 */
internal fun dialableNumber(raw: String): String? {
    val cleaned = raw.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
    val digits = cleaned.count { it.isDigit() }
    if (digits == 0 || cleaned.drop(1).contains('+') || raw.any { it.isLetter() }) return null
    return cleaned
}

/** ACTION_CALL places the call at once (needs the Call permission); ACTION_DIAL opens the dialer with the number filled in. */
internal fun callIntent(number: String, permitted: Boolean): Intent? {
    val dial = dialableNumber(number) ?: return null
    return Intent(if (permitted) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.fromParts("tel", dial, null))
}

/**
 * Returns a function that calls a number. The Call permission is asked the first time it is needed; if it is refused,
 * the dialer opens with the number filled in, so calling still works (one more tap) and nothing is blocked.
 */
@Composable
internal fun rememberCaller(launch: ((Intent) -> Unit)?, onError: (String) -> Unit): (String) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    fun go(number: String, permitted: Boolean) {
        val intent = callIntent(number, permitted) ?: return
        try { (launch ?: { i: Intent -> context.startActivity(i) })(intent) } catch (_: ActivityNotFoundException) { onError("No phone app is available.") }
        catch (_: SecurityException) { go(number, false) }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.let { go(it, granted) }
        pending = null
    }
    return { number ->
        if (hasCallPermission(context)) go(number, true)
        else if (dialableNumber(number) != null) { pending = number; ask.launch(Manifest.permission.CALL_PHONE) }
    }
}

private fun hasCallPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
