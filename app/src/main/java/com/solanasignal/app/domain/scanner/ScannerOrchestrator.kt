package com.solanasignal.app.domain.scanner

import android.content.Context
import com.solanasignal.app.data.market.LiveMarketStateRepository
import com.solanasignal.app.data.market.LiveTradeTick
import com.solanasignal.app.data.market.MarketDataSource
import com.solanasignal.app.data.codecraft.CodeCraftClient
import com.solanasignal.app.data.dexscreener.DexScreenerClient
import com.solanasignal.app.data.dexscreener.DexScreenerPairInfo
import com.solanasignal.app.data.pumpportal.*
import com.solanasignal.app.data.room.AppDatabase
import com.solanasignal.app.data.room.SignalOutcomeRecorder
import com.solanasignal.app.data.room.entities.*
import com.solanasignal.app.data.settings.BatteryMode
import com.solanasignal.app.data.settings.SettingsRepository
import com.solanasignal.app.data.telemetry.DiagnosticSeverity
import com.solanasignal.app.data.telemetry.DiagnosticJson
import com.solanasignal.app.data.telemetry.MarketFeedProvider
import com.solanasignal.app.data.telemetry.StructuredTelemetryLogger
import com.solanasignal.app.data.telemetry.DiagnosticWindowAccumulator
import com.solanasignal.app.domain.metrics.MetricsEngine
import com.solanasignal.app.domain.mc.McObservation
import com.solanasignal.app.domain.mc.McTrendPressureEngine
import com.solanasignal.app.domain.evidence.SignalEvidenceEngine
import com.solanasignal.app.domain.momentum.MomentumEngine
import com.solanasignal.app.domain.manipulation.ManipulationRiskEngine
import com.solanasignal.app.domain.safety.SafetyEngine
import com.solanasignal.app.domain.scoring.ScoringEngine
import com.solanasignal.app.domain.signals.SignalEngine
import com.solanasignal.app.domain.signals.SignalType
import com.solanasignal.app.domain.signals.SignalLifecycleEngine
import com.solanasignal.app.domain.signals.SignalLifecycleState
import com.solanasignal.app.notifications.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

/**
 * Wires the full pipeline: selected market provider -> WebSocketManager -> token/trade
 * Streams -> Metrics -> Safety -> Momentum Score -> Signal Engine -> Android Alert
 * -> (user) -> Photon. This class does not execute trades.
 *
 * Providers report source-specific SOL/quote values. SolPriceProvider supplies a live SOL/USD rate so the app can show
 * the USD figures the spec's filters/UI are defined in; until a price has been
 * fetched at least once, USD fields stay null/UNKNOWN rather than being guessed.
 */
