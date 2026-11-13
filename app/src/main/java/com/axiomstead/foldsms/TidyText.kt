package com.axiomstead.foldsms

import android.content.Context

/** A tidied text with the two selection ends moved to follow the text they were on. */
internal data class Tidied(val text: String, val start: Int, val end: Int)

private val domainLike = Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)*\\.(com|net|org|ph|io|gov|edu|co|me|app|ly|info|biz|ai|dev|us|uk|ru|jp|cn|de|fr|au|ca)([/?#:].*)?$")
private val abbreviations = setOf("etc", "vs", "mr", "mrs", "ms", "dr", "sr", "jr", "no", "inc", "ltd", "st", "ave", "approx")

/** Web addresses, e-mail addresses and file-like names are left exactly as typed. */
private fun linkLike(token: String): Boolean {
    val t = token.trimEnd('.', ',', '!', '?', ';', ':', ')').lowercase()
    return '@' in t || "://" in t || t.startsWith("www.") || domainLike.matches(t)
}

/**
 * Puts one space after , . ! ? when a letter follows straight away (also after : and ; when they follow a letter, so ":D" stays),
 * and capitalizes the first letter of the message and the first letter after . ! ? and a space. Left alone: numbers like 3.5 or 1,000 or 10:30, web and e-mail
 * addresses, letters-and-dots like "e.g", "..." and common abbreviations such as "etc." or "Mr.". Nothing else is changed.
 */
internal fun tidyPunctuation(text: String, selStart: Int = 0, selEnd: Int = 0): Tidied {
    val protected = BooleanArray(text.length)
    var i = 0
    while (i < text.length) {
        if (text[i].isWhitespace()) { i++; continue }
        var j = i
        while (j < text.length && !text[j].isWhitespace()) j++
        if (linkLike(text.substring(i, j))) for (k in i until j) protected[k] = true
        i = j
    }
    fun wordBefore(dot: Int): String { var s = dot; while (s > 0 && text[s - 1].isLetter()) s--; return text.substring(s, dot).lowercase() }
    fun spaceAfter(p: Int): Boolean = when (text[p]) {
        ',', '!', '?' -> true
        '.' -> p >= 1 && !text[p - 1].isDigit() && wordBefore(p).length != 1 // not 3.5 and not initials like e.g or U.S
        ':', ';' -> p >= 1 && text[p - 1].isLetter()
        else -> false
    }
    fun endsSentence(p: Int): Boolean = when (text[p]) {
        '!', '?' -> true
        '.' -> !(p >= 1 && text[p - 1] == '.') && !(p + 1 < text.length && text[p + 1] == '.') && p >= 1 && !text[p - 1].isDigit() &&
            wordBefore(p).let { it.length > 1 && it !in abbreviations } && !(p >= 2 && text[p - 2] == '.')
        else -> false
    }
    val out = StringBuilder()
    val map = IntArray(text.length + 1)
    var capNext = true // the first letter of the message counts as the start of a sentence
    for (idx in 0..text.length) {
        map[idx] = out.length
        if (idx == text.length) break
        val c = text[idx]
        val gap = idx > 0 && c.isLetter() && !protected[idx] && !protected[idx - 1] && spaceAfter(idx - 1)
        if (gap) out.append(' ')
        val startsWord = c.isLetter() && (gap || idx == 0 || text[idx - 1].isWhitespace())
        if (c.isLetter() && capNext && startsWord && c.isLowerCase() && !protected[idx]) out.append(c.uppercaseChar()) else out.append(c)
        when {
            c.isWhitespace() -> {}
            c.isLetter() -> capNext = false
            (!protected[idx] || idx == text.length - 1 || text[idx + 1].isWhitespace()) && endsSentence(idx) -> capNext = true
            c == ')' || c == '"' || c == '\'' || c == '”' -> {}
            else -> capNext = false
        }
    }
    return Tidied(out.toString(), map[selStart.coerceIn(0, text.length)], map[selEnd.coerceIn(0, text.length)])
}

/**
 * Tidies the text as it is typed, but only at a natural pause: when a space or new line has just been typed at the cursor. The
 * word being typed is never rewritten under the cursor, and deleting is never undone. Returns the text and selection to show.
 */
internal fun tidyWhileTyping(old: String, new: String, selStart: Int, selEnd: Int): Tidied =
    if (new.length > old.length && selStart == selEnd && selStart > 0 && new[selStart - 1].isWhitespace()) tidyPunctuation(new, selStart, selEnd)
    else Tidied(new, selStart, selEnd)

/** The "Tidy punctuation while typing" setting; on unless the owner turns it off. */
internal object TypingPrefs {
    private const val FILE = "typing_prefs"
    private const val KEY = "tidy_punctuation"
    fun tidy(context: Context): Boolean = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, true)
    fun setTidy(context: Context, on: Boolean) { context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply() }
}
