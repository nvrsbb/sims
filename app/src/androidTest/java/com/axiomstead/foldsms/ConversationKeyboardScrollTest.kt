package com.axiomstead.foldsms

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test

/** The keyboard is simulated by shrinking the screen and setting pinToLatest, as the real inset does. */
class ConversationKeyboardScrollTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.parse("2026-11-06T04:00:00Z")
    private val messages = (1L..30L).map {
        DeviceMessage(DeviceMessageKind.SMS, it, "2025550101", "Message number $it", "", now.toEpochMilli() - (31 - it) * 60_000, it % 2 == 0L, true, 1, 0, 0, false)
    }
    private val conversation = DeviceConversation("thread-1", 1, "2025550101", messages)

    @Test fun newestMessageStaysVisibleWhenTheKeyboardShrinksTheList() {
        val height = mutableStateOf(700.dp)
        val pinned = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                DeviceConversationScreen(conversation, { null }, { null }, onBack = null, modifier = Modifier.height(height.value),
                    now = { now }, zone = ZoneId.of("UTC"), pinToLatest = pinned.value)
            }
        }
        compose.onNodeWithTag("device-message-sms-30").assertIsDisplayed() // opens at the newest message
        compose.runOnIdle { height.value = 320.dp; pinned.value = true } // keyboard opens
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("device-message-sms-30").assertIsDisplayed() }.isSuccess }
        compose.onNodeWithText("Message number 30").assertIsDisplayed()
    }

    @Test fun scrollingUpToReadIsNotForcedBackDownWhileNoKeyboardIsOpen() {
        val pinned = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                DeviceConversationScreen(conversation, { null }, { null }, onBack = null, modifier = Modifier.height(500.dp),
                    now = { now }, zone = ZoneId.of("UTC"), pinToLatest = pinned.value)
            }
        }
        compose.onNodeWithTag("device-conversation-list").performScrollToIndex(2)
        compose.waitForIdle()
        compose.onNodeWithTag("device-message-sms-30").assertIsNotDisplayed()
    }
}
