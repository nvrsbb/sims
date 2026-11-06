package com.axiomstead.foldsms

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MessageDetailsSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun detailsCopyAndDismissUseTheSelectedMessage() {
        val selected = exampleThreads.first().history[1]
        val copied = mutableListOf<String>()
        compose.setContent {
            var open by remember { mutableStateOf(true) }
            MaterialTheme {
                Text("Unsent test draft")
                if (open) MessageDetailsSheet(selected, "Alex", onDismiss = { open = false }, onCopy = { copied += it })
            }
        }
        compose.onNodeWithText("Sender: You").assertExists()
        compose.onNodeWithText("Direction: Outgoing").assertExists()
        compose.onNodeWithText("Time: ${selected.fullTimestamp}").assertExists()
        compose.onNodeWithText(selected.body).assertExists()
        compose.onNodeWithText("Copy text").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(selected.body), copied) }
        compose.onNodeWithText("Close").performScrollTo().performClick()
        compose.onNodeWithText("Message details").assertDoesNotExist()
        compose.onNodeWithText("Unsent test draft").assertExists()
    }
}
