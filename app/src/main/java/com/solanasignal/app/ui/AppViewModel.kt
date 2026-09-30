package com.solanasignal.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.solanasignal.app.data.market.LiveMarketState
import com.solanasignal.app.data.market.MarketDataSource
import com.solanasignal.app.data.market.MarketDataStatus
import com.solanasignal.app.data.chart.ChartCandle
import com.solanasignal.app.data.chart.ChartDataRepository
import com.solanasignal.app.data.chart.ChartInterval
import com.solanasignal.app.data.room.entities.SignalEntity
import com.solanasignal.app.data.room.entities.SystemEventEntity
import com.solanasignal.app.data.room.entities.TokenEntity
import com.solanasignal.app.data.room.entities.PaperPortfolioEntity
import com.solanasignal.app.data.room.entities.PaperPositionEntity
import com.solanasignal.app.data.room.entities.PaperTradeEntity
import com.solanasignal.app.data.room.entities.PaperWatchlistEntity
import com.solanasignal.app.data.room.entities.DiagnosticEventEntity
import com.solanasignal.app.data.telemetry.DiagnosticReportExporter
import com.solanasignal.app.data.telemetry.DiagnosticSeverity
import com.solanasignal.app.data.telemetry.MarketFeedProvider
import com.solanasignal.app.data.telemetry.StructuredTelemetryLogger
import com.solanasignal.app.domain.paper.PaperTradeResult
import com.solanasignal.app.domain.paper.PaperTradingEngine
import com.solanasignal.app.domain.paper.AutoPaperConfig
import com.solanasignal.app.domain.paper.AutoPaperStatus
import com.solanasignal.app.domain.scanner.LiveCandidateRank
import com.solanasignal.app.domain.scanner.LiveCandidateRanker
import com.solanasignal.app.data.settings.*
import com.solanasignal.app.di.ServiceLocator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx get() = getApplication<Application>()
    val settings: SettingsRepository = ServiceLocator.settings(ctx)
    private val db = ServiceLocator.database(ctx)
    private val orchestrator = ServiceLocator.orchestrator(ctx)
    private val marketStateRepository = ServiceLocator.marketState(ctx)
    private val telemetry: StructuredTelemetryLogger = ServiceLocator.telemetry(ctx)
    val liveMarketStates = marketStateRepository.states
    private val candidateRanker = LiveCandidateRanker()
    private val chartRepository = ChartDataRepository(db)
    private val paperEngine = PaperTradingEngine(db)
    val diagnosticRuntime = telemetry.runtime
    val diagnosticSessionId = telemetry.sessionId
    val diagnosticWindows = orchestrator.diagnosticWindowMetrics
    val diagnosticEvents: StateFlow<List<DiagnosticEventEntity>> = telemetry.sessionId
        .flatMapLatest { sessionId ->
            if (sessionId == null) flowOf(emptyList()) else db.diagnosticDao().observeRecent(sessionId, 200)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private var autoPaperJob: kotlinx.coroutines.Job? = null
    private val autoPaperConfig = AutoPaperConfig()
    private val _autoPaperStatus = MutableStateFlow(AutoPaperStatus())
    val autoPaperStatus: StateFlow<AutoPaperStatus> = _autoPaperStatus.asStateFlow()
    private val autoEntryTimes = mutableMapOf<String, Long>()
    private val previousScannerRanks = mutableMapOf<String, Int>()

    val running = orchestrator.running
    val connectionState = orchestrator.connectionState
    val solUsdPrice = orchestrator.solUsdPrice
    val reconnectCount = orchestrator.reconnectCount
    val parserErrorCount = orchestrator.parserErrorCount
    val eventsPerSecond = orchestrator.eventsPerSecond
    val trackedSubscriptionCount = orchestrator.trackedSubscriptionCount
    val dexScreenerEnrichedCount = orchestrator.dexScreenerEnrichedCount
    val tradesReceivedCount = orchestrator.tradesReceivedCount

    val tokens: StateFlow<List<TokenEntity>> =
        db.tokenDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val rankTicker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(2_000L)
        }
    }.onStart { emit(System.currentTimeMillis()) }

    val rankedTokens: StateFlow<List<RankedToken>> = combine(tokens, liveMarketStates, rankTicker) { values, market, now ->
        values.map { token ->
            val state = market[token.mint] ?: LiveMarketState(
                mint = token.mint,
                symbol = token.symbol,
                name = token.name,
                priceUsd = token.lastPriceUsd,
                marketCapUsd = token.marketCapUsd,
                liquidityUsd = token.liquidityUsd,
                status = MarketDataStatus.UNKNOWN
            )
            RankedToken(token, state, candidateRanker.rank(state, now))
        }.sortedWith(
            compareByDescending<RankedToken> { statusPriority(it.market.status) }
                .thenByDescending { it.rank.score ?: -1 }
                .thenByDescending { it.rank.return60sPct ?: Double.NEGATIVE_INFINITY }
                .thenByDescending { it.token.firstSeenAtEpochMs }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val signals: StateFlow<List<SignalEntity>> =
        db.signalDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val systemEvents: StateFlow<List<SystemEventEntity>> =
        db.systemEventDao().observeRecent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val paperPortfolio: StateFlow<PaperPortfolioEntity?> =
        db.paperTradingDao().observePortfolio().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val paperPositions: StateFlow<List<PaperPositionEntity>> =
        db.paperTradingDao().observePositions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val paperTrades: StateFlow<List<PaperTradeEntity>> =
        db.paperTradingDao().observeTrades().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val paperWatchlist: StateFlow<List<PaperWatchlistEntity>> =
        db.paperTradingDao().observeWatchlist().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val paperAnalytics: StateFlow<PaperAnalytics> = combine(paperPortfolio, paperPositions, paperTrades, liveMarketStates) { portfolio, positions, trades, market ->
        val freshMarks = positions.mapNotNull { position ->
            market[position.mint]?.takeIf { isFreshSelectedQuote(it, System.currentTimeMillis()) }
                ?.priceUsd?.let { price -> (price - position.averageEntryPriceUsd) * position.quantity to (price * position.quantity) }
        }
        val unrealized = when {
            positions.isEmpty() -> 0.0
            freshMarks.size == positions.size -> freshMarks.sumOf { it.first }
            else -> null
        }
        val equity = when {
            portfolio == null -> null
            positions.isEmpty() -> portfolio.cashUsd
            freshMarks.size == positions.size -> portfolio.cashUsd + freshMarks.sumOf { it.second }
            else -> null
        }
        val completed = trades.filter { it.side == "PAPER_SELL" }
        val wins = completed.count { (it.realizedPnlUsd ?: 0.0) > 0.0 }
        val losses = completed.count { (it.realizedPnlUsd ?: 0.0) < 0.0 }
        PaperAnalytics(portfolio?.cashUsd ?: 1_000.0, unrealized, portfolio?.realizedPnlUsd ?: 0.0, completed.size, if (completed.isEmpty()) null else wins.toDouble() / completed.size, wins, losses, equity)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PaperAnalytics())

    init {
        viewModelScope.launch { settings.traceLoggingEnabled.collect { telemetry.setTraceEnabled(it) } }
        viewModelScope.launch {
            rankedTokens.collect { ranked ->
                val now = System.currentTimeMillis()
                val currentMints = ranked.mapTo(mutableSetOf()) { it.token.mint }
                val removed = previousScannerRanks.keys.filter { it !in currentMints }
                removed.forEach { mint ->
                    val previous = previousScannerRanks.remove(mint)
                    telemetry.record(
                        component = "SCANNER_RANKING", eventType = "SCANNER_CANDIDATE_EXITED",
                        severity = DiagnosticSeverity.INFO, message = "Token left the scanner candidate set",
                        tokenAddress = mint, dataSource = settings.marketFeedProvider.value.sourceId,
                        receivedAtMs = now, metadata = mapOf("previousRank" to previous)
                    )
                }
                ranked.forEachIndexed { index, item ->
                    val mint = item.token.mint
                    val rank = index + 1
                    val previous = previousScannerRanks.put(mint, rank)
                    if (previous == rank) return@forEachIndexed
                    val market = item.market
                    val fields = linkedMapOf<String, Any?>(
                        "priceUsd" to market.priceUsd,
                        "marketCapUsd" to market.marketCapUsd,
                        "liquidityUsd" to market.liquidityUsd,
                        "volumeUsd60s" to market.volumeUsd60s,
                        "buyCount60s" to market.buyCount60s,
                        "sellCount60s" to market.sellCount60s,
                        "buyVolumeUsd60s" to market.buyVolumeUsd60s,
                        "sellVolumeUsd60s" to market.sellVolumeUsd60s,
                        "lastEventAtMs" to market.lastTradeAtMs
                    )
                    val available = fields.filterValues { it != null }.keys
                    telemetry.record(
                        component = "SCANNER_RANKING",
                        eventType = if (previous == null) "SCANNER_CANDIDATE_ENTERED" else "RANK_CHANGED",
                        severity = DiagnosticSeverity.INFO,
                        message = if (previous == null) "Token entered scanner ranking" else "Scanner rank changed",
                        tokenAddress = mint, dataSource = market.priceSource.name,
                        receivedAtMs = now,
                        metadata = mapOf(
                            "entryTimestampMs" to item.token.firstSeenAtEpochMs,
                            "currentRank" to rank, "previousRank" to previous,
                            "rankDelta" to previous?.minus(rank),
                            "rankScore" to item.rank.score, "rankState" to item.rank.state,
                            "return60sPct" to item.rank.return60sPct,
                            "accelerationPctPer15s" to item.rank.accelerationPctPer15s,
                            "buyPressurePct" to item.rank.buyPressurePct,
                            "positiveIntervalsPct" to item.rank.positiveIntervalsPct,
                            "marketState" to market.status.name,
                            "marketFields" to fields,
                            "availableFields" to available,
                            "missingFields" to fields.keys - available
                        )
                    )
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                recordPaperPortfolioSnapshot()
                delay(30_000L)
            }
        }
    }

    fun startScanner() = orchestrator.start()
    fun stopScanner() = orchestrator.stop()

    suspend fun paperBuy(mint: String, amountUsd: Double): PaperTradeResult {
        val token = db.tokenDao().getByMint(mint) ?: return PaperTradeResult.Rejected("Token not found")
        val state = liveMarketStates.value[mint] ?: return PaperTradeResult.Rejected("No live trade quote for this token")
        val now = System.currentTimeMillis()
        if (!canPaperTrade(mint, now)) {
            return PaperTradeResult.Rejected("Paper BUY requires a fresh quote from the selected live trade stream; REST snapshots and stale quotes are not executable")
        }
        val result = paperEngine.buy(mint, token.symbol, amountUsd, state.priceUsd ?: 0.0, state.liquidityUsd, state.marketCapUsd, marketDataSource = selectedMarketDataSource().name, signalTimestampMs = latestSignalTimestamp(mint, now))
        recordPaperTrade(result)
        return result
    }

    fun canPaperTrade(mint: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val provider = settings.marketFeedProvider.value
        val state = liveMarketStates.value[mint] ?: return false
        return settings.liveTradeStreamingEnabled.value && (!provider.requiresApiKey || settings.getApiKeyOrNull() != null) && !settings.mockMode.value &&
            isFreshSelectedQuote(state, nowMs)
    }

    suspend fun paperSell(mint: String, quantity: Double): PaperTradeResult {
        val token = db.tokenDao().getByMint(mint) ?: return PaperTradeResult.Rejected("Token not found")
        val state = liveMarketStates.value[mint] ?: return PaperTradeResult.Rejected("No live trade quote for this token")
        val now = System.currentTimeMillis()
        if (!canPaperTrade(mint, now)) {
            return PaperTradeResult.Rejected("Paper SELL requires a fresh quote from the selected live trade stream; REST snapshots and stale quotes are not executable")
        }
        val result = paperEngine.sell(mint, token.symbol, quantity, state.priceUsd ?: 0.0, state.liquidityUsd, state.marketCapUsd, marketDataSource = selectedMarketDataSource().name, signalTimestampMs = latestSignalTimestamp(mint, now))
        recordPaperTrade(result)
        return result
    }

    fun setAutoPaperTrading(enabled: Boolean) {
        if (enabled) {
            if (autoPaperJob?.isActive == true) return
            _autoPaperStatus.value = _autoPaperStatus.value.copy(enabled = true, state = "RUNNING", lastDecision = "Scanning live evidence")
            autoPaperJob = viewModelScope.launch { runAutoPaperLoop() }
        } else {
            autoPaperJob?.cancel()
            autoPaperJob = null
            _autoPaperStatus.value = _autoPaperStatus.value.copy(enabled = false, state = "OFF", lastDecision = "Automatic paper trading is disabled")
        }
    }

    private suspend fun runAutoPaperLoop() {
        while (true) {
            val now = System.currentTimeMillis()
            val currentTokens = tokens.value
            val currentPositions = db.paperTradingDao().let { dao -> currentTokens.mapNotNull { token -> dao.position(token.mint)?.let { token to it } } }
            var lastDecision = "No token passed all entry gates"
            currentPositions.forEach { (token, position) ->
                val quote = liveMarketStates.value[token.mint]?.takeIf { canPaperTrade(token.mint) } ?: return@forEach
                val price = quote.priceUsd ?: return@forEach
                val pnlPct = ((price - position.averageEntryPriceUsd) / position.averageEntryPriceUsd) * 100.0
                if (pnlPct >= autoPaperConfig.takeProfitPct || pnlPct <= autoPaperConfig.stopLossPct || (token.marketCapVelocityPct ?: 0.0) < 0.0) {
                    val result = paperEngine.sell(token.mint, token.symbol, position.quantity, price, quote.liquidityUsd, quote.marketCapUsd, "Auto exit: ${if (pnlPct >= autoPaperConfig.takeProfitPct) "take-profit" else if (pnlPct <= autoPaperConfig.stopLossPct) "stop-loss" else "MC trend turned negative"}", selectedMarketDataSource().name, signalTimestampMs = latestSignalTimestamp(token.mint, now))
                    recordPaperTrade(result)
                    if (result is PaperTradeResult.Success) _autoPaperStatus.value = _autoPaperStatus.value.copy(exits = _autoPaperStatus.value.exits + 1)
                }
            }
            if (currentPositions.size < autoPaperConfig.maxOpenPositions) {
                currentTokens.filter { token ->
                    val velocity = token.marketCapVelocityPct
                    val recent = autoEntryTimes[token.mint] ?: 0L
                    canPaperTrade(token.mint) && (liveMarketStates.value[token.mint]?.liquidityUsd ?: 0.0) >= autoPaperConfig.minLiquidityUsd &&
                        (token.momentumScore ?: -1) >= autoPaperConfig.minMomentum &&
                        (token.manipulationRiskScore ?: 101) <= autoPaperConfig.maxRisk &&
                        (token.dataConfidenceScore ?: 0) >= autoPaperConfig.minDataConfidence &&
                        velocity != null && velocity >= autoPaperConfig.minMcVelocityPctPerMinute &&
                        now - recent >= autoPaperConfig.cooldownMs && currentPositions.none { it.first.mint == token.mint }
                }.take(autoPaperConfig.maxOpenPositions - currentPositions.size).forEach { token ->
                    val quote = liveMarketStates.value[token.mint] ?: return@forEach
                    val result = paperEngine.buy(token.mint, token.symbol, autoPaperConfig.entryAmountUsd, quote.priceUsd ?: 0.0, quote.liquidityUsd, quote.marketCapUsd, "Auto entry: momentum + rising MC + risk gates", selectedMarketDataSource().name, signalTimestampMs = latestSignalTimestamp(token.mint, now))
                    recordPaperTrade(result)
                    if (result is PaperTradeResult.Success) {
                        autoEntryTimes[token.mint] = now
                        _autoPaperStatus.value = _autoPaperStatus.value.copy(entries = _autoPaperStatus.value.entries + 1)
                        lastDecision = "Entered ${token.symbol ?: token.mint.take(8)}: all gates passed"
                    } else if (result is PaperTradeResult.Rejected) lastDecision = "Entry rejected: ${result.reason}"
                }
            }
            _autoPaperStatus.value = _autoPaperStatus.value.copy(state = "RUNNING", lastDecision = lastDecision)
            delay(2_000L)
        }
    }

    suspend fun resetPaperPortfolio() = paperEngine.reset()

    fun addToWatchlist(mint: String) { viewModelScope.launch { db.paperTradingDao().addWatchlist(PaperWatchlistEntity(mint)) } }
    fun removeFromWatchlist(mint: String) { viewModelScope.launch { db.paperTradingDao().removeWatchlist(mint) } }

    fun setApiKey(key: String) {
        settings.setApiKey(key)
        orchestrator.refreshMarketFeedConnection()
    }
    fun clearApiKey() {
        settings.clearApiKey()
        orchestrator.refreshMarketFeedConnection()
    }
    fun setCodeCraftKey(key: String) = settings.setCodeCraftKey(key)
    fun clearCodeCraftKey() = settings.clearCodeCraftKey()
    fun setCodeCraftModel(model: String) = settings.setCodeCraftModel(model)
    fun setMockMode(enabled: Boolean) { settings.setMockMode(enabled); orchestrator.refreshMarketFeedConnection() }
    fun setLiveTradeStreamingEnabled(enabled: Boolean) = settings.setLiveTradeStreamingEnabled(enabled)
    fun setMarketFeedProvider(provider: MarketFeedProvider) { settings.setMarketFeedProvider(provider); orchestrator.refreshMarketFeedConnection() }
    fun setTraceLoggingEnabled(enabled: Boolean) = settings.setTraceLoggingEnabled(enabled)
    fun setBatteryMode(mode: BatteryMode) = settings.setBatteryMode(mode)
    fun updateFilters(config: FilterConfig) = settings.updateFilters(config)
    fun updateWeights(weights: ScoreWeights) = settings.updateWeights(weights)
    fun setRetention(policy: RetentionPolicy) = settings.setRetentionPolicy(policy)

    suspend fun signalCountToday(): Int {
        val since = todayStartMs()
        return db.signalDao().countSince(since)
    }

    suspend fun loadChart(mint: String, interval: ChartInterval): List<ChartCandle> =
        chartRepository.loadHistory(mint, interval)

    suspend fun exportDiagnostics(uri: android.net.Uri): Long {
        val session = diagnosticSessionId.value ?: error("No diagnostic session is active")
        val output = ctx.contentResolver.openOutputStream(uri) ?: error("Unable to open export destination")
        return output.use { DiagnosticReportExporter(db).writeCurrentSession(session, it, telemetry.runtime.value) }
    }

    fun diagnosticWindowsFor(mint: String, nowMs: Long = System.currentTimeMillis()) =
        diagnosticWindows.snapshot(mint, nowMs)

    private fun selectedMarketDataSource(): MarketDataSource = when (settings.marketFeedProvider.value) {
        MarketFeedProvider.PUMPPORTAL -> MarketDataSource.PUMPPORTAL_TRADE
        MarketFeedProvider.PUMPDEV -> MarketDataSource.PUMPDEV_TRADE
    }

    private fun isFreshSelectedQuote(state: LiveMarketState, nowMs: Long): Boolean =
        state.priceSource == selectedMarketDataSource() && state.isFreshTradeQuote(nowMs, marketStateRepository.staleAfterMs)

    private suspend fun latestSignalTimestamp(mint: String, nowMs: Long): Long? =
        db.signalDao().latestForMint(mint)?.timestamp?.takeIf { nowMs - it in 0..300_000L }

    private suspend fun recordPaperTrade(result: PaperTradeResult) {
        when (result) {
            is PaperTradeResult.Success -> {
                val trade = db.paperTradingDao().tradeById(result.tradeId) ?: return
                telemetry.record(
                    component = "PAPER_TRADING", eventType = "PAPER_FILL", severity = DiagnosticSeverity.INFO,
                    message = "Simulated ${trade.side} fill",
                    tokenAddress = trade.mint, dataSource = trade.marketDataSource,
                    eventTimestampMs = trade.signalTimestampMs, receivedAtMs = trade.timestamp,
                    metadata = mapOf(
                        "tradeId" to trade.id, "symbol" to trade.symbol, "side" to trade.side,
                        "requestedUsd" to trade.requestedUsd, "quantity" to trade.quantity,
                        "marketPriceUsd" to trade.marketPriceUsd, "fillPriceUsd" to trade.fillPriceUsd,
                        "liquidityUsd" to liveMarketStates.value[trade.mint]?.liquidityUsd,
                        "feeUsd" to trade.feeUsd, "slippageUsd" to trade.slippageUsd,
                        "realizedPnlUsd" to trade.realizedPnlUsd, "holdingDurationMs" to trade.holdingDurationMs,
                        "signalTimestampMs" to trade.signalTimestampMs, "reason" to trade.reason,
                        "simulated" to trade.simulated
                    )
                )
                recordPaperPortfolioSnapshot()
            }
            is PaperTradeResult.Rejected -> telemetry.record(
                component = "PAPER_TRADING", eventType = "PAPER_REJECTED", severity = DiagnosticSeverity.WARN,
                message = result.reason, dataSource = settings.marketFeedProvider.value.sourceId,
                metadata = mapOf("reason" to result.reason)
            )
        }
    }

    private suspend fun recordPaperPortfolioSnapshot() {
        val now = System.currentTimeMillis()
        val portfolio = db.paperTradingDao().portfolio() ?: return
        val positions = db.paperTradingDao().openPositionMints().mapNotNull { mint -> db.paperTradingDao().position(mint) }
        val marks = positions.map { position ->
            val state = liveMarketStates.value[position.mint]
            val fresh = state?.let { isFreshSelectedQuote(it, now) } == true
            mapOf(
                "mint" to position.mint,
                "quantity" to position.quantity,
                "averageEntryPriceUsd" to position.averageEntryPriceUsd,
                "markPriceUsd" to state?.priceUsd.takeIf { fresh },
                "markSource" to state?.priceSource?.name,
                "markReceivedAtMs" to state?.priceReceivedAtMs,
                "quoteStatus" to if (fresh) "FRESH" else "UNKNOWN_OR_STALE",
                "unrealizedPnlUsd" to if (fresh && state?.priceUsd != null) (state.priceUsd - position.averageEntryPriceUsd) * position.quantity else null
            )
        }
        val freshValues = marks.mapNotNull { it["unrealizedPnlUsd"] as? Double }
        val unrealized = if (freshValues.size == positions.size) freshValues.sum() else null
        telemetry.record(
            component = "PAPER_TRADING", eventType = "PORTFOLIO_SNAPSHOT", severity = DiagnosticSeverity.INFO,
            message = "Paper portfolio state snapshot", dataSource = settings.marketFeedProvider.value.sourceId,
            receivedAtMs = now,
            metadata = mapOf(
                "cashUsd" to portfolio.cashUsd, "realizedPnlUsd" to portfolio.realizedPnlUsd,
                "unrealizedPnlUsd" to unrealized,
                "equityUsd" to if (unrealized != null) portfolio.cashUsd + positions.sumOf { it.quantity * (liveMarketStates.value[it.mint]?.priceUsd ?: it.averageEntryPriceUsd) } else null,
                "openPositionCount" to positions.size, "positions" to marks
            )
        )
    }

    private fun todayStartMs(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

data class RankedToken(
    val token: TokenEntity,
    val market: LiveMarketState,
    val rank: LiveCandidateRank
)

private fun statusPriority(status: MarketDataStatus): Int = when (status) {
    MarketDataStatus.LIVE -> 3
    MarketDataStatus.STALE -> 2
    MarketDataStatus.UNKNOWN -> 1
    MarketDataStatus.DISCONNECTED -> 0
}

data class PaperAnalytics(
    val cashUsd: Double = 1_000.0,
    val unrealizedPnlUsd: Double? = 0.0,
    val realizedPnlUsd: Double = 0.0,
    val closedTrades: Int = 0,
    val winRate: Double? = null,
    val wins: Int = 0,
    val losses: Int = 0,
    val equityUsd: Double? = 1_000.0
)
