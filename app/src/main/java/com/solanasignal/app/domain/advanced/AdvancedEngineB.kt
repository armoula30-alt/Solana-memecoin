package com.solanasignal.app.domain.advanced

import com.solanasignal.app.domain.live.LiveFreshness
import com.solanasignal.app.domain.live.LiveMarketState
import kotlin.math.abs
import kotlin.math.max

/** Experimental Baseline B outputs. Null feature values are UNKNOWN, never zero-filled. */
data class BFeature(val name: String, val value: Double?, val reason: String)

data class MomentumBResult(
    val momentum: Double?,
    val priceVelocity: Double?,
    val priceAcceleration: Double?,
    val marketCapVelocity: Double?,
    val marketCapAcceleration: Double?,
    val volumeVelocity: Double?,
    val volumeAcceleration: Double?,
    val tradeFrequencyAcceleration: Double?,
    val consistency: Double?,
    val pullbackDepth: Double?,
    val recoverySpeed: Double?,
    val higherHighs: Int?,
    val higherLows: Int?,
    val features: List<BFeature>
)

data class BuyPressureBResult(
    val buyVolume: Double?,
    val sellVolume: Double?,
    val netFlow: Double?,
    val imbalance: Double?,
    val persistentBuySideDominance: Double?,
    val features: List<BFeature>
)

data class LiquidityBResult(
    val liquidity: Double?,
    val liquidityToMarketCap: Double?,
    val liquidityVelocity: Double?,
    val deterioration: Double?,
    val stability: Double?,
    val features: List<BFeature>
)

data class WalletBResult(
    val uniqueBuyers: Int?,
    val uniqueSellers: Int?,
    val buyerGrowth: Double?,
    val sellerGrowth: Double?,
    val buyerSellerRatio: Double?,
    val repeatedWalletRatio: Double?,
    val concentration: Double?,
    val clustering: Double?,
    val features: List<BFeature>
)

enum class MarketRegime { QUIET, NORMAL, HOT, EXTREME, UNKNOWN }

data class CollapseRiskBResult(val collapseRisk: Double?, val reasonCodes: List<String>, val features: List<BFeature>)
data class PumpPotentialBResult(val pumpPotential: Double?, val reasonCodes: List<String>, val features: List<BFeature>)
data class DataConfidenceBResult(val dataConfidence: Double, val missingFields: List<String>, val reasons: List<String>)
data class SignalQualityBResult(val signalQuality: Double?, val reasons: List<String>, val warnings: List<String>)

enum class AdvancedSignalState { DISCOVERED, WATCH, BUILDING, ACCELERATING, CONFIRMED, STRONG_SIGNAL, PEAKING, WEAKENING, EXIT_RISK, STALE, REMOVED }

data class AdvancedSignalResult(
    val mint: String,
    val state: AdvancedSignalState,
    val signalQuality: Double?,
    val pumpPotential: Double?,
    val collapseRisk: Double?,
    val dataConfidence: Double,
    val marketRegime: MarketRegime,
    val momentum: MomentumBResult,
    val buyPressure: BuyPressureBResult,
    val liquidity: LiquidityBResult,
    val wallets: WalletBResult,
    val reasons: List<String>,
    val warnings: List<String>
)

