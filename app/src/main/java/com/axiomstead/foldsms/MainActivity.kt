package com.axiomstead.foldsms

import kotlinx.coroutines.launch
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.isSystemInDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            val colors = if (dark) darkColorScheme(
                primary = Color(0xFFF2A26A), onPrimary = Color(0xFF112022),
                secondary = Color(0xFF6DC3B8), background = Color(0xFF051214),
                surface = Color(0xFF112022), surfaceVariant = Color(0xFF1B292C),
                onSurface = Color(0xFFD9ECEF), onSurfaceVariant = Color(0xFF9DAFB2),
                outline = Color(0xFF314043), outlineVariant = Color(0xFF253336)
            ) else lightColorScheme(
                primary = Color(0xFFAC5700), onPrimary = Color(0xFFFDF8EF),
                secondary = Color(0xFF00877C), background = Color(0xFFF4ECE0),
                surface = Color(0xFFFDF8EF), surfaceVariant = Color(0xFFEBDCC9),
                onSurface = Color(0xFF211C15), onSurfaceVariant = Color(0xFF756A58),
                outline = Color(0xFFD6C8B4), outlineVariant = Color(0xFFE2D3BF)
            )
            MaterialTheme(colorScheme = colors) { FoldSmsApp() }
        }
    }
}

@Composable
internal fun FoldSmsApp(
    store: DraftStore? = null,
    wide: Boolean = LocalConfiguration.current.screenWidthDp >= 600,
    metadataStore: ConversationMetadataStore? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val draftStore = store ?: remember(context) { DraftStore(context.applicationContext) }
    val drafts = remember(draftStore) { mutableStateMapOf<Int, String>().apply {
        exampleThreads.forEach { put(it.id, draftStore.get(it.id)) }
    } }
    val metadata = remember(context, metadataStore) { metadataStore ?: ConversationMetadataStore.get(context) }
    val overrides by metadata.categories.collectAsState(initial = emptyList())
    val threads = exampleThreads.map { thread ->
        thread.copy(category = overrides.firstOrNull { it.threadId == thread.id }?.category ?: thread.category)
    }
    val scope = rememberCoroutineScope()
    var showCategories by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("All") }
    var search by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableIntStateOf(1) }
    var showConversation by rememberSaveable { mutableStateOf(false) }
    val inboxState = rememberLazyListState()
    val categoryState = rememberLazyListState()
    val conversationStates = rememberSaveableStateHolder()
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    BackHandler(enabled = !wide && showConversation && !keyboardVisible) { showConversation = false }
    val filtered = searchConversations(threads, search, filter)
    val active = threads.first { it.id == selected }
    var detailMessageId by rememberSaveable(selected) { mutableStateOf<Int?>(null) }
    active.history.firstOrNull { it.id == detailMessageId }?.let { message ->
        MessageDetailsSheet(message, active.sender, onDismiss = { detailMessageId = null })
    }
    if (showCategories) CategoryPicker(
        category = active.category,
        onChoose = { category ->
            scope.launch { metadata.setCategory(active.id, category) }
            showCategories = false
        },
        onReset = {
            scope.launch { metadata.setCategory(active.id, null) }
            showCategories = false
        },
        onDismiss = { showCategories = false }
    )
    Surface(Modifier.fillMaxSize().imePadding(), color = MaterialTheme.colorScheme.background) {
        Row {
            if (wide || !showConversation) {
                Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 18.dp, vertical = 20.dp)) {
                    Text("FoldSMS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(3.dp))
                    Text("SAMPLE INBOX", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Search sample messages") },
                        trailingIcon = { if (search.isNotEmpty()) TextButton(onClick = { search = "" }) { Text("Clear search") } },
                        modifier = Modifier.fillMaxWidth().testTag("inbox-search"),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.foundation.lazy.LazyRow(state = categoryState, modifier = Modifier.testTag("category-filters"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("All") + conversationCategories) { c ->
                            FilterChip(modifier = Modifier.testTag("category-filter-$c"), selected = c == filter, onClick = { filter = c }, label = { Text(c) })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (filtered.isEmpty()) Text("No sample conversations found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(state = inboxState, modifier = Modifier.testTag("inbox-list"), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                        items(filtered, key = { it.conversation.id }) { result ->
                            val thread = result.conversation
                            Card(
                                onClick = { selected = thread.id; showConversation = true },
                                modifier = Modifier.testTag("thread_${thread.id}"),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                colors = CardDefaults.cardColors(containerColor = if (thread.id == selected && wide) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(thread.sender, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                                        Text(thread.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    if (!drafts[thread.id].isNullOrEmpty()) {
                                        Text("Draft saved", modifier = Modifier.testTag("draft_indicator_${thread.id}"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                    }
                                    Text(result.snippet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(7.dp))
                                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                                        Text(thread.category, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (wide || showConversation) {
                if (wide) VerticalDivider()
                Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 20.dp, vertical = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!wide) TextButton(onClick = { showConversation = false }) { Text("‹ Back") }
                        Column(Modifier.weight(1f)) {
                            Text(active.sender, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Sample conversation · ${active.category}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    TextButton(onClick = { showCategories = true }) { Text("Change category") }
                    HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Sample history · 2 Nov 2026 · Manila time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    conversationStates.SaveableStateProvider(active.id) {
                        val historyState = rememberLazyListState()
                        LazyColumn(
                            state = historyState,
                            modifier = Modifier.weight(1f).fillMaxWidth().testTag("conversation-history-${active.id}"),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            items(active.history, key = { it.id }) { message ->
                                val outgoing = message.direction == MessageDirection.OUTGOING
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start) {
                                    Surface(
                                        onClick = { detailMessageId = message.id },
                                        modifier = Modifier.fillMaxWidth(0.9f),
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (outgoing) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Text(message.body, Modifier.padding(horizontal = 16.dp, vertical = 13.dp), color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    Text(
                                        if (outgoing) "You · ${message.time} · Sample sent" else "${active.sender} · ${message.time}",
                                        Modifier.padding(top = 4.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                    key(selected) {
                        DraftComposer(
                            draft = drafts[selected].orEmpty(),
                            onDraftChange = { updated ->
                                draftStore.save(selected, updated)
                                drafts[selected] = updated
                            }
                        )
                    }
                }
            }
        }
    }
}
