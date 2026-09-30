package com.solanasignal.app.data.pumpdev

import com.solanasignal.app.data.pumpportal.ConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** Read-only PumpDev market-data WebSocket. No wallet, signing, or trading API. */
class PumpDevWebSocketManager(
    private val onMessage: (String, Long) -> Unit,
    private val onSystemEvent: (String, String) -> Unit,
    private val apiKeyProvider: () -> String? = { null }
) {
    companion object {
        private const val MAX_BACKOFF_MS = 60_000L
    }

    private val client = OkHttpClient.Builder().pingInterval(15, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val outboundQueue = Channel<JSONObject>(Channel.UNLIMITED)
    private val requestedMints = Collections.synchronizedSet(mutableSetOf<String>())

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    private val _activeSubscriptions = MutableStateFlow(0)
    val activeSubscriptions: StateFlow<Int> = _activeSubscriptions.asStateFlow()
    private val _tradesReceived = MutableStateFlow(0)
    val tradesReceived: StateFlow<Int> = _tradesReceived.asStateFlow()

    private var socket: WebSocket? = null
    private var pumpJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private var stopped = true
    private var generation = 0L

    fun start() {
        if (!stopped) return
        stopped = false
        startOutboundPump()
        connect()
    }

    fun stop() {
        stopped = true
        pumpJob?.cancel()
        reconnectJob?.cancel()
        socket?.close(1000, "scanner stopped")
        socket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _activeSubscriptions.value = 0
    }

    /** Reconnects once using the latest value from the secure credential provider. */
    fun reconnectWithCurrentConfiguration() {
        if (stopped) return
        generation++
        socket?.close(1000, "configuration changed")
        socket = null
        reconnectAttempt = 0
        connect()
    }

    /** Opens a temporary read-only socket, reports only success/failure, then closes it. */
    suspend fun testConnection(timeoutMs: Long = 8_000L): Boolean {
        val result = CompletableDeferred<Boolean>()
        val temporary = client.newWebSocket(
            Request.Builder().url(PumpDevWebSocketUrl.build(apiKeyProvider())).build(),
            object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) { result.complete(true) }
                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) { result.complete(false) }
            }
        )
        val connected = withTimeoutOrNull(timeoutMs) { result.await() } ?: false
        temporary.close(1000, "test complete")
        return connected
    }

    fun subscribeTokenTrade(mint: String) {
        if (!requestedMints.add(mint)) return
        _activeSubscriptions.value = requestedMints.size
        onSystemEvent("PUMPDEV_SUBSCRIBE_REQUEST", "Requested token-trade subscription for $mint")
        enqueue(subscriptionMessage(mint))
    }

    fun unsubscribeTokenTrade(mint: String) {
        if (!requestedMints.remove(mint)) return
        _activeSubscriptions.value = requestedMints.size
        enqueue(JSONObject().put("method", "unsubscribeTokenTrade").put("keys", JSONArray().put(mint)))
    }

    private fun subscriptionMessage(mint: String) =
        JSONObject().put("method", "subscribeTokenTrade").put("keys", JSONArray().put(mint))

    private fun enqueue(message: JSONObject) {
        outboundQueue.trySend(message)
    }

    private fun startOutboundPump() {
        pumpJob?.cancel()
        pumpJob = scope.launch {
            for (message in outboundQueue) {
                val ws = socket
                if (ws != null && _connectionState.value == ConnectionState.CONNECTED) {
                    if (!ws.send(message.toString())) {
                        onSystemEvent("PUMPDEV_SUBSCRIBE_FAILURE", "PumpDev rejected ${message.optString("method", "unknown")} request")
                    }
                } else {
                    delay(500)
                    outboundQueue.trySend(message)
                }
            }
        }
    }

    private fun connect() {
        if (stopped) return
        val currentGeneration = ++generation
        _connectionState.value = if (reconnectAttempt == 0) ConnectionState.CONNECTING else ConnectionState.RECONNECTING
        val url = PumpDevWebSocketUrl.build(apiKeyProvider())
        socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                if (currentGeneration != generation || stopped) {
                    ws.close(1000, "superseded")
                    return
                }
                _connectionState.value = ConnectionState.CONNECTED
                reconnectAttempt = 0
                onSystemEvent("PUMPDEV_CONNECTED", "PumpDev read-only WebSocket connected")
                resubscribeAll()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                val now = System.currentTimeMillis()
                val parsed = PumpDevParser.parseSubscriptionAck(text)
                if (parsed != null) {
                    if (parsed.confirmed) onSystemEvent("PUMPDEV_SUBSCRIBE_CONFIRMED", "PumpDev confirmed ${parsed.keys.size} token-trade subscription(s)")
                    else onSystemEvent("PUMPDEV_SUBSCRIBE_FAILURE", "PumpDev subscription acknowledgement was not accepted")
                    return
                }
                if (PumpDevParser.isTrade(text)) _tradesReceived.value += 1
                onMessage(text, now)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (!stopped && currentGeneration == generation) scheduleReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (!stopped && currentGeneration == generation) {
                    onSystemEvent("PUMPDEV_RECONNECT", PumpDevWebSocketUrl.sanitize("PumpDev WebSocket failure: ${t.message ?: t.javaClass.simpleName}"))
                    scheduleReconnect()
                }
            }
        })
    }

    private fun scheduleReconnect() {
        if (stopped || reconnectJob?.isActive == true) return
        _connectionState.value = ConnectionState.RECONNECTING
        onSystemEvent("PUMPDEV_RECONNECT", "Reconnecting PumpDev trade feed")
        val backoff = min(MAX_BACKOFF_MS, (1000L * 2.0.pow(reconnectAttempt)).toLong())
        reconnectAttempt++
        reconnectJob = scope.launch {
            delay(backoff)
            reconnectJob = null
            if (!stopped) connect()
        }
    }

    private fun resubscribeAll() {
        val mints = requestedMints.toList()
        if (mints.isEmpty()) return
        onSystemEvent("PUMPDEV_RESUBSCRIBE", "Restoring ${mints.size} PumpDev token-trade subscription(s)")
        mints.forEach { enqueue(subscriptionMessage(it)) }
    }
}
