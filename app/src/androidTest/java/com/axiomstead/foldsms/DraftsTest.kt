package com.axiomstead.foldsms

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class DraftsTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences("draft_test_${UUID.randomUUID()}", Context.MODE_PRIVATE)
    private val database = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
    private val metadata = ConversationMetadataStore(database.metadata())
    private lateinit var store: DraftStore

    @Before fun prepare() { store = DraftStore(preferences) }
    @After fun clean() { database.close(); preferences.edit().clear().commit() }

    @Test fun legacyKeysMultilineAndIndependentClearingSurviveStoreReopening() {
        preferences.edit().putString("thread_1", "Existing fabricated\nmultiline draft").commit()
        assertEquals("Existing fabricated\nmultiline draft", store.get(1))
        store.save(2, "  Sample draft\n ")
        store.save(1, "")
        val reopened = DraftStore(preferences)
        assertEquals("", reopened.get(1))
        assertFalse(preferences.contains("thread_1"))
        assertEquals("  Sample draft\n ", reopened.get(2))
    }

    @Test fun longMultilineDraftKeepsClearReachable() {
        compose.setContent {
            var draft by remember { mutableStateOf((1..40).joinToString("\n") { "Fabricated line $it" }) }
            MaterialTheme {
                androidx.compose.foundation.layout.Column {
                    DraftComposer(draft, onDraftChange = { draft = it })
                }
            }
        }
        compose.onNodeWithText("Clear draft").assertIsDisplayed().performClick()
        compose.onNodeWithText("Clear", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Clear draft").assertDoesNotExist()
    }

    @Test fun restoredIndicatorsAndConfirmedClearAffectOnlySelectedConversation() {
        store.save(1, "Sample Alex draft\nsecond line")
        store.save(2, "Sample second draft")
        val reopened = DraftStore(preferences)
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { screenWidthDp = 840 }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme { FoldSmsApp(reopened, metadataStore = metadata, customStore = CustomSampleConversationStore(database, reopened), deletionStore = SampleDeletionStore(preferences)) }
            }
        }
        compose.onNodeWithTag("draft_indicator_1", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("draft_indicator_2", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Clear draft").performClick()
        compose.onNodeWithText("Keep draft").performClick()
        compose.onNodeWithText("Sample Alex draft\nsecond line").assertExists()
        compose.onNodeWithText("Clear draft").performClick()
        compose.onNodeWithText("Clear", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("draft_indicator_1", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("draft_indicator_2", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("thread_2").performClick()
        compose.onNodeWithText("Sample second draft").assertExists()
        compose.onNodeWithTag("draft-composer").performTextReplacement("")
        compose.onNodeWithTag("draft_indicator_2", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("draft-composer").performTextInput("New fabricated\nmultiline draft")
        compose.onNodeWithTag("draft_indicator_2", useUnmergedTree = true).assertExists()
        compose.runOnIdle {
            assertEquals("", DraftStore(preferences).get(1))
            assertEquals("New fabricated\nmultiline draft", DraftStore(preferences).get(2))
        }
    }
}
