package com.solanasignal.app.data.pumpportal

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONObject
import android.net.Uri
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

/**
 * Manages ONE persistent WebSocket connection to PumpPortal (spec #4: never opens a
 * new socket per token). Handles optional auth via the API-key URL, new-token
 * discovery, reconnection with exponential backoff, discovery restore, and
 * staleness/latency monitoring.
 *
 * Uses the official discovery subscription method on this single persistent
 * connection.
 */
class PumpPortalWebSocketManager(
    private val getApiKey: () -> String?,
    private val onEvent: (ParseResult, Long) -> Unit,
    private val onSystemEvent: (category: String, message: String) -> Unit
) {
    companion object {
        private const val TAG = "PumpPortalWS"
        private const val BASE_URL = "wss://pumpportal.fun/api/data"
        private const val MAX_SUB_MSGS_PER_SEC = 200          // spec #8
        private const val STALE_THRESHOLD_MS = 30_000L
        private const val MAX_BACKOFF_MS = 60_000L
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _latencyMs = MutableStateFlow<Long?>(null)
    val latencyMs: StateFlow<Long?> = _latencyMs.asStateFlow()

    private val _lastEventAtMs = MutableStateFlow<Long?>(null)
    val lastEventAtMs: StateFlow<Long?> = _lastEventAtMs.asStateFlow()

    private val _eventsPerSecond = MutableStateFlow(0)
    val eventsPerSecond: StateFlow<Int> = _eventsPerSecond.asStateFlow()

    private val _reconnectCount = MutableStateFlow(0)
    val reconnectCount: StateFlow<Int> = _reconnectCount.asStateFlow()

    private val _parserErrorCount = MutableStateFlow(0)
    val parserErrorCount: StateFlow<Int> = _parserErrorCount.asStateFlow()

    // This count covers only the discovery feed; token trades are not subscribed
    // from this wallet-linked provider in the read-only app.
    private val _activeFeedSubscriptions = MutableStateFlow(0)
    val activeFeedSubscriptions: StateFlow<Int> = _activeFeedSubscriptions.asStateFlow()

    private var subscribedNewToken = false

    private val outboundQueue = Channel<JSONObject>(capacity = Channel.UNLIMITED)
    private var pumpJob: Job? = null
    private var monitorJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private var manuallyStopped = true
    private var socketGeneration = 0L

    private var eventCounterWindowStart = System.currentTimeMillis()
    private var eventCounterCount = 0

    fun start() {
        if (!manuallyStopped) return
        manuallyStopped = false
        connect()
        startOutboundPump()
        startMonitor()
    }

    fun stop() {
        manuallyStopped = true
        pumpJob?.cancel()
        monitorJob?.cancel()
        reconnectJob?.cancel()
        webSocket?.close(1000, "user stopped scanner")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _activeFeedSubscriptions.value = 0
    }

    fun subscribeNewToken() {
        subscribedNewToken = true
        enqueue(JSONObject().put("method", "subscribeNewToken"))
        if (_connectionState.value == ConnectionState.CONNECTED) {
            _activeFeedSubscriptions.value = 1
        }
    }


    private fun enqueue(msg: JSONObject) {
        outboundQueue.trySend(msg)
    }

    /** Drains the outbound queue at <= MAX_SUB_MSGS_PER_SEC to respect rate limits (spec #8). */
    private fun startOutboundPump() {
        pumpJob?.cancel()
        pumpJob = scope.launch {
            val minIntervalMs = 1000L / MAX_SUB_MSGS_PER_SEC
            for (msg in outboundQueue) {
                val ws = webSocket
                if (ws != null && _connectionState.value == ConnectionState.CONNECTED) {
                    if (!ws.send(msg.toString())) {
                        onSystemEvent("SUBSCRIPTION_ERROR", "PumpPortal rejected outbound message: ${msg.optString("method", "unknown")}")
                    }
                } else {
                    // Not connected - re-enqueue and wait; avoids dropping subscriptions.
                    delay(500)
                    outboundQueue.trySend(msg)
                }
                delay(minIntervalMs)
            }
        }
    }

    private fun startMonitor() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                delay(1000)
                val last = _lastEventAtMs.value
                if (_connectionState.value == ConnectionState.CONNECTED && last != null &&
                    System.currentTimeMillis() - last > STALE_THRESHOLD_MS
                ) {
                    _connectionState.value = ConnectionState.DEGRADED
                    onSystemEvent("CONNECTION", "Stream stale for >${STALE_THRESHOLD_MS / 1000}s, marked DEGRADED")
                }

                val now = System.currentTimeMillis()
                if (now - eventCounterWindowStart >= 1000) {
                    _eventsPerSecond.value = eventCounterCount
                    eventCounterCount = 0
                    eventCounterWindowStart = now
                }
            }
        }
    }

    private fun connect() {
        if (manuallyStopped) return
        val generation = ++socketGeneration
        val apiKey = getApiKey()
        // Build the wss URL as a string. HttpUrl is an HTTP URL builder and can
        // throw at runtime for websocket schemes on some OkHttp versions.
        val url = apiKey?.takeIf { it.isNotBlank() }?.let {
            "$BASE_URL?api-key=${Uri.encode(it)}"
        } ?: BASE_URL
        _connectionState.value = if (reconnectAttempt == 0) ConnectionState.CONNECTING else ConnectionState.RECONNECTING

        val request = Request.Builder().url(url).build()
        val pingSentAt = longArrayOf(0L)

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                if (generation != socketGeneration || manuallyStopped) {
                    ws.close(1000, "superseded connection")
                    return
                }
                _connectionState.value = ConnectionState.CONNECTED
                _activeFeedSubscriptions.value = if (subscribedNewToken) 1 else 0
                reconnectAttempt = 0
                reconnectJob = null
                onSystemEvent("CONNECTION", "WebSocket connected")
                restoreSubscriptions()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                val now = System.currentTimeMillis()
                _lastEventAtMs.value = now
                eventCounterCount++
                if (_connectionState.value == ConnectionState.DEGRADED) {
                    _connectionState.value = ConnectionState.CONNECTED
                }
                pingSentAt[0].takeIf { it > 0 }?.let { _latencyMs.value = now - it }

                val result = PumpPortalEventParser.parse(text, now)
                if (result is ParseResult.Malformed) {
                    _parserErrorCount.value += 1
                    onSystemEvent("PARSER_ERROR", "Malformed message: ${result.error}")
                }
                onEvent(result, now)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (!manuallyStopped && generation == socketGeneration) {
                    onSystemEvent("CONNECTION", "WebSocket closed ($code): ${reason.ifBlank { "no reason" }}")
                    scheduleReconnect()
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (generation != socketGeneration || manuallyStopped) return
                onSystemEvent("CONNECTION", "WebSocket failure: ${t.message ?: t.javaClass.simpleName}")
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (manuallyStopped || reconnectJob?.isActive == true) return
        _connectionState.value = ConnectionState.RECONNECTING
        _reconnectCount.value += 1
        val backoff = min(MAX_BACKOFF_MS, (1000L * 2.0.pow(reconnectAttempt)).toLong())
        reconnectAttempt++
        reconnectJob = scope.launch {
            delay(backoff)
            reconnectJob = null
            if (!manuallyStopped) connect()
        }
    }

    private fun restoreSubscriptions() {
        // spec #43: after reconnect, resubscribe to new-token/migration streams and
        // restore all active per-token/account subscriptions. Missed events during
        // the gap are NOT assumed received - callers should treat resumed tracking
        // as having a potential data gap.
        if (subscribedNewToken) enqueue(JSONObject().put("method", "subscribeNewToken"))
        onSystemEvent("CONNECTION", "Restored new-token discovery after reconnect")
    }
}
