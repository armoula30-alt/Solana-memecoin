package com.solanasignal.app.domain.manipulation

import com.solanasignal.app.domain.metrics.WindowMetrics
import kotlin.math.max

enum class ManipulationRiskLevel { LOW, MODERATE, HIGH, CRITICAL, UNKNOWN }

data class ManipulationFinding(val name: String, val points: Int, val explanation: String)

data class ManipulationRiskResult(
    val score: Int?,
    val level: ManipulationRiskLevel,
    val findings: List<ManipulationFinding>
)

/** Heuristic risk indicators only; this engine never labels a wallet or token as fraudulent. */
class ManipulationRiskEngine {
    fun evaluate(windows: Map<Int, WindowMetrics>, liquidityUsd: Double?): ManipulationRiskResult {
        val m1 = windows[60]
        val m5 = windows[300]
        if (m5 == null) return ManipulationRiskResult(null, ManipulationRiskLevel.UNKNOWN, emptyList())
        val findings = mutableListOf<ManipulationFinding>()
        val totalVolume = m5.buyVolumeUsd + m5.sellVolumeUsd
        val totalTrades = m5.totalTrades
        val avgTrade = if (totalTrades > 0) totalVolume / totalTrades else 0.0

        if (m5.uniqueBuyers >= 10 && avgTrade < 5.0) {
            findings += ManipulationFinding("many_small_buys", 25, "Many buyers with very small average trade size")
        }
        if (totalVolume > 0.0 && m5.largestBuyUsd / totalVolume > 0.60) {
            findings += ManipulationFinding("large_buy_dominance", 20, "One buy dominates recent volume")
        }
        if (totalTrades >= 20 && m5.uniqueBuyers <= 2) {
            findings += ManipulationFinding("repeated_buyer_activity", 25, "High trade frequency with very few unique buyers")
        }
        if (m5.buys >= 8 && m5.sells == 0) {
            findings += ManipulationFinding("one_sided_flow", 15, "No observed sells in a busy window; sellability remains unknown")
        }
        if (m1?.volumeVelocity != null && m1.volumeVelocity > 4.0 && m5.uniqueBuyers < 5) {
            findings += ManipulationFinding("volume_spike_narrow_participation", 20, "Sharp volume spike without broad participant growth")
        }
        if (liquidityUsd != null && totalVolume > 0.0 && totalVolume / max(liquidityUsd, 1.0) > 2.0) {
            findings += ManipulationFinding("volume_liquidity_stress", 15, "Recent volume is very large relative to observed liquidity")
        }
        val score = findings.sumOf { it.points }.coerceIn(0, 100)
        val level = when {
            score >= 75 -> ManipulationRiskLevel.CRITICAL
            score >= 50 -> ManipulationRiskLevel.HIGH
            score >= 25 -> ManipulationRiskLevel.MODERATE
            else -> ManipulationRiskLevel.LOW
        }
        return ManipulationRiskResult(score, level, findings)
    }
}