class MomentumEngineB {
    fun evaluate(state: LiveMarketState): MomentumBResult {
        val windows = state.windows.values.sortedBy { it.seconds }
        val m5 = state.windows[300]
        val priceVelocity = state.priceVelocity ?: pctPerMinute(m5?.priceChangePct, m5?.seconds)
        val marketCapVelocity = state.marketCapVelocity ?: pctPerMinute(m5?.marketCapChangePct, m5?.seconds)
        val consistency = state.momentumConsistency ?: windows.mapNotNull { it.priceChangePct }.takeIf { it.size >= 2 }?.let { values ->
            values.count { it > 0.0 }.toDouble() / values.size * 100.0
        }
        val higherHighs = windows.mapNotNull { it.marketCapUsd }.zipWithNext().count { it.second > it.first }.takeIf { windows.size >= 2 }
        val higherLows = windows.mapNotNull { it.priceUsd }.zipWithNext().count { it.second > it.first }.takeIf { windows.size >= 2 }
        val values = listOfNotNull(
            state.momentum,
            priceVelocity?.let { bounded(50.0 + it * 2.0) },
            marketCapVelocity?.let { bounded(50.0 + it * 2.0) },
            state.volumeVelocity?.let { bounded(it * 50.0) },
            consistency
        )
        val momentum = values.takeIf { it.isNotEmpty() }?.average()?.coerceIn(0.0, 100.0)
        return MomentumBResult(
            momentum, priceVelocity, state.priceAcceleration, marketCapVelocity,
            state.marketCapAcceleration, state.volumeVelocity, state.volumeAcceleration,
            state.tradeFrequencyAcceleration, consistency, state.pullbackDepth,
            state.recoverySpeed, higherHighs, higherLows,
            listOf(
                BFeature("price_velocity", priceVelocity, "Real price observations only"),
                BFeature("price_acceleration", state.priceAcceleration, "Requires multiple real price observations"),
                BFeature("market_cap_velocity", marketCapVelocity, "Real market-cap observations only"),
                BFeature("market_cap_acceleration", state.marketCapAcceleration, "Requires multiple real market-cap observations"),
                BFeature("volume_velocity", state.volumeVelocity, "Provider/window volume velocity"),
                BFeature("volume_acceleration", state.volumeAcceleration, "Requires comparable volume windows"),
                BFeature("consistency", consistency, "Share of available windows with positive movement"),
                BFeature("pullback_depth", state.pullbackDepth, "Requires a prior observed high"),
                BFeature("recovery_speed", state.recoverySpeed, "Requires observed pullback and recovery")
            )
        )
    }

    private fun pctPerMinute(change: Double?, seconds: Int?): Double? =
        if (change != null && seconds != null && seconds > 0) change * 60.0 / seconds else null

    private fun bounded(value: Double) = value.coerceIn(0.0, 100.0)
}

class BuyPressureEngineB {
    fun evaluate(state: LiveMarketState): BuyPressureBResult {
        val buy = state.buyVolumeUsd
        val sell = state.sellVolumeUsd
        val total = if (buy != null && sell != null) buy + sell else null
        val imbalance = if (total != null && total > 0.0) (buy!! - sell!!) / total else null
        val windows = state.windows.values.sortedBy { it.seconds }
        val dominance = windows.mapNotNull { window ->
            val b = window.buyVolumeUsd
            val s = window.sellVolumeUsd
            if (b != null && s != null && b + s > 0.0) b > s else null
        }.takeIf { it.size >= 2 }?.let { it.count { value -> value }.toDouble() / it.size * 100.0 }
        return BuyPressureBResult(
            buy, sell, if (buy != null && sell != null) buy - sell else null, imbalance,
            dominance,
            listOf(
                BFeature("buy_volume", buy, "Only observed trade amounts"),
                BFeature("sell_volume", sell, "Only observed trade amounts"),
                BFeature("buy_sell_imbalance", imbalance, "Net flow divided by observed total volume"),
                BFeature("buy_velocity", state.buyVelocity, "Requires comparable windows"),
                BFeature("sell_velocity", state.sellVelocity, "Requires comparable windows"),
                BFeature("persistent_buy_side_dominance", dominance, "Requires at least two observed windows")
            )
        )
    }
}

