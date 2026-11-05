package com.axiomstead.foldsms

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PreviewExportUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun exportRequiresConfirmationAndCannotStartTwiceWhileBusy() {
        var requests = 0
        val busy = mutableStateOf(false)
        compose.setContent {
            MaterialTheme { Column { PreviewExportControl(busy.value, null) { requests++; busy.value = true } } }
        }
        compose.onNodeWithText("Export preview data").performClick()
        compose.onNodeWithText("The unencrypted file includes", substring = true).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithText("Export preview data").performClick()
        compose.onNodeWithText("Choose file").performClick()
        compose.runOnIdle { assertEquals(1, requests) }
        compose.onNodeWithText("Export preview data").assertIsNotEnabled()
    }

    @Test fun restoredWritingStateAllowsRetryInsteadOfRemainingBusy() {
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                dispatchResult(requestCode, Uri.parse("content://fabricated-preview/document"))
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        val pending = CompletableDeferred<PreviewExportResult>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                MaterialTheme { Column { PreviewExportSection(onBusyChange = {}, exportDocument = { pending.await() }) } }
            }
        }
        compose.onNodeWithText("Export preview data").performClick()
        compose.onNodeWithText("Choose file").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText("Exporting preview data…").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Export preview data").assertIsNotEnabled()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText("The previous export was interrupted.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Export preview data").assertIsEnabled()
    }
}
