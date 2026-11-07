package com.axiomstead.foldsms

import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID

/**
 * Writes messages as XML in the layout used by SMS Backup & Restore (`<smses>` with `<sms>` and `<mms>` elements), so the file
 * is readable by people and by tools that know that layout. Whether another app restores it has not been verified, and
 * FoldSMS itself cannot restore yet. Only reading happens here; nothing in the provider is changed.
 */
internal class ExportResult(val sms: Int, val mms: Int, val attachments: Int, val skippedAttachments: Int)

/** XML 1.0 cannot hold most control characters or unpaired surrogates; dropping them keeps the file valid. */
internal fun xmlText(value: String): String {
    val out = StringBuilder(value.length + 16)
    var i = 0
    while (i < value.length) {
        val c = value[i]
        when {
            c == '&' -> out.append("&amp;")
            c == '<' -> out.append("&lt;")
            c == '>' -> out.append("&gt;")
            c == '"' -> out.append("&quot;")
            c == '\n' -> out.append("&#10;")
            c == '\r' -> out.append("&#13;")
            c == '\t' -> out.append("&#9;")
            c.isHighSurrogate() && i + 1 < value.length && value[i + 1].isLowSurrogate() -> { out.append(c).append(value[i + 1]); i++ }
            c.isSurrogate() || c < ' ' || c == '￾' || c == '￿' -> Unit
            else -> out.append(c)
        }
        i++
    }
    return out.toString()
}

private val readable = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm:ss a", java.util.Locale.ENGLISH)

private fun smsXml(sms: DeviceSms, name: String?, zone: ZoneId) =
    "  <sms protocol=\"0\" address=\"${xmlText(sms.address)}\" date=\"${sms.date}\" type=\"${sms.type}\" subject=\"null\" body=\"${xmlText(sms.body)}\" " +
        "toa=\"null\" sc_toa=\"null\" service_center=\"null\" read=\"${if (sms.read) 1 else 0}\" status=\"-1\" locked=\"0\" date_sent=\"0\" " +
        "sub_id=\"${sms.subscriptionId}\" readable_date=\"${xmlText(readable.format(Instant.ofEpochMilli(sms.date).atZone(zone)))}\" " +
        "contact_name=\"${xmlText(name ?: "(Unknown)")}\" />\n"

/**
 * @param partData copies one attachment's bytes to the stream and returns false when it cannot be read.
 * @param includeAttachments false leaves the data of pictures and other attachments out (text parts are always kept).
 */
internal fun writeBackup(
    out: OutputStream, sms: List<DeviceSms>, mms: List<DeviceMms>, includeAttachments: Boolean,
    partData: (Long, OutputStream) -> Boolean, nameFor: (String) -> String?, now: Instant, zone: ZoneId = ZoneId.systemDefault(),
    onProgress: (Int, Int) -> Unit = { _, _ -> }, cancelled: () -> Boolean = { false }
): ExportResult {
    val total = sms.size + mms.size
    var done = 0; var attachments = 0; var skipped = 0
    fun write(text: String) = out.write(text.toByteArray(Charsets.UTF_8))
    write("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>\n")
    write("<smses count=\"$total\" backup_set=\"${UUID.randomUUID()}\" backup_date=\"${now.toEpochMilli()}\" type=\"full\">\n")
    for (row in sms.sortedBy { it.date }) {
        if (cancelled()) break
        write(smsXml(row, nameFor(row.address), zone)); done++
        if (done % 200 == 0) onProgress(done, total)
    }
    for (row in mms.sortedBy { it.date }) {
        if (cancelled()) break
        val direction = if (row.box == 1) 137 else 151 // from / to
        write("  <mms date=\"${row.date}\" msg_box=\"${row.box}\" address=\"${xmlText(row.address)}\" sub=\"${xmlText(row.subject)}\" read=\"${if (row.read) 1 else 0}\" " +
            "m_type=\"${if (row.box == 1) 132 else 128}\" ct_t=\"application/vnd.wap.multipart.related\" " +
            "readable_date=\"${xmlText(readable.format(Instant.ofEpochMilli(row.date).atZone(zone)))}\" contact_name=\"${xmlText(nameFor(row.address) ?: "(Unknown)")}\">\n")
        write("    <parts>\n")
        row.parts.forEachIndexed { seq, part ->
            val isText = part.mimeType == "text/plain"
            write("      <part seq=\"$seq\" ct=\"${xmlText(part.mimeType)}\" name=\"${xmlText(part.name)}\"")
            if (isText) write(" text=\"${xmlText(part.text)}\" />\n")
            else if (!includeAttachments || part.mimeType == "application/smil") write(" />\n")
            else {
                write(" data=\"")
                val encoder = Base64.getEncoder().wrap(object : OutputStream() { // writes through, and closing the encoder must not close the file
                    override fun write(b: Int) = out.write(b)
                    override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
                    override fun close() {}
                })
                val ok = try { partData(part.id, encoder) } catch (_: Exception) { false }
                encoder.close()
                write("\" />\n")
                if (ok) attachments++ else skipped++
            }
        }
        write("    </parts>\n    <addrs>\n      <addr address=\"${xmlText(row.address)}\" type=\"$direction\" charset=\"106\" />\n    </addrs>\n  </mms>\n")
        done++
        onProgress(done, total)
    }
    write("</smses>\n")
    out.flush()
    return ExportResult(sms.size, mms.size, attachments, skipped)
}

/** A readable default file name such as foldsms-backup-2026-11-07.xml. */
internal fun backupFileName(now: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    "foldsms-backup-" + DateTimeFormatter.ISO_LOCAL_DATE.format(now.atZone(zone)) + ".xml"
