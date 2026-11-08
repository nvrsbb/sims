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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Set by a notification: open the conversation with this sender. */
internal const val EXTRA_OPEN_ADDRESS = "com.axiomstead.foldsms.OPEN_ADDRESS"
internal const val OPEN_CONVERSATION_ACTION = "com.axiomstead.foldsms.OPEN_CONVERSATION"

class MainActivity : ComponentActivity() {
    private var recipient by mutableStateOf<String?>(null)
    private var openAddress by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) recipient = recipientFrom(intent)
        // Read even after a recreation: the system may have recreated the screen to deliver a notification tap. The extra is
        // removed once it has been used (below), so a later recreation does not open the same conversation again.
        intent?.getStringExtra(EXTRA_OPEN_ADDRESS)?.let { openAddress = it; Diagnostics.record(this, "NOTIFICATION_TAP", "arrived=onCreate recreated=${savedInstanceState != null}") }
        setContent {
            val context = LocalContext.current
            val themeStore = remember(context) { ThemeStore(context.applicationContext) }
            FoldSmsApp(themeStore, launchRecipient = recipient, onLaunchRecipientHandled = { recipient = null },
                openAddress = openAddress, onOpenAddressHandled = { openAddress = null; intent?.removeExtra(EXTRA_OPEN_ADDRESS) })
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        recipientFrom(intent)?.let { recipient = it }
        setIntent(intent)
        intent.getStringExtra(EXTRA_OPEN_ADDRESS)?.let { openAddress = it; Diagnostics.record(this, "NOTIFICATION_TAP", "arrived=onNewIntent") }
    }
}

/** The number from an sms:/smsto:/mms:/mmsto: link, or null. The text of the link is ignored: the owner writes and reviews it. */
internal fun recipientFrom(intent: android.content.Intent?): String? =
    intent?.data?.let { recipientFromLink(it.scheme, it.schemeSpecificPart) }

internal fun recipientFromLink(scheme: String?, schemeSpecificPart: String?): String? {
    if (scheme?.lowercase() !in setOf("sms", "smsto", "mms", "mmsto")) return null
    // Only the first number; "?body=..." and further recipients are ignored.
    return schemeSpecificPart?.substringBefore('?')?.substringBefore(',')?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }
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
    wide: Boolean = LocalConfiguration.current.screenWidthDp >= 600,
    launchRecipient: String? = null,
    onLaunchRecipientHandled: () -> Unit = {},
    codeCopier: ((String) -> Unit)? = null,
    exportTarget: (() -> android.net.Uri)? = null,
    openAddress: String? = null,
    onOpenAddressHandled: () -> Unit = {},
    intentLauncher: ((android.content.Intent) -> Unit)? = null
) {
    var theme by remember { mutableStateOf(themeStore.get()) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showContacts by rememberSaveable { mutableStateOf(false) }
    var contactTarget by remember { mutableStateOf<String?>(null) } // a number picked in Contacts: opens a conversation with it
    var permissionsAsked by rememberSaveable { mutableStateOf(false) }
    val dark = theme.usesDarkColors(isSystemInDarkTheme())
    val colors = if (dark) darkAppColors else lightAppColors
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
    val export = rememberExportFlow(state, exportTarget)
    val requestPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionsAsked = true
        scope.launch { state.refresh() }
    }
    val context = LocalContext.current
    val callNumber = rememberCaller(intentLauncher) { message -> state.error = message }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val roleResult = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        scope.launch { state.refresh() }
        // Notifications need their own permission on Android 13+; asked once the role is granted, when they matter.
        if (android.os.Build.VERSION.SDK_INT >= 33 && state.services.platform.access().isDefaultApp)
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    val makeDefault: (() -> Unit)? = if (state.services.actions != null && roleAvailable(context)) {
        { defaultRoleRequest(context)?.let { roleResult.launch(it) } }
    } else null
    // The owner can change the default app in Android settings at any time; pick the change up when returning.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) scope.launch { state.refresh() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
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
                    permissionsAsked = permissionsAsked,
                    onMakeDefault = makeDefault,
                    launchRecipient = contactTarget ?: launchRecipient, onLaunchRecipientHandled = { contactTarget = null; onLaunchRecipientHandled() },
                    onOpenContacts = { showContacts = true }, onCall = callNumber, intentLauncher = intentLauncher,
                    codeCopier = codeCopier, openAddress = openAddress, onOpenAddressHandled = onOpenAddressHandled
                )
            }
        }
        export.Dialogs()
        if (showContacts) {
            val groups = remember(state.contacts) { groupContacts(state.contacts) }
            ContactsScreen(
                groups = groups, canRead = state.contactsCanRead || state.services.contacts == null,
                onMessage = { number -> contactTarget = number; showContacts = false },
                onCall = callNumber,
                onEdit = { group -> editContactIntent(group.contactId, group.lookupKey)?.let { intent ->
                    try { (intentLauncher ?: { i: android.content.Intent -> context.startActivity(i) })(intent) } catch (_: android.content.ActivityNotFoundException) { state.error = "No contacts app is available." }
                } },
                onAdd = { try { (intentLauncher ?: { i: android.content.Intent -> context.startActivity(i) })(newContactIntent()) } catch (_: android.content.ActivityNotFoundException) { state.error = "No contacts app is available." } },
                onAllow = { requestPermissions.launch(smsPermissions) },
                onDismiss = { showContacts = false }
            )
        }
        if (showSettings) AppSettings(
            theme = theme,
            onExport = if (state.access.canRead) ({ showSettings = false; export.start() }) else null,
            blockedNumbers = state.blockedNumbers,
            onUnblock = state.services.blocker?.takeIf { state.canBlock }?.let { blocker -> { number: String -> scope.launch { withContext(kotlinx.coroutines.Dispatchers.IO) { blocker.unblock(number) }; state.refresh() } } },
            isDefaultApp = state.access.isDefaultApp,
            onMakeDefault = makeDefault.takeIf { !state.access.isDefaultApp },
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
