package com.axiomstead.foldsms

import java.io.ByteArrayOutputStream
import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** PDUs here are fabricated from the OMA MMS encoding rules; they are not captured from any carrier. */
class MmsPduTest {
    private fun out(block: ByteArrayOutputStream.() -> Unit) = ByteArrayOutputStream().apply(block).toByteArray()
    private fun ByteArrayOutputStream.text(s: String) { write(s.toByteArray()); write(0) }
    private fun ByteArrayOutputStream.uintvar(n: Int) {
        val groups = mutableListOf(n and 0x7F); var v = n ushr 7
        while (v > 0) { groups += (v and 0x7F) or 0x80; v = v ushr 7 }
        groups.reversed().forEach { write(it) }
    }
    private fun ByteArrayOutputStream.address(a: String) { write(a.length + 2); write(0x80); text(a) }

    private fun notification(location: String = "http://mmsc.example/m1", type: Int = 0x82) = out {
        write(0x8C); write(type)
        write(0x98); text("tr-1")
        write(0x8D); write(0x92)
        write(0x89); address("+15550100/TYPE=PLMN")
        write(0x96); text("Trip")
        write(0x8A); write(0x80)
        write(0x8E); write(2); write(0x01); write(0x00) // size 256
        write(0x88); write(0x81) // expiry (unknown to the parser)
        write(0x83); text(location)
    }

    @Test fun aNotificationYieldsTransactionLocationSenderSubjectAndSize() {
        val n = parseMmsNotification(notification())!!
        assertEquals(MmsNotification("tr-1", "http://mmsc.example/m1", "+15550100", "Trip", 256), n)
    }

    @Test fun otherMessageTypesAndMissingLocationAreRejected() {
        assertNull(parseMmsNotification(notification(type = 0x84)))
        assertNull(parseMmsNotification(out { write(0x8C); write(0x82); write(0x98); text("tr") }))
    }

    @Test fun malformedInputNeverThrows() {
        val random = Random(7)
        repeat(2_000) {
            val junk = ByteArray(random.nextInt(60)).also(random::nextBytes)
            parseMmsNotification(junk); parseMmsRetrieved(junk)
        }
        val good = notification()
        for (cut in good.indices) parseMmsNotification(good.copyOf(cut))
    }

    private fun retrieve(parts: List<Triple<String, ByteArray, String>>, wellKnownJpeg: Boolean = false) = out {
        write(0x8C); write(0x84)
        write(0x98); text("tr-2")
        write(0x8D); write(0x90)
        write(0x8B); text("mid-1")
        write(0x85); write(4); write(0x66); write(0x00); write(0x00); write(0x00) // date 0x66000000
        write(0x89); address("+15550100/TYPE=PLMN")
        write(0x97); text("+15550199/TYPE=PLMN")
        write(0x96); text("Hi")
        write(0x84); write(0xB3) // application/vnd.wap.multipart.related
        uintvar(parts.size)
        parts.forEach { (mime, data, name) ->
            val head = out {
                if (wellKnownJpeg && mime == "image/jpeg") write(0x9E) else text(mime)
                if (name.isNotEmpty()) { write(0x8E); text(name) }
                write(0xC0); text("<c1>")
            }
            uintvar(head.size); uintvar(data.size); write(head); write(data)
        }
    }

    @Test fun aRetrievedMessageGivesItsMetadataAndPartsInOrder() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)
        val m = parseMmsRetrieved(retrieve(listOf(
            Triple("application/smil", "<smil/>".toByteArray(), "smil.xml"),
            Triple("image/jpeg", jpeg, "photo.jpg"),
            Triple("text/plain", "Hello there".toByteArray(), "text_0.txt"))))!!
        assertEquals("+15550100", m.sender); assertEquals(listOf("+15550199"), m.recipients)
        assertEquals("Hi", m.subject); assertEquals("mid-1", m.messageId); assertEquals("tr-2", m.transactionId)
        assertEquals(0x66000000L, m.dateSeconds)
        assertEquals(listOf("application/smil", "image/jpeg", "text/plain"), m.parts.map { it.mimeType })
        assertArrayEquals(jpeg, m.parts[1].data); assertEquals("photo.jpg", m.parts[1].name); assertEquals("c1", m.parts[1].contentId)
        assertEquals("Hello there", m.parts[2].text)
    }

    @Test fun aWellKnownContentTypeNumberIsDecoded() {
        val m = parseMmsRetrieved(retrieve(listOf(Triple("image/jpeg", byteArrayOf(9), "")), wellKnownJpeg = true))!!
        assertEquals("image/jpeg", m.parts.single().mimeType)
    }

    @Test fun aTruncatedRetrievedMessageIsRejectedNotPartlyKept() {
        val full = retrieve(listOf(Triple("text/plain", "Hello there".toByteArray(), "t.txt")))
        assertNotNull(parseMmsRetrieved(full))
        assertNull(parseMmsRetrieved(full.copyOf(full.size - 3)))
        assertNull(parseMmsRetrieved(notification()))
    }

    @Test fun theAcknowledgementCarriesTheTransactionIdAndRetrievedStatus() {
        val ack = buildMmsRetrievedAck("tr-2")
        assertArrayEquals(byteArrayOf(0x8C.toByte(), 0x83.toByte(), 0x98.toByte()) + "tr-2".toByteArray() + byteArrayOf(0,
            0x8D.toByte(), 0x92.toByte(), 0x95.toByte(), 0x81.toByte()), ack)
    }

    private fun message(subject: String, vararg parts: MmsRetrievedPart) = MmsRetrieved("+1", emptyList(), subject, 0, "m", "t", parts.toList())
    private fun part(mime: String, text: String = "") = MmsRetrievedPart(mime, "", "", text.toByteArray())

    @Test fun theSummaryShowsSubjectTextAndAttachmentCount() {
        assertEquals("Trip · Look [2 attachments]", summary(message("Trip", part("text/plain", "Look"), part("image/jpeg"), part("image/png"), part("application/smil")), true))
        assertEquals("[1 attachment]", summary(message("", part("image/jpeg")), true))
        assertEquals("Picture message", summary(message(""), true))
        assertEquals("Hi (could not be saved)", summary(message("", part("text/plain", "Hi")), false))
    }
}
