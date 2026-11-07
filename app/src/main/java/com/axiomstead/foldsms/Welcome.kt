package com.axiomstead.foldsms

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** First launch, and after "Turn off": the one place access is requested. Nothing is read before the button is pressed. */
@Composable
internal fun WelcomeScreen(error: String?, onAllow: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("FoldSMS", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Read and send your SMS and MMS in one place. Messages stay on this phone. Google Messages stays your default app.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAllow, modifier = Modifier.testTag("welcome-allow")) { Text("Allow access") }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.testTag("welcome-error"))
        }
    }
}
