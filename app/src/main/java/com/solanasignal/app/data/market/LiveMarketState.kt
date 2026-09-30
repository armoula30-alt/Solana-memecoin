package com.solanasignal.app.data.market

import com.solanasignal.app.data.pumpportal.ConnectionState
import com.solanasignal.app.data.pumpportal.TradeSide
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The origin is kept with every quote so simulated execution can reject REST snapshots. */
enum class MarketDataSource { PUMPPORTAL_TRADE, PUMPDEV_TRADE, DEXSCREENER, MOCK, UNKNOWN }
enum class MarketDataStatus { LIVE, STALE, DISCONNECTED, UNKNOWN }

data class MarketPoint(
    val timestampMs: Long,
    val priceUsd: Double?,
    val marketCapUsd: Double?,
    val liquidityUsd: Double?,
    val volumeUsd: Double?,
    val source: MarketDataSource,
    val side: TradeSide? = null
)

data class LiveTradeTick(
    val timestampMs: Long,
    val side: TradeSide,
    val priceUsd: Double?,
    val amountUsd: Double?,
    val trader: String?,
    val signature: String?
)

data class LiveMarketState(
    val mint: String,
    val symbol: String? = null,
    val name: String? = null,
    /** Latest known quote. Its source and timestamp must always be inspected. */
    val priceUsd: Double? = null,
    val priceSource: MarketDataSource = MarketDataSource.UNKNOWN,
    val priceUpdatedAtMs: Long? = null,
    val priceReceivedAtMs: Long? = null,
    val marketCapUsd: Double? = null,
    val liquidityUsd: Double? = null,
    val volumeUsd60s: Double? = null,
    val buyCount60s: Int? = null,
    val sellCount60s: Int? = null,
    val buyVolumeUsd60s: Double? = null,
    val sellVolumeUsd60s: Double? = null,
    val buyPressurePct60s: Double? = null,
    val lastTradePriceUsd: Double? = null,
    val lastTradeAtMs: Long? = null,
    val lastTradeReceivedAtMs: Long? = null,
    val tradeTrackingStartedAtMs: Long? = null,
    val updatedAtMs: Long = 0L,
    val status: MarketDataStatus = MarketDataStatus.UNKNOWN,
    val points: List<MarketPoint> = emptyList(),
    val recentTrades: List<LiveTradeTick> = emptyList()
) {
    fun isFreshTradeQuote(nowMs: Long, staleAfterMs: Long): Boolean =
        status == MarketDataStatus.LIVE &&
            priceSource in setOf(MarketDataSource.PUMPPORTAL_TRADE, MarketDataSource.PUMPDEV_TRADE) &&
            priceUsd != null && priceUsd > 0.0 &&
            priceUpdatedAtMs != null && nowMs - priceUpdatedAtMs in 0..staleAfterMs
            && priceReceivedAtMs != null && nowMs - priceReceivedAtMs in 0..staleAfterMs
            && lastTradeReceivedAtMs != null && nowMs - lastTradeReceivedAtMs in 0..staleAfterMs
}

/**
 * Single process-wide market snapshot shared by the scanner, chart, ranking and paper terminal.
 * It stores only bounded event/snapshot history; a timer may update freshness but never creates ticks.
 */
