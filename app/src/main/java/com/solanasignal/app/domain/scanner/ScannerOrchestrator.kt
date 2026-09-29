package com.solanasignal.app.domain.scanner

import android.content.Context
import com.solanasignal.app.data.codecraft.CodeCraftClient
import com.solanasignal.app.data.dexscreener.DexScreenerClient
import com.solanasignal.app.data.dexscreener.DexScreenerPairInfo
import com.solanasignal.app.data.pumpportal.*
import com.solanasignal.app.data.room.AppDatabase
import com.solanasignal.app.data.room.entities.*
import com.solanasignal.app.data.settings.BatteryMode
import com.solanasignal.app.data.settings.SettingsRepository
import com.solanasignal.app.domain.metrics.MetricsEngine
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
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

/**
 * Wires the full pipeline: PumpPortal -> WebSocketManager -> Token Discovery / Trade
 * Streams -> Metrics -> Safety -> Momentum Score -> Signal Engine -> Android Alert
 * -> (user) -> Photon. This class does not execute trades.
 *
 * PumpPortal reports everything in SOL (marketCapSol, vSolInBondingCurve, solAmount,
 * tokenAmount). SolPriceProvider supplies a live SOL/USD rate so the app can show
 * the USD figures the spec's filters/UI are defined in; until a price has been
 * fetched at least once, USD fields stay null/UNKNOWN rather than being guessed.
 */
