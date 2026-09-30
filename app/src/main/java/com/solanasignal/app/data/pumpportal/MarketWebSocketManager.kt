package com.solanasignal.app.data.pumpportal

import android.net.Uri
import com.solanasignal.app.data.telemetry.MarketFeedProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

/** One socket for the selected provider; source identity is supplied on every callback. */
class MarketWebSocketManager(
    val provider: MarketFeedProvider,
    private val getApiKey: () -> String?,
    private val onEvent: (MarketFeedProvider, ParseResult, Long) -> Unit,
    private val onSystemEvent: (MarketFeedProvider, String, String) -> Unit
) {
    companion object {
        private const val MAX_SUB_MSGS_PER_SEC = 200
        private const val STALE_THRESHOLD_MS = 30_000L
        private const val MAX_BACKOFF_MS = 60_000L
    }

    private val client = OkHttpClient.Builder().pingInterval(15, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
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

    private var subscribedNewToken = false
    private var subscribedMigration = false
    private val subscribedTokenMints = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val confirmedPumpDevMints = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val sentThisConnection = mutableSetOf<String>()
    private val outboundQueue = Channel<JSONObject>(capacity = Channel.UNLIMITED)
    private var pumpJob: Job? = null
    private var monitorJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    @Volatile private var manuallyStopped = true
    private var socketGeneration = 0L
    private var eventCounterWindowStart = System.currentTimeMillis()
    private var eventCounterCount = 0
    @Volatile private var quietWarningLogged = false

    val activeTokenSubscriptions: Int get() = activeTokenMints.size
    val activeTokenMints: Set<String> get() =
        if (provider == MarketFeedProvider.PUMPDEV) confirmedPumpDevMints.toSet() else subscribedTokenMints.toSet()
    val requestedTokenMints: Set<String> get() = subscribedTokenMints.toSet()

    @Synchronized
    fun start() {
        if (!manuallyStopped) return
        manuallyStopped = false
        connect()
        startOutboundPump()
        startMonitor()
    }

    @Synchronized
    fun stop() {
        manuallyStopped = true
        pumpJob?.cancel(); pumpJob = null
        monitorJob?.cancel(); monitorJob = null
        reconnectJob?.cancel(); reconnectJob = null
        webSocket?.close(1000, "scanner stopped")
        webSocket = null
        confirmedPumpDevMints.clear()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    @Synchronized
    fun reconnectNow() {
        if (manuallyStopped) return
        val oldSocket = webSocket
        socketGeneration++
        reconnectJob?.cancel(); reconnectJob = null
        reconnectAttempt = 0
        webSocket = null
        _connectionState.value = ConnectionState.RECONNECTING
        oldSocket?.close(1000, "credentials or provider settings changed")
        connect()
    }

    fun subscribeNewToken() {
        if (subscribedNewToken) return
        subscribedNewToken = true
        enqueue(JSONObject().put("method", "subscribeNewToken"))
    }

    fun subscribeMigrations() {
        if (provider != MarketFeedProvider.PUMPPORTAL || subscribedMigration) return
        subscribedMigration = true
        enqueue(JSONObject().put("method", "subscribeMigration"))
    }

    fun subscribeTokenTrades(mints: Collection<String>) {
        val current = subscribedTokenMints.size
        val limit = provider.maxLiveTokenSubscriptions
        val room = limit?.let { (it - current).coerceAtLeast(0) } ?: Int.MAX_VALUE
        val distinct = mints.asSequence().filter { it.isNotBlank() }.distinct().filter { it !in subscribedTokenMints }.toList()
        val added = distinct.take(room).filter { subscribedTokenMints.add(it) }
        if (added.isNotEmpty()) enqueue(tokenKeysMessage("subscribeTokenTrade", added))
        if (added.size < distinct.size) {
            onSystemEvent(provider, "SUBSCRIPTION_LIMIT", "${provider.displayName} accepted ${added.size} of ${distinct.size} requested token subscriptions; active limit=${limit ?: "provider-managed"}")
        }
    }

    fun unsubscribeTokenTrades(mints: Collection<String>) {
        val removed = mints.asSequence().filter { subscribedTokenMints.remove(it) }.toList()
        confirmedPumpDevMints.removeAll(removed.toSet())
        if (removed.isNotEmpty()) enqueue(tokenKeysMessage("unsubscribeTokenTrade", removed))
    }

    fun confirmObservedTokenTrade(mint: String) {
        if (provider == MarketFeedProvider.PUMPDEV && mint in subscribedTokenMints) {
            confirmedPumpDevMints.add(mint)
        }
    }

    private fun tokenKeysMessage(method: String, mints: Collection<String>) =
        JSONObject().put("method", method).put("keys", JSONArray(mints))

    private fun enqueue(message: JSONObject) { outboundQueue.trySend(message) }

    private fun startOutboundPump() {
        pumpJob?.cancel()
        pumpJob = scope.launch {
            val minIntervalMs = 1000L / MAX_SUB_MSGS_PER_SEC
            for (message in outboundQueue) {
                val ws = webSocket
                if (ws != null && _connectionState.value == ConnectionState.CONNECTED) {
                    val prepared = prepareForCurrentConnection(message) ?: continue
                    val method = prepared.optString("method")
                    val tokenKeys = prepared.optJSONArray("keys")?.let { array ->
                        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
                    }.orEmpty()
                    if (method == "subscribeTokenTrade" && provider == MarketFeedProvider.PUMPDEV) {
                        confirmedPumpDevMints.removeAll(tokenKeys.toSet())
                    }
                    if (!ws.send(prepared.toString())) {
                        releasePreparedMessage(prepared)
                        onSystemEvent(provider, "SUBSCRIPTION_ERROR", "WebSocket rejected outbound ${message.optString("method", "unknown")} command")
                        outboundQueue.trySend(message)
                    } else {
                        if (method == "unsubscribeTokenTrade") confirmedPumpDevMints.removeAll(tokenKeys.toSet())
                        if (method in setOf("subscribeNewToken", "subscribeMigration", "subscribeTokenTrade", "unsubscribeTokenTrade")) {
                            onSystemEvent(provider, "SUBSCRIPTION", "$method sent for ${tokenKeys.size} token key(s)")
                        }
                    }
                } else {
                    delay(500)
                    outboundQueue.trySend(message)
                }
                delay(minIntervalMs)
            }
        }
    }

    private fun startMonitor() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                delay(1_000)
                val last = _lastEventAtMs.value
                if (_connectionState.value == ConnectionState.CONNECTED && last != null && System.currentTimeMillis() - last > STALE_THRESHOLD_MS && !quietWarningLogged) {
                    quietWarningLogged = true
                    onSystemEvent(provider, "STREAM_QUIET", "No provider frames for more than ${STALE_THRESHOLD_MS / 1_000}s; websocket remains connected, quiet markets are possible")
                }
                val now = System.currentTimeMillis()
                if (now - eventCounterWindowStart >= 1_000) {
                    _eventsPerSecond.value = synchronized(this@MarketWebSocketManager) { eventCounterCount.also { eventCounterCount = 0 } }
                    eventCounterWindowStart = now
                }
            }
        }
    }

    private fun connect() {
        if (manuallyStopped) return
        val generation = ++socketGeneration
        val url = when (provider) {
            MarketFeedProvider.PUMPPORTAL -> getApiKey()?.takeIf { it.isNotBlank() }
                ?.let { "${provider.websocketUrl}?api-key=${Uri.encode(it)}" } ?: provider.websocketUrl
            MarketFeedProvider.PUMPDEV -> provider.websocketUrl
        }
        _connectionState.value = if (reconnectAttempt == 0) ConnectionState.CONNECTING else ConnectionState.RECONNECTING
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                if (generation != socketGeneration || manuallyStopped) { ws.close(1000, "superseded connection"); return }
                synchronized(this@MarketWebSocketManager) { sentThisConnection.clear() }
                confirmedPumpDevMints.clear()
                quietWarningLogged = false
                _connectionState.value = ConnectionState.CONNECTED
                reconnectAttempt = 0
                reconnectJob = null
                onSystemEvent(provider, "CONNECTION", "${provider.displayName} WebSocket connected")
                restoreSubscriptions()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                val now = System.currentTimeMillis()
                _lastEventAtMs.value = now
                quietWarningLogged = false
                synchronized(this@MarketWebSocketManager) { eventCounterCount++ }
                val result = when (provider) {
                    MarketFeedProvider.PUMPPORTAL -> PumpPortalEventParser.parse(text, now)
                    MarketFeedProvider.PUMPDEV -> PumpDevEventParser.parse(text, now)
                }
                if (result is ParseResult.Control) {
                    handleControlFrame(result, now)
                } else if (_connectionState.value == ConnectionState.DEGRADED) {
                    // A real market frame is evidence that the upstream feed recovered.
                    _connectionState.value = ConnectionState.CONNECTED
                }
                if (result is ParseResult.Malformed) {
                    _parserErrorCount.value += 1
                    onSystemEvent(provider, "PARSER_ERROR", "Malformed provider frame (${result.error})")
                }
                if (result is ParseResult.Unknown) {
                    val obj = runCatching { JSONObject(text) }.getOrNull()
                    if (obj?.optString("type") == "error") {
                        onSystemEvent(provider, "PROVIDER_ERROR", "Provider error ${obj.optString("code", "UNKNOWN")}")
                    }
                }
                onEvent(provider, result, now)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) { ws.close(1000, null) }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (!manuallyStopped && generation == socketGeneration) {
                    onSystemEvent(provider, "CONNECTION", "WebSocket closed ($code)")
                    scheduleReconnect()
                }
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (generation != socketGeneration || manuallyStopped) return
                onSystemEvent(provider, "CONNECTION", "WebSocket failure (${t.javaClass.simpleName})")
                scheduleReconnect()
            }
        })
    }

    private fun handleControlFrame(control: ParseResult.Control, receivedAtMs: Long) {
        val obj = runCatching { JSONObject(control.raw) }.getOrNull() ?: return
        when (control.controlType) {
            "connectionStatus" -> {
                val connected = obj.optBoolean("connected", true)
                _connectionState.value = if (connected) ConnectionState.CONNECTED else ConnectionState.DEGRADED
                onSystemEvent(provider, "UPSTREAM_STATUS", "${provider.displayName} upstream connected=$connected")
            }
            "connected" -> {
                _connectionState.value = ConnectionState.CONNECTED
                onSystemEvent(provider, "CONNECTION", "${provider.displayName} provider handshake received")
            }
            "subscribed" -> {
                if (provider == MarketFeedProvider.PUMPDEV && obj.optString("method") == "subscribeTokenTrade") {
                    val accepted = obj.optJSONArray("keys")?.let { array ->
                        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
                    }.orEmpty().filter { it in subscribedTokenMints }
                    val desired = subscribedTokenMints.toSet()
                    val rejected = desired - accepted.toSet()
                    confirmedPumpDevMints.removeAll(desired)
                    confirmedPumpDevMints.addAll(accepted)
                    subscribedTokenMints.removeAll(rejected)
                    onSystemEvent(provider, if (rejected.isEmpty()) "SUBSCRIPTION_ACK" else "SUBSCRIPTION_REJECTED",
                        "PumpDev confirmed ${accepted.size} token subscriptions; rejected ${rejected.size}")
                }
            }
            "error" -> onSystemEvent(provider, "PROVIDER_ERROR", "Provider control error ${obj.optString("code", "UNKNOWN")}")
            else -> onSystemEvent(provider, "PROVIDER_CONTROL", "${provider.displayName} control frame: ${control.controlType}")
        }
        _lastEventAtMs.value = receivedAtMs
    }

    private fun scheduleReconnect() {
        if (manuallyStopped || reconnectJob?.isActive == true) return
        _connectionState.value = ConnectionState.RECONNECTING
        _reconnectCount.value += 1
        val backoff = min(MAX_BACKOFF_MS, (1_000.0 * 2.0.pow(reconnectAttempt)).toLong())
        reconnectAttempt++
        onSystemEvent(provider, "RECONNECT", "Reconnect attempt ${reconnectAttempt} scheduled after ${backoff}ms")
        reconnectJob = scope.launch {
            delay(backoff)
            reconnectJob = null
            if (!manuallyStopped) connect()
        }
    }

    private fun restoreSubscriptions() {
        if (subscribedNewToken) enqueue(JSONObject().put("method", "subscribeNewToken"))
        if (subscribedMigration) enqueue(JSONObject().put("method", "subscribeMigration"))
        val keys = subscribedTokenMints.toList()
        val chunkSize = provider.maxLiveTokenSubscriptions ?: 1_000
        keys.chunked(chunkSize.coerceAtLeast(1)).filter { it.isNotEmpty() }
            .forEach { enqueue(tokenKeysMessage("subscribeTokenTrade", it)) }
        onSystemEvent(provider, "CONNECTION", "Restoring ${provider.displayName} subscriptions after reconnect; coverage restarts at this point")
    }

    @Synchronized
    private fun prepareForCurrentConnection(message: JSONObject): JSONObject? {
        val method = message.optString("method")
        if (method == "subscribeNewToken" || method == "subscribeMigration") {
            val desired = if (method == "subscribeNewToken") subscribedNewToken else subscribedMigration
            if (!desired || !sentThisConnection.add("subscribe:$method")) return null
            return message
        }
        if (method != "subscribeTokenTrade" && method != "unsubscribeTokenTrade") return message
        val keys = message.optJSONArray("keys") ?: return null
        val accepted = JSONArray()
        for (index in 0 until keys.length()) {
            val mint = keys.optString(index).takeIf { it.isNotBlank() } ?: continue
            val key = if (method == "subscribeTokenTrade") "subscribe:$mint" else "unsubscribe:$mint"
            val currentlyDesired = mint in subscribedTokenMints
            val shouldSend = if (method == "subscribeTokenTrade") currentlyDesired else !currentlyDesired && "subscribe:$mint" in sentThisConnection
            if (shouldSend && sentThisConnection.add(key)) accepted.put(mint)
        }
        if (accepted.length() == 0) return null
        return JSONObject().put("method", method).put("keys", accepted)
    }

    @Synchronized
    private fun releasePreparedMessage(message: JSONObject) {
        val method = message.optString("method")
        if (method == "subscribeNewToken" || method == "subscribeMigration") sentThisConnection.remove("subscribe:$method")
        val keys = message.optJSONArray("keys") ?: return
        val prefix = if (method == "subscribeTokenTrade") "subscribe:" else "unsubscribe:"
        for (index in 0 until keys.length()) sentThisConnection.remove(prefix + keys.optString(index))
    }
}
