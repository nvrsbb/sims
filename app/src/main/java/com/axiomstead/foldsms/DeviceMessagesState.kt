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
    Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.RECEIVE_MMS,
    Manifest.permission.READ_CONTACTS) // contacts are optional: without them numbers are shown as they are

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
    var blockedNumbers by mutableStateOf(emptyList<String>())
    var canBlock by mutableStateOf(false)
    var contacts by mutableStateOf(emptyList<ContactEntry>())
    var contactsCanRead by mutableStateOf(services.contacts?.canRead() == true)
    private val contactNames by derivedStateOf { contactNameMap(contacts) }
    private var smsLimit by mutableIntStateOf(services.pages.sms)
    private var mmsLimit by mutableIntStateOf(services.pages.mms)
    /** True once the newest-first window reaches the end of the provider, i.e. nothing older is waiting to be loaded. */
    var exhausted by mutableStateOf(true)
    var loadingMore by mutableStateOf(false)
    // Full histories of threads that were opened, so a conversation is complete even when it is older than the window.
    private var threadSms by mutableStateOf(emptyMap<Long, List<DeviceSms>>())
    private var threadMms by mutableStateOf(emptyMap<Long, List<DeviceMms>>())
    val conversations by derivedStateOf {
        withContactNames(groupDeviceConversations(mergeRows(messages, threadSms.values, DeviceSms::id), mergeRows(mmsMessages, threadMms.values, DeviceMms::id)), contactNames)
    }

    /** Reads older messages when the list is scrolled to its end. */
    suspend fun loadMore() {
        if (loadingMore || exhausted) return
        loadingMore = true
        try { smsLimit = nextPageLimit(smsLimit); mmsLimit = nextPageLimit(mmsLimit); refresh() } finally { loadingMore = false }
    }

    /** Reads everything, so a search can find old messages. */
    suspend fun loadAll() {
        if (loadingMore) return
        loadingMore = true
        try { smsLimit = Int.MAX_VALUE; mmsLimit = Int.MAX_VALUE; refresh() } finally { loadingMore = false }
    }

    /** Makes one conversation complete, however old its messages are. */
    suspend fun loadThread(threadId: Long) {
        if (threadId <= 0 || threadSms.containsKey(threadId) || !access.canRead) return
        lock.withLock {
            val (sms, mms) = withContext(Dispatchers.IO) {
                val smsRows = try { services.platform.thread(threadId) } catch (_: Exception) { emptyList() }
                val mmsRows = try { if (services.mms?.canRead() == true) services.mms.thread(threadId) else emptyList() } catch (_: Exception) { emptyList() }
                smsRows to mmsRows
            }
            threadSms = threadSms + (threadId to sms); threadMms = threadMms + (threadId to mms)
        }
    }

    /** Reloads the contact list; empty without permission. Called on resume and when the permission changes. */
    suspend fun refreshContacts() {
        val platform = services.contacts ?: return
        val allowed = platform.canRead()
        val list = if (allowed) withContext(Dispatchers.IO) { try { platform.all() } catch (_: Exception) { emptyList() } } else emptyList()
        contactsCanRead = allowed; contacts = list
    }

    suspend fun refresh() = lock.withLock {
        refreshing = true
        try {
            if ((services.contacts?.canRead() == true) != contactsCanRead) refreshContacts()
            services.blocker?.let { blocker -> val allowed = blocker.canBlock(); canBlock = allowed; blockedNumbers = if (allowed) withContext(Dispatchers.IO) { blocker.blocked() } else emptyList() }
            val next = services.platform.access()
            access = next
            if (!next.canRead) messages = emptyList()
            if (services.mms?.canRead() != true) mmsMessages = emptyList()
            if (!next.canSend) subscriptions = emptyList()
            withContext(Dispatchers.IO) {
                val rows = if (next.canRead) services.platform.recent(smsLimit) else emptyList()
                val sims = if (next.canSend) services.platform.subscriptions() else emptyList()
                val mmsResult: Result<List<DeviceMms>> = try { Result.success(if (services.mms?.canRead() == true) services.mms.recent(mmsLimit) else emptyList()) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { Result.failure(failure) }
                val loadedThreads = threadSms.keys.toList()
                val reloaded = loadedThreads.associateWith { id ->
                    val smsRows = try { services.platform.thread(id) } catch (_: Exception) { emptyList() }
                    val mmsRows = try { if (services.mms?.canRead() == true) services.mms.thread(id) else emptyList() } catch (_: Exception) { emptyList() }
                    smsRows to mmsRows
                }
                withContext(Dispatchers.Main) {
                    val current = services.platform.access()
                    access = current
                    messages = if (current.canRead) rows else emptyList()
                    subscriptions = if (current.canSend) sims else emptyList()
                    mmsCanRead = services.mms?.canRead() == true
                    mmsMessages = if (mmsCanRead) mmsResult.getOrDefault(emptyList()) else emptyList()
                    exhausted = rows.size < smsLimit && mmsMessages.size < mmsLimit
                    threadSms = reloaded.filterValues { it.first.isNotEmpty() || it.second.isNotEmpty() }.mapValues { it.value.first }
                    threadMms = reloaded.filterValues { it.first.isNotEmpty() || it.second.isNotEmpty() }.mapValues { it.value.second }
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
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) scope.launch { state.refreshContacts(); state.refresh() } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state, state.access.canRead, state.access.canSend, state.access.enabled) {
        services.platform.changes().collect { state.refresh() }
    }
    LaunchedEffect(state) { state.refreshContacts() }
    LaunchedEffect(state, state.mmsCanRead) { services.mms?.changes()?.collect { state.refresh() } }
    return state
}
