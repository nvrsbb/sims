package com.axiomstead.foldsms

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PreviewSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences get() = context.getSharedPreferences("test_preview_settings", Context.MODE_PRIVATE)

    private val database = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
    private val metadata = ConversationMetadataStore(database.metadata())
    private val draftPreferences get() = context.getSharedPreferences("test_settings_drafts", Context.MODE_PRIVATE)
    @Before fun clearBefore() { preferences.edit().clear().commit(); draftPreferences.edit().clear().commit() }
    @After fun clearAfter() { database.close(); preferences.edit().clear().commit(); draftPreferences.edit().clear().commit() }

    @Test fun themeSurvivesReopeningStorage() {
        val store = ThemeStore(preferences)
        assertEquals(ThemeChoice.DEVICE, store.get())
        store.save(ThemeChoice.DARK)
        assertEquals(ThemeChoice.DARK, ThemeStore(preferences).get())
        store.save(ThemeChoice.DEVICE)
        assertEquals(ThemeChoice.DEVICE, ThemeStore(preferences).get())
    }

    @Test fun settingsReturnToSelectedConversationAndRememberTheme() {
        val drafts = DraftStore(draftPreferences)
        val custom = CustomSampleConversationStore(database, drafts)
        compose.setContent { PreviewApp(ThemeStore(preferences), drafts, metadata, custom, wide = false, deletionStore = SampleDeletionStore(draftPreferences)) }
        compose.onAllNodesWithText("Alex").onFirst().performClick()
        compose.onNodeWithTag("draft-composer").performTextInput("Fabricated settings draft")
        compose.onNodeWithTag("conversation-actions").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Dark", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithTag("draft-composer").assertExists()
        compose.onNodeWithText("Fabricated settings draft").assertExists()
        compose.onNodeWithText("Sample conversation · Personal").assertExists()
        compose.onNodeWithTag("conversation-actions").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Dark").assertIsSelected()
        compose.runOnIdle { assertEquals(ThemeChoice.DARK, ThemeStore(preferences).get()) }
    }
}