class LiquidityEngineB {
    fun evaluate(state: LiveMarketState): LiquidityBResult {
        val ratio = state.liquidityToMarketCap ?: if (state.liquidityUsd != null && state.marketCapUsd != null && state.marketCapUsd > 0.0) state.liquidityUsd / state.marketCapUsd else null
        val deterioration = state.liquidityRisk ?: state.windows.values.sortedBy { it.seconds }.mapNotNull { it.liquidityUsd }.takeIf { it.size >= 2 }?.let { values ->
            val first = values.first()
            if (first > 0.0) ((first - values.last()) / first * 100.0).coerceIn(0.0, 100.0) else null
        }
        val stability = deterioration?.let { (100.0 - it).coerceIn(0.0, 100.0) }
        return LiquidityBResult(
            state.liquidityUsd, ratio, null, deterioration, stability,
            listOf(
                BFeature("liquidity", state.liquidityUsd, "Provider liquidity only"),
                BFeature("liquidity_to_market_cap", ratio, "Requires both liquidity and market cap"),
                BFeature("liquidity_velocity", null, "Requires timestamped liquidity observations"),
                BFeature("liquidity_deterioration", deterioration, "Requires multiple real liquidity observations"),
                BFeature("liquidity_stability", stability, "Derived only when deterioration is observable")
            )
        )
    }
}

class WalletIntelligenceEngineB {
    fun evaluate(state: LiveMarketState): WalletBResult = WalletBResult(
        state.uniqueBuyers, state.uniqueSellers, state.buyerGrowth, state.sellerGrowth,
        state.buyerSellerRatio ?: if (state.uniqueBuyers != null && state.uniqueSellers != null && state.uniqueSellers > 0) state.uniqueBuyers.toDouble() / state.uniqueSellers else null,
        state.repeatedWalletRatio, state.walletConcentration, state.clusteringIndicator,
        listOf(
            BFeature("unique_buyers", state.uniqueBuyers?.toDouble(), "Provider wallet identities only"),
            BFeature("unique_sellers", state.uniqueSellers?.toDouble(), "Provider wallet identities only"),
            BFeature("buyer_growth", state.buyerGrowth, "Requires comparable windows"),
            BFeature("seller_growth", state.sellerGrowth, "Requires comparable windows"),
            BFeature("repeated_wallet_ratio", state.repeatedWalletRatio, "Not available from current provider unless supplied"),
            BFeature("wallet_concentration", state.walletConcentration, "Not available from current provider unless supplied"),
            BFeature("wallet_clustering", state.clusteringIndicator, "Not available from current provider unless supplied")
        )
    )
}

class MarketRegimeEngineB {
    fun evaluate(state: LiveMarketState): MarketRegime = when {
        state.tradeFrequency == null && state.volumeVelocity == null -> MarketRegime.UNKNOWN
        (state.tradeFrequency ?: 0.0) >= 10.0 || (state.volumeVelocity ?: 0.0) >= 4.0 -> MarketRegime.EXTREME
        (state.tradeFrequency ?: 0.0) >= 4.0 || (state.volumeVelocity ?: 0.0) >= 2.0 -> MarketRegime.HOT
        (state.tradeFrequency ?: 0.0) > 0.0 || (state.volumeVelocity ?: 0.0) > 0.0 -> MarketRegime.NORMAL
        else -> MarketRegime.QUIET
    }
}

class DataConfidenceEngineB {
    fun evaluate(state: LiveMarketState, nowMs: Long): DataConfidenceBResult {
        val required = listOf(
            "price" to state.priceUsd, "market_cap" to state.marketCapUsd,
            "liquidity" to state.liquidityUsd, "volume" to state.volumeUsd,
            "buy_volume" to state.buyVolumeUsd, "sell_volume" to state.sellVolumeUsd,
            "buyers" to state.uniqueBuyers, "sellers" to state.uniqueSellers,
            "event_timestamp" to state.lastEventTimestampMs, "source" to state.source
        )
        val missing = required.filter { it.second == null }.map { it.first }
        val coverage = (required.size - missing.size).toDouble() / required.size * 100.0
        val freshnessPenalty = when (state.freshness(nowMs)) {
            LiveFreshness.FRESH -> 0.0
            LiveFreshness.AGING -> 15.0
            LiveFreshness.STALE -> 50.0
            LiveFreshness.UNKNOWN -> 35.0
        }
        val confidence = (coverage - freshnessPenalty).coerceIn(0.0, 100.0)
        return DataConfidenceBResult(
            confidence, missing,
            buildList {
                add("${required.size - missing.size}/${required.size} required fields available")
                if (freshnessPenalty > 0.0) add("Freshness penalty applied: ${state.freshness(nowMs)}")
                if (missing.isNotEmpty()) add("UNKNOWN fields: ${missing.joinToString()}")
            }
        )
    }
}

