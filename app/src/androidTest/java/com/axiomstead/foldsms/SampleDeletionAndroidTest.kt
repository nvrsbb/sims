package com.axiomstead.foldsms

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class SampleDeletionAndroidTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val deletionName = "deletion_test_${UUID.randomUUID()}"
    private val draftName = "deletion_drafts_${UUID.randomUUID()}"
    private val preferences = context.getSharedPreferences(deletionName, Context.MODE_PRIVATE)
    private val drafts = DraftStore(context.getSharedPreferences(draftName, Context.MODE_PRIVATE))
    private val deletions = SampleDeletionStore(preferences)
    private val database = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
    private val metadata = ConversationMetadataStore(database.metadata())
    private val custom = CustomSampleConversationStore(database, drafts)

    @After fun cleanup() {
        database.close()
        context.deleteSharedPreferences(deletionName)
        context.deleteSharedPreferences(draftName)
    }

    @Test fun deletionAndUndoSurviveReopeningWithoutChangingLegacyDraftOrCategories() = runBlocking {
        drafts.save(1, "  Fabricated\ndraft  ")
        metadata.setCategory(1, "Finance")
        assertTrue(deletions.delete(setOf(101, 102, 103)))
        val reopened = SampleDeletionStore(preferences)
        assertTrue(reopened.state.value.visible(exampleThreads).first().history.isEmpty())
        assertEquals(setOf(101, 102, 103), reopened.state.value.undoIds)
        assertTrue(reopened.undo())
        assertEquals(exampleThreads, SampleDeletionStore(preferences).state.value.visible(exampleThreads))
        assertEquals("  Fabricated\ndraft  ", drafts.get(1))
        assertEquals("Finance", database.metadata().let {
            it.observeCategories().first().single().category
        })
    }

    @Test fun selectionCancellationMixedDirectionsAndUndo() {
        drafts.save(1, "Fabricated preserved draft")
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme { FoldSmsApp(drafts, wide = false, metadataStore = metadata, deletionStore = deletions, customStore = custom) }
        }
        compose.onNodeWithTag("thread_1").performClick()
        compose.onNodeWithTag("conversation-actions").performClick()
        compose.onNodeWithText("Select messages").performClick()
        select(102)
        select(103)
        select(102) // deselect and reselect
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("delete-selected").assertTextEquals("Delete selected (1)")
        select(102)
        compose.onNodeWithTag("delete-selected").performClick()
        compose.onNodeWithText("Delete 2 sample messages selected in Alex?", substring = true).assertExists()
        compose.onNodeWithText("Keep messages").performClick()
        compose.runOnIdle { assertTrue(deletions.state.value.deletedIds.isEmpty()) }
        compose.onNodeWithTag("delete-selected").performClick()
        compose.onNodeWithTag("confirm-delete").performClick()
        compose.waitUntil(5_000) { deletions.state.value.deletedIds == setOf(102, 103) }
        compose.onNodeWithTag("message-102").assertDoesNotExist()
        compose.onNodeWithTag("message-103").assertDoesNotExist()
        compose.onNodeWithText("Fabricated preserved draft").assertExists()
        compose.onNodeWithText("Undo last deletion (2)").performClick()
        compose.waitUntil(5_000) { deletions.state.value.deletedIds.isEmpty() }
        selectVisible(103)
        compose.onNodeWithText("See you this evening!").assertExists()
        compose.onNodeWithTag("conversation-actions").performClick()
        compose.onNodeWithText("Select messages").performClick()
        select(101)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("thread_2").performClick()
        compose.onNodeWithTag("conversation-actions").performClick()
        compose.onNodeWithText("Select messages").performClick()
        compose.onNodeWithTag("delete-selected").assertTextEquals("Delete selected (0)").assertIsNotEnabled()
    }

    @Test fun failedStorageCommitDoesNotPublishDeletionOrOverwriteUndo() = runBlocking {
        assertTrue(deletions.delete(setOf(201)))
        val failingPreferences = object : SharedPreferences by preferences {
            override fun edit(): SharedPreferences.Editor {
                val editor = preferences.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                        editor.putStringSet(key, values)
                        return this
                    }
                    override fun commit() = false
                }
            }
        }
        val failing = SampleDeletionStore(failingPreferences)
        assertFalse(failing.delete(setOf(101)))
        assertEquals(setOf(201), failing.state.value.deletedIds)
        assertEquals(setOf(201), SampleDeletionStore(preferences).state.value.undoIds)
    }

    @Test fun deleteAllScopesKeepCountsIndependentOfSearchAndCategory() {
        drafts.save(1, "Fabricated Alex draft")
        drafts.save(2, "Fabricated Bank draft")
        compose.setContent {
            MaterialTheme { FoldSmsApp(drafts, wide = false, metadataStore = metadata, deletionStore = deletions, customStore = custom) }
        }
        compose.onNodeWithTag("inbox-search").performTextInput("meeting")
        compose.onNodeWithTag("thread_1").performClick()
        compose.onNodeWithTag("conversation-actions").performScrollTo().performClick()
        compose.onNodeWithText("Delete all in conversation").performClick()
        compose.onNodeWithText("Delete 3 sample messages in the entire Alex conversation?", substring = true).assertExists()
        compose.onNodeWithText("Keep messages").performClick()
        compose.runOnIdle { assertTrue(deletions.state.value.deletedIds.isEmpty()) }
        compose.onNodeWithTag("conversation-actions").performClick()
        compose.onNodeWithText("Delete all in conversation").performClick()
        compose.onNodeWithTag("confirm-delete").performClick()
        compose.waitUntil(5_000) { deletions.state.value.deletedIds.size == 3 }
        compose.onNodeWithTag("empty-history").assertExists()
        compose.onNodeWithText("Fabricated Alex draft").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("category-filter-Finance").performScrollTo().performClick()
        compose.onNodeWithTag("inbox-search").performTextReplacement("Bank")
        compose.onNodeWithTag("thread_1").assertDoesNotExist()
        compose.onNodeWithTag("inbox-actions").performClick()
        compose.onNodeWithText("Delete all messages in app").performClick()
        compose.onNodeWithText("Delete 8 sample messages across the entire app", substring = true).assertExists()
        compose.onNodeWithText("Keep messages").performClick()
        compose.runOnIdle { assertEquals(setOf(101, 102, 103), deletions.state.value.deletedIds) }
        compose.onNodeWithTag("inbox-actions").performClick()
        compose.onNodeWithText("Delete all messages in app").performClick()
        compose.onNodeWithTag("confirm-delete").performClick()
        compose.waitUntil(5_000) { deletions.state.value.deletedIds.size == 11 }
        compose.runOnIdle {
            val reopened = SampleDeletionStore(preferences).state.value
            assertTrue(reopened.visible(exampleThreads).all { it.messages.isEmpty() })
            assertEquals(8, reopened.undoIds.size)
            assertEquals("Fabricated Alex draft", drafts.get(1))
            assertEquals("Fabricated Bank draft", drafts.get(2))
        }
        compose.onNodeWithTag("undo-inbox").performClick()
        compose.waitUntil(5_000) { deletions.state.value.deletedIds.size == 3 }
        compose.runOnIdle {
            val visible = SampleDeletionStore(preferences).state.value.visible(exampleThreads)
            assertTrue(visible.first().messages.isEmpty())
            assertEquals(exampleThreads.drop(1), visible.drop(1))
        }
    }

    @Test fun appWideDeletionIncludesArchiveAndPreservesPinsAndCustomDraft() {
        val created = runBlocking {
            metadata.setArchived(1, true)
            metadata.setPinned(2, true)
            custom.create("Sample Robin", "+1 202-555-0199", "Fabricated custom draft", exampleThreads.map { it.address })
        }
        compose.setContent {
            MaterialTheme { FoldSmsApp(drafts, wide = false, metadataStore = metadata, deletionStore = deletions, customStore = custom) }
        }
        compose.waitUntil { compose.onAllNodesWithTag("thread_1").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("inbox-actions").performClick()
        compose.onNodeWithText("Delete all messages in app").performClick()
        compose.onNodeWithText("Delete 11 sample messages across the entire app", substring = true).assertExists()
        compose.onNodeWithTag("confirm-delete").performClick()
        compose.waitUntil(5_000) { deletions.state.value.deletedIds.size == 11 }
        compose.runOnIdle {
            runBlocking {
                val rows = metadata.categories.first()
                assertTrue(rows.single { it.threadId == 1 }.archived)
                assertTrue(rows.single { it.threadId == 2 }.pinned)
                assertEquals(created, database.customConversations().getConversations().single())
                assertEquals("Fabricated custom draft", drafts.get(created.id))
            }
        }
        compose.onNodeWithTag("undo-inbox").performClick()
        compose.waitUntil(5_000) { deletions.state.value.deletedIds.isEmpty() }
        compose.onNodeWithTag("inbox-actions").performClick()
        compose.onNodeWithText("Archive", substring = false).performClick()
        compose.waitUntil { compose.onAllNodesWithTag("thread_1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("thread_1").performClick()
        compose.onNodeWithTag("conversation-history-1").performScrollToNode(hasTestTag("message-103"))
        compose.onNodeWithText("See you this evening!").assertExists()
    }

    private fun selectVisible(id: Int) {
        compose.onNodeWithTag("conversation-history-1").performScrollToNode(hasTestTag("message-$id"))
    }

    private fun select(id: Int) {
        selectVisible(id)
        compose.onNodeWithTag("message-$id").performClick()
    }
}
