package com.axiomstead.foldsms

import android.app.Application
import android.content.Context
import java.time.Instant

/*
 * A small troubleshooting log kept on the phone. It records that something failed (which step, which error class, numeric
 * result codes and sizes) and uncaught crashes (class names and code locations). It never records message text, numbers,
 * names, codes or exception messages, and it is never sent anywhere: the owner chooses to share it from Settings.
 */

internal const val DIAGNOSTICS_MAX_CHARS = 60_000

/** One line: time, version, kind and a short detail made of codes only. */
internal fun diagnosticLine(timeMillis: Long, version: String, kind: String, detail: String): String =
    "${Instant.ofEpochMilli(timeMillis)} v$version $kind ${detail.replace('\n', ' ').take(400)}"

/** What identifies a crash without what it carried: class names and code locations, no messages (they can hold private text). */
internal fun crashDetail(error: Throwable): String {
    val frames = error.stackTrace.take(25).joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
    val causes = generateSequence(error.cause) { it.cause }.take(4).joinToString(" <- ") { it.javaClass.name }
    return "${error.javaClass.name} at $frames" + if (causes.isNotEmpty()) " caused by $causes" else ""
}

/** Keeps the newest whole lines within [max] characters. */
internal fun trimLog(text: String, max: Int = DIAGNOSTICS_MAX_CHARS): String {
    if (text.length <= max) return text
    val kept = text.takeLast(max)
    return kept.substringAfter('\n', "")
}

internal object Diagnostics {
    private const val FILE = "diagnostics.txt"
    private val lock = Any()

    private fun version(context: Context) = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** Appends one entry. Never throws: a broken log must not break the thing being logged. */
    fun record(context: Context, kind: String, detail: String) {
        try {
            val app = context.applicationContext
            synchronized(lock) {
                val file = java.io.File(app.filesDir, FILE)
                val old = if (file.exists()) file.readText() else ""
                file.writeText(trimLog(old + diagnosticLine(System.currentTimeMillis(), version(app), kind, detail) + "\n"))
            }
        } catch (_: Exception) { }
    }

    fun read(context: Context): String = try {
        synchronized(lock) { java.io.File(context.applicationContext.filesDir, FILE).takeIf { it.exists() }?.readText().orEmpty() }
    } catch (_: Exception) { "" }

    fun entryCount(context: Context): Int = read(context).lines().count { it.isNotBlank() }

    fun clear(context: Context) { synchronized(lock) { java.io.File(context.applicationContext.filesDir, FILE).delete() } }

    /** Records an uncaught exception, then lets Android handle it as before. */
    fun installCrashHandler(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(app, "CRASH", "thread=${thread.name} ${crashDetail(error)}")
            previous?.uncaughtException(thread, error)
        }
    }
}

class FoldSmsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Diagnostics.installCrashHandler(this)
    }
}