class ScannerOrchestrator(
    private val context: Context,
    private val settings: SettingsRepository = SettingsRepository.get(context),
    private val marketStates: LiveMarketStateRepository = LiveMarketStateRepository(),
    private val telemetry: StructuredTelemetryLogger
) {
    private val db = AppDatabase.get(context)
    private val signalOutcomeRecorder = SignalOutcomeRecorder()
    private val metricsEngine = MetricsEngine()
    private val safetyEngine = SafetyEngine()
    private val scoringEngine = ScoringEngine()
    private val signalEngine = SignalEngine()
    private val momentumEngine = MomentumEngine()
    private val manipulationRiskEngine = ManipulationRiskEngine()
    private val signalEvidenceEngine = SignalEvidenceEngine()
    private val mcTrendPressureEngine = McTrendPressureEngine()
    private val signalLifecycleEngine = SignalLifecycleEngine()
    private val solPriceProvider = SolPriceProvider()
    private val dexScreenerClient = DexScreenerClient()
    private val codeCraftClient = CodeCraftClient()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var mockSource: MockEventSource? = null
    private var webSocketManager: MarketWebSocketManager? = null
    private var managerObservationJob: Job? = null
    private val diagnosticWindows = DiagnosticWindowAccumulator()
    val diagnosticWindowMetrics get() = diagnosticWindows
    private var dexScreenerJob: Job? = null
    private var marketStatusJob: Job? = null

    private val lastScoreByMint = ConcurrentHashMap<String, Int>()
    private val marketCapHistoryByMint = ConcurrentHashMap<String, MutableList<Pair<Long, Double>>>()
    private val trackedMints = ConcurrentHashMap.newKeySet<String>()
    private val lastActivityByMint = ConcurrentHashMap<String, Long>()
    private val lastAiAnalysisByMint = ConcurrentHashMap<String, Long>()
    private var evictionJob: Job? = null

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    // IMPORTANT: these must be single, stable StateFlow instances that live for the
    // lifetime of the orchestrator. Earlier this was `get() = webSocketManager?.x`,
    // which returns a *different* flow object depending on whether a manager
    // currently exists. A collector that reads the property once (e.g. a ViewModel
    // doing `val connectionState = orchestrator.connectionState` at construction
    // time, before start() has ever run) would lock onto a dead fallback flow
    // forever and never see real updates - exactly the "shows DISCONNECTED while
    // actually connected and receiving events" bug. Forwarding into a fixed
    // MutableStateFlow avoids that regardless of when/how callers subscribe.
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _reconnectCount = MutableStateFlow(0)
    val reconnectCount: StateFlow<Int> = _reconnectCount.asStateFlow()

    private val _parserErrorCount = MutableStateFlow(0)
    val parserErrorCount: StateFlow<Int> = _parserErrorCount.asStateFlow()

    private val _eventsPerSecond = MutableStateFlow(0)
    val eventsPerSecond: StateFlow<Int> = _eventsPerSecond.asStateFlow()

    private val _trackedSubscriptionCount = MutableStateFlow(0)
    val trackedSubscriptionCount: StateFlow<Int> = _trackedSubscriptionCount.asStateFlow()

    private val _dexScreenerEnrichedCount = MutableStateFlow(0)
    val dexScreenerEnrichedCount: StateFlow<Int> = _dexScreenerEnrichedCount.asStateFlow()

    private val _tradesReceivedCount = MutableStateFlow(0)
    val tradesReceivedCount: StateFlow<Int> = _tradesReceivedCount.asStateFlow()

    val solUsdPrice: StateFlow<Double?> get() = solPriceProvider.priceUsd

    fun start() {
        if (_running.value) return
        _running.value = true
        try {
            startMarketStatusTicker()
            solPriceProvider.start(scope)
            if (settings.mockMode.value) startMock() else startLive()
            startDexScreenerEnrichment()
            startStaleTokenEviction()
        } catch (e: Exception) {
            _running.value = false
            _connectionState.value = ConnectionState.DISCONNECTED
            scope.launch {
                logSystemEvent("STARTUP_ERROR", "Scanner could not start: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun stop() {
        _running.value = false
        mockSource?.stop()
        webSocketManager?.stop()
        webSocketManager = null
        managerObservationJob?.cancel(); managerObservationJob = null
        solPriceProvider.stop()
        dexScreenerJob?.cancel()
        evictionJob?.cancel()
        marketStatusJob?.cancel()
        _connectionState.value = ConnectionState.DISCONNECTED
        marketStates.setConnectionState(ConnectionState.DISCONNECTED)
        diagnosticWindows.endCoverage(trackedMints.toList(), System.currentTimeMillis())
    }

    fun refreshMarketFeedConnection() {
        if (!_running.value) return
        val now = System.currentTimeMillis()
        val activeMints = webSocketManager?.activeTokenMints.orEmpty()
        diagnosticWindows.endCoverage(activeMints, now)
        marketStates.endTradeTracking(activeMints, now)
        managerObservationJob?.cancel(); managerObservationJob = null
        webSocketManager?.stop()
        webSocketManager = null
        mockSource?.stop(); mockSource = null
        if (settings.mockMode.value) startMock() else {
            solPriceProvider.start(scope)
            startLive()
        }
        startDexScreenerEnrichment()
    }

    /**
     * BUG FIX: trackedMints previously only ever grew, up to maxTrackedForBatteryMode()
     * (e.g. 60 in Balanced mode), and then permanently stopped accepting new tokens -
     * the app would get stuck watching the same first-60 tokens forever, most of which
     * go dead within minutes, while every newer (possibly actually pumping) token was
     * silently ignored. Every 60s, drop any tracked mint with no trade activity in the
     * last 5 minutes, unsubscribe it, and free its slot for a fresher token.
     */
    private fun startStaleTokenEviction() {
        evictionJob?.cancel()
        evictionJob = scope.launch {
            while (isActive) {
                delay(60_000)
                val staleCutoff = System.currentTimeMillis() - 5 * 60_000
                val openPaperMints = db.paperTradingDao().openPositionMints().toSet()
                val stale = trackedMints.filter { mint ->
                    val last = lastActivityByMint[mint]
                    mint !in openPaperMints && (last == null || last < staleCutoff)
                }
                if (stale.isEmpty()) continue
                diagnosticWindows.endCoverage(stale, System.currentTimeMillis())
                stale.forEach { mint ->
                    trackedMints.remove(mint)
                    lastActivityByMint.remove(mint)
                    metricsEngine.dropToken(mint)
                }
                webSocketManager?.unsubscribeTokenTrades(stale)
                stale.forEach(marketStates::markRemoved)
                _trackedSubscriptionCount.value = trackedMints.size
                logSystemEvent("SUBSCRIPTION", "Evicted ${stale.size} stale token(s) with no trades in 5m, freeing slots")
            }
        }
    }

    /** Refreshes freshness on a local cadence; it never polls or fabricates a market tick. */
    private fun startMarketStatusTicker() {
        marketStatusJob?.cancel()
        marketStatusJob = scope.launch {
            var previouslyStale = emptySet<String>()
            while (isActive) {
                val now = System.currentTimeMillis()
                marketStates.refreshStatuses(now)
                val source = if (settings.mockMode.value) "MOCK" else settings.marketFeedProvider.value.sourceId
                val states = marketStates.states.value.values
                val staleStates = states.filter { it.status == com.solanasignal.app.data.market.MarketDataStatus.STALE }.associateBy { it.mint }
                val staleNow = staleStates.keys
                (staleNow - previouslyStale).forEach { mint ->
                    val state = staleStates[mint] ?: return@forEach
                    telemetry.record(
                        component = "DATA_QUALITY", eventType = "STALE_MARKET_DATA", severity = DiagnosticSeverity.WARN,
                        message = "Market quote became stale", tokenAddress = mint, dataSource = state.priceSource.name,
                        receivedAtMs = now,
                        metadata = mapOf("status" to state.status.name, "priceUsd" to state.priceUsd,
                            "priceSource" to state.priceSource.name, "lastEventAtMs" to state.lastTradeAtMs,
                            "lastEventReceivedAtMs" to state.lastTradeReceivedAtMs,
                            "staleAgeMs" to state.priceReceivedAtMs?.let { now - it })
                    )
                }
                (previouslyStale - staleNow).forEach { mint ->
                    telemetry.record(
                        component = "DATA_QUALITY", eventType = "MARKET_DATA_FRESH", severity = DiagnosticSeverity.INFO,
                        message = "Market quote freshness recovered", tokenAddress = mint, dataSource = source,
                        receivedAtMs = now
                    )
                }
                previouslyStale = staleNow
                telemetry.updateRuntime(
                    dataSource = source,
                    connectionState = _connectionState.value.name,
                    activeTokens = trackedMints.size,
                    subscriptions = webSocketManager?.activeTokenSubscriptions ?: 0,
                    staleTokens = states.count { it.status == com.solanasignal.app.data.market.MarketDataStatus.STALE }
                )
                delay(2_000L)
            }
        }
    }

    /**
     * DexScreener enrichment (free, keyless): every 20s, batch-fetch the tokens we're
     * currently tracking and overwrite our PumpPortal/SOL-derived MC & liquidity
     * estimates with DexScreener's own USD figures once a token is indexed there -
     * and, importantly, pick up its real LP pool address for the Photon deep link
     * (works post-migration too, unlike PumpPortal's pre-migration bondingCurveKey).
     * Skipped entirely in Mock Mode - there's nothing real to look up.
     */
    private fun startDexScreenerEnrichment() {
        dexScreenerJob?.cancel()
        if (settings.mockMode.value) return
        dexScreenerJob = scope.launch {
            // First pass is deliberately short so a newly discovered token can be
            // enriched as soon as DexScreener indexes it; later passes are batched.
            delay(3_000)
            while (isActive) {
                val mints = trackedMints.toList()
                if (mints.isNotEmpty()) {
                    try {
                        val pairs = dexScreenerClient.fetchBestPairs(mints)
                        var enrichedCount = 0
                        pairs.forEach { (mint, info) ->
                            val token = db.tokenDao().getByMint(mint) ?: return@forEach
                            val dataQuality = calculateDataQuality(info)
                            val enrichedToken = token.copy(
                                    poolAddress = info.pairAddress,
                                    dexId = info.dexId,
                                    dexUrl = info.url,
                                    marketCapUsd = info.marketCapUsd ?: token.marketCapUsd,
                                    liquidityUsd = info.liquidityUsd ?: token.liquidityUsd,
                                    lastPriceUsd = info.priceUsd ?: token.lastPriceUsd,
                                    dexPairCreatedAtEpochMs = info.pairCreatedAtEpochMs,
                                    dexVolume5mUsd = info.volume5mUsd,
                                    dexVolume1hUsd = info.volume1hUsd,
                                    dexVolume6hUsd = info.volume6hUsd,
                                    dexVolume24hUsd = info.volume24hUsd,
                                    dexBuys5m = info.buys5m,
                                    dexSells5m = info.sells5m,
                                    dexBuys1h = info.buys1h,
                                    dexSells1h = info.sells1h,
                                    dexPriceChange5mPct = info.priceChange5mPct,
                                    dexPriceChange1hPct = info.priceChange1hPct,
                                    dexPriceChange6hPct = info.priceChange6hPct,
                                    dexPriceChange24hPct = info.priceChange24hPct,
                                    dexFdVUsd = info.fdvUsd,
                                    dexLiquidityBase = info.liquidityBase,
                                    dexLiquidityQuote = info.liquidityQuote,
                                    dexActiveBoosts = info.activeBoosts,
                                    dexImageUrl = info.imageUrl,
                                    dexDescription = info.description,
                                    dexWebsitesJson = info.websitesJson,
                                    dexSocialsJson = info.socialsJson,
                                    dataQualityScore = dataQuality.first,
                                    dataQualityLabel = dataQuality.second
                                )
                            db.tokenDao().upsert(enrichedToken)
                            marketStates.updateRestSnapshot(
                                mint = mint,
                                symbol = enrichedToken.symbol,
                                name = enrichedToken.name,
                                priceUsd = info.priceUsd,
                                marketCapUsd = info.marketCapUsd,
                                liquidityUsd = info.liquidityUsd,
                                volumeUsd = info.volume5mUsd,
                                nowMs = System.currentTimeMillis()
                            )
                            analyzeDexAndMaybeSignal(enrichedToken, info)
                            enrichedCount++
                        }
                        _dexScreenerEnrichedCount.value = enrichedCount
                    } catch (e: Exception) {
                        logSystemEvent("DEXSCREENER", "Enrichment batch failed: ${e.message}")
                    }
                }
                delay(20_000)
            }
        }
    }

    private fun startLive() {
        val provider = settings.marketFeedProvider.value
        val manager = MarketWebSocketManager(
            provider = provider,
            getApiKey = { if (provider.requiresApiKey) settings.getApiKeyOrNull() else null },
            onEvent = { source, result, nowMs -> scope.launch { handleParseResult(source, result, nowMs) } },
            onSystemEvent = { source, category, message ->
                logSystemEvent(category, message, source.sourceId)
            }
        )
        webSocketManager = manager
        manager.start()
        manager.subscribeNewToken()
        manager.subscribeMigrations()

        managerObservationJob?.cancel()
        managerObservationJob = scope.launch {
            launch {
            var previousState: ConnectionState? = null
            manager.connectionState.collect { state ->
                _connectionState.value = state
                val now = System.currentTimeMillis()
                marketStates.setConnectionState(state, now)
                if (previousState != state) telemetry.record(
                    component = "MARKET_FEED", eventType = "CONNECTION_STATE",
                    severity = if (state in setOf(ConnectionState.DEGRADED, ConnectionState.DISCONNECTED)) DiagnosticSeverity.WARN else DiagnosticSeverity.INFO,
                    message = "Selected provider WebSocket state changed", dataSource = provider.sourceId,
                    receivedAtMs = now,
                    metadata = mapOf("previousState" to previousState?.name, "currentState" to state.name,
                        "reconnectCount" to manager.reconnectCount.value)
                )
                if (state == ConnectionState.CONNECTED && previousState != ConnectionState.CONNECTED && liveStreamReady(provider)) {
                    val requested = trackedMints.toList()
                    manager.subscribeTokenTrades(requested)
                    val active = manager.activeTokenMints
                    marketStates.beginTradeTracking(active, now)
                    diagnosticWindows.beginCoverage(active, now)
                    active.forEach(metricsEngine::dropToken)
                } else if (state != ConnectionState.CONNECTED && previousState == ConnectionState.CONNECTED) {
                    val active = manager.activeTokenMints
                    diagnosticWindows.endCoverage(active, now)
                    marketStates.endTradeTracking(active, now)
                    active.forEach(metricsEngine::dropToken)
                }
                previousState = state
            }
            }
            launch { manager.reconnectCount.collect { _reconnectCount.value = it } }
            launch { manager.parserErrorCount.collect { _parserErrorCount.value = it } }
            launch { manager.eventsPerSecond.collect { _eventsPerSecond.value = it } }
            launch {
                settings.liveTradeStreamingEnabled.collectLatest { enabled ->
                    val active = trackedMints.toList()
                    if (enabled && liveStreamReady(provider)) {
                        manager.subscribeTokenTrades(active)
                        val subscribed = manager.activeTokenMints
                        if (manager.connectionState.value == ConnectionState.CONNECTED) {
                            marketStates.beginTradeTracking(subscribed)
                            diagnosticWindows.beginCoverage(subscribed, System.currentTimeMillis())
                        }
                        subscribed.forEach(metricsEngine::dropToken)
                    } else {
                        val subscribed = manager.requestedTokenMints
                        manager.unsubscribeTokenTrades(subscribed)
                        marketStates.endTradeTracking(subscribed)
                        diagnosticWindows.endCoverage(subscribed, System.currentTimeMillis())
                        subscribed.forEach(metricsEngine::dropToken)
                    }
                    _trackedSubscriptionCount.value = manager.activeTokenSubscriptions
                }
            }
        }
    }

    private fun liveStreamReady(provider: MarketFeedProvider): Boolean =
        settings.liveTradeStreamingEnabled.value && (!provider.requiresApiKey || settings.getApiKeyOrNull() != null) && !settings.mockMode.value

    private fun startMock() {
        // Mock mode fabricates a fake SOL price too, so USD figures aren't stuck on
        // UNKNOWN while demoing - clearly labeled MOCK MODE in the UI regardless.
        solPriceProvider.stop()
        // No real socket in mock mode - report CONNECTED so the status screen
        // doesn't falsely suggest something is broken while simulating data.
        _connectionState.value = ConnectionState.CONNECTED
        marketStates.setConnectionState(ConnectionState.CONNECTED)
        val source = MockEventSource(
            onTokenCreated = { event -> scope.launch { handleTokenCreated(event) } },
            onTrade = { event -> scope.launch { handleTrade(event) } }
        )
        mockSource = source
        source.start(scope)
    }

    private fun currentSolUsdPrice(): Double? = if (settings.mockMode.value) 180.0 else solPriceProvider.priceUsd.value

    private suspend fun handleParseResult(provider: MarketFeedProvider, result: ParseResult, nowMs: Long) {
        when (result) {
            is ParseResult.TokenCreated -> {
                val event = result.event
                val solUsd = currentSolUsdPrice()
                telemetry.recordMarketEvent(provider.sourceId, "TOKEN_CREATED", event.mint, event.sourceTimestampEpochMs, nowMs,
                    mapOf("name" to event.name, "symbol" to event.symbol, "creator" to event.creator, "uri" to event.uri,
                        "marketCapSol" to event.marketCapSol, "vSolInBondingCurve" to event.vSolInBondingCurve,
                        "vTokensInBondingCurve" to event.vTokensInBondingCurve,
                        "marketCapUsd" to usd(event.marketCapSol, solUsd), "liquidityUsd" to usd(event.vSolInBondingCurve, solUsd),
                        "solUsdRate" to solUsd), event.rawFrame)
                val missing = buildList {
                    if (event.marketCapSol == null) add("marketCapSol")
                    if (event.vSolInBondingCurve == null) add("liquidityOrQuoteReserve")
                    if (solUsd == null) add("solUsdRate")
                }
                if (missing.isNotEmpty()) telemetry.record(
                    component = "DATA_QUALITY", eventType = "MISSING_MARKET_FIELDS", severity = DiagnosticSeverity.WARN,
                    message = "Token discovery frame lacks optional market fields", tokenAddress = event.mint,
                    dataSource = provider.sourceId, eventTimestampMs = event.sourceTimestampEpochMs, receivedAtMs = nowMs,
                    metadata = mapOf("missingFields" to missing, "eventType" to "TOKEN_CREATED")
                )
                handleTokenCreated(event, provider)
            }
            is ParseResult.Migration -> {
                val event = result.event
                val replayFields = setOf("txType", "mint", "signature", "timestamp", "blockTime", "slot", "quoteMint",
                    "pairQuoteMint", "solAmount", "quoteAmount", "tokenAmount", "marketCapSol", "marketCapQuote",
                    "poolBaseReservesUi", "poolEffectiveQuoteReservesUi", "bondingCurveKey", "poolAddress")
                telemetry.recordMarketEvent(provider.sourceId, "MIGRATION", event.mint, event.sourceTimestampEpochMs, nowMs,
                    event.raw.filterKeys { it in replayFields }, event.rawFrame)
                handleMigration(event)
            }
            is ParseResult.Trade -> {
                val event = result.event
                val amountUsd = usd(event.solAmount, currentSolUsdPrice())
                val priceUsd = usd(event.priceSol, currentSolUsdPrice())
                telemetry.recordMarketEvent(provider.sourceId, "TRADE", event.mint, event.sourceTimestampEpochMs, nowMs,
                    mapOf("signature" to event.signature, "side" to event.side.name, "trader" to event.trader,
                        "solAmount" to event.solAmount, "tokenAmount" to event.tokenAmount, "amountUsd" to amountUsd,
                        "priceSol" to event.priceSol, "priceUsd" to priceUsd, "marketCapSol" to event.marketCapSol,
                        "marketCapUsd" to usd(event.marketCapSol, currentSolUsdPrice()),
                        "liquiditySol" to event.vSolInBondingCurve,
                        "liquidityUsd" to usd(event.vSolInBondingCurve, currentSolUsdPrice()),
                        "solUsdRate" to currentSolUsdPrice()), event.rawFrame)
                handleTrade(event, provider)
            }
            is ParseResult.Control -> {
                val obj = runCatching { org.json.JSONObject(result.raw) }.getOrNull()
                val method = obj?.optString("method")
                val keys = obj?.optJSONArray("keys")?.let { array ->
                    (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
                }.orEmpty()
                val controlEventType = if (result.controlType == "error") "PROVIDER_ERROR" else "PROVIDER_CONTROL"
                telemetry.recordMarketEvent(provider.sourceId, controlEventType, null, null, nowMs,
                    mapOf("controlType" to result.controlType, "method" to method, "keys" to keys,
                        "code" to obj?.optString("code")), result.raw)
                if (provider == MarketFeedProvider.PUMPDEV && result.controlType == "subscribed" && method == "subscribeTokenTrade") {
                    val active = webSocketManager?.activeTokenMints.orEmpty()
                    marketStates.beginTradeTracking(active, nowMs)
                    diagnosticWindows.beginCoverage(active, nowMs)
                    active.forEach(metricsEngine::dropToken)
                    _trackedSubscriptionCount.value = webSocketManager?.activeTokenSubscriptions ?: 0
                }
            }
            is ParseResult.Unknown -> {
                telemetry.recordMarketEvent(provider.sourceId, "UNKNOWN_EVENT", null, null, nowMs,
                    mapOf("providerType" to result.rawType, "rawFrameLength" to result.raw.length), result.raw)
                logSystemEvent("PARSER_UNKNOWN", "Unrecognized ${provider.displayName} frame (type=${result.rawType ?: "unknown"})")
            }
            is ParseResult.Malformed -> {
                telemetry.recordMarketEvent(provider.sourceId, "MALFORMED_EVENT", null, null, nowMs,
                    mapOf("error" to result.error, "rawFrameLength" to result.raw.length), result.raw)
            }
        }
    }

    // --- 9. TOKEN DISCOVERY -------------------------------------------------
    private suspend fun handleTokenCreated(event: NormalizedTokenCreatedEvent, provider: MarketFeedProvider? = null) {
        val sourceId = if (settings.mockMode.value) "MOCK" else (provider ?: settings.marketFeedProvider.value).sourceId
        val solPrice = currentSolUsdPrice()
        val token = TokenEntity(
            mint = event.mint,
            name = event.name,
            symbol = event.symbol,
            creator = event.creator,
            uri = event.uri,
            poolAddress = event.bondingCurveKey,
            createdAtEpochMs = event.createdAtEpochMs,
            firstSeenAtEpochMs = event.receivedAtEpochMs,
            marketCapSol = event.marketCapSol,
            liquiditySol = event.vSolInBondingCurve,
            marketCapUsd = usd(event.marketCapSol, solPrice),
            liquidityUsd = usd(event.vSolInBondingCurve, solPrice),
            lastPriceUsd = null,
            lifecycle = "NEW",
            source = sourceId.lowercase()
        )
        db.tokenDao().upsert(token)
        marketStates.updateDiscovery(
            mint = event.mint,
            symbol = event.symbol,
            name = event.name,
            marketCapUsd = token.marketCapUsd,
            liquidityUsd = token.liquidityUsd,
            nowMs = event.receivedAtEpochMs
        )
        recordMarketCap(event.mint, event.receivedAtEpochMs, token.marketCapUsd)

        val maxTracked = maxTrackedForBatteryMode()
        if (trackedMints.size < maxTracked) {
            trackedMints.add(event.mint)
            lastActivityByMint[event.mint] = event.receivedAtEpochMs
            _trackedSubscriptionCount.value = webSocketManager?.activeTokenSubscriptions ?: 0
            val provider = webSocketManager?.provider ?: settings.marketFeedProvider.value
            if (liveStreamReady(provider)) {
                metricsEngine.dropToken(event.mint)
                webSocketManager?.subscribeTokenTrades(listOf(event.mint))
                val active = webSocketManager?.activeTokenMints.orEmpty()
                if (event.mint in active && webSocketManager?.connectionState?.value == ConnectionState.CONNECTED) {
                    marketStates.beginTradeTracking(listOf(event.mint), event.receivedAtEpochMs)
                    diagnosticWindows.beginCoverage(listOf(event.mint), event.receivedAtEpochMs)
                }
            }
            _trackedSubscriptionCount.value = webSocketManager?.activeTokenSubscriptions ?: 0
        }
    }

    private suspend fun handleMigration(event: NormalizedMigrationEvent) {
        val existing = db.tokenDao().getByMint(event.mint) ?: return
        db.tokenDao().upsert(existing.copy(lifecycle = "MIGRATED"))
    }

    // --- 11. TRADE TRACKING + 12. DUPLICATE PROTECTION ----------------------
    private suspend fun handleTrade(event: NormalizedTradeEvent, provider: MarketFeedProvider? = null) {
        val source = if (settings.mockMode.value) "MOCK" else (provider?.sourceId ?: event.providerSource)
        val dedupeKey = "$source:${event.dedupeKey()}"
        if (db.tradeDao().existsByDedupeKey(dedupeKey) > 0) {
            telemetry.record(
                component = "DATA_QUALITY", eventType = "DUPLICATE_EVENT", severity = DiagnosticSeverity.INFO,
                message = "Duplicate trade ignored by existing dedupe guard", tokenAddress = event.mint,
                dataSource = source, eventTimestampMs = event.sourceTimestampEpochMs, receivedAtMs = System.currentTimeMillis(),
                metadata = mapOf("signature" to event.signature, "side" to event.side.name)
            )
            return // preserve existing duplicate protection
        }
        if (provider == MarketFeedProvider.PUMPDEV && event.mint in webSocketManager?.requestedTokenMints.orEmpty()) {
            webSocketManager?.confirmObservedTokenTrade(event.mint)
            if (event.mint !in webSocketManager?.activeTokenMints.orEmpty()) {
                marketStates.beginTradeTracking(listOf(event.mint), event.timestampEpochMs)
                diagnosticWindows.beginCoverage(listOf(event.mint), event.timestampEpochMs)
                metricsEngine.dropToken(event.mint)
                _trackedSubscriptionCount.value = webSocketManager?.activeTokenSubscriptions ?: 0
            }
        }
        _tradesReceivedCount.value += 1
        lastActivityByMint[event.mint] = event.timestampEpochMs

        val solPrice = currentSolUsdPrice()
        val amountUsd = usd(event.solAmount, solPrice)
        val priceUsd = usd(event.priceSol, solPrice)
        val missingFields = buildList {
            if (event.priceSol == null) add("priceSol")
            if (event.marketCapSol == null) add("marketCapSol")
            if (event.solAmount == null) add("solAmount")
            if (event.tokenAmount == null) add("tokenAmount")
            if (event.vSolInBondingCurve == null) add("liquidityOrQuoteReserve")
            if (solPrice == null) add("solUsdRate")
            if (amountUsd == null && event.solAmount != null) add("amountUsd")
            if (priceUsd == null && event.priceSol != null) add("priceUsd")
        }
        if (missingFields.isNotEmpty()) telemetry.record(
            component = "DATA_QUALITY", eventType = "MISSING_MARKET_FIELDS", severity = DiagnosticSeverity.WARN,
            message = "Trade event has unavailable market fields; values remain null", tokenAddress = event.mint,
            dataSource = source, eventTimestampMs = event.sourceTimestampEpochMs, receivedAtMs = System.currentTimeMillis(),
            metadata = mapOf("missingFields" to missingFields, "side" to event.side.name, "signature" to event.signature)
        )
        val invalidNumericFields = buildList {
            if (event.solAmount?.let { !it.isFinite() || it < 0.0 } == true) add("solAmount")
            if (event.tokenAmount?.let { !it.isFinite() || it < 0.0 } == true) add("tokenAmount")
            if (event.marketCapSol?.let { !it.isFinite() || it < 0.0 } == true) add("marketCapSol")
            if (event.vSolInBondingCurve?.let { !it.isFinite() || it < 0.0 } == true) add("vSolInBondingCurve")
            if (event.vTokensInBondingCurve?.let { !it.isFinite() || it < 0.0 } == true) add("vTokensInBondingCurve")
        }
        if (invalidNumericFields.isNotEmpty()) telemetry.record(
            component = "DATA_QUALITY", eventType = "INVALID_NUMERIC_VALUE", severity = DiagnosticSeverity.ERROR,
            message = "Provider trade contains a negative or non-finite numeric value", tokenAddress = event.mint,
            dataSource = source, eventTimestampMs = event.sourceTimestampEpochMs, receivedAtMs = System.currentTimeMillis(),
            metadata = mapOf("invalidFields" to invalidNumericFields, "signature" to event.signature)
        )

        db.tradeDao().insert(
            TradeEntity(
                mint = event.mint,
                dedupeKey = dedupeKey,
                side = event.side.name,
                trader = event.trader,
                amountUsd = amountUsd,
                priceUsd = priceUsd,
                timestamp = event.timestampEpochMs,
                source = source.lowercase()
            )
        )
        // MetricsEngine works in USD internally; feed it the converted amount/price.
        metricsEngine.record(event.mint, event.side, event.trader, amountUsd, priceUsd, event.timestampEpochMs)

        val marketDataSource = when (source) {
            "PUMPDEV" -> MarketDataSource.PUMPDEV_TRADE
            "PUMPPORTAL" -> MarketDataSource.PUMPPORTAL_TRADE
            else -> MarketDataSource.MOCK
        }
        if (!settings.mockMode.value && settings.liveTradeStreamingEnabled.value) {
            val orderingIssue = diagnosticWindows.observe(
                event.mint, event.timestampEpochMs, event.side, priceUsd,
                usd(event.marketCapSol, solPrice), amountUsd, event.sourceTimestampEpochMs, event.trader
            )
            if (orderingIssue != null) telemetry.record(
                component = "MARKET_FEED", eventType = orderingIssue, severity = DiagnosticSeverity.WARN,
                message = "Provider event timestamp ordering/continuity issue", tokenAddress = event.mint,
                dataSource = source, eventTimestampMs = event.sourceTimestampEpochMs, receivedAtMs = System.currentTimeMillis(),
                metadata = mapOf("signature" to event.signature, "providerSourceTimestampMs" to event.sourceTimestampEpochMs)
            )
            telemetry.record(
                component = "FEATURE_WINDOWS", eventType = "WINDOW_INPUTS", severity = DiagnosticSeverity.DEBUG,
                message = "Diagnostic rolling windows after observed trade", tokenAddress = event.mint,
                dataSource = source, eventTimestampMs = event.sourceTimestampEpochMs, receivedAtMs = System.currentTimeMillis(),
                metadata = mapOf("side" to event.side.name, "amountUsd" to amountUsd, "priceUsd" to priceUsd,
                    "windows" to diagnosticWindows.snapshot(event.mint, System.currentTimeMillis()).associate { it.windowSeconds.toString() to it.asMap() })
            )
        }
        db.tokenDao().getByMint(event.mint)?.let { token ->
            val currentMarketCapUsd = usd(event.marketCapSol, solPrice) ?: token.marketCapUsd
            val currentLiquidityUsd = usd(event.vSolInBondingCurve, solPrice) ?: token.liquidityUsd
            marketStates.updateTrade(
                mint = event.mint,
                symbol = token.symbol,
                name = token.name,
                priceUsd = priceUsd,
                marketCapUsd = currentMarketCapUsd,
                liquidityUsd = currentLiquidityUsd,
                tick = LiveTradeTick(
                    timestampMs = event.timestampEpochMs,
                    side = event.side,
                    priceUsd = priceUsd,
                    amountUsd = amountUsd,
                    trader = event.trader,
                    signature = event.signature
                ),
                source = marketDataSource,
                nowMs = System.currentTimeMillis()
            )
            db.tokenDao().upsert(
                token.copy(
                    lastPriceUsd = priceUsd ?: token.lastPriceUsd,
                    marketCapSol = event.marketCapSol ?: token.marketCapSol,
                    liquiditySol = event.vSolInBondingCurve ?: token.liquiditySol,
                    marketCapUsd = currentMarketCapUsd,
                    liquidityUsd = usd(event.vSolInBondingCurve, solPrice) ?: token.liquidityUsd
                )
            )
            recordMarketCap(event.mint, event.timestampEpochMs, currentMarketCapUsd)
        }

        marketStates.states.value[event.mint]?.let { state ->
            val now = System.currentTimeMillis()
            val fields = mapOf(
                "priceUsd" to state.priceUsd, "marketCapUsd" to state.marketCapUsd,
                "liquidityUsd" to state.liquidityUsd, "volumeUsd60s" to state.volumeUsd60s,
                "buyCount60s" to state.buyCount60s, "sellCount60s" to state.sellCount60s,
                "buyVolumeUsd60s" to state.buyVolumeUsd60s, "sellVolumeUsd60s" to state.sellVolumeUsd60s,
                "tradeFrequency60s" to state.buyCount60s?.let { buys -> state.sellCount60s?.let { sells -> (buys + sells) / 60.0 } },
                "lastEventAtMs" to state.lastTradeAtMs, "lastEventReceivedAtMs" to state.lastTradeReceivedAtMs
            )
            telemetry.record(
                component = "LIVE_MARKET_STATE", eventType = "MARKET_STATE_SNAPSHOT", severity = DiagnosticSeverity.DEBUG,
                message = "Observed market state after selected-provider trade", tokenAddress = event.mint,
                dataSource = source, eventTimestampMs = event.sourceTimestampEpochMs, receivedAtMs = now,
                metadata = mapOf(
                    "status" to state.status.name, "priceSource" to state.priceSource.name,
                    "priceUpdatedAtMs" to state.priceUpdatedAtMs, "priceReceivedAtMs" to state.priceReceivedAtMs,
                    "freshnessAgeMs" to state.priceReceivedAtMs?.let { now - it },
                    "tokenAgeSeconds" to db.tokenDao().getByMint(event.mint)?.let { (now - it.firstSeenAtEpochMs).coerceAtLeast(0L) / 1_000L },
                    "eventSide" to event.side.name, "eventAmountUsd" to amountUsd,
                    "eventPriceUsd" to priceUsd, "eventMarketCapUsd" to usd(event.marketCapSol, solPrice),
                    "eventLiquidityUsd" to usd(event.vSolInBondingCurve, solPrice),
                    "fields" to fields,
                    "availableFields" to fields.filterValues { it != null }.keys,
                    "missingFields" to (fields.filterValues { it == null }.keys)
                )
            )
        }

        analyzeAndMaybeSignal(event.mint, event.timestampEpochMs, priceUsd)
        if (!settings.mockMode.value) {
            signalOutcomeRecorder.onLiveTrade(db, event.mint, event.timestampEpochMs, priceUsd, source.lowercase())
        }
    }

    /**
     * The REST analysis path for snapshots when token-trade streaming is not opted in;
     * the selected provider's normalized live trades use the separate direct path.
     */
    private suspend fun analyzeDexAndMaybeSignal(token: TokenEntity, info: DexScreenerPairInfo) {
        // In metered live-stream mode, tracked tokens reach the unchanged SignalEngine
        // through analyzeAndMaybeSignal() on each normalized PumpPortal trade. Do not
        // also emit signals from a slower REST snapshot for the same mint.
        if (!settings.mockMode.value && settings.liveTradeStreamingEnabled.value && token.mint in trackedMints) return
        val nowMs = System.currentTimeMillis()
        val ageSeconds = (nowMs - token.firstSeenAtEpochMs).coerceAtLeast(0L) / 1000L
        // The filter must use the fresh DexScreener snapshot, not the older
        // PumpPortal/SOL estimate stored on the token.
        val effectiveMarketCapUsd = info.marketCapUsd ?: token.marketCapUsd
        recordMarketCap(token.mint, nowMs, effectiveMarketCapUsd)
        val marketCapVelocity = marketCapVelocity(token.mint, nowMs)
        val safety = safetyEngine.evaluate(
            mint = token.mint,
            creatorSellVolumeUsdRecent = null,
            totalVolumeUsdRecent = info.volume5mUsd ?: 0.0,
            migrationState = if (token.lifecycle == "MIGRATED") "MIGRATED" else null,
            holderConcentrationPct = null,
            liquidityUsd = info.liquidityUsd,
            largestSingleTradeUsd = null,
            nowMs = nowMs
        )
        val score = scoringEngine.scoreDex(
            buys5m = info.buys5m,
            sells5m = info.sells5m,
            buyVolume5mUsd = null,
            sellVolume5mUsd = null,
            volume5mUsd = info.volume5mUsd,
            volume1hUsd = info.volume1hUsd,
            priceChange5mPct = info.priceChange5mPct,
            liquidityUsd = info.liquidityUsd,
            safety = safety,
            weights = settings.scoreWeights.value
        )
        db.scoreDao().insert(
            ScoreEntity(
                mint = token.mint, timestamp = nowMs, score = score.total,
                buyerPressure = score.components.find { it.label == "Buyer Pressure" }?.value,
                volumePressure = score.components.find { it.label == "Volume Pressure" }?.value,
                volumeVelocity = score.components.find { it.label == "Volume Velocity" }?.value,
                priceMomentum = score.components.find { it.label == "Price Momentum" }?.value,
                liquidity = score.components.find { it.label == "Liquidity" }?.value,
                holderDistribution = null,
                safety = score.components.find { it.label == "Safety" }?.value
            )
        )
        val decision = signalEngine.evaluateDex(
            mint = token.mint,
            ageSeconds = ageSeconds,
            marketCapUsd = effectiveMarketCapUsd,
            buys5m = info.buys5m,
            sells5m = info.sells5m,
            buyVolume5mUsd = null,
            sellVolume5mUsd = null,
            volumeVelocity = if (info.volume5mUsd != null && info.volume1hUsd != null && info.volume1hUsd > 0.0)
                (info.volume5mUsd * 12.0) / info.volume1hUsd else null,
            priceChange5mPct = info.priceChange5mPct,
            score = score,
            safety = safety,
            config = settings.filterConfig.value,
            nowMs = nowMs,
            marketCapVelocityPct = marketCapVelocity
        )
        telemetry.record(
            component = "SIGNAL_ENGINE", eventType = "SIGNAL_DECISION", severity = DiagnosticSeverity.DEBUG,
            message = "Dex candidate evaluation", tokenAddress = token.mint, dataSource = "DEXSCREENER",
            eventTimestampMs = nowMs, receivedAtMs = nowMs,
            metadata = mapOf("signalType" to decision.type.name, "shouldNotify" to decision.shouldNotify,
                "reasons" to decision.reasons, "score" to score.total, "ageSeconds" to ageSeconds,
                "marketCapUsd" to effectiveMarketCapUsd, "buys5m" to info.buys5m, "sells5m" to info.sells5m,
                "volume5mUsd" to info.volume5mUsd, "priceChange5mPct" to info.priceChange5mPct,
                "marketCapVelocityPct" to marketCapVelocity,
                "availableData" to listOfNotNull(
                    "ageSeconds".takeIf { ageSeconds >= 0 }, "marketCapUsd".takeIf { effectiveMarketCapUsd != null },
                    "liquidityUsd".takeIf { info.liquidityUsd != null }, "buys5m".takeIf { info.buys5m != null },
                    "sells5m".takeIf { info.sells5m != null }, "volume5mUsd".takeIf { info.volume5mUsd != null },
                    "priceChange5mPct".takeIf { info.priceChange5mPct != null }
                ),
                "missingData" to listOfNotNull(
                    "marketCapUsd".takeIf { effectiveMarketCapUsd == null }, "liquidityUsd".takeIf { info.liquidityUsd == null },
                    "buys5m".takeIf { info.buys5m == null }, "sells5m".takeIf { info.sells5m == null },
                    "volume5mUsd".takeIf { info.volume5mUsd == null }, "priceChange5mPct".takeIf { info.priceChange5mPct == null }
                ),
                "scoringComponents" to score.components.map { mapOf("name" to it.label, "value" to it.value) },
                "safety" to mapOf("overall" to safety.overall.name, "checks" to safety.checks.map {
                    mapOf("check" to it.check, "status" to it.status.name, "reason" to it.reason, "source" to it.source)
                }),
                "filterConfig" to mapOf("maxTokenAgeSeconds" to settings.filterConfig.value.maxTokenAgeSeconds,
                    "minMarketCapUsd" to settings.filterConfig.value.minMarketCapUsd,
                    "requireBuyersGtSellers" to settings.filterConfig.value.requireBuyersGtSellers,
                    "requireBuyVolumeGtSellVolume" to settings.filterConfig.value.requireBuyVolumeGtSellVolume,
                    "minScoreForBuy" to settings.filterConfig.value.minScoreForBuy,
                    "watchScoreFloor" to settings.filterConfig.value.watchScoreFloor)
            )
        )
        // AI is advisory and runs off the real-time path. Deterministic signals
        // must not wait for a provider response or fail when AI is unavailable.
        launchCodeCraftAnalysis(token, info, score.total, nowMs)
        val signalReasons = decision.reasons
        if (decision.shouldNotify) {
            val id = db.signalDao().insert(
                SignalEntity(
                    mint = token.mint, symbol = token.symbol, timestamp = nowMs,
                    signalType = decision.type.name, score = score.total,
                    reasonsJson = JSONArray(signalReasons).toString(),
                    marketCapUsd = effectiveMarketCapUsd, liquidityUsd = info.liquidityUsd,
                    buyers = info.buys5m ?: 0, sellers = info.sells5m ?: 0,
                    buyVolumeUsd = 0.0, sellVolumeUsd = 0.0,
                    priceUsd = info.priceUsd
                )
            )
            if (decision.type == SignalType.BUY || decision.type == SignalType.SELL) {
                NotificationHelper.showSignalNotification(
                    context, id, token.mint, token.poolAddress, token.symbol ?: token.mint.take(6),
                    decision.type, score.total,
                    com.solanasignal.app.domain.metrics.WindowMetrics(
                        windowSeconds = 300, totalTrades = (info.buys5m ?: 0) + (info.sells5m ?: 0),
                        buys = info.buys5m ?: 0, sells = info.sells5m ?: 0,
                        uniqueBuyers = info.buys5m ?: 0, uniqueSellers = info.sells5m ?: 0,
                        buyVolumeUsd = 0.0, sellVolumeUsd = 0.0,
                        avgBuySizeUsd = 0.0, avgSellSizeUsd = 0.0,
                        largestBuyUsd = 0.0, largestSellUsd = 0.0,
                        latestPriceUsd = info.priceUsd, priceChangePct = info.priceChange5mPct,
                        volumeVelocity = if (info.volume5mUsd != null && info.volume1hUsd != null && info.volume1hUsd > 0.0)
                            (info.volume5mUsd * 12.0) / info.volume1hUsd else null,
                        buyerVelocity = null, sellerVelocity = null
                    ), signalReasons
                )
            }
        }
    }

    private fun launchCodeCraftAnalysis(
        token: TokenEntity,
        info: DexScreenerPairInfo,
        score: Int,
        nowMs: Long
    ) {
        val key = settings.getCodeCraftKeyOrNull() ?: return
        if (score < settings.filterConfig.value.watchScoreFloor) return
        val last = lastAiAnalysisByMint[token.mint]
        if (last != null && nowMs - last < 300_000L) return
        lastAiAnalysisByMint[token.mint] = nowMs
        scope.launch(Dispatchers.IO) {
            val analysis = codeCraftClient.analyze(
                key, settings.codeCraftModel.value, token, info,
                token.dataQualityScore ?: 0
            )
        if (analysis == null) {
            logSystemEvent("CODECRAFT", "AI analysis unavailable for ${token.symbol ?: token.mint.take(8)}")
            return@launch
        }
        db.tokenDao().upsert(
            token.copy(
                aiDecision = analysis.decision,
                aiConfidence = analysis.confidence,
                aiRisk = analysis.risk,
                aiSummary = analysis.summary,
                aiReasonsJson = JSONArray(analysis.positiveFactors).toString(),
                aiNegativeFactorsJson = JSONArray(analysis.negativeFactors).toString(),
                aiRedFlagsJson = JSONArray(analysis.redFlags).toString(),
                aiContradictionsJson = JSONArray(analysis.contradictions).toString(),
                aiMissingDataJson = JSONArray(analysis.missingData).toString(),
                aiRecommendedMonitoringJson = JSONArray(analysis.recommendedMonitoring).toString(),
                aiShouldNotify = analysis.shouldNotify,
                aiProvider = "CodeCraft/${settings.codeCraftModel.value}",
                aiAnalyzedAtEpochMs = nowMs
            )
        )
        logSystemEvent("CODECRAFT", "${token.symbol ?: token.mint.take(8)}: ${analysis.decision} (${analysis.confidence ?: "?"}%, ${analysis.risk ?: "UNKNOWN"} risk)")
        }
    }

    private fun recordMarketCap(mint: String, timestampMs: Long, marketCapUsd: Double?) {
        if (marketCapUsd == null || marketCapUsd <= 0.0) return
        val history = marketCapHistoryByMint.getOrPut(mint) { mutableListOf() }
        synchronized(history) {
            history += timestampMs to marketCapUsd
            val cutoff = timestampMs - 15 * 60_000L
            history.removeAll { it.first < cutoff }
        }
    }

    private fun marketCapVelocity(mint: String, atMs: Long): Double? {
        val history = marketCapHistoryByMint[mint].orEmpty()
        val current = history.lastOrNull { it.first <= atMs } ?: return null
        val previous = history.lastOrNull { it.first <= atMs - 60_000L } ?: return null
        if (previous.second <= 0.0) return null
        return (current.second - previous.second) / previous.second * 100.0
    }

    private fun calculateDataQuality(info: DexScreenerPairInfo): Pair<Int, String> {
        val available = listOf(
            info.priceUsd, info.marketCapUsd, info.fdvUsd, info.liquidityUsd,
            info.volume5mUsd, info.volume1hUsd, info.buys5m, info.sells5m,
            info.priceChange5mPct, info.pairCreatedAtEpochMs
        ).count { it != null }
        val score = (available * 100 / 10).coerceIn(0, 100)
        val label = when {
            score >= 90 -> "HIGH"
            score >= 70 -> "GOOD"
            score >= 50 -> "LIMITED"
            else -> "POOR"
        }
        return score to label
    }

    // --- Metrics -> Safety -> Score -> Signal pipeline ----------------------
    private suspend fun analyzeAndMaybeSignal(mint: String, nowMs: Long, eventPriceUsd: Double?) {
        val token = db.tokenDao().getByMint(mint) ?: return
        val windows = metricsEngine.computeAll(mint, nowMs)
        val m5 = windows[300] ?: return
        val m1 = windows[60] ?: return

        // Cache live metrics onto the token row so the Scanner list always shows
        // current buyer/seller/volume counts, not just whatever was true the last
        // time a signal happened to be emitted (which could be minutes stale due
        // to cooldown/dedupe suppression).
        db.tokenDao().upsert(
            token.copy(
                buyers5m = m5.uniqueBuyers, sellers5m = m5.uniqueSellers,
                buyVolume5mUsd = m5.buyVolumeUsd, sellVolume5mUsd = m5.sellVolumeUsd
            )
        )

        db.metricsDao().insert(
            MetricsSnapshotEntity(
                mint = mint, timestamp = nowMs, windowSeconds = 300,
                totalTrades = m5.totalTrades, buys = m5.buys, sells = m5.sells,
                uniqueBuyers = m5.uniqueBuyers, uniqueSellers = m5.uniqueSellers,
                buyVolumeUsd = m5.buyVolumeUsd, sellVolumeUsd = m5.sellVolumeUsd,
                avgBuySizeUsd = m5.avgBuySizeUsd, avgSellSizeUsd = m5.avgSellSizeUsd,
                largestBuyUsd = m5.largestBuyUsd, largestSellUsd = m5.largestSellUsd,
                latestPriceUsd = m5.latestPriceUsd, priceChangePct = m5.priceChangePct,
                volumeVelocity = m5.volumeVelocity, buyerVelocity = m5.buyerVelocity, sellerVelocity = m5.sellerVelocity
            )
        )

        val safety = safetyEngine.evaluate(
            mint = mint,
            creatorSellVolumeUsdRecent = null,
            totalVolumeUsdRecent = m5.buyVolumeUsd + m5.sellVolumeUsd,
            migrationState = if (token.lifecycle == "MIGRATED") "MIGRATED" else null,
            holderConcentrationPct = null,
            liquidityUsd = token.liquidityUsd,
            largestSingleTradeUsd = maxOf(m5.largestBuyUsd, m5.largestSellUsd),
            nowMs = nowMs
        )

        val advancedMomentum = momentumEngine.evaluate(windows, token.liquidityUsd)
        val manipulationRisk = manipulationRiskEngine.evaluate(windows, token.liquidityUsd)
        val evidence = signalEvidenceEngine.evaluate(
            windows = windows,
            marketCapUsd = token.marketCapUsd,
            liquidityUsd = token.liquidityUsd,
            nowMs = nowMs,
            latestTradeAtMs = lastActivityByMint[mint],
            marketCapHistory = marketCapHistoryByMint[mint].orEmpty(),
            config = settings.engineConfig.value
        )
        val mcTrend = mcTrendPressureEngine.evaluate(
            observations = marketCapHistoryByMint[mint].orEmpty().map { McObservation(it.first, it.second) },
            windowSeconds = settings.engineConfig.value.mcAnalysisWindowsSeconds,
            nowMs = nowMs
        )
        val mcHistory = marketCapHistoryByMint[mint].orEmpty()
        val mcDelta = mcHistory.takeIf { it.size >= 2 }?.let { it[it.lastIndex].second - it[it.lastIndex - 1].second }
        val mcVelocityPctPerMinute = mcTrend.primary?.velocityPerSecond?.let { velocity ->
            token.marketCapUsd?.takeIf { it > 0.0 }?.let { velocity / it * 60.0 * 100.0 }
        }
        db.featureSnapshotDao().insertObservation(
            TokenObservationEntity(
                mint = mint, timestamp = nowMs, priceUsd = eventPriceUsd,
                marketCapUsd = token.marketCapUsd, liquidityUsd = token.liquidityUsd,
                buyVolumeUsd = m5.buyVolumeUsd, sellVolumeUsd = m5.sellVolumeUsd,
                buyers = m5.uniqueBuyers, sellers = m5.uniqueSellers,
                source = if (settings.mockMode.value) "mock" else
                    (marketStates.states.value[mint]?.priceSource?.name?.lowercase() ?: settings.marketFeedProvider.value.sourceId.lowercase())
            )
        )
        db.featureSnapshotDao().insertSnapshot(
            TokenFeatureSnapshotEntity(
                mint = mint, timestamp = nowMs, opportunityScore = evidence.momentumScore,
                momentumScore = evidence.momentumScore, riskScore = evidence.collapseRisk,
                qualityScore = evidence.signalQuality, dataConfidenceScore = evidence.dataConfidence,
                featuresJson = org.json.JSONObject((evidence.featureValues + mapOf(
                    "mcDirectionalPressure" to mcTrend.primary?.directionalPressure,
                    "mcPersistence" to mcTrend.primary?.persistence,
                    "mcClassification" to mcTrend.primary?.classification?.name
                )).mapValues { it.value ?: org.json.JSONObject.NULL }).toString(),
                classification = evidence.classification,
                mcDelta = mcDelta,
                mcVelocity = mcTrend.primary?.velocityPerSecond,
                mcAcceleration = mcTrend.primary?.accelerationPerSecond,
                mcDirectionalPressure = mcTrend.primary?.directionalPressure,
                mcPersistence = mcTrend.primary?.persistence,
                mcNetChange = mcTrend.primary?.netMcChange,
                mcNetChangePct = mcTrend.primary?.netMcChangePercent,
                mcRecentHigh = mcTrend.primary?.recentHigh,
                mcDrawdownPct = mcTrend.primary?.drawdownFromHighPct,
                mcHigherHighCount = mcTrend.primary?.higherHighCount,
                mcLowerHighCount = mcTrend.primary?.lowerHighCount,
                mcTrendClassification = mcTrend.primary?.classification?.name
            )
        )
        val previousLifecycle = runCatching { SignalLifecycleState.valueOf(token.lifecycle) }.getOrNull()
        val lifecycleTransition = signalLifecycleEngine.transition(
            previous = previousLifecycle,
            momentum = advancedMomentum.state,
            score = advancedMomentum.score,
            manipulation = manipulationRisk.level,
            safetyFailed = safety.hasFailed,
            dataQualityScore = token.dataQualityScore
        )
        if (lifecycleTransition.changed) {
            db.signalTransitionDao().insert(
                com.solanasignal.app.data.room.entities.SignalTransitionEntity(
                    mint = mint,
                    timestamp = nowMs,
                    previousState = lifecycleTransition.previous?.name,
                    newState = lifecycleTransition.current.name,
                    score = advancedMomentum.score,
                    reasonsJson = JSONArray(
                        advancedMomentum.reasons + manipulationRisk.findings.map { "${it.name}: ${it.explanation}" } + lifecycleTransition.reason
                    ).toString(),
                    manipulationRisk = manipulationRisk.score,
                    dataQualityScore = token.dataQualityScore
                )
            )
        }
        val weights = settings.scoreWeights.value
        val baseScore = scoringEngine.score(
            metrics5m = m5, metrics1m = m1,
            holderConcentrationPct = null,
            liquidityUsd = token.liquidityUsd,
            safety = safety,
            weights = weights
        )
        val manipulationPenalty = manipulationRisk.score ?: 0
        val adjustedTotal = (
            baseScore.total * 0.60 +
                advancedMomentum.score * 0.30 +
                (100 - manipulationPenalty) * 0.10
            ).toInt().coerceIn(0, 100)
        val score = baseScore.copy(
            total = adjustedTotal,
            components = baseScore.components + listOf(
                com.solanasignal.app.domain.scoring.ComponentScore("Advanced Momentum", advancedMomentum.score.toDouble()),
                com.solanasignal.app.domain.scoring.ComponentScore("Manipulation Risk", manipulationRisk.score?.let { 100.0 - it })
            )
        )

        val analyzedToken = token.copy(
            lifecycle = lifecycleTransition.current.name,
            momentumScore = advancedMomentum.score,
            momentumState = advancedMomentum.state.name,
            momentumPersistencePct = advancedMomentum.persistence,
                manipulationRiskScore = manipulationRisk.score,
                manipulationRiskLevel = manipulationRisk.level.name,
                manipulationFindingsJson = JSONArray(manipulationRisk.findings.map { "${it.name}: ${it.explanation}" }).toString(),
                opportunityScore = evidence.momentumScore,
                qualityScore = evidence.signalQuality,
                dataConfidenceScore = evidence.dataConfidence,
                evidenceJson = org.json.JSONObject(mapOf("classification" to evidence.classification, "reasons" to JSONArray(evidence.reasons), "warnings" to JSONArray(evidence.warnings))).toString(),
                marketCapVelocityPct = mcVelocityPctPerMinute,
                marketCapAccelerationPct = mcTrend.primary?.accelerationPerSecond
            )
        db.tokenDao().upsert(analyzedToken)

        db.scoreDao().insert(
            ScoreEntity(
                mint = mint, timestamp = nowMs, score = score.total,
                buyerPressure = score.components.find { it.label == "Buyer Pressure" }?.value,
                volumePressure = score.components.find { it.label == "Volume Pressure" }?.value,
                volumeVelocity = score.components.find { it.label == "Volume Velocity" }?.value,
                priceMomentum = score.components.find { it.label == "Price Momentum" }?.value,
                liquidity = score.components.find { it.label == "Liquidity" }?.value,
                holderDistribution = score.components.find { it.label == "Holder Distribution" }?.value,
                safety = score.components.find { it.label == "Safety" }?.value,
                advancedMomentum = advancedMomentum.score.toDouble(),
                manipulationRisk = manipulationRisk.score?.toDouble(),
                dataQuality = token.dataQualityScore?.toDouble()
            )
        )

        val config = settings.filterConfig.value
        val ageSeconds = (nowMs - token.firstSeenAtEpochMs) / 1000

        val buyDecision = signalEngine.evaluate(
            mint = mint, ageSeconds = ageSeconds, marketCapUsd = token.marketCapUsd,
            metrics5m = m5, score = score, safety = safety, config = config, nowMs = nowMs,
            marketCapVelocityPct = mcVelocityPctPerMinute
        )
        telemetry.record(
            component = "SIGNAL_ENGINE", eventType = "SIGNAL_DECISION", severity = DiagnosticSeverity.DEBUG,
            message = "Live trade evaluation", tokenAddress = mint,
            dataSource = if (settings.mockMode.value) "MOCK" else settings.marketFeedProvider.value.sourceId,
            eventTimestampMs = nowMs, receivedAtMs = System.currentTimeMillis(),
            metadata = mapOf("signalType" to buyDecision.type.name, "shouldNotify" to buyDecision.shouldNotify,
                "reasons" to buyDecision.reasons, "score" to score.total, "buyerPressurePct" to m5.buyerVelocity,
                "priceChangePct" to m5.priceChangePct, "volumeVelocity" to m5.volumeVelocity,
                "buyVolumeUsd" to m5.buyVolumeUsd, "sellVolumeUsd" to m5.sellVolumeUsd,
                "marketCapVelocityPct" to mcVelocityPctPerMinute, "riskScore" to manipulationRisk.score,
                "dataConfidence" to evidence.dataConfidence, "ageSeconds" to ageSeconds,
                "marketCapUsd" to token.marketCapUsd, "liquidityUsd" to token.liquidityUsd,
                "metrics5m" to windowMetricsMap(m5), "metrics1m" to windowMetricsMap(m1),
                "scoringComponents" to score.components.map { mapOf("name" to it.label, "value" to it.value) },
                "availableData" to listOfNotNull(
                    "marketCapUsd".takeIf { token.marketCapUsd != null }, "liquidityUsd".takeIf { token.liquidityUsd != null },
                    "latestPriceUsd5m".takeIf { m5.latestPriceUsd != null }, "priceChangePct5m".takeIf { m5.priceChangePct != null },
                    "volumeVelocity5m".takeIf { m5.volumeVelocity != null }, "buyerVelocity5m".takeIf { m5.buyerVelocity != null },
                    "sellerVelocity5m".takeIf { m5.sellerVelocity != null }, "marketCapVelocityPct".takeIf { mcVelocityPctPerMinute != null }
                ),
                "missingData" to listOfNotNull(
                    "marketCapUsd".takeIf { token.marketCapUsd == null }, "liquidityUsd".takeIf { token.liquidityUsd == null },
                    "latestPriceUsd5m".takeIf { m5.latestPriceUsd == null }, "priceChangePct5m".takeIf { m5.priceChangePct == null },
                    "volumeVelocity5m".takeIf { m5.volumeVelocity == null }, "buyerVelocity5m".takeIf { m5.buyerVelocity == null },
                    "sellerVelocity5m".takeIf { m5.sellerVelocity == null }, "marketCapVelocityPct".takeIf { mcVelocityPctPerMinute == null }
                ),
                "safety" to mapOf("overall" to safety.overall.name, "checks" to safety.checks.map {
                    mapOf("check" to it.check, "status" to it.status.name, "reason" to it.reason, "source" to it.source)
                }),
                "filterConfig" to mapOf("maxTokenAgeSeconds" to config.maxTokenAgeSeconds,
                    "minMarketCapUsd" to config.minMarketCapUsd, "requireBuyersGtSellers" to config.requireBuyersGtSellers,
                    "requireBuyVolumeGtSellVolume" to config.requireBuyVolumeGtSellVolume,
                    "minScoreForBuy" to config.minScoreForBuy, "watchScoreFloor" to config.watchScoreFloor)
            )
        )

        if (buyDecision.shouldNotify) {
            persistAndNotify(analyzedToken, buyDecision.type, score.total, buyDecision.reasons, m5)
        }

        val prevScore = lastScoreByMint[mint]
        lastScoreByMint[mint] = score.total
        if (prevScore != null) {
            val sellDecision = signalEngine.evaluateSellTrigger(
                mint = mint, previousScore = prevScore, currentScore = score.total,
                metrics5m = m5, safety = safety, config = config, nowMs = nowMs
            )
            telemetry.record(
                component = "SIGNAL_ENGINE", eventType = "SELL_TRIGGER_DECISION", severity = DiagnosticSeverity.DEBUG,
                message = if (sellDecision == null) "No sell-trigger decision" else "Sell-trigger evaluation",
                tokenAddress = mint, dataSource = if (settings.mockMode.value) "MOCK" else settings.marketFeedProvider.value.sourceId,
                eventTimestampMs = nowMs, receivedAtMs = System.currentTimeMillis(),
                metadata = mapOf("previousScore" to prevScore, "currentScore" to score.total,
                    "shouldNotify" to sellDecision?.shouldNotify, "reasons" to sellDecision?.reasons)
            )
            if (sellDecision != null && sellDecision.shouldNotify) {
                persistAndNotify(analyzedToken, SignalType.SELL, score.total, sellDecision.reasons, m5)
            }
        }
    }

    private fun windowMetricsMap(metrics: com.solanasignal.app.domain.metrics.WindowMetrics): Map<String, Any?> = mapOf(
        "windowSeconds" to metrics.windowSeconds,
        "totalTrades" to metrics.totalTrades,
        "buys" to metrics.buys,
        "sells" to metrics.sells,
        "uniqueBuyers" to metrics.uniqueBuyers,
        "uniqueSellers" to metrics.uniqueSellers,
        "buyVolumeUsd" to metrics.buyVolumeUsd,
        "sellVolumeUsd" to metrics.sellVolumeUsd,
        "avgBuySizeUsd" to metrics.avgBuySizeUsd,
        "avgSellSizeUsd" to metrics.avgSellSizeUsd,
        "largestBuyUsd" to metrics.largestBuyUsd,
        "largestSellUsd" to metrics.largestSellUsd,
        "latestPriceUsd" to metrics.latestPriceUsd,
        "priceChangePct" to metrics.priceChangePct,
        "volumeVelocity" to metrics.volumeVelocity,
        "buyerVelocity" to metrics.buyerVelocity,
        "sellerVelocity" to metrics.sellerVelocity
    )

    private suspend fun persistAndNotify(
        token: TokenEntity, type: SignalType, score: Int, reasons: List<String>, m5: com.solanasignal.app.domain.metrics.WindowMetrics
    ) {
        val signalTimestamp = System.currentTimeMillis()
        val id = db.signalDao().insert(
            SignalEntity(
                mint = token.mint, symbol = token.symbol, timestamp = signalTimestamp,
                signalType = type.name, score = score,
                reasonsJson = JSONArray(reasons).toString(),
                marketCapUsd = token.marketCapUsd, liquidityUsd = token.liquidityUsd,
                buyers = m5.uniqueBuyers, sellers = m5.uniqueSellers,
                buyVolumeUsd = m5.buyVolumeUsd, sellVolumeUsd = m5.sellVolumeUsd,
                priceUsd = m5.latestPriceUsd,
                lifecycleState = token.lifecycle,
                momentumScore = token.momentumScore,
                manipulationRiskScore = token.manipulationRiskScore,
                dataQualityScore = token.dataQualityScore
            )
        )
        telemetry.record(
            component = "SIGNAL_ENGINE", eventType = "SIGNAL_EMITTED", severity = DiagnosticSeverity.INFO,
            message = "${type.name} signal persisted", tokenAddress = token.mint,
            dataSource = if (settings.mockMode.value) "MOCK" else settings.marketFeedProvider.value.sourceId,
            eventTimestampMs = signalTimestamp, receivedAtMs = signalTimestamp,
            metadata = mapOf("signalId" to id, "signalType" to type.name, "score" to score,
                "reasons" to reasons, "marketCapUsd" to token.marketCapUsd, "liquidityUsd" to token.liquidityUsd,
                "buyers5m" to m5.uniqueBuyers, "sellers5m" to m5.uniqueSellers,
                "buyVolume5mUsd" to m5.buyVolumeUsd, "sellVolume5mUsd" to m5.sellVolumeUsd,
                "priceUsd" to m5.latestPriceUsd)
        )
        if (type == SignalType.BUY || type == SignalType.SELL) {
            NotificationHelper.showSignalNotification(
                context, id, token.mint, token.poolAddress, token.symbol ?: token.mint.take(6), type, score, m5, reasons
            )
        }
    }

    private fun logSystemEvent(category: String, message: String, source: String? = null) {
        val now = System.currentTimeMillis()
        val safeMessage = DiagnosticJson.redactText(message).take(1_000)
        val severity = when {
            category in setOf("STARTUP_ERROR", "PARSER_ERROR", "SUBSCRIPTION_ERROR") -> DiagnosticSeverity.ERROR
            category in setOf("PROVIDER_ERROR", "SUBSCRIPTION_LIMIT", "SUBSCRIPTION_REJECTED", "STREAM_QUIET", "PARSER_UNKNOWN") -> DiagnosticSeverity.WARN
            category == "UPSTREAM_STATUS" && safeMessage.contains("connected=false") -> DiagnosticSeverity.WARN
            else -> DiagnosticSeverity.INFO
        }
        telemetry.record(
            component = if (category in setOf("PARSER_ERROR", "PROVIDER_ERROR", "SUBSCRIPTION_ERROR", "SUBSCRIPTION_LIMIT", "SUBSCRIPTION_REJECTED", "STREAM_QUIET", "CONNECTION", "RECONNECT", "SUBSCRIPTION", "SUBSCRIPTION_ACK", "UPSTREAM_STATUS")) "MARKET_FEED" else "SYSTEM",
            eventType = category, severity = severity, message = safeMessage,
            dataSource = source ?: if (settings.mockMode.value) "MOCK" else settings.marketFeedProvider.value.sourceId,
            receivedAtMs = now, metadata = mapOf("category" to category)
        )
        scope.launch {
            runCatching {
                db.systemEventDao().insert(SystemEventEntity(timestamp = now, category = category, message = safeMessage))
            }
        }
    }

    private fun maxTrackedForBatteryMode(): Int = when (settings.batteryMode.value) {
        // DexScreener requests are batched in groups of 30 and are read-only;
        // these limits are not PumpPortal trade-subscription limits.
        BatteryMode.PERFORMANCE -> 500
        BatteryMode.BALANCED -> 250
        BatteryMode.BATTERY_SAVER -> 100
    }

    private fun usd(solValue: Double?, solPriceUsd: Double?): Double? =
        if (solValue != null && solPriceUsd != null) solValue * solPriceUsd else null
}
