package com.axiomstead.foldsms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Up to two initials from the first two words, ignoring symbols; "#" when nothing usable remains. */
internal fun avatarInitials(name: String): String {
    val words = name.split(Regex("\\s+")).map { word -> word.filter { it.isLetterOrDigit() } }.filter { it.isNotEmpty() }
    return words.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "#" }
}

/** Stable avatar tone for a conversation, independent of list position. */
internal fun avatarToneIndex(id: Int, tones: Int): Int = Math.floorMod(id, tones)

/** One overflow-menu action. Labels stay identical to the former inline buttons. */
internal data class MenuAction(val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

@Composable
internal fun ConversationAvatar(name: String, id: Int, size: Dp) {
    val tones = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSurfaceVariant)
    // Decorative: the name is always shown beside the avatar, so screen readers skip the initials.
    Box(
        Modifier.size(size).background(tones[avatarToneIndex(id, tones.size)], CircleShape).clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        Text(avatarInitials(name), color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.36f).sp)
    }
}

@Composable
internal fun OverflowMenu(tag: String, description: String, actions: List<MenuAction>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.testTag(tag)) {
            Icon(Icons.Default.MoreVert, contentDescription = description)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(text = { Text(action.label) }, enabled = action.enabled, onClick = {
                    expanded = false
                    action.onClick()
                })
            }
        }
    }
}

/** Telegram-like bubble: rounded, with a small corner on the sender's side. */
internal fun bubbleShape(outgoing: Boolean) = if (outgoing)
    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)

/** Flat tint for the chat area so bubbles stand out without a patterned wallpaper. */
@Composable
internal fun chatBackground(): Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    .compositeOver(MaterialTheme.colorScheme.background)
