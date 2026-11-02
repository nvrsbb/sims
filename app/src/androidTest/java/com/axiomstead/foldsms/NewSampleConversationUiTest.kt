package com.axiomstead.foldsms

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class NewSampleConversationUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
    private val preferences = context.getSharedPreferences("new-sample-ui-${UUID.randomUUID()}", Context.MODE_PRIVATE)
    private val drafts = DraftStore(preferences)
    private val metadata = ConversationMetadataStore(database.metadata())
    private val custom = CustomSampleConversationStore(database, drafts)

    @After fun clean() {
        database.close()
        preferences.edit().clear().commit()
    }

    @Test fun creationOpensAnUnsentDraftAndRestoresItsConversationAndCategory() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme { FoldSmsApp(store = drafts, wide = false, metadataStore = metadata, customStore = custom) }
        }
        compose.onNodeWithText("New sample").performClick()
        compose.onNodeWithText("Sample name").performScrollTo().performTextInput("Sample Robin")
        compose.onNodeWithText("Fabricated address").performScrollTo().performTextInput("+1 202-555-0199")
        compose.onNodeWithText("Initial draft (optional)").performScrollTo().performTextInput("Fabricated draft\nsecond line")
        compose.onNodeWithText("Create sample").performClick()
        compose.waitUntil { compose.onAllNodesWithTag("conversation-history--1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Local sample · Unsent draft only").assertExists()
        compose.onNodeWithText("Fabricated draft\nsecond line").assertExists()
        compose.onNodeWithText("Change category").performClick()
        compose.onNodeWithText("Finance", useUnmergedTree = true).performClick()
        compose.waitUntil { runBlocking { metadata.categories.first().any { it.threadId == -1 && it.category == "Finance" } } }
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil { compose.onAllNodesWithTag("conversation-history--1").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil { compose.onAllNodesWithText("Sample conversation · Finance").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Sample conversation · Finance").assertExists()
        compose.onNodeWithText("Fabricated draft\nsecond line").assertExists()
        compose.onNodeWithText("‹ Back").performClick()
        compose.onNodeWithTag("inbox-search").performTextInput("Sample Robin")
        compose.onNodeWithTag("thread_-1").assertExists()
        compose.onNodeWithTag("draft_indicator_-1").assertExists()
    }
}
