package com.axiomstead.foldsms

import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class ConversationCategoriesTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun categoriesSurviveReopeningAndResetIndependentlyOfDrafts() = runBlocking {
        val name = "category-test-${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        preferences.edit().putString("thread_1", "line one\nline two").commit()
        fun open() = Room.databaseBuilder(context, ConversationMetadataDatabase::class.java, name).build()
        var database = open()
        try {
            var store = ConversationMetadataStore(database.metadata())
            store.setCategory(1, "Unknown")
            store.setCategory(2, "Personal")
            database.close()
            database = open()
            store = ConversationMetadataStore(database.metadata())
            assertEquals(setOf(ConversationMetadata(1, "Unknown"), ConversationMetadata(2, "Personal")), store.categories.first().toSet())
            store.setCategory(1, null)
            assertEquals(setOf(ConversationMetadata(1), ConversationMetadata(2, "Personal")), store.categories.first().toSet())
            try {
                store.setCategory(2, "Invalid category")
                fail("Invalid category must be rejected")
            } catch (_: IllegalArgumentException) { }
            assertEquals(setOf(ConversationMetadata(1), ConversationMetadata(2, "Personal")), store.categories.first().toSet())
            assertEquals("line one\nline two", preferences.getString("thread_1", null))
        } finally {
            database.close()
            context.deleteDatabase(name)
            preferences.edit().clear().commit()
        }
    }

    @Test fun choosingCategoryImmediatelyChangesInboxFilteringAndResetRestoresDefault() {
        val preferenceName = "category-ui-test-${UUID.randomUUID()}"
        val isolatedContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences("$preferenceName-$name", mode)
        }
        val database = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
        try {
            val store = ConversationMetadataStore(database.metadata())
            val drafts = DraftStore(isolatedContext)
            val custom = CustomSampleConversationStore(database, drafts)
            compose.setContent {
                CompositionLocalProvider(LocalContext provides isolatedContext, LocalConfiguration provides Configuration().apply { screenWidthDp = 400 }) {
                    MaterialTheme { FoldSmsApp(store = drafts, metadataStore = store, customStore = custom, deletionStore = SampleDeletionStore(isolatedContext)) }
                }
            }
            compose.onNodeWithText("Alex").performClick()
            compose.onNodeWithTag("conversation-actions").performClick()
            compose.onNodeWithText("Change category").performClick()
            compose.onNodeWithText("Unknown", useUnmergedTree = true).performClick()
            compose.waitUntil { runBlocking { store.categories.first().any { it.threadId == 1 && it.category == "Unknown" } } }
            compose.onNodeWithContentDescription("Back").performClick()
            // Unknown is the last filter in the horizontally scrolling row.
            compose.onNodeWithTag("category-filters").performScrollToNode(hasTestTag("category-filter-Unknown"))
            compose.onNodeWithTag("category-filter-Unknown").performClick()
            compose.onNodeWithText("Alex").assertExists().performClick()
            compose.onNodeWithTag("conversation-actions").performClick()
            compose.onNodeWithText("Change category").performClick()
            compose.onNodeWithText("Reset to sample default").performScrollTo().performClick()
            compose.waitUntil { runBlocking { store.categories.first().all { it.category == null } } }
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Alex").assertDoesNotExist()
            compose.onNodeWithTag("category-filters").performScrollToNode(hasTestTag("category-filter-Personal"))
            compose.onNodeWithTag("category-filter-Personal").performClick()
            compose.onNodeWithText("Alex").assertExists()
        } finally {
            database.close()
            context.getSharedPreferences("$preferenceName-sample_drafts", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
