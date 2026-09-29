package com.solanasignal.app.data.settings

/** Central, transparent configuration for the quantitative signal engine. */
data class EngineConfig(
    val windowsSeconds: List<Int> = listOf(10, 30, 60, 180, 300, 600),
    val minimumObservationCount: Int = 3,
    val minimumLiquidityUsd: Double = 5_000.0,
    val dataFreshnessSeconds: Int = 30,
    val antiSpikeLargestTradeShare: Double = 0.60,
    val antiSpikeMinimumUniqueBuyers: Int = 3,
    val earlyMomentumThreshold: Int = 60,
    val strongMomentumThreshold: Int = 75,
    val highRiskThreshold: Int = 60,
    val collapseRiskThreshold: Int = 70,
    val signalQualityThreshold: Int = 55,
    val momentumMcWeight: Double = 0.20,
    val momentumBuyPressureWeight: Double = 0.20,
    val momentumBuyAccelerationWeight: Double = 0.15,
    val momentumBuyerGrowthWeight: Double = 0.15,
    val momentumPriceWeight: Double = 0.10,
    val momentumLiquidityWeight: Double = 0.10,
    val momentumHolderWeight: Double = 0.05,
    val momentumDeployerWeight: Double = 0.05
)
