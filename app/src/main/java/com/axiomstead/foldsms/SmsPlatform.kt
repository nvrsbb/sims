package com.axiomstead.foldsms

import android.Manifest
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import java.util.UUID
import java.util.WeakHashMap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** The only boundary allowed to read the device SMS provider or call SmsManager. Tests use fakes. */
internal interface SmsPlatform {
    fun access(): SmsAccess
    fun subscriptions(): List<SmsSubscription>
    fun split(body: String, subscriptionId: Int): List<String>
    fun dispatch(operationId: String, recipient: String, parts: List<String>, subscriptionId: Int)
    fun recent(): List<DeviceSms>
    fun changes(): Flow<Unit>
}

internal class SmsConsentStore(private val preferences: SharedPreferences) {
    fun enabled(): Boolean = synchronized(lock) { blocked[preferences] != true && preferences.getBoolean("enabled", false) }
    fun setEnabled(value: Boolean): Boolean = synchronized(lock) {
        blocked[preferences] = true // Fail closed even when a preference commit fails.
        val saved = preferences.edit().putBoolean("enabled", value).putString("consent_nonce", UUID.randomUUID().toString()).commit()
        if (saved) blocked[preferences] = !value
        saved
    }
    fun listen(callback: () -> Unit): () -> Unit {
        val handler = Handler(Looper.getMainLooper())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> handler.post { callback() } }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    companion object {
        private val lock = Any()
        private val blocked = WeakHashMap<SharedPreferences, Boolean>()
    }
}

internal object SmsReceiveEvents {
    val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
}

internal class AndroidSmsPlatform(private val context: Context, private val consent: SmsConsentStore) : SmsPlatform {
    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    override fun access(): SmsAccess {
        val capable = context.packageManager.hasSystemFeature(if (Build.VERSION.SDK_INT >= 33)
            PackageManager.FEATURE_TELEPHONY_MESSAGING else PackageManager.FEATURE_TELEPHONY)
        val defaultPackage = Telephony.Sms.getDefaultSmsPackage(context)
        return SmsAccess(consent.enabled(), capable, defaultPackage != null,
            granted(Manifest.permission.READ_SMS), granted(Manifest.permission.RECEIVE_SMS),
            granted(Manifest.permission.SEND_SMS), granted(Manifest.permission.READ_PHONE_STATE),
            isDefaultApp = defaultPackage == context.packageName)
    }
    @Suppress("MissingPermission")
    override fun subscriptions(): List<SmsSubscription> {
        check(access().canSend) { "SMS/SIM access is unavailable." }
        val manager = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        return manager.activeSubscriptionInfoList.orEmpty().filter { it.subscriptionId >= 0 }
            .map { SmsSubscription(it.subscriptionId, it.simSlotIndex, it.displayName?.toString().orEmpty()) }
            .sortedWith(compareBy({ it.slot }, { it.id }))
    }
    @Suppress("DEPRECATION")
    private fun manager(id: Int): SmsManager {
        check(access().canSend && subscriptions().any { it.id == id }) { "The selected SIM or SMS access changed. Review permissions and choose a SIM again." }
        return if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java).createForSubscriptionId(id)
        else SmsManager.getSmsManagerForSubscriptionId(id)
    }
    override fun split(body: String, subscriptionId: Int): List<String> = manager(subscriptionId).divideMessage(body)
    @Suppress("MissingPermission")
    override fun dispatch(operationId: String, recipient: String, parts: List<String>, subscriptionId: Int) {
        val service = manager(subscriptionId) // Recheck access and SIM immediately before dispatch. No default-SIM fallback.
        require(parts.size in 1..MAX_SMS_PARTS)
        val callbacks = ArrayList(parts.indices.map { part ->
            val intent = Intent(context, SmsSentReceiver::class.java)
                .setAction(SmsSentReceiver.ACTION)
                .setData(Uri.Builder().scheme("foldsms-sent").authority("result").appendPath(operationId).appendPath(part.toString()).build())
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        })
        DefaultAppProviderWrites(context).recordOutgoing(operationId, recipient, parts, subscriptionId) // only while default; best effort
        if (parts.size == 1) service.sendTextMessage(recipient, null, parts.single(), callbacks.single(), null)
        else service.sendMultipartTextMessage(recipient, null, ArrayList(parts), callbacks, null)
    }
    override fun recent(): List<DeviceSms> {
        check(access().canRead) { "SMS read access is unavailable." }
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
            Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.SUBSCRIPTION_ID, Telephony.Sms.READ)
        return context.contentResolver.query(Telephony.Sms.CONTENT_URI, projection, null, null, "date DESC, _id DESC")?.use { cursor ->
            buildList {
                while (size < 200 && cursor.moveToNext()) add(DeviceSms(cursor.getLong(0), cursor.getLong(1),
                    cursor.getString(2).orEmpty(), cursor.getString(3).orEmpty(), cursor.getLong(4), cursor.getInt(5), cursor.getInt(6), cursor.getInt(7) != 0))
            }
        } ?: emptyList()
    }
    @Suppress("MissingPermission", "DEPRECATION")
    override fun changes(): Flow<Unit> = callbackFlow {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        var observing = false
        if (access().canRead) {
            try { resolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer); observing = true }
            catch (_: SecurityException) { trySend(Unit) }
        }
        val manager = context.getSystemService(SubscriptionManager::class.java)
        val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
            override fun onSubscriptionsChanged() { trySend(Unit) }
        }
        var listening = false
        if (access().canSend && manager != null) {
            try {
                if (Build.VERSION.SDK_INT >= 30) manager.addOnSubscriptionsChangedListener(context.mainExecutor, listener)
                else manager.addOnSubscriptionsChangedListener(listener)
                listening = true
            } catch (_: SecurityException) { trySend(Unit) }
        }
        val stopConsent = consent.listen { trySend(Unit) }
        val receiptJob = launch { SmsReceiveEvents.events.collect { trySend(Unit) } }
        trySend(Unit)
        awaitClose {
            if (observing) resolver.unregisterContentObserver(observer)
            if (listening) manager?.removeOnSubscriptionsChangedListener(listener)
            stopConsent(); receiptJob.cancel()
        }
    }.conflate()
}

internal data class SmsServices(val platform: SmsPlatform, val consent: SmsConsentStore, val drafts: SmsDraftStore, val journal: SmsSendDao, val mms: MmsPlatform? = null,
    val actions: MessageActions? = null, val defaultPrompt: DefaultPromptStore? = null) {
    val controller = SmsSendController(platform, consent, drafts, journal)
}

internal fun smsServices(context: Context): SmsServices {
    val app = context.applicationContext
    val consent = SmsConsentStore(app.getSharedPreferences("sms_companion_access", Context.MODE_PRIVATE))
    return SmsServices(AndroidSmsPlatform(app, consent), consent,
        SmsDraftStore(app.getSharedPreferences("sms_compose_v1", Context.MODE_PRIVATE)), SmsJournalProvider.get(app).sends(),
        AndroidMmsPlatform(app, consent), DefaultAppProviderWrites(app),
        DefaultPromptStore(app.getSharedPreferences("default_prompt", Context.MODE_PRIVATE)))
}
