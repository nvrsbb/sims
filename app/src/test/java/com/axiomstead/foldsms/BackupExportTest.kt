package com.axiomstead.foldsms

import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Invented messages only. The output is parsed back as XML to prove the file is well formed. */
class BackupExportTest {
    private val now = Instant.ofEpochMilli(1_700_000_000_000)
    private fun sms(id: Long, body: String, date: Long, type: Int = 1, address: String = "+15550100", read: Boolean = true) =
        DeviceSms(id, 1, address, body, date, type, 3, read)

    private fun export(
        sms: List<DeviceSms>, mms: List<DeviceMms> = emptyList(), attachments: Boolean = true,
        data: Map<Long, ByteArray> = emptyMap(), names: Map<String, String> = emptyMap(), cancelAfter: Int = Int.MAX_VALUE
    ): Pair<org.w3c.dom.Document, ExportResult> {
        val bytes = ByteArrayOutputStream()
        var written = 0
        val result = writeBackup(bytes, sms, mms, attachments, { id, out -> data[id]?.let { out.write(it); true } ?: false },
            { names[it] }, now, ZoneOffset.UTC, onProgress = { d, _ -> written = d }, cancelled = { written >= cancelAfter })
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(bytes.toByteArray().inputStream())
        return doc to result
    }

    private fun Element.kids(tag: String) = getElementsByTagName(tag)

    @Test fun smsAreWrittenOldestFirstWithTheirFieldsAndTheCountMatches() {
        val (doc, result) = export(listOf(sms(2, "second", 2_000, type = 2, read = false), sms(1, "first", 1_000)))
        val root = doc.documentElement
        assertEquals("smses", root.tagName); assertEquals("2", root.getAttribute("count"))
        val rows = root.kids("sms")
        assertEquals("first", (rows.item(0) as Element).getAttribute("body"))
        val second = rows.item(1) as Element
        assertEquals("second", second.getAttribute("body")); assertEquals("2", second.getAttribute("type")); assertEquals("0", second.getAttribute("read"))
        assertEquals("+15550100", second.getAttribute("address")); assertEquals("2000", second.getAttribute("date"))
        assertEquals(2, result.sms); assertEquals(0, result.mms)
    }

    @Test fun specialCharactersEmojiAndNewlinesSurviveAndInvalidOnesAreDropped() {
        val tricky = "Tom & \"Jerry\" <b>bold</b>\nline two\ttab 😀 ₱500\u0001\u0000 end"
        val (doc, _) = export(listOf(sms(1, tricky, 1_000)))
        val body = (doc.getElementsByTagName("sms").item(0) as Element).getAttribute("body")
        assertEquals("Tom & \"Jerry\" <b>bold</b>\nline two\ttab 😀 ₱500 end", body)
    }

    @Test fun anUnpairedSurrogateIsDroppedSoTheFileStaysValid() {
        val (doc, _) = export(listOf(sms(1, "bad \uD83D end", 1_000)))
        assertEquals("bad  end", (doc.getElementsByTagName("sms").item(0) as Element).getAttribute("body"))
    }

    @Test fun contactNamesAreUsedWhenKnown() {
        val (doc, _) = export(listOf(sms(1, "hi", 1_000)), names = mapOf("+15550100" to "Ana Cruz"))
        assertEquals("Ana Cruz", (doc.getElementsByTagName("sms").item(0) as Element).getAttribute("contact_name"))
        val (other, _) = export(listOf(sms(1, "hi", 1_000)))
        assertEquals("(Unknown)", (other.getElementsByTagName("sms").item(0) as Element).getAttribute("contact_name"))
    }

    private val picture = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    private val mmsRow = DeviceMms(7, 1, "+15550100", "Trip", 5_000, 1, true, listOf(
        DeviceMmsPart(1, "application/smil", "<smil/>", ""), DeviceMmsPart(2, "text/plain", "Look at this", "text_0.txt"),
        DeviceMmsPart(3, "image/jpeg", "", "photo.jpg"), DeviceMmsPart(4, "image/png", "", "missing.png")))

    @Test fun anMmsKeepsTextAndWritesAttachmentsAsBase64WhenAsked() {
        val (doc, result) = export(emptyList(), listOf(mmsRow), attachments = true, data = mapOf(3L to picture))
        val mms = doc.getElementsByTagName("mms").item(0) as Element
        assertEquals("Trip", mms.getAttribute("sub")); assertEquals("1", mms.getAttribute("msg_box")); assertEquals("5000", mms.getAttribute("date"))
        val parts = mms.getElementsByTagName("part")
        assertEquals(4, parts.length)
        assertEquals("Look at this", (parts.item(1) as Element).getAttribute("text"))
        assertEquals(Base64.getEncoder().encodeToString(picture), (parts.item(2) as Element).getAttribute("data"))
        assertEquals("", (parts.item(3) as Element).getAttribute("data")) // unreadable: empty, and counted
        assertEquals("137", (mms.getElementsByTagName("addr").item(0) as Element).getAttribute("type"))
        assertEquals(1, result.attachments); assertEquals(1, result.skippedAttachments)
    }

    @Test fun withoutAttachmentsOnlyTextPartsCarryContent() {
        val (doc, result) = export(emptyList(), listOf(mmsRow), attachments = false, data = mapOf(3L to picture))
        val parts = doc.getElementsByTagName("part")
        assertEquals("Look at this", (parts.item(1) as Element).getAttribute("text"))
        assertFalse((parts.item(2) as Element).hasAttribute("data"))
        assertEquals(0, result.attachments); assertEquals(0, result.skippedAttachments)
    }

    @Test fun aSentMmsPointsToItsRecipient() {
        val sent = mmsRow.copy(box = 2)
        val (doc, _) = export(emptyList(), listOf(sent))
        assertEquals("151", (doc.getElementsByTagName("addr").item(0) as Element).getAttribute("type"))
    }

    @Test fun cancellingStopsEarlyButLeavesAValidFile() {
        val rows = (1L..500L).map { sms(it, "m$it", it * 10) }
        val (doc, _) = export(rows, cancelAfter = 200)
        assertTrue(doc.getElementsByTagName("sms").length in 200..400)
    }

    @Test fun theFileNameCarriesTheDate() {
        assertEquals("foldsms-backup-2023-11-14.xml", backupFileName(now, ZoneOffset.UTC))
    }
}
