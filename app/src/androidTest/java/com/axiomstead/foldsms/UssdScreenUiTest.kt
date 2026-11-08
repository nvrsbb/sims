package com.axiomstead.foldsms

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The screen only, with a fake carrier: no real USSD request is ever sent. */
@org.junit.runner.RunWith(RetryRunner::class)
class UssdScreenUiTest {
    @get:Rule val compose = createComposeRule()
    private val ran = mutableListOf<Pair<String, Int?>>()
    private var reply: UssdResult = UssdResult.Reply("Your balance is P10.00")

    private fun show(sims: List<SmsSubscription>, codes: List<UssdCode> = listOf(UssdCode("Load balance", "*123#")),
                     saved: MutableList<UssdCode> = mutableListOf(), removed: MutableList<String> = mutableListOf()) {
        compose.setContent {
            MaterialTheme {
                UssdScreen(codes, sims, onRun = { code, sub, done -> ran += code to sub; done(reply) },
                    onSave = { saved += it; true }, onRemove = { removed += it }, onDismiss = {})
            }
        }
    }

    @Test fun oneSimRunsTheCodeAndShowsTheAnswer() {
        show(listOf(SmsSubscription(5, 0, "Smart")))
        compose.onNodeWithTag("ussd-run").performClick()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("ussd-result").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Your balance is P10.00").assertExists()
        assertEquals(listOf("*123#" to 5), ran)
    }

    @Test fun twoSimsNeedAChoiceBeforeRunning() {
        show(listOf(SmsSubscription(5, 0, "Smart"), SmsSubscription(6, 1, "Other")))
        compose.onNodeWithTag("ussd-run").assertIsNotEnabled()
        compose.onNodeWithTag("ussd-sim-6").performClick()
        compose.onNodeWithTag("ussd-run").assertIsEnabled().performClick()
        compose.waitUntil(UI_WAIT_MS) { ran.isNotEmpty() }
        assertEquals(listOf("*123#" to 6), ran)
    }

    @Test fun aFailureIsShownInPlainWords() {
        reply = UssdResult.Failed("No answer from the network.")
        show(listOf(SmsSubscription(5, 0, "Smart")))
        compose.onNodeWithTag("ussd-run").performClick()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithText("No answer from the network.").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun addingNeedsAValidCodeAndRemovingAsksFirst() {
        val saved = mutableListOf<UssdCode>(); val removed = mutableListOf<String>()
        show(listOf(SmsSubscription(5, 0, "Smart")), saved = saved, removed = removed)
        compose.onNodeWithTag("ussd-add").performClick()
        compose.onNodeWithTag("ussd-new-label").performTextInput("Data")
        compose.onNodeWithTag("ussd-new-code").performTextInput("12")
        compose.onNodeWithTag("ussd-new-save").assertIsNotEnabled()
        compose.onNodeWithTag("ussd-new-code").performTextClearance()
        compose.onNodeWithTag("ussd-new-code").performTextInput("*143#")
        compose.onNodeWithTag("ussd-new-save").assertIsEnabled().performClick()
        assertEquals(listOf(UssdCode("Data", "*143#")), saved)
        compose.onNodeWithTag("ussd-remove").performClick()
        assertEquals(emptyList<String>(), removed) // not until confirmed
        compose.onNodeWithTag("ussd-remove-confirm").performClick()
        assertEquals(listOf("*123#"), removed)
    }
}
