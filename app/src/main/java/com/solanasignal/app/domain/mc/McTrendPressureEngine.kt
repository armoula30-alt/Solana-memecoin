package com.solanasignal.app.domain.mc

import kotlin.math.abs
import kotlin.math.max

/** One observation available at the time the signal was evaluated. */
data class McObservation(val timestampMs: Long, val marketCapUsd: Double)

enum class McTrendClassification {
    ACCELERATING_UP, RISING_STABLE, RISING_SLOWING, FLAT, FALLING, ACCELERATING_DOWN, UNKNOWN
}

data class McWindowPressure(
    val windowSeconds: Int,
    val totalUpMove: Double?,
    val totalDownMove: Double?,
    val netMcChange: Double?,
    val netMcChangePercent: Double?,
    val directionalPressure: Double?,
    val persistence: Double?,
    val positiveIntervalsPct: Double?,
    val negativeIntervalsPct: Double?,
    val consecutivePositive: Int,
    val consecutiveNegative: Int,
    val higherHighCount: Int,
    val higherLowCount: Int,
    val lowerHighCount: Int,
    val lowerLowCount: Int,
    val recentHigh: Double?,
    val drawdownFromHighPct: Double?,
    val velocityPerSecond: Double?,
    val previousVelocityPerSecond: Double?,
    val accelerationPerSecond: Double?,
    val classification: McTrendClassification,
    val dataConfidence: Int,
    val validIntervals: Int
)

data class McTrendPressureResult(
    val windows: Map<Int, McWindowPressure>,
    val primary: McWindowPressure?,
    val featureScore: Int?,
    val dataConfidence: Int,
    val explanation: String,
    val warnings: List<String>
)