class CollapseRiskEngineB {
    fun evaluate(state: LiveMarketState, pressure: BuyPressureBResult, liquidity: LiquidityBResult, momentum: MomentumBResult): CollapseRiskBResult {
        val values = listOfNotNull(
            state.sellPressureRisk,
            liquidity.deterioration,
            state.holderConcentrationRisk,
            state.deployerRisk,
            state.abnormalActivityRisk,
            state.collapseRisk,
            momentum.priceVelocity?.let { if (it < 0.0) (-it * 2.0).coerceIn(0.0, 100.0) else null },
            pressure.imbalance?.let { if (it < 0.0) (-it * 100.0).coerceIn(0.0, 100.0) else 0.0 }
        )
        val reasons = buildList {
            if ((pressure.imbalance ?: 0.0) < 0.0) add("SELL_PRESSURE_DOMINANT")
            if ((liquidity.deterioration ?: 0.0) > 20.0) add("LIQUIDITY_DETERIORATING")
            if ((momentum.priceVelocity ?: 0.0) < 0.0) add("PRICE_REVERSAL")
            if (state.holderConcentrationRisk == null) add("HOLDER_CONCENTRATION_UNKNOWN")
            if (state.deployerRisk == null) add("DEPLOYER_BEHAVIOR_UNKNOWN")
        }
        return CollapseRiskBResult(values.takeIf { it.isNotEmpty() }?.average()?.coerceIn(0.0, 100.0), reasons, listOf(
            BFeature("collapse_risk", values.takeIf { it.isNotEmpty() }?.average(), "Independent risk inputs only")
        ))
    }
}

class PumpPotentialEngineB {
    fun evaluate(momentum: MomentumBResult, pressure: BuyPressureBResult, liquidity: LiquidityBResult, regime: MarketRegime): PumpPotentialBResult {
        val values = listOfNotNull(
            momentum.momentum,
            momentum.priceAcceleration?.let { bounded(50.0 + it * 2.0) },
            pressure.imbalance?.let { bounded(50.0 + it * 50.0) },
            liquidity.stability,
            momentum.consistency
        )
        val reasons = buildList {
            if ((momentum.momentum ?: 0.0) >= 60.0) add("MOMENTUM_PRESENT")
            if ((pressure.persistentBuySideDominance ?: 0.0) >= 66.0) add("PERSISTENT_BUY_DOMINANCE")
            if (regime == MarketRegime.HOT || regime == MarketRegime.EXTREME) add("ACTIVE_MARKET_REGIME")
        }
        return PumpPotentialBResult(values.takeIf { it.isNotEmpty() }?.average()?.coerceIn(0.0, 100.0), reasons, emptyList())
    }

    private fun bounded(value: Double) = value.coerceIn(0.0, 100.0)
}

class SignalQualityEngineB {
    fun evaluate(pump: PumpPotentialBResult, risk: CollapseRiskBResult, confidence: DataConfidenceBResult, momentum: MomentumBResult, liquidity: LiquidityBResult): SignalQualityBResult {
        val values = listOfNotNull(pump.pumpPotential, risk.collapseRisk?.let { 100.0 - it }, momentum.consistency, liquidity.stability)
        val raw = if (values.isEmpty()) null else (values.average() * 0.8 + confidence.dataConfidence * 0.2).coerceIn(0.0, 100.0)
        val warnings = buildList {
            if (confidence.dataConfidence < 60.0) add("LOW_DATA_CONFIDENCE")
            if (risk.collapseRisk != null && risk.collapseRisk >= 60.0) add("HIGH_COLLAPSE_RISK")
            if (liquidity.liquidity == null) add("LIQUIDITY_UNKNOWN")
        }
        return SignalQualityBResult(raw, pump.reasonCodes + risk.reasonCodes, warnings)
    }
}