class LiveMarketStateRepository(
    val staleAfterMs: Long = 15_000L,
    private val historyWindowMs: Long = 5 * 60_000L,
    private val maxPointsPerMint: Int = 1_000,
    private val maxTradesPerMint: Int = 500
) {
    private val lock = Any()
    private val _states = MutableStateFlow<Map<String, LiveMarketState>>(emptyMap())
    val states: StateFlow<Map<String, LiveMarketState>> = _states.asStateFlow()
    private var socketState: ConnectionState = ConnectionState.DISCONNECTED
    private var socketConnectedAtMs: Long? = null

    fun updateDiscovery(
        mint: String,
        symbol: String?,
        name: String?,
        marketCapUsd: Double?,
        liquidityUsd: Double?,
        nowMs: Long
    ) = synchronized(lock) {
        val old = _states.value[mint]
        put((old ?: LiveMarketState(mint)).copy(
            symbol = symbol ?: old?.symbol,
            name = name ?: old?.name,
            marketCapUsd = marketCapUsd ?: old?.marketCapUsd,
            liquidityUsd = liquidityUsd ?: old?.liquidityUsd,
            updatedAtMs = nowMs
        ))
    }

    /** REST is hydration/fallback only; it is never considered a live execution quote. */
    fun updateRestSnapshot(
        mint: String,
        symbol: String?,
        name: String?,
        priceUsd: Double?,
        marketCapUsd: Double?,
        liquidityUsd: Double?,
        volumeUsd: Double?,
        nowMs: Long,
        source: MarketDataSource = MarketDataSource.DEXSCREENER
    ) = synchronized(lock) {
        val old = _states.value[mint] ?: LiveMarketState(mint)
        val useRestPrice = old.priceSource !in setOf(MarketDataSource.PUMPPORTAL_TRADE, MarketDataSource.PUMPDEV_TRADE) || old.priceUsd == null
        val validPrice = priceUsd?.takeIf { it.isFinite() && it > 0.0 }
        val point = if (validPrice != null || marketCapUsd != null) {
            MarketPoint(nowMs, validPrice, marketCapUsd, liquidityUsd, volumeUsd, source)
        } else null
        val points = appendPoint(old.points, point, nowMs)
        val next = old.copy(
            symbol = symbol ?: old.symbol,
            name = name ?: old.name,
            priceUsd = if (useRestPrice) validPrice ?: old.priceUsd else old.priceUsd,
            priceSource = if (useRestPrice && validPrice != null) source else old.priceSource,
            priceUpdatedAtMs = if (useRestPrice && validPrice != null) nowMs else old.priceUpdatedAtMs,
            priceReceivedAtMs = if (useRestPrice && validPrice != null) nowMs else old.priceReceivedAtMs,
            marketCapUsd = marketCapUsd ?: old.marketCapUsd,
            liquidityUsd = liquidityUsd ?: old.liquidityUsd,
            // Dex transaction aggregates may use 5m/1h buckets, not a verified rolling 60s window.
            // Keep them out of these counters; absent live event coverage remains UNKNOWN.
            updatedAtMs = nowMs,
            status = statusFor(old.lastTradeAtMs, if (useRestPrice && validPrice != null) nowMs else old.priceUpdatedAtMs, nowMs, old.tradeTrackingStartedAtMs),
            points = points
        )
        put(next)
    }

    fun updateTrade(
        mint: String,
        symbol: String?,
        name: String?,
        priceUsd: Double?,
        marketCapUsd: Double?,
        liquidityUsd: Double?,
        tick: LiveTradeTick,
        source: MarketDataSource = MarketDataSource.PUMPPORTAL_TRADE,
        nowMs: Long = tick.timestampMs
    ) = synchronized(lock) {
        val old = _states.value[mint] ?: LiveMarketState(mint)
        val recent = (old.recentTrades + tick)
            .filter { it.timestampMs in (nowMs - 60_000L)..nowMs && (old.tradeTrackingStartedAtMs == null || it.timestampMs >= old.tradeTrackingStartedAtMs) }
            .takeLast(maxTradesPerMint)
        val buys = recent.filter { it.side == TradeSide.BUY }
        val sells = recent.filter { it.side == TradeSide.SELL }
        val allAmountsKnown = recent.all { it.amountUsd != null }
        val buyAmountsKnown = buys.all { it.amountUsd != null }
        val sellAmountsKnown = sells.all { it.amountUsd != null }
        val validPrice = priceUsd?.takeIf { it.isFinite() && it > 0.0 }
        val validMc = marketCapUsd?.takeIf { it.isFinite() && it > 0.0 }
        val validLiquidity = liquidityUsd?.takeIf { it.isFinite() && it >= 0.0 }
        val point = if (validPrice != null || validMc != null) {
            MarketPoint(tick.timestampMs, validPrice, validMc, validLiquidity, tick.amountUsd, source, tick.side)
        } else null
        val next = old.copy(
            symbol = symbol ?: old.symbol,
            name = name ?: old.name,
            priceUsd = validPrice ?: old.priceUsd,
            priceSource = if (validPrice != null) source else old.priceSource,
            priceUpdatedAtMs = if (validPrice != null) tick.timestampMs else old.priceUpdatedAtMs,
            priceReceivedAtMs = if (validPrice != null) nowMs else old.priceReceivedAtMs,
            marketCapUsd = validMc ?: old.marketCapUsd,
            liquidityUsd = validLiquidity ?: old.liquidityUsd,
            volumeUsd60s = if (hasFullTradeWindow(old, nowMs) && allAmountsKnown) recent.sumOf { it.amountUsd!! } else null,
            buyCount60s = if (hasFullTradeWindow(old, nowMs)) buys.size else null,
            sellCount60s = if (hasFullTradeWindow(old, nowMs)) sells.size else null,
            buyVolumeUsd60s = if (hasFullTradeWindow(old, nowMs) && buyAmountsKnown) buys.sumOf { it.amountUsd!! } else null,
            sellVolumeUsd60s = if (hasFullTradeWindow(old, nowMs) && sellAmountsKnown) sells.sumOf { it.amountUsd!! } else null,
            buyPressurePct60s = if (hasFullTradeWindow(old, nowMs)) (buys.size + sells.size).takeIf { it > 0 }?.let { buys.size * 100.0 / it } else null,
            lastTradePriceUsd = validPrice ?: old.lastTradePriceUsd,
            lastTradeAtMs = tick.timestampMs,
            lastTradeReceivedAtMs = nowMs,
            updatedAtMs = nowMs,
            status = if ((socketState == ConnectionState.CONNECTED || source == MarketDataSource.MOCK) &&
                nowMs - tick.timestampMs <= staleAfterMs &&
                (source == MarketDataSource.MOCK || old.tradeTrackingStartedAtMs != null) &&
                (source == MarketDataSource.MOCK || socketConnectedAtMs == null || nowMs >= socketConnectedAtMs!!)
            ) MarketDataStatus.LIVE else statusFor(tick.timestampMs, if (validPrice != null) tick.timestampMs else old.priceUpdatedAtMs, nowMs, old.tradeTrackingStartedAtMs),
            points = appendPoint(old.points, point, nowMs),
            recentTrades = recent
        )
        put(next)
    }

    /** Called by the single two-second evaluator; this never appends a price point. */
    fun refreshStatuses(nowMs: Long) = synchronized(lock) {
        val updated = _states.value.mapValues { (_, old) ->
            val status = when {
                socketState == ConnectionState.DISCONNECTED || socketState == ConnectionState.RECONNECTING || socketState == ConnectionState.CONNECTING -> MarketDataStatus.DISCONNECTED
                socketState == ConnectionState.DEGRADED -> MarketDataStatus.STALE
                old.tradeTrackingStartedAtMs != null && old.lastTradeAtMs != null && nowMs - old.lastTradeAtMs <= staleAfterMs &&
                    old.lastTradeReceivedAtMs != null && nowMs - old.lastTradeReceivedAtMs <= staleAfterMs &&
                    old.lastTradeReceivedAtMs >= old.tradeTrackingStartedAtMs &&
                    (socketConnectedAtMs == null || old.lastTradeReceivedAtMs >= socketConnectedAtMs!!) -> MarketDataStatus.LIVE
                old.priceUpdatedAtMs != null -> MarketDataStatus.STALE
                else -> MarketDataStatus.UNKNOWN
            }
            val fullWindow = socketState == ConnectionState.CONNECTED && hasFullTradeWindow(old, nowMs)
            val recent = old.recentTrades.filter { it.timestampMs in (nowMs - 60_000L)..nowMs }
            val buys = recent.filter { it.side == TradeSide.BUY }
            val sells = recent.filter { it.side == TradeSide.SELL }
            val allAmountsKnown = recent.all { it.amountUsd != null }
            val buyAmountsKnown = buys.all { it.amountUsd != null }
            val sellAmountsKnown = sells.all { it.amountUsd != null }
            old.copy(
                status = status,
                buyCount60s = if (fullWindow) buys.size else null,
                sellCount60s = if (fullWindow) sells.size else null,
                volumeUsd60s = if (fullWindow && allAmountsKnown) recent.sumOf { it.amountUsd!! } else null,
                buyVolumeUsd60s = if (fullWindow && buyAmountsKnown) buys.sumOf { it.amountUsd!! } else null,
                sellVolumeUsd60s = if (fullWindow && sellAmountsKnown) sells.sumOf { it.amountUsd!! } else null,
                buyPressurePct60s = if (fullWindow) (buys.size + sells.size).takeIf { it > 0 }?.let { buys.size * 100.0 / it } else null,
                recentTrades = recent
            )
        }
        if (updated != _states.value) _states.value = updated
    }

    fun setConnectionState(state: ConnectionState, nowMs: Long = System.currentTimeMillis()) = synchronized(lock) {
        if (state == ConnectionState.CONNECTED && socketState != ConnectionState.CONNECTED) {
            socketConnectedAtMs = nowMs
            _states.value = _states.value.mapValues { (_, old) ->
                old.copy(
                    // Discovery connection alone is not consent/subscription for trade coverage.
                    tradeTrackingStartedAtMs = null,
                    buyCount60s = null,
                    sellCount60s = null,
                    buyVolumeUsd60s = null,
                    sellVolumeUsd60s = null,
                    volumeUsd60s = null,
                    buyPressurePct60s = null,
                    recentTrades = emptyList(),
                    status = if (old.priceUsd == null) MarketDataStatus.UNKNOWN else MarketDataStatus.STALE
                )
            }
        } else if (state == ConnectionState.DISCONNECTED || state == ConnectionState.RECONNECTING || state == ConnectionState.DEGRADED) {
            _states.value = _states.value.mapValues { (_, old) ->
                old.copy(
                    tradeTrackingStartedAtMs = null,
                    buyCount60s = null,
                    sellCount60s = null,
                    buyVolumeUsd60s = null,
                    sellVolumeUsd60s = null,
                    volumeUsd60s = null,
                    buyPressurePct60s = null,
                    recentTrades = emptyList()
                )
            }
        }
        socketState = state
        refreshStatuses(nowMs)
    }

    fun beginTradeTracking(mints: Collection<String>, nowMs: Long = System.currentTimeMillis()) = synchronized(lock) {
        val updated = _states.value.toMutableMap()
        mints.forEach { mint ->
            val old = updated[mint] ?: LiveMarketState(mint)
            updated[mint] = old.copy(
                tradeTrackingStartedAtMs = nowMs,
                buyCount60s = null,
                sellCount60s = null,
                buyVolumeUsd60s = null,
                sellVolumeUsd60s = null,
                volumeUsd60s = null,
                buyPressurePct60s = null,
                recentTrades = emptyList(),
                status = if (old.priceUsd == null) MarketDataStatus.UNKNOWN else MarketDataStatus.STALE,
                updatedAtMs = nowMs
            )
        }
        _states.value = updated
    }

    fun endTradeTracking(mints: Collection<String>, nowMs: Long = System.currentTimeMillis()) = synchronized(lock) {
        val updated = _states.value.toMutableMap()
        mints.forEach { mint ->
            val old = updated[mint] ?: return@forEach
            updated[mint] = old.copy(
                tradeTrackingStartedAtMs = null,
                buyCount60s = null,
                sellCount60s = null,
                buyVolumeUsd60s = null,
                sellVolumeUsd60s = null,
                volumeUsd60s = null,
                buyPressurePct60s = null,
                recentTrades = emptyList(),
                status = if (old.priceUsd == null) MarketDataStatus.UNKNOWN else MarketDataStatus.STALE,
                updatedAtMs = nowMs
            )
        }
        _states.value = updated
    }

    fun markRemoved(mint: String) = synchronized(lock) {
        val old = _states.value[mint] ?: return@synchronized
        put(old.copy(status = MarketDataStatus.STALE))
    }

    private fun appendPoint(old: List<MarketPoint>, point: MarketPoint?, nowMs: Long): List<MarketPoint> {
        val cutoff = nowMs - historyWindowMs
        val trimmed = old.filter { it.timestampMs >= cutoff }
        return (if (point == null) trimmed else trimmed + point).sortedBy { it.timestampMs }.takeLast(maxPointsPerMint)
    }

    private fun hasFullTradeWindow(state: LiveMarketState, nowMs: Long): Boolean =
        state.tradeTrackingStartedAtMs?.let { nowMs - it >= 60_000L } == true

    private fun statusFor(lastTradeAt: Long?, lastPriceAt: Long?, nowMs: Long, trackingStartedAtMs: Long?): MarketDataStatus = when {
        socketState == ConnectionState.DISCONNECTED || socketState == ConnectionState.RECONNECTING || socketState == ConnectionState.CONNECTING -> MarketDataStatus.DISCONNECTED
        socketState == ConnectionState.DEGRADED -> MarketDataStatus.STALE
        trackingStartedAtMs != null && lastTradeAt != null && nowMs - lastTradeAt <= staleAfterMs &&
            lastTradeAt >= trackingStartedAtMs && (socketConnectedAtMs == null || lastTradeAt >= socketConnectedAtMs!!) -> MarketDataStatus.LIVE
        lastPriceAt != null -> MarketDataStatus.STALE
        else -> MarketDataStatus.UNKNOWN
    }

    private fun put(state: LiveMarketState) {
        _states.value = _states.value + (state.mint to state)
    }
}
