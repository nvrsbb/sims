package com.axiomstead.foldsms

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Telephony
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Only the disposable CI emulator injects a fabricated inbound SMS. Never calls native sending. */
class SmsProviderSmokeTest {
    @Test fun readsSyntheticInboundFromTheExternalDefaultsProviderWithoutDuplicatingIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Dedicated synthetic-inbound CI phase only", arguments.getString("smsSmoke") == "true")
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Synthetic SMS smoke requires an emulator" }
        val expected = arguments.getString("expectedBody")!!
        check(expected.startsWith("FoldSMS fabricated inbound CI "))
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("sms-smoke-${UUID.randomUUID()}", Context.MODE_PRIVATE)
        val consent = SmsConsentStore(preferences)
        try {
            check(consent.setEnabled(true))
            val platform = AndroidSmsPlatform(context, consent)
            assertTrue("An external default app and ordinary read/receive grants are required", platform.access().canRead)
            val deadline = SystemClock.elapsedRealtime() + 15_000
            var rows = platform.recent().filter { it.body == expected }
            while (rows.isEmpty() && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(100)
                rows = platform.recent().filter { it.body == expected }
            }
            assertEquals(1, rows.size)
            assertEquals(Telephony.Sms.MESSAGE_TYPE_INBOX, rows.single().type)
            assertEquals(rows.single().id, platform.recent().single { it.body == expected }.id)
        } finally { preferences.edit().clear().commit() }
    }
}
