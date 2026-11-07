package com.axiomstead.foldsms

import java.io.ByteArrayOutputStream

/** The M-Notification.ind a carrier pushes when an MMS is waiting. */
internal data class MmsNotification(val transactionId: String, val contentLocation: String, val sender: String, val subject: String, val size: Long)

internal data class MmsRetrievedPart(val mimeType: String, val name: String, val contentId: String, val data: ByteArray) {
    val text get() = String(data, Charsets.UTF_8)
    override fun equals(other: Any?) = other is MmsRetrievedPart && mimeType == other.mimeType && name == other.name &&
        contentId == other.contentId && data.contentEquals(other.data)
    override fun hashCode() = 31 * (31 * mimeType.hashCode() + name.hashCode()) + data.contentHashCode()
}

/** The downloaded M-Retrieve.conf: sender, subject, send time (seconds), message id and the body parts. */
internal data class MmsRetrieved(val sender: String, val recipients: List<String>, val subject: String, val dateSeconds: Long,
    val messageId: String, val transactionId: String, val parts: List<MmsRetrievedPart>)

internal class MmsFormatException(message: String) : Exception(message)

/** Cursor over a byte array with the WSP/MMS primitive encodings (OMA-TS-MMS_ENC, WAP-230). */
private class Reader(val bytes: ByteArray, var pos: Int = 0, val end: Int = bytes.size) {
    fun more() = pos < end
    fun peek(): Int { if (pos >= end) throw MmsFormatException("truncated"); return bytes[pos].toInt() and 0xFF }
    fun byte(): Int = peek().also { pos++ }
    fun uintvar(): Int {
        var value = 0L
        repeat(5) {
            val b = byte(); value = (value shl 7) or (b and 0x7F).toLong()
            if (b and 0x80 == 0) { if (value > Int.MAX_VALUE) throw MmsFormatException("length"); return value.toInt() }
        }
        throw MmsFormatException("uintvar")
    }
    /** text-string: optional 0x7F quote, bytes up to NUL. */
    fun text(): String {
        if (peek() == 0x7F) pos++
        val start = pos
        while (byte() != 0) { /* scan */ }
        return String(bytes, start, pos - 1 - start, Charsets.UTF_8)
    }
    /** value-length: byte < 31, or 31 followed by uintvar. */
    fun valueLength(): Int { val first = byte(); return when { first < 31 -> first; first == 31 -> uintvar(); else -> throw MmsFormatException("value-length") } }
    fun long(): Long { val n = byte(); if (n !in 1..8) throw MmsFormatException("long-integer"); var v = 0L; repeat(n) { v = (v shl 8) or byte().toLong() }; return v }
    /** short-integer (0x80+n) or long-integer. */
    fun integer(): Long = if (peek() >= 0x80) (byte() and 0x7F).toLong() else long()
    fun slice(length: Int): Reader {
        if (length < 0 || pos + length > end) throw MmsFormatException("length")
        return Reader(bytes, pos, pos + length).also { pos += length }
    }
    /** Skips one header value of unknown field. */
    fun skipValue() {
        val first = peek()
        when {
            first < 31 -> { pos++; slice(first) }
            first == 31 -> { pos++; slice(uintvar()) }
            first < 128 -> text()
            else -> pos++
        }
    }
}

private const val MESSAGE_TYPE = 0x0C; private const val TRANSACTION_ID = 0x18; private const val VERSION = 0x0D
private const val FROM = 0x09; private const val TO = 0x17; private const val SUBJECT = 0x16; private const val DATE = 0x05
private const val MESSAGE_ID = 0x0B; private const val SIZE = 0x0E; private const val LOCATION = 0x03; private const val CONTENT_TYPE = 0x04
private const val NOTIFICATION_IND = 0x82; private const val RETRIEVE_CONF = 0x84; private const val NOTIFYRESP_IND = 0x83

private fun Reader.encodedString(): String {
    // Either text-string or value-length + charset + text-string.
    if (peek() in 32..127) return text()
    val body = slice(valueLength())
    if (!body.more()) return ""
    if (body.peek() >= 0x80 || body.peek() < 31) body.integer()
    return body.text()
}

private fun Reader.address(): String {
    val body = slice(valueLength())
    if (!body.more()) return ""
    val token = body.byte()
    if (token == 0x81) return "" // insert-address-token
    return body.text().removeSuffix("/TYPE=PLMN").removeSuffix("/TYPE=IPV4").removeSuffix("/TYPE=IPV6")
}

/** Returns null when [pdu] is not a well-formed M-Notification.ind; never throws. */
internal fun parseMmsNotification(pdu: ByteArray): MmsNotification? = try {
    val r = Reader(pdu)
    var type = -1; var tr = ""; var location = ""; var sender = ""; var subject = ""; var size = 0L
    while (r.more()) {
        val field = r.byte()
        if (field < 0x80) throw MmsFormatException("header")
        when (field and 0x7F) {
            MESSAGE_TYPE -> type = r.byte()
            TRANSACTION_ID -> tr = r.text()
            LOCATION -> location = r.text()
            FROM -> sender = r.address()
            SUBJECT -> subject = r.encodedString()
            SIZE -> size = r.long()
            else -> r.skipValue()
        }
    }
    if (type != NOTIFICATION_IND || tr.isEmpty() || location.isEmpty()) null else MmsNotification(tr, location, sender, subject, size)
} catch (_: MmsFormatException) { null } catch (_: IndexOutOfBoundsException) { null }

