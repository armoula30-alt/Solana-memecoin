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
    private val solPriceProvider = SolPriceProvider()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var mockSource: MockEventSource? = null
    private var webSocketManager: PumpPortalWebSocketManager? = null

    private val lastScoreByMint = ConcurrentHashMap<String, Int>()
    private val trackedMints = ConcurrentHashMap.newKeySet<String>()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    val connectionState: StateFlow<ConnectionState>
        get() = webSocketManager?.connectionState ?: MutableStateFlow(ConnectionState.DISCONNECTED).asStateFlow()

    val solUsdPrice: StateFlow<Double?> get() = solPriceProvider.priceUsd

    fun start() {
        if (_running.value) return
        _running.value = true
        solPriceProvider.start(scope)

        if (settings.mockMode.value) startMock() else startLive()
    }

    fun stop() {
        _running.value = false
        mockSource?.stop()
        webSocketManager?.stop()
        webSocketManager = null
        solPriceProvider.stop()
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
        // Mock mode fabricates a fake SOL price too, so USD figures aren't stuck on
        // UNKNOWN while demoing - clearly labeled MOCK MODE in the UI regardless.
        solPriceProvider.stop()
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
            is ParseResult.Unknown -> logSystemEvent("PARSER_UNKNOWN", "Unhandled event type: ${result.rawType}")
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
            db.tokenDao().upsert(
                token.copy(
                    lastPriceUsd = priceUsd ?: token.lastPriceUsd,
                    marketCapSol = event.marketCapSol ?: token.marketCapSol,
                    liquiditySol = event.vSolInBondingCurve ?: token.liquiditySol,
                    marketCapUsd = usd(event.marketCapSol, solPrice) ?: token.marketCapUsd,
                    liquidityUsd = usd(event.vSolInBondingCurve, solPrice) ?: token.liquidityUsd
                )
            )
        }

        analyzeAndMaybeSignal(event.mint, event.timestampEpochMs)
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
        val ageSeconds = (nowMs - token.firstSeenAtEpochMs) / 1000

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
            NotificationHelper.showSignalNotification(
                context, id, token.mint, token.poolAddress, token.symbol ?: token.mint.take(6), type, score, m5, reasons
            )
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

    private fun usd(solValue: Double?, solPriceUsd: Double?): Double? =
        if (solValue != null && solPriceUsd != null) solValue * solPriceUsd else null
}
