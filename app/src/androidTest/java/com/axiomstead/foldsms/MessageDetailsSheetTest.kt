package com.axiomstead.foldsms

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MessageDetailsSheetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun detailsCopyAndDismissUseTheSelectedMessage() {
        val selected = exampleThreads.first().history[1]
        compose.setContent {
            var open by remember { mutableStateOf(true) }
            MaterialTheme {
                Text("Unsent test draft")
                if (open) MessageDetailsSheet(selected, "Alex", onDismiss = { open = false })
            }
        }
        compose.onNodeWithText("Sender: You").assertExists()
        compose.onNodeWithText("Direction: Outgoing").assertExists()
        compose.onNodeWithText("Time: ${selected.fullTimestamp}").assertExists()
        compose.onNodeWithText(selected.body).assertExists()
        // Android only lets the focused window read the clipboard, so wait for focus before copying and reading.
        compose.waitUntil(10_000) { compose.activity.window.decorView.hasWindowFocus() }
        compose.onNodeWithText("Copy text").performScrollTo().performClick()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        // Clipboard reads can lag behind the click on a freshly focused emulator window, so poll briefly.
        var copied: String? = null
        val deadline = System.currentTimeMillis() + 5_000
        while (copied == null && System.currentTimeMillis() < deadline) {
            compose.waitForIdle()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                copied = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            }
            if (copied == null) Thread.sleep(100)
        }
        assertEquals(selected.body, copied)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { clipboard.clearPrimaryClip() }
        compose.onNodeWithText("Close").performScrollTo().performClick()
        compose.onNodeWithText("Message details").assertDoesNotExist()
        compose.onNodeWithText("Unsent test draft").assertExists()
    }
}
