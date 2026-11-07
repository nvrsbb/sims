package com.axiomstead.foldsms

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class FakeMmsPlatform(private val consent: SmsConsentStore) : MmsPlatform {
    var granted = true
    var rows = emptyList<DeviceMms>()
    var reads = 0
    var failRead = false
    var images = 0
    val events = MutableStateFlow(0)
    override fun canRead() = consent.enabled() && granted
    override fun recent(limit: Int): List<DeviceMms> {
        check(canRead()); reads++; if (failRead) error("Fabricated provider failure")
        return rows.sortedWith(compareByDescending<DeviceMms> { it.date }.thenByDescending { it.id }).take(limit)
    }
    override fun thread(threadId: Long): List<DeviceMms> { check(canRead()); if (failRead) error("Fabricated provider failure"); return rows.filter { it.threadId == threadId } }
    override fun image(partId: Long): Bitmap? { images++; return null }
    override fun changes(): Flow<Unit> = events.map { Unit }
}
