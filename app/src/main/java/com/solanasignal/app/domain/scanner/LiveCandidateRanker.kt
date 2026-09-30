package com.solanasignal.app.domain.scanner

import com.solanasignal.app.data.market.LiveMarketState
import com.solanasignal.app.data.market.MarketDataSource
import com.solanasignal.app.data.market.MarketDataStatus
import com.solanasignal.app.data.pumpportal.TradeSide

/** UI discovery rank only; it never feeds or changes production SignalEngine decisions. */
data class LiveCandidateRank(
    val score: Int?,
    val state: String,
    val return60sPct: Double?,
    val accelerationPctPer15s: Double?,
    val buyPressurePct: Double?,
    val positiveIntervalsPct: Double?
)

class LiveCandidateRanker {
    fun rank(state: LiveMarketState, nowMs: Long): LiveCandidateRank {
        val points = state.points.filter {
            it.source in setOf(MarketDataSource.PUMPPORTAL_TRADE, MarketDataSource.PUMPDEV_TRADE) &&
                it.timestampMs in (nowMs - 60_000L)..nowMs &&
                (state.tradeTrackingStartedAtMs == null || it.timestampMs >= state.tradeTrackingStartedAtMs) &&
                it.priceUsd != null
        }.sortedBy { it.timestampMs }
        val prices = points.mapNotNull { point -> point.priceUsd?.takeIf { it > 0.0 }?.let { point.timestampMs to it } }
        val return60 = observedReturn(prices, nowMs - 60_000L, nowMs, minimumSpanMs = 45_000L)
        val earlierReturn = observedReturn(prices, nowMs - 30_000L, nowMs - 15_000L, minimumSpanMs = 10_000L)
        val recentReturn = observedReturn(prices, nowMs - 15_000L, nowMs, minimumSpanMs = 10_000L)
        val acceleration = if (earlierReturn != null && recentReturn != null) recentReturn - earlierReturn else null
        val actualTrades = if (state.buyCount60s != null && state.sellCount60s != null) {
            state.recentTrades.filter { it.timestampMs in (nowMs - 60_000L)..nowMs }
        } else emptyList()
        val pressure = actualTrades.takeIf { it.isNotEmpty() }?.let { trades ->
            trades.count { it.side == TradeSide.BUY } * 100.0 / trades.size
        }
        val deltas = prices.zipWithNext().map { (left, right) -> right.second - left.second }
        val persistence = deltas.takeIf { it.isNotEmpty() }?.let { values -> values.count { it > 0.0 } * 100.0 / values.size }
        val enoughData = return60 != null && pressure != null && persistence != null && state.status == MarketDataStatus.LIVE
        val score = if (!enoughData) null else {
            val returnComponent = ((return60 ?: 0.0) / 25.0 * 50.0 + 50.0).coerceIn(0.0, 100.0)
            val accelerationComponent = ((acceleration ?: 0.0) / 10.0 * 50.0 + 50.0).coerceIn(0.0, 100.0)
            val pressureComponent = pressure ?: 50.0
            val persistenceComponent = persistence ?: 50.0
            (returnComponent * 0.35 + accelerationComponent * 0.25 + pressureComponent * 0.20 + persistenceComponent * 0.20).toInt().coerceIn(0, 100)
        }
        val classification = when {
            state.status == MarketDataStatus.DISCONNECTED -> "DISCONNECTED"
            state.status == MarketDataStatus.STALE -> "STALE"
            !enoughData -> "GATHERING DATA"
            (return60 ?: 0.0) > 0.0 && (persistence ?: 0.0) >= 60.0 && (acceleration ?: 0.0) > 0.0 -> "ACCELERATING"
            (return60 ?: 0.0) > 0.0 -> "RISING"
            (return60 ?: 0.0) < 0.0 -> "WEAKENING"
            else -> "FLAT"
        }
        return LiveCandidateRank(score, classification, return60, acceleration, pressure, persistence)
    }

    private fun observedReturn(
        points: List<Pair<Long, Double>>,
        fromMs: Long,
        toMs: Long,
        minimumSpanMs: Long
    ): Double? {
        val window = points.filter { it.first in fromMs..toMs }
        val first = window.firstOrNull() ?: return null
        val last = window.lastOrNull() ?: return null
        if (last.first - first.first < minimumSpanMs || first.second <= 0.0) return null
        return (last.second - first.second) / first.second * 100.0
    }
}
