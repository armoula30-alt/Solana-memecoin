package com.solanasignal.app.domain.advanced

import com.solanasignal.app.domain.ab.EngineABHarness
import com.solanasignal.app.domain.ab.LiveMarketStateConsumer
import com.solanasignal.app.domain.live.LiveMarketState
import com.solanasignal.app.domain.live.LiveWindowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AdvancedEngineBTest {
    @Test
    fun missingProviderFieldsRemainUnknown() {
        val result = AdvancedSignalEngineB().evaluate(
            LiveMarketState(mint = "MINT", source = "PUMPDEV"),
            nowMs = 1_000L
        )
        assertEquals(0.0, result.dataConfidence, 0.0)
        assertNull(result.momentum.momentum)
        assertNull(result.buyPressure.buyVolume)
        assertNull(result.liquidity.liquidity)
        assertNull(result.collapseRisk)
    }

    @Test
    fun staleStateCannotProduceStrongSignal() {
        val state = richState(lastReceivedAtMs = 0L)
        val result = AdvancedSignalEngineB().evaluate(state, nowMs = 20_000L)
        assertEquals(AdvancedSignalState.STALE, result.state)
        assertNotEquals(AdvancedSignalState.STRONG_SIGNAL, result.state)
    }

    @Test
    fun strongStateRequiresRepeatedObservations() {
        val state = richState(lastReceivedAtMs = 9_000L)
        val engine = AdvancedSignalEngineB()
        val first = engine.evaluate(state, nowMs = 10_000L)
        val second = engine.evaluate(state.copy(lastReceivedAtMs = 11_000L), nowMs = 12_000L)
        val third = engine.evaluate(state.copy(lastReceivedAtMs = 13_000L), nowMs = 14_000L)
        assertEquals(AdvancedSignalState.ACCELERATING, first.state)
        assertEquals(AdvancedSignalState.CONFIRMED, second.state)
        assertEquals(AdvancedSignalState.STRONG_SIGNAL, third.state)
    }

    @Test
    fun abHarnessPassesTheSameStateObjectToBothConsumers() {
        var baselineSeen: LiveMarketState? = null
        var bSeen: LiveMarketState? = null
        val baseline = LiveMarketStateConsumer<String> { state -> baselineSeen = state; "A" }
        val advanced = object : AdvancedSignalEngineB() {
            override fun evaluate(state: LiveMarketState, nowMs: Long): AdvancedSignalResult {
                bSeen = state
                return super.evaluate(state, nowMs)
            }
        }
        val state = LiveMarketState(mint = "SAME")
        val result = EngineABHarness(baseline, advanced).evaluate(state, 1_000L)
        assertSame(state, result.state)
        assertSame(state, baselineSeen)
        assertSame(state, bSeen)
    }

    private fun richState(lastReceivedAtMs: Long): LiveMarketState {
        val window = LiveWindowState(
            seconds = 300,
            observationCount = 12,
            priceUsd = 1.3,
            firstPriceUsd = 1.0,
            marketCapUsd = 130_000.0,
            firstMarketCapUsd = 100_000.0,
            liquidityUsd = 30_000.0,
            volumeUsd = 12_000.0,
            buyCount = 9,
            sellCount = 3,
            buyVolumeUsd = 9_000.0,
            sellVolumeUsd = 3_000.0,
            uniqueBuyers = 8,
            uniqueSellers = 2,
            eventTimestampMs = lastReceivedAtMs,
            receiveTimestampMs = lastReceivedAtMs,
            source = "PUMPDEV"
        )
        return LiveMarketState(
            mint = "MINT",
            source = "PUMPDEV",
            lastEventTimestampMs = lastReceivedAtMs,
            lastReceivedAtMs = lastReceivedAtMs,
            priceUsd = 1.3,
            marketCapUsd = 130_000.0,
            liquidityUsd = 30_000.0,
            liquidityToMarketCap = 0.23,
            volumeUsd = 12_000.0,
            volumeVelocity = 3.0,
            buyCount = 9,
            sellCount = 3,
            buyVolumeUsd = 9_000.0,
            sellVolumeUsd = 3_000.0,
            uniqueBuyers = 8,
            uniqueSellers = 2,
            buyerGrowth = 2.0,
            sellerGrowth = 0.5,
            buyerSellerRatio = 4.0,
            momentum = 85.0,
            momentumConsistency = 90.0,
            priceVelocity = 4.0,
            priceAcceleration = 3.0,
            marketCapVelocity = 4.0,
            volumeAcceleration = 2.0,
            windows = mapOf(300 to window),
            sourceLatencyMs = 100L,
            dataAgeMs = 1_000L
        )
    }
}
