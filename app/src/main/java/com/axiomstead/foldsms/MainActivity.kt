package com.axiomstead.foldsms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val themeStore = remember(context) { ThemeStore(context.applicationContext) }
            FoldSmsApp(themeStore)
        }
    }
}

/**
 * The whole app: a welcome screen until the owner allows access, then the conversation list and conversations.
 * Consent, permissions and the Google Messages default stay as before; nothing is read until consent is on.
 */
@Composable
internal fun FoldSmsApp(
    themeStore: ThemeStore,
    smsServices: SmsServices? = null,
    deviceMetadataStore: DeviceMetadataStore? = null,
    wide: Boolean = LocalConfiguration.current.screenWidthDp >= 600
) {
    var theme by remember { mutableStateOf(themeStore.get()) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var permissionsAsked by rememberSaveable { mutableStateOf(false) }
    val dark = theme.usesDarkColors(isSystemInDarkTheme())
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
    val activity = LocalContext.current as? ComponentActivity
    DisposableEffect(activity, dark) {
        // Edge-to-edge with status/navigation icons that follow the app theme, not only the device theme.
        val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        onDispose {}
    }
    val state = rememberDeviceMessagesState(smsServices)
    val scope = rememberCoroutineScope()
    val requestPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionsAsked = true
        scope.launch { state.refresh() }
    }
    MaterialTheme(colorScheme = colors) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                if (!state.access.enabled) WelcomeScreen(error = state.error) {
                    if (state.services.consent.setEnabled(true)) {
                        state.error = null
                        state.access = state.services.platform.access()
                        requestPermissions.launch(smsPermissions)
                    } else state.error = "Could not save your choice. Nothing was turned on."
                } else DeviceInboxHost(
                    state = state, wide = wide, metadataStore = deviceMetadataStore,
                    onOpenSettings = { showSettings = true },
                    onRequestPermissions = { requestPermissions.launch(smsPermissions) },
                    permissionsAsked = permissionsAsked
                )
            }
        }
        if (showSettings) AppSettings(
            theme = theme,
            onThemeChange = { theme = it; themeStore.save(it) },
            onTurnOff = {
                val saved = state.services.consent.setEnabled(false)
                state.access = state.services.platform.access()
                state.messages = emptyList(); state.mmsMessages = emptyList(); state.subscriptions = emptyList()
                state.error = if (saved) null else "Turned off for now, but Android could not save it. Remove SMS permission in Android settings."
                showSettings = false
            },
            onDismiss = { showSettings = false }
        )
    }
}
