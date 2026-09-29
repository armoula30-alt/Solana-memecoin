package com.solanasignal.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import com.solanasignal.app.domain.paper.PaperTradeResult
import com.solanasignal.app.domain.paper.PaperTradingEngine
import com.solanasignal.app.data.settings.*
import com.solanasignal.app.di.ServiceLocator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx get() = getApplication<Application>()
    val settings: SettingsRepository = ServiceLocator.settings(ctx)
    private val db = ServiceLocator.database(ctx)
    private val orchestrator = ServiceLocator.orchestrator(ctx)
    private val chartRepository = ChartDataRepository(db)
    private val paperEngine = PaperTradingEngine(db)

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

    val paperAnalytics: StateFlow<PaperAnalytics> = combine(paperPortfolio, paperPositions, paperTrades, tokens) { portfolio, positions, trades, marketTokens ->
        val tokenMap = marketTokens.associateBy { it.mint }
        val unrealized = positions.sumOf { position ->
            val current = tokenMap[position.mint]?.lastPriceUsd ?: position.currentPriceUsd
            (current - position.averageEntryPriceUsd) * position.quantity
        }
        val completed = trades.filter { it.side == "PAPER_SELL" }
        val wins = completed.count { (it.realizedPnlUsd ?: 0.0) > 0.0 }
        val losses = completed.count { (it.realizedPnlUsd ?: 0.0) < 0.0 }
        PaperAnalytics(portfolio?.cashUsd ?: 1_000.0, unrealized, portfolio?.realizedPnlUsd ?: 0.0, completed.size, if (completed.isEmpty()) null else wins.toDouble() / completed.size, wins, losses)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PaperAnalytics())

    fun startScanner() = orchestrator.start()
    fun stopScanner() = orchestrator.stop()

    suspend fun paperBuy(mint: String, amountUsd: Double): PaperTradeResult {
        val token = db.tokenDao().getByMint(mint) ?: return PaperTradeResult.Rejected("Token not found")
        return paperEngine.buy(mint, token.symbol, amountUsd, token.lastPriceUsd ?: 0.0, token.liquidityUsd, token.marketCapUsd)
    }

    suspend fun paperSell(mint: String, quantity: Double): PaperTradeResult {
        val token = db.tokenDao().getByMint(mint) ?: return PaperTradeResult.Rejected("Token not found")
        return paperEngine.sell(mint, token.symbol, quantity, token.lastPriceUsd ?: 0.0, token.liquidityUsd, token.marketCapUsd)
    }

    suspend fun resetPaperPortfolio() = paperEngine.reset()

    fun addToWatchlist(mint: String) { viewModelScope.launch { db.paperTradingDao().addWatchlist(PaperWatchlistEntity(mint)) } }
    fun removeFromWatchlist(mint: String) { viewModelScope.launch { db.paperTradingDao().removeWatchlist(mint) } }

    fun setApiKey(key: String) = settings.setApiKey(key)
    fun clearApiKey() = settings.clearApiKey()
    fun setCodeCraftKey(key: String) = settings.setCodeCraftKey(key)
    fun clearCodeCraftKey() = settings.clearCodeCraftKey()
    fun setCodeCraftModel(model: String) = settings.setCodeCraftModel(model)
    fun setMockMode(enabled: Boolean) = settings.setMockMode(enabled)
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

    private fun todayStartMs(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

data class PaperAnalytics(
    val cashUsd: Double = 1_000.0,
    val unrealizedPnlUsd: Double = 0.0,
    val realizedPnlUsd: Double = 0.0,
    val closedTrades: Int = 0,
    val winRate: Double? = null,
    val wins: Int = 0,
    val losses: Int = 0
)
