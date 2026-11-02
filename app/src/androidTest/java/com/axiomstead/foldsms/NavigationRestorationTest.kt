package com.axiomstead.foldsms

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test

class NavigationRestorationTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferenceName = "navigation_test_${UUID.randomUUID()}"
    private val isolatedContext = object : ContextWrapper(context) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            context.getSharedPreferences(preferenceName, mode)
    }
    private fun scrollPosition(tag: String): Float = compose.onNodeWithTag(tag)
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @After fun cleanup() { context.deleteSharedPreferences(preferenceName) }

    @Composable private fun IsolatedApp(wide: Boolean, height: Int) {
        CompositionLocalProvider(LocalContext provides isolatedContext) {
            MaterialTheme {
                Box(Modifier.width(380.dp).height(height.dp)) { FoldSmsApp(wide = wide) }
            }
        }
    }

    @Test fun nonDefaultConversationSearchAndFilterRestoreAndBackReturnsToInbox() {
        val restoration = StateRestorationTester(compose)
        lateinit var back: OnBackPressedDispatcher
        lateinit var dismissKeyboard: () -> Unit
        restoration.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            dismissKeyboard = { focus.clearFocus(); keyboard?.hide() }
            IsolatedApp(wide = false, height = 600)
        }
        compose.onNodeWithTag("category-Finance").performScrollTo().performClick()
        compose.onNodeWithTag("inbox-search").performTextInput("Bank")
        compose.runOnIdle { dismissKeyboard() }
        compose.onNodeWithTag("thread_2").performClick()
        compose.onNodeWithTag("conversation-history-2").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("conversation-history-2").assertExists()
        compose.onNodeWithTag("inbox-list").assertDoesNotExist()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("inbox-list").assertExists()
        compose.onNodeWithTag("inbox-search").assertTextContains("Bank")
        compose.onNodeWithTag("category-Finance").assertIsSelected()
        compose.onNodeWithTag("thread_2").assertExists()
        compose.onNodeWithTag("thread_1").assertDoesNotExist()
    }

    @Test fun inboxPositionSurvivesRestorationAndConversationRoundTrip() {
        val restoration = StateRestorationTester(compose)
        lateinit var back: OnBackPressedDispatcher
        restoration.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            IsolatedApp(wide = false, height = 500)
        }
        compose.onNodeWithTag("inbox-list").performScrollToIndex(4)
        val position = scrollPosition("inbox-list")
        assertTrue(position > 0f)
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(position, scrollPosition("inbox-list"), 0.00001f)
        compose.onNodeWithTag("thread_5").assertIsDisplayed().performClick()
        compose.runOnIdle { back.onBackPressed() }
        assertEquals(position, scrollPosition("inbox-list"), 0.00001f)
        compose.onNodeWithTag("thread_5").assertIsDisplayed()
    }

    @Test fun historyPositionAndSelectionSurviveRestorationAndWidthChanges() {
        val restoration = StateRestorationTester(compose)
        val wide = mutableStateOf(false)
        restoration.setContent {
            IsolatedApp(wide = wide.value, height = 400)
        }
        compose.onNodeWithTag("inbox-list").performScrollToNode(hasTestTag("thread_3"))
        compose.onNodeWithTag("thread_3").performClick()
        compose.onNodeWithTag("conversation-history-3").performScrollToIndex(1)
        val position = scrollPosition("conversation-history-3")
        assertTrue(position > 0f)
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(position, scrollPosition("conversation-history-3"), 0.00001f)
        compose.onNodeWithText("Sample package is on the way").assertIsDisplayed()
        compose.runOnIdle { wide.value = true }
        compose.onNodeWithTag("inbox-list").assertExists()
        compose.onNodeWithTag("conversation-history-3").assertExists()
        compose.runOnIdle { wide.value = false }
        compose.onNodeWithTag("inbox-list").assertDoesNotExist()
        compose.onNodeWithTag("conversation-history-3").assertExists()
    }
}