class ScannerOrchestrator(
    private val context: Context,
    private val settings: SettingsRepository = SettingsRepository.get(context)
) {
    private val db = AppDatabase.get(context)
    private val metricsEngine = MetricsEngine()
    private val safetyEngine = SafetyEngine()
    private val scoringEngine = ScoringEngine()
    private val signalEngine = SignalEngine()
    private val momentumEngine = MomentumEngine()
    private val manipulationRiskEngine = ManipulationRiskEngine()
    private val signalEvidenceEngine = SignalEvidenceEngine()
    private val signalLifecycleEngine = SignalLifecycleEngine()
    private val solPriceProvider = SolPriceProvider()
    private val dexScreenerClient = DexScreenerClient()
    private val codeCraftClient = CodeCraftClient()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var mockSource: MockEventSource? = null
    private var webSocketManager: PumpPortalWebSocketManager? = null
    private var dexScreenerJob: Job? = null

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
        solPriceProvider.stop()
        dexScreenerJob?.cancel()
        evictionJob?.cancel()
        _connectionState.value = ConnectionState.DISCONNECTED
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
                val stale = trackedMints.filter { mint ->
                    val last = lastActivityByMint[mint]
                    last == null || last < staleCutoff
                }
                if (stale.isEmpty()) continue
                stale.forEach { mint ->
                    trackedMints.remove(mint)
                    lastActivityByMint.remove(mint)
                    metricsEngine.dropToken(mint)
                }
                _trackedSubscriptionCount.value = trackedMints.size
                logSystemEvent("SUBSCRIPTION", "Evicted ${stale.size} stale token(s) with no trades in 5m, freeing slots")
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
                            lastActivityByMint[mint] = System.currentTimeMillis()
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
        val manager = PumpPortalWebSocketManager(
            getApiKey = { settings.getApiKeyOrNull() },
            onEvent = { result, nowMs -> scope.launch { handleParseResult(result, nowMs) } },
            onSystemEvent = { category, message -> scope.launch { logSystemEvent(category, message) } }
        )
        webSocketManager = manager
        manager.start()
        manager.subscribeNewToken()

        // Forward the manager's live state into our stable, always-observable flows.
        // (Trade counting is NOT forwarded from here - handleTrade() below increments
        // _tradesReceivedCount itself, since that path also runs in Mock Mode and
        // counts post-dedupe; forwarding the manager's own separate raw-message
        // counter here as well would race two writers against the same flow.)
        scope.launch { manager.connectionState.collect { _connectionState.value = it } }
        scope.launch { manager.reconnectCount.collect { _reconnectCount.value = it } }
        scope.launch { manager.parserErrorCount.collect { _parserErrorCount.value = it } }
        scope.launch { manager.eventsPerSecond.collect { _eventsPerSecond.value = it } }
    }

    private fun startMock() {
        // Mock mode fabricates a fake SOL price too, so USD figures aren't stuck on
        // UNKNOWN while demoing - clearly labeled MOCK MODE in the UI regardless.
        solPriceProvider.stop()
        // No real socket in mock mode - report CONNECTED so the status screen
        // doesn't falsely suggest something is broken while simulating data.
        _connectionState.value = ConnectionState.CONNECTED
        val source = MockEventSource(
            onTokenCreated = { event -> scope.launch { handleTokenCreated(event) } },
            onTrade = { event -> scope.launch { handleTrade(event) } }
        )
        mockSource = source
        source.start(scope)
    }

    private fun currentSolUsdPrice(): Double? = if (settings.mockMode.value) 180.0 else solPriceProvider.priceUsd.value

    private suspend fun handleParseResult(result: ParseResult, nowMs: Long) {
        when (result) {
            is ParseResult.TokenCreated -> handleTokenCreated(result.event)
            is ParseResult.Migration -> handleMigration(result.event)
            is ParseResult.Trade -> handleTrade(result.event)
            is ParseResult.Unknown -> logSystemEvent(
                "PARSER_UNKNOWN",
                "Unrecognized message (likely a subscription ack or an error from PumpPortal - check content): " +
                    result.raw.take(400)
            )
            is ParseResult.Malformed -> Unit // already counted/logged by the WS manager
        }
    }

    // --- 9. TOKEN DISCOVERY -------------------------------------------------
    private suspend fun handleTokenCreated(event: NormalizedTokenCreatedEvent) {
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
            source = if (settings.mockMode.value) "mock" else "pumpportal"
        )
        db.tokenDao().upsert(token)
        recordMarketCap(event.mint, event.receivedAtEpochMs, token.marketCapUsd)

        val maxTracked = maxTrackedForBatteryMode()
        if (trackedMints.size < maxTracked) {
            trackedMints.add(event.mint)
            lastActivityByMint[event.mint] = event.receivedAtEpochMs
            _trackedSubscriptionCount.value = trackedMints.size
        }
    }

    private suspend fun handleMigration(event: NormalizedMigrationEvent) {
        val existing = db.tokenDao().getByMint(event.mint) ?: return
        db.tokenDao().upsert(existing.copy(lifecycle = "MIGRATED"))
    }

    // --- 11. TRADE TRACKING + 12. DUPLICATE PROTECTION ----------------------
    private suspend fun handleTrade(event: NormalizedTradeEvent) {
        val dedupeKey = event.dedupeKey()
        if (db.tradeDao().existsByDedupeKey(dedupeKey) > 0) return // never process the same trade twice
        _tradesReceivedCount.value += 1
        lastActivityByMint[event.mint] = event.timestampEpochMs

        val solPrice = currentSolUsdPrice()
        val amountUsd = usd(event.solAmount, solPrice)
        val priceUsd = usd(event.priceSol, solPrice)

        db.tradeDao().insert(
            TradeEntity(
                mint = event.mint,
                dedupeKey = dedupeKey,
                side = event.side.name,
                trader = event.trader,
                amountUsd = amountUsd,
                priceUsd = priceUsd,
                timestamp = event.timestampEpochMs
            )
        )
        // MetricsEngine works in USD internally; feed it the converted amount/price.
        metricsEngine.record(event.mint, event.side, event.trader, amountUsd, priceUsd, event.timestampEpochMs)

        db.tokenDao().getByMint(event.mint)?.let { token ->
            val currentMarketCapUsd = usd(event.marketCapSol, solPrice) ?: token.marketCapUsd
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

        analyzeAndMaybeSignal(event.mint, event.timestampEpochMs)
    }

    /**
     * The primary live analysis path: PumpPortal only discovers new mints;
     * DexScreener supplies the market activity used for scoring and signals.
     */
    private suspend fun analyzeDexAndMaybeSignal(token: TokenEntity, info: DexScreenerPairInfo) {
        val nowMs = System.currentTimeMillis()
        val ageSeconds = (nowMs - token.firstSeenAtEpochMs).coerceAtLeast(0L) / 1000L
        // The filter must use the fresh DexScreener snapshot, not the older
        // PumpPortal/SOL estimate stored on the token.
        val effectiveMarketCapUsd = info.marketCapUsd ?: token.marketCapUsd
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
            nowMs = nowMs
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
    private suspend fun analyzeAndMaybeSignal(mint: String, nowMs: Long) {
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
        db.featureSnapshotDao().insertObservation(
            TokenObservationEntity(
                mint = mint, timestamp = nowMs, priceUsd = token.lastPriceUsd,
                marketCapUsd = token.marketCapUsd, liquidityUsd = token.liquidityUsd,
                buyVolumeUsd = m5.buyVolumeUsd, sellVolumeUsd = m5.sellVolumeUsd,
                buyers = m5.uniqueBuyers, sellers = m5.uniqueSellers, source = token.source
            )
        )
        db.featureSnapshotDao().insertSnapshot(
            TokenFeatureSnapshotEntity(
                mint = mint, timestamp = nowMs, opportunityScore = evidence.momentumScore,
                momentumScore = evidence.momentumScore, riskScore = evidence.collapseRisk,
                qualityScore = evidence.signalQuality, dataConfidenceScore = evidence.dataConfidence,
                featuresJson = org.json.JSONObject(evidence.featureValues.mapValues { it.value ?: org.json.JSONObject.NULL }).toString(),
                classification = evidence.classification
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
                marketCapVelocityPct = evidence.marketCapVelocityPct,
                marketCapAccelerationPct = evidence.marketCapAccelerationPct
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
            metrics5m = m5, score = score, safety = safety, config = config, nowMs = nowMs
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
            if (sellDecision != null && sellDecision.shouldNotify) {
                persistAndNotify(analyzedToken, SignalType.SELL, score.total, sellDecision.reasons, m5)
            }
        }
    }

    private suspend fun persistAndNotify(
        token: TokenEntity, type: SignalType, score: Int, reasons: List<String>, m5: com.solanasignal.app.domain.metrics.WindowMetrics
    ) {
        val id = db.signalDao().insert(
            SignalEntity(
                mint = token.mint, symbol = token.symbol, timestamp = System.currentTimeMillis(),
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
        if (type == SignalType.BUY || type == SignalType.SELL) {
            NotificationHelper.showSignalNotification(
                context, id, token.mint, token.poolAddress, token.symbol ?: token.mint.take(6), type, score, m5, reasons
            )
        }
    }

    private suspend fun logSystemEvent(category: String, message: String) {
        db.systemEventDao().insert(SystemEventEntity(timestamp = System.currentTimeMillis(), category = category, message = message))
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