/** Bounded, look-ahead-safe calculation. Observations must be ordered and historical only. */
class McTrendPressureEngine(
    private val epsilon: Double = 1e-9,
    private val minimumIntervals: Int = 3
) {
    fun evaluate(
        observations: List<McObservation>,
        windowSeconds: List<Int> = listOf(60, 180, 300),
        nowMs: Long = observations.maxOfOrNull { it.timestampMs } ?: 0L
    ): McTrendPressureResult {
        val clean = observations.asSequence()
            .filter { it.marketCapUsd.isFinite() && it.marketCapUsd > 0.0 && it.timestampMs <= nowMs }
            .distinctBy { it.timestampMs }
            .sortedBy { it.timestampMs }
            .toList()
        val calculated = windowSeconds.distinct().sorted().associateWith { window -> calculate(clean, window, nowMs) }
        val primary = calculated[windowSeconds.minByOrNull { abs(it - 180) }] ?: calculated.values.firstOrNull()
        val available = calculated.values.filter { it.directionalPressure != null }
        val confidence = if (available.isEmpty()) 0 else available.map { it.dataConfidence }.average().toInt().coerceIn(0, 100)
        val score = primary?.let { p ->
            val pressure = p.directionalPressure ?: return@let null
            val persistence = p.persistence ?: return@let null
            val velocity = ((p.velocityPerSecond ?: 0.0) / max(abs(primary.netMcChange ?: 1.0), 1.0) * 100.0 + 50.0).coerceIn(0.0, 100.0)
            (pressure * 45.0 + persistence * 30.0 + velocity * 15.0 + (100.0 - (p.drawdownFromHighPct ?: 100.0).coerceIn(0.0, 100.0)) * 0.10).toInt().coerceIn(0, 100)
        }
        val explanation = primary?.let { p ->
            if (p.directionalPressure == null) "MC trend pressure UNKNOWN: insufficient observations"
            else "MC trend pressure ${"%.0f".format(p.directionalPressure * 100)}% over ${p.windowSeconds}s, net ${"%+.2f".format(p.netMcChangePercent)}%, ${"%.0f".format((p.persistence ?: 0.0) * 100)}% positive intervals, ${p.higherHighCount} higher highs"
        } ?: "MC trend pressure UNKNOWN: no observations"
        val warnings = buildList {
            if (primary == null || primary.validIntervals < minimumIntervals) add("Insufficient MC observations")
            if (primary?.drawdownFromHighPct ?: 0.0 > 20.0) add("MC drawdown from recent high is elevated")
            if (primary?.let { it.consecutiveNegative > it.consecutivePositive } == true) add("Negative MC intervals currently dominate")
        }
        return McTrendPressureResult(calculated, primary, score, confidence, explanation, warnings)
    }

    private fun calculate(all: List<McObservation>, windowSeconds: Int, nowMs: Long): McWindowPressure {
        val start = nowMs - windowSeconds * 1000L
        val points = all.filter { it.timestampMs >= start && it.timestampMs <= nowMs }
        val deltas = points.zipWithNext().map { (a, b) -> b.marketCapUsd - a.marketCapUsd }
        val valid = deltas.size
        if (points.size < 2 || valid < minimumIntervals) return unknown(windowSeconds, valid)
        val up = deltas.sumOf { max(it, 0.0) }
        val down = deltas.sumOf { max(-it, 0.0) }
        val first = points.first().marketCapUsd
        val last = points.last().marketCapUsd
        val net = last - first
        val elapsed = (points.last().timestampMs - points.first().timestampMs).coerceAtLeast(1L) / 1000.0
        val half = max(1, points.size / 2)
        val previousPoints = points.take(half)
        val recentPoints = points.drop(half)
        val previousVelocity = velocity(previousPoints)
        val velocity = net / elapsed
        val acceleration = velocity - (previousVelocity ?: velocity)
        val pos = deltas.count { it > 0.0 }
        val neg = deltas.count { it < 0.0 }
        val highs = points.map { it.marketCapUsd }
        val recentHigh = highs.maxOrNull() ?: last
        val drawdown = ((recentHigh - last) / recentHigh * 100.0).coerceAtLeast(0.0)
        val higherHighs = highs.zipWithNext().count { (a, b) -> b > a }
        val lowerLows = highs.zipWithNext().count { (a, b) -> b < a }
        val pressure = up / (up + down + epsilon)
        val netPct = net / first * 100.0
        val classification = classify(pressure, velocity, previousVelocity, acceleration, netPct)
        val confidence = ((valid.coerceAtMost(10) / 10.0) * 60.0 + (if (points.size >= 5) 20 else 0) + (if (recentHigh > 0) 20 else 0)).toInt().coerceIn(0, 100)
        return McWindowPressure(windowSeconds, up, down, net, netPct, pressure, pos.toDouble() / valid, pos * 100.0 / valid, neg * 100.0 / valid, consecutive(deltas, true), consecutive(deltas, false), higherHighs, max(0, higherHighs - lowerLows), 0, lowerLows, recentHigh, drawdown, velocity, previousVelocity, acceleration, classification, confidence, valid)
    }

    private fun unknown(window: Int, valid: Int) = McWindowPressure(window, null, null, null, null, null, null, null, null, 0, 0, 0, 0, 0, 0, null, null, null, null, null, McTrendClassification.UNKNOWN, 0, valid)
    private fun velocity(points: List<McObservation>): Double? = if (points.size < 2) null else (points.last().marketCapUsd - points.first().marketCapUsd) / ((points.last().timestampMs - points.first().timestampMs).coerceAtLeast(1L) / 1000.0)
    private fun consecutive(deltas: List<Double>, positive: Boolean): Int = deltas.asReversed().takeWhile { if (positive) it > 0 else it < 0 }.size
    private fun classify(pressure: Double, velocity: Double, previous: Double?, acceleration: Double, netPct: Double): McTrendClassification = when {
        netPct < 0 && acceleration < 0 -> McTrendClassification.ACCELERATING_DOWN
        netPct < 0 -> McTrendClassification.FALLING
        velocity <= 0.0 -> McTrendClassification.FLAT
        previous != null && acceleration > abs(previous) * 0.15 -> McTrendClassification.ACCELERATING_UP
        previous != null && acceleration < -abs(previous) * 0.15 -> McTrendClassification.RISING_SLOWING
        pressure >= 0.55 -> McTrendClassification.RISING_STABLE
        else -> McTrendClassification.FLAT
    }
}