private val wellKnownTypes = listOf("*/*", "text/*", "text/html", "text/plain", "text/x-hdml", "text/x-ttml", "text/x-vCalendar", "text/x-vCard",
    "text/vnd.wap.wml", "text/vnd.wap.wmlscript", "text/vnd.wap.wta-event", "multipart/*", "multipart/mixed", "multipart/form-data",
    "multipart/byteranges", "multipart/alternative", "application/*", "application/java-vm", "application/x-www-form-urlencoded",
    "application/x-hdmlc", "application/vnd.wap.wmlc", "application/vnd.wap.wmlscriptc", "application/vnd.wap.wta-eventc",
    "application/vnd.wap.uaprof", "application/vnd.wap.wtls-ca-certificate", "application/vnd.wap.wtls-user-certificate",
    "application/x-x509-ca-cert", "application/x-x509-user-cert", "image/*", "image/gif", "image/jpeg", "image/tiff", "image/png",
    "image/vnd.wap.wbmp", "application/vnd.wap.multipart.*", "application/vnd.wap.multipart.mixed", "application/vnd.wap.multipart.form-data",
    "application/vnd.wap.multipart.byteranges", "application/vnd.wap.multipart.alternative", "application/xml", "text/xml",
    "application/vnd.wap.wbxml", "application/x-x968-cross-cert-request", "application/x-x968-ca-cert", "application/x-x968-user-cert",
    "text/vnd.wap.si", "application/vnd.wap.sic", "text/vnd.wap.sl", "application/vnd.wap.slc", "text/vnd.wap.co", "application/vnd.wap.coc",
    "application/vnd.wap.multipart.related")

private fun contentType(r: Reader): Pair<String, String> { // mime type, name parameter (if any)
    val first = r.peek()
    if (first >= 0x80) { r.pos++; return (wellKnownTypes.getOrNull(first and 0x7F) ?: "application/octet-stream") to "" }
    if (first in 32..127) return r.text() to ""
    val body = r.slice(r.valueLength())
    val mime = if (body.peek() >= 0x80) wellKnownTypes.getOrNull(body.byte() and 0x7F) ?: "application/octet-stream" else body.text()
    var name = ""
    while (body.more()) { // parameters: Name (0x85, 0x97) and Filename (0x86, 0x98) carry text; others are skipped
        val p = body.byte()
        if (p == 0x85 || p == 0x97 || p == 0x86 || p == 0x98) name = body.text() else body.skipValue()
    }
    return mime to name
}

/** Returns null when [pdu] is not a well-formed M-Retrieve.conf; never throws. Parts keep their order. */
internal fun parseMmsRetrieved(pdu: ByteArray): MmsRetrieved? = try {
    val r = Reader(pdu)
    var type = -1; var tr = ""; var id = ""; var sender = ""; var subject = ""; var date = 0L
    val to = mutableListOf<String>()
    var parts: List<MmsRetrievedPart>? = null
    while (r.more() && parts == null) {
        val field = r.byte()
        if (field < 0x80) throw MmsFormatException("header")
        when (field and 0x7F) {
            MESSAGE_TYPE -> type = r.byte()
            TRANSACTION_ID -> tr = r.text()
            MESSAGE_ID -> id = r.text()
            FROM -> sender = r.address()
            TO -> r.encodedString().takeIf { it.isNotEmpty() }?.let { to += it.removeSuffix("/TYPE=PLMN") }
            SUBJECT -> subject = r.encodedString()
            DATE -> date = r.long()
            CONTENT_TYPE -> { contentType(r); parts = readParts(r) }
            else -> r.skipValue()
        }
    }
    if (type != RETRIEVE_CONF || parts == null) null else MmsRetrieved(sender, to, subject, date, id, tr, parts)
} catch (_: MmsFormatException) { null } catch (_: IndexOutOfBoundsException) { null }

private fun readParts(r: Reader): List<MmsRetrievedPart> {
    val count = r.uintvar()
    if (count > 200) throw MmsFormatException("too many parts")
    return List(count) {
        val headerLength = r.uintvar(); val dataLength = r.uintvar()
        val headers = r.slice(headerLength)
        val (mime, typeName) = contentType(headers)
        var location = ""; var cid = ""
        while (headers.more()) {
            when (headers.byte()) {
                0x8E -> location = headers.text() // Content-Location
                0xC0 -> cid = headers.text().trim('<', '>') // Content-ID
                else -> headers.skipValue()
            }
        }
        val data = r.slice(dataLength)
        MmsRetrievedPart(mime, location.ifEmpty { typeName }, cid, data.bytes.copyOfRange(data.pos, data.end))
    }
}

/** M-NotifyResp.ind telling the carrier the message was retrieved (status 0x81) so it stops re-sending the notification. */
internal fun buildMmsRetrievedAck(transactionId: String): ByteArray = ByteArrayOutputStream().apply {
    write(0x8C); write(NOTIFYRESP_IND)
    write(0x98); write(transactionId.toByteArray(Charsets.UTF_8)); write(0)
    write(0x8D); write(0x92) // MMS version 1.2
    write(0x95); write(0x81) // status: retrieved
}.toByteArray()

/** Notification text from the message itself; the subject and text appear only on the unlocked notification. */
internal fun summary(message: MmsRetrieved, stored: Boolean): String {
    val text = message.parts.filter { it.mimeType == "text/plain" }.joinToString(" ") { it.text }.trim()
    val media = message.parts.count { it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/") || it.mimeType.startsWith("audio/") }
    val lead = listOf(message.subject.trim(), text).filter { it.isNotEmpty() }.joinToString(" · ")
    val tail = if (media > 0) "[$media attachment${if (media > 1) "s" else ""}]" else ""
    return listOf(lead, tail, if (stored) "" else "(could not be saved)").filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { "Picture message" }
}
