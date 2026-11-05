package com.axiomstead.foldsms

import kotlinx.coroutines.flow.*

internal class FakeSmsPlatform(private val consent: SmsConsentStore) : SmsPlatform {
    var gate = SmsAccess(true, true, true, true, true, true, true)
    var sims = listOf(SmsSubscription(7, 0, "Fabricated SIM"))
    var rows = emptyList<DeviceSms>()
    var partLength = 70
    var reads = 0
    var splits = 0
    data class Request(val id: String, val address: String, val parts: List<String>, val sim: Int)
    val requests = java.util.concurrent.CopyOnWriteArrayList<Request>()
    var onDispatch: ((Request) -> Unit)? = null
    val events = MutableStateFlow(0)
    override fun access() = gate.copy(enabled = consent.enabled())
    override fun subscriptions() = sims
    override fun recent(): List<DeviceSms> { reads++; return rows }
    override fun split(body: String, subscriptionId: Int): List<String> { splits++; return body.chunked(partLength) }
    override fun dispatch(operationId: String, recipient: String, parts: List<String>, subscriptionId: Int) {
        val request = Request(operationId, recipient, parts.toList(), subscriptionId)
        requests.add(request); onDispatch?.invoke(request)
    }
    override fun changes(): Flow<Unit> = merge(events.map { Unit }, SmsReceiveEvents.events)
}
