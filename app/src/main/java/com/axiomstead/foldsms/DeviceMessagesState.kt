package com.axiomstead.foldsms

import android.Manifest
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal val smsPermissions = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS,
    Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.RECEIVE_MMS)

/** Access, SIMs and stored SMS/MMS for the opt-in device messages, refreshed from the providers. */
@Stable
internal class DeviceMessagesState(val services: SmsServices) {
    var access by mutableStateOf(services.platform.access())
    var subscriptions by mutableStateOf(emptyList<SmsSubscription>())
    var messages by mutableStateOf(emptyList<DeviceSms>())
    var mmsMessages by mutableStateOf(emptyList<DeviceMms>())
    var mmsCanRead by mutableStateOf(services.mms?.canRead() == true)
    var error by mutableStateOf<String?>(null)
    var refreshing by mutableStateOf(false)
    private var loaded by mutableStateOf(false)
    private val lock = Mutex()

    /** False until the first refresh finishes, so screens can show loading instead of an empty list. */
    val hasLoaded get() = loaded
    val conversations by derivedStateOf { groupDeviceConversations(messages, mmsMessages) }

    suspend fun refresh() = lock.withLock {
        refreshing = true
        try {
            val next = services.platform.access()
            access = next
            if (!next.canRead) messages = emptyList()
            if (services.mms?.canRead() != true) mmsMessages = emptyList()
            if (!next.canSend) subscriptions = emptyList()
            withContext(Dispatchers.IO) {
                val rows = if (next.canRead) services.platform.recent() else emptyList()
                val sims = if (next.canSend) services.platform.subscriptions() else emptyList()
                val mmsResult: Result<List<DeviceMms>> = try { Result.success(if (services.mms?.canRead() == true) services.mms.recent() else emptyList()) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { Result.failure(failure) }
                withContext(Dispatchers.Main) {
                    val current = services.platform.access()
                    access = current
                    messages = if (current.canRead) rows else emptyList()
                    subscriptions = if (current.canSend) sims else emptyList()
                    mmsCanRead = services.mms?.canRead() == true
                    mmsMessages = if (mmsCanRead) mmsResult.getOrDefault(emptyList()) else emptyList()
                    if (mmsResult.isFailure) error = "MMS provider could not be read. SMS remains available; check MMS permission and refresh."
                }
            }
            services.controller.reconcileCompletedDraft()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            messages = emptyList(); mmsMessages = emptyList(); subscriptions = emptyList()
            mmsCanRead = services.mms?.canRead() == true
            access = services.platform.access()
            error = "SMS access changed or the provider could not be read. Review permissions and refresh."
        } finally { refreshing = false; loaded = true }
    }
}

/** Remembers the state and keeps it fresh: on resume, on provider/consent/SIM changes and when MMS access changes. */
@Composable
internal fun rememberDeviceMessagesState(supplied: SmsServices? = null): DeviceMessagesState {
    val context = LocalContext.current
    val services = remember(context, supplied) { supplied ?: smsServices(context) }
    val state = remember(services) { DeviceMessagesState(services) }
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    DisposableEffect(owner, state) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) scope.launch { state.refresh() } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state, state.access.canRead, state.access.canSend, state.access.enabled) {
        services.platform.changes().collect { state.refresh() }
    }
    LaunchedEffect(state, state.mmsCanRead) { services.mms?.changes()?.collect { state.refresh() } }
    return state
}
