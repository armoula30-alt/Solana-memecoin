package com.solanasignal.app.domain.outcome

val OUTCOME_HORIZONS_SECONDS = listOf(30, 60, 120, 300, 600, 900, 1800, 3600, 21600, 86400)

data class OutcomeObservation(
    val signalTimestamp: Long,
    val entryPriceUsd: Double?,
    val entryMarketCapUsd: Double?,
    val entryLiquidityUsd: Double?
)

data class FutureMarketPoint(
    val timestamp: Long,
    val priceUsd: Double?,
    val marketCapUsd: Double?,
    val liquidityUsd: Double?
)

data class OutcomeCheckpoint(
    val horizonSeconds: Int,
    val point: FutureMarketPoint?,
    val returnPct: Double?,
    val maxGainPct: Double?,
    val maxDrawdownPct: Double?,
    val timeToPeakSeconds: Int?,
    val timeToMaxDrawdownSeconds: Int?
)

/** Only points at or after the signal time are considered. This class is not used by engines. */
object OutcomeTracker {
    fun checkpoints(signal: OutcomeObservation, futurePoints: List<FutureMarketPoint>): List<OutcomeCheckpoint> {
        val ordered = futurePoints.filter { it.timestamp >= signal.signalTimestamp }.sortedBy { it.timestamp }
        return OUTCOME_HORIZONS_SECONDS.map { horizon ->
            val target = signal.signalTimestamp + horizon * 1000L
            val point = ordered.firstOrNull { it.timestamp >= target }
            val prices = ordered.filter { it.timestamp <= target }.mapNotNull { it.priceUsd }
            val entry = signal.entryPriceUsd
            val change = if (entry != null && entry > 0.0) point?.priceUsd?.let { (it - entry) / entry * 100.0 } else null
            val gains = prices.mapNotNull { if (entry != null && entry > 0.0) (it - entry) / entry * 100.0 else null }
            OutcomeCheckpoint(
                horizon, point, change, gains.maxOrNull(), gains.minOrNull(),
                gains.maxOrNull()?.let { peak -> ordered.firstOrNull { it.priceUsd?.let { p -> entry?.let { e -> (p - e) / e * 100.0 } == peak } == true }?.let { ((it.timestamp - signal.signalTimestamp) / 1000L).toInt() } },
                gains.minOrNull()?.let { low -> ordered.firstOrNull { it.priceUsd?.let { p -> entry?.let { e -> (p - e) / e * 100.0 } == low } == true }?.let { ((it.timestamp - signal.signalTimestamp) / 1000L).toInt() } }
            )
        }
    }
}
