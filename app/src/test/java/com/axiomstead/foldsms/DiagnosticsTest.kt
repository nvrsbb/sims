package com.axiomstead.foldsms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {
    @Test fun aCrashDetailNamesTheErrorAndWhereButNeverCarriesItsMessage() {
        val secret = "Your code is 482913 for +639175550199"
        val error = IllegalStateException(secret, RuntimeException("cause message with 777888"))
        val detail = crashDetail(error)
        assertTrue(detail.startsWith("java.lang.IllegalStateException at "))
        assertTrue("DiagnosticsTest" in detail)
        assertTrue("caused by java.lang.RuntimeException" in detail)
        assertFalse("482913" in detail); assertFalse("639175550199" in detail); assertFalse("777888" in detail)
    }

    @Test fun crashDetailKeepsAtMostTwentyFiveFramesAndFourCauses() {
        var error: Throwable = RuntimeException("x")
        repeat(8) { error = RuntimeException("c$it", error) }
        val detail = crashDetail(error)
        assertTrue(detail.substringAfter(" at ").substringBefore(" caused by").split(" < ").size <= 25)
        assertEquals(4, detail.substringAfter("caused by ").split(" <- ").size)
    }

    @Test fun aLineIsOneLineWithTimeVersionKindAndDetail() {
        val line = diagnosticLine(0, "0.9.0", "MMS_DOWNLOAD_RESULT", "resultCode=5\nhttpStatus=0")
        assertEquals("1970-01-01T00:00:00Z v0.9.0 MMS_DOWNLOAD_RESULT resultCode=5 httpStatus=0", line)
        assertFalse('\n' in diagnosticLine(0, "1", "K", "a\nb\nc"))
    }

    @Test fun trimmingKeepsTheNewestWholeLines() {
        val text = (1..100).joinToString("") { "line number $it\n" }
        val trimmed = trimLog(text, 200)
        assertTrue(trimmed.length <= 200)
        assertTrue(trimmed.endsWith("line number 100\n"))
        assertTrue(trimmed.lines().filter { it.isNotEmpty() }.all { it.startsWith("line number ") }) // no half line at the start
        assertEquals(text, trimLog(text, 10_000))
    }
}