open class AdvancedSignalEngineB(
    private val freshnessThresholds: com.solanasignal.app.domain.live.FreshnessThresholds = com.solanasignal.app.domain.live.FreshnessThresholds()
) {
    private val momentumEngine = MomentumEngineB()
    private val pressureEngine = BuyPressureEngineB()
    private val liquidityEngine = LiquidityEngineB()
    private val walletEngine = WalletIntelligenceEngineB()
    private val regimeEngine = MarketRegimeEngineB()
    private val confidenceEngine = DataConfidenceEngineB()
    private val riskEngine = CollapseRiskEngineB()
    private val pumpEngine = PumpPotentialEngineB()
    private val qualityEngine = SignalQualityEngineB()
    private val consecutiveStrong = mutableMapOf<String, Int>()
    private val priorStates = mutableMapOf<String, AdvancedSignalState>()

    @Synchronized
    open fun evaluate(state: LiveMarketState, nowMs: Long): AdvancedSignalResult {
        val momentum = momentumEngine.evaluate(state)
        val pressure = pressureEngine.evaluate(state)
        val liquidity = liquidityEngine.evaluate(state)
        val wallets = walletEngine.evaluate(state)
        val regime = regimeEngine.evaluate(state)
        val confidence = confidenceEngine.evaluate(state, nowMs)
        val risk = riskEngine.evaluate(state, pressure, liquidity, momentum)
        val pump = pumpEngine.evaluate(momentum, pressure, liquidity, regime)
        val quality = qualityEngine.evaluate(pump, risk, confidence, momentum, liquidity)
        val next = nextState(state, quality, confidence, risk, nowMs)
        priorStates[state.mint] = next
        return AdvancedSignalResult(
            state.mint, next, quality.signalQuality, pump.pumpPotential, risk.collapseRisk,
            confidence.dataConfidence, regime, momentum, pressure, liquidity, wallets,
            quality.reasons, quality.warnings + confidence.reasons
        )
    }

    private fun nextState(state: LiveMarketState, quality: SignalQualityBResult, confidence: DataConfidenceBResult, risk: CollapseRiskBResult, nowMs: Long): AdvancedSignalState {
        if (state.isStale(nowMs, freshnessThresholds)) return AdvancedSignalState.STALE
        if (risk.collapseRisk != null && risk.collapseRisk >= 75.0) return AdvancedSignalState.EXIT_RISK
        if (confidence.dataConfidence < 40.0) return AdvancedSignalState.WATCH
        val strong = quality.signalQuality != null && quality.signalQuality >= 70.0 && (risk.collapseRisk ?: 100.0) < 45.0 && confidence.dataConfidence >= 60.0
        val count = if (strong) (consecutiveStrong[state.mint] ?: 0) + 1 else 0
        consecutiveStrong[state.mint] = count
        return when {
            count >= 3 -> AdvancedSignalState.STRONG_SIGNAL
            count == 2 -> AdvancedSignalState.CONFIRMED
            count == 1 -> AdvancedSignalState.ACCELERATING
            quality.signalQuality != null && quality.signalQuality >= 45.0 -> AdvancedSignalState.BUILDING
            else -> AdvancedSignalState.WATCH
        }
    }
}

data class RankedBToken(val mint: String, val rankScore: Double?, val result: AdvancedSignalResult)

class DynamicRankerB {
    fun rank(results: List<AdvancedSignalResult>): List<RankedBToken> = results
        .map { result ->
            val score = result.signalQuality?.let { quality ->
                val riskPenalty = result.collapseRisk ?: 50.0
                (quality * 0.7 + (100.0 - riskPenalty) * 0.3).coerceIn(0.0, 100.0)
            }
            RankedBToken(result.mint, score, result)
        }
        .sortedByDescending { it.rankScore ?: -1.0 }
}
