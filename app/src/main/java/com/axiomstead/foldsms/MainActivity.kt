package com.axiomstead.foldsms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class ThreadPreview(val id: Int, val sender: String, val preview: String, val category: String, val time: String)
private val exampleThreads = listOf(
    ThreadPreview(1, "Alex", "See you this evening!", "Personal", "10:42"),
    ThreadPreview(2, "Bank Alert", "Sample transaction: ₱1,250", "Finance", "09:18"),
    ThreadPreview(3, "Parcel Updates", "Sample package is on the way", "Services", "Yesterday"),
    ThreadPreview(4, "Promo Center", "Sample weekend offer", "Promotions", "Monday"),
    ThreadPreview(5, "Verification", "Your sample code is 123456", "OTP", "Sunday")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF334C80))) { FoldSmsApp() } }
    }
}

@Composable
private fun FoldSmsApp() {
    var filter by remember { mutableStateOf("All") }
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableIntStateOf(1) }
    var showConversation by remember { mutableStateOf(false) }
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    val filtered = exampleThreads.filter { (filter == "All" || it.category == filter) && (it.sender.contains(search, true) || it.preview.contains(search, true)) }
    val active = exampleThreads.first { it.id == selected }
    Surface(Modifier.fillMaxSize()) {
        Row {
            if (wide || !showConversation) {
                Column(Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
                    Text("FoldSMS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("LOCAL PREVIEW · Sample data only", color = Color(0xFF687184), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(search, { search = it }, label = { Text("Search sample messages") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("All", "Personal", "Finance", "Services", "Promotions", "OTP")) { c ->
                            FilterChip(selected = c == filter, onClick = { filter = c }, label = { Text(c) })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(filtered) { thread ->
                            Card(onClick = { selected = thread.id; showConversation = true }, colors = CardDefaults.cardColors(containerColor = if (thread.id == selected && wide) Color(0xFFE3EBFA) else Color(0xFFF4F6FA))) {
                                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(thread.sender, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                        Text(thread.time, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Spacer(Modifier.height(5.dp))
                                    Text(thread.preview, style = MaterialTheme.typography.bodyMedium)
                                    Text(thread.category, color = Color(0xFF486193), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            if (wide || showConversation) {
                if (wide) VerticalDivider()
                Column(Modifier.weight(1f).fillMaxHeight().padding(20.dp)) {
                    if (!wide) TextButton(onClick = { showConversation = false }) { Text("‹ Back") }
                    Text(active.sender, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Demo conversation · no real messages", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFEAF0FA)) {
                        Text(active.preview, Modifier.padding(16.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedTextField(value = "", onValueChange = {}, enabled = false, label = { Text("Sending disabled in preview") }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
