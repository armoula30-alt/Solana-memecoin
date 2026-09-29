package com.solanasignal.app.domain.paper

/** Conservative defaults for simulation only; automatic mode is OFF until explicitly enabled. */
data class AutoPaperConfig(
    val entryAmountUsd: Double = 25.0,
    val minMomentum: Int = 80,
    val maxRisk: Int = 40,
    val minDataConfidence: Int = 70,
    val minMcVelocityPctPerMinute: Double = 0.25,
    val minLiquidityUsd: Double = 5_000.0,
    val takeProfitPct: Double = 30.0,
    val stopLossPct: Double = -15.0,
    val cooldownMs: Long = 120_000L,
    val maxOpenPositions: Int = 5
)

data class AutoPaperStatus(
    val enabled: Boolean = false,
    val state: String = "OFF",
    val lastDecision: String = "Automatic paper trading is disabled",
    val entries: Int = 0,
    val exits: Int = 0
)
