package com.axiomstead.foldsms

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class ConversationArchiveUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun archivedConversationLeavesInboxAndRestoresFromArchive() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferencePrefix = "archive-ui-${UUID.randomUUID()}"
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences("$preferencePrefix-$name", mode)
        }
        val database = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
        try {
            val store = ConversationMetadataStore(database.metadata())
            val drafts = DraftStore(isolated)
            val custom = CustomSampleConversationStore(database, drafts)
            compose.setContent {
                CompositionLocalProvider(LocalContext provides isolated, LocalConfiguration provides Configuration().apply { screenWidthDp = 400 }) {
                    MaterialTheme { FoldSmsApp(store = drafts, metadataStore = store, customStore = custom, deletionStore = SampleDeletionStore(isolated)) }
                }
            }
            compose.onNodeWithText("Alex").performClick()
            compose.onNodeWithTag("conversation-actions").performClick()
            compose.onNodeWithText("Archive conversation").performClick()
            compose.waitUntil { compose.onAllNodesWithText("Alex").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("Alex").assertDoesNotExist()
            compose.onNodeWithTag("inbox-actions").performClick()
            compose.onNodeWithText("Archive").performClick()
            compose.onNodeWithText("Alex").performClick()
            compose.onNodeWithTag("conversation-actions").performClick()
            compose.onNodeWithText("Restore conversation").performClick()
            compose.waitUntil { compose.onAllNodesWithText("Archive is empty").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Archive is empty").assertExists()
            compose.onNodeWithTag("inbox-actions").performClick()
            compose.onNodeWithText("Inbox").performClick()
            compose.onNodeWithText("Alex").assertExists()
        } finally {
            database.close()
            isolated.getSharedPreferences("sample_drafts", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
