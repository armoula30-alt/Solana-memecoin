package com.solanasignal.app.domain.scanner

import android.content.Context
import com.solanasignal.app.data.pumpportal.*
import com.solanasignal.app.data.room.AppDatabase
import com.solanasignal.app.data.room.entities.*
import com.solanasignal.app.data.settings.BatteryMode
import com.solanasignal.app.data.settings.SettingsRepository
import com.solanasignal.app.domain.metrics.MetricsEngine
import com.solanasignal.app.domain.safety.SafetyEngine
import com.solanasignal.app.domain.scoring.ScoringEngine
import com.solanasignal.app.domain.signals.SignalEngine
import com.solanasignal.app.domain.signals.SignalType
import com.solanasignal.app.notifications.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

/**
 * Wires the full pipeline described in the spec's architecture diagram:
 * PumpPortal -> WebSocketManager -> Token Discovery / Trade Streams -> Metrics
 * -> Safety -> Momentum Score -> Signal Engine -> Android Alert -> (user) -> Photon.
 *
 * This class does not execute trades. It only detects, analyzes, scores, and alerts.
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var mockSource: MockEventSource? = null
    private var webSocketManager: PumpPortalWebSocketManager? = null

    private val lastScoreByMint = ConcurrentHashMap<String, Int>()
    private val trackedMints = ConcurrentHashMap.newKeySet<String>()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    val connectionState: StateFlow<ConnectionState>
        get() = webSocketManager?.connectionState ?: MutableStateFlow(ConnectionState.DISCONNECTED).asStateFlow()

    fun start() {
        if (_running.value) return
        _running.value = true

        if (settings.mockMode.value) {
            startMock()
        } else {
            startLive()
        }
    }

    fun stop() {
        _running.value = false
        mockSource?.stop()
        webSocketManager?.stop()
        webSocketManager = null
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
        manager.subscribeMigration()
    }

    private fun startMock() {
        val source = MockEventSource(
            onTokenCreated = { event -> scope.launch { handleTokenCreated(event) } },
            onTrade = { event -> scope.launch { handleTrade(event) } }
        )
        mockSource = source
        source.start(scope)
    }

    private suspend fun handleParseResult(result: ParseResult, nowMs: Long) {
        when (result) {
            is ParseResult.TokenCreated -> handleTokenCreated(result.event)
            is ParseResult.Migration -> handleMigration(result.event)
            is ParseResult.Trade -> handleTrade(result.event)
            is ParseResult.Unknown -> logSystemEvent("PARSER_UNKNOWN", "Unhandled event type: ${result.rawType}")
            is ParseResult.Malformed -> Unit // already counted/logged by the WS manager
        }
    }

    // --- 9. TOKEN DISCOVERY -------------------------------------------------
    private suspend fun handleTokenCreated(event: NormalizedTokenCreatedEvent) {
        val token = TokenEntity(
            mint = event.mint,
            name = event.name,
            symbol = event.symbol,
            creator = event.creator,
            uri = event.uri,
            createdAtEpochMs = event.createdAtEpochMs,
            firstSeenAtEpochMs = event.receivedAtEpochMs,
            marketCapUsd = event.initialMarketCapUsd,
            liquidityUsd = null,
            lastPriceUsd = null,
            lifecycle = "NEW",
            source = if (settings.mockMode.value) "mock" else "pumpportal"
        )
        db.tokenDao().upsert(token)

        // Initial filters (spec #10): age is trivially 0 here, so this always passes the
        // age check on creation - eligibility is really decided as trades accumulate.
        val maxTracked = maxTrackedForBatteryMode()
        if (trackedMints.size < maxTracked) {
            trackedMints.add(event.mint)
            webSocketManager?.subscribeTokenTrade(listOf(event.mint))
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

        db.tradeDao().insert(
            TradeEntity(
                mint = event.mint,
                dedupeKey = dedupeKey,
                side = event.side.name,
                trader = event.trader,
                amountUsd = event.amountUsd,
                priceUsd = event.priceUsd,
                timestamp = event.timestampEpochMs
            )
        )
        metricsEngine.record(event)
        event.priceUsd?.let { price ->
            db.tokenDao().getByMint(event.mint)?.let { db.tokenDao().upsert(it.copy(lastPriceUsd = price)) }
        }

        analyzeAndMaybeSignal(event.mint, event.timestampEpochMs)
    }

    // --- Metrics -> Safety -> Score -> Signal pipeline ----------------------
    private suspend fun analyzeAndMaybeSignal(mint: String, nowMs: Long) {
        val token = db.tokenDao().getByMint(mint) ?: return
        val windows = metricsEngine.computeAll(mint, nowMs)
        val m5 = windows[300] ?: return
        val m1 = windows[60] ?: return

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
            creatorSellVolumeUsdRecent = null, // requires creator-tagged trade attribution not guaranteed by source
            totalVolumeUsdRecent = m5.buyVolumeUsd + m5.sellVolumeUsd,
            migrationState = if (token.lifecycle == "MIGRATED") "MIGRATED" else null,
            holderConcentrationPct = null, // UNKNOWN unless a reliable holders endpoint is wired in
            liquidityUsd = token.liquidityUsd,
            largestSingleTradeUsd = maxOf(m5.largestBuyUsd, m5.largestSellUsd),
            nowMs = nowMs
        )

        val weights = settings.scoreWeights.value
        val score = scoringEngine.score(
            metrics5m = m5, metrics1m = m1,
            holderConcentrationPct = null,
            liquidityUsd = token.liquidityUsd,
            safety = safety,
            weights = weights
        )

        db.scoreDao().insert(
            ScoreEntity(
                mint = mint, timestamp = nowMs, score = score.total,
                buyerPressure = score.components.find { it.label == "Buyer Pressure" }?.value,
                volumePressure = score.components.find { it.label == "Volume Pressure" }?.value,
                volumeVelocity = score.components.find { it.label == "Volume Velocity" }?.value,
                priceMomentum = score.components.find { it.label == "Price Momentum" }?.value,
                liquidity = score.components.find { it.label == "Liquidity" }?.value,
                holderDistribution = score.components.find { it.label == "Holder Distribution" }?.value,
                safety = score.components.find { it.label == "Safety" }?.value
            )
        )

        val config = settings.filterConfig.value
        val ageSeconds = token.firstSeenAtEpochMs.let { (nowMs - it) / 1000 }

        val buyDecision = signalEngine.evaluate(
            mint = mint, ageSeconds = ageSeconds, marketCapUsd = token.marketCapUsd,
            metrics5m = m5, score = score, safety = safety, config = config, nowMs = nowMs
        )

        if (buyDecision.shouldNotify) {
            persistAndNotify(token, buyDecision.type, score.total, buyDecision.reasons, m5)
        }

        val prevScore = lastScoreByMint[mint]
        lastScoreByMint[mint] = score.total
        if (prevScore != null) {
            val sellDecision = signalEngine.evaluateSellTrigger(
                mint = mint, previousScore = prevScore, currentScore = score.total,
                metrics5m = m5, safety = safety, config = config, nowMs = nowMs
            )
            if (sellDecision != null && sellDecision.shouldNotify) {
                persistAndNotify(token, SignalType.SELL, score.total, sellDecision.reasons, m5)
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
                priceUsd = m5.latestPriceUsd
            )
        )
        if (type == SignalType.BUY || type == SignalType.SELL) {
            NotificationHelper.showSignalNotification(context, id, token.mint, token.symbol ?: token.mint.take(6), type, score, m5, reasons)
        }
    }

    private suspend fun logSystemEvent(category: String, message: String) {
        db.systemEventDao().insert(SystemEventEntity(timestamp = System.currentTimeMillis(), category = category, message = message))
    }

    private fun maxTrackedForBatteryMode(): Int = when (settings.batteryMode.value) {
        BatteryMode.PERFORMANCE -> 150
        BatteryMode.BALANCED -> 60
        BatteryMode.BATTERY_SAVER -> 20
    }
}
