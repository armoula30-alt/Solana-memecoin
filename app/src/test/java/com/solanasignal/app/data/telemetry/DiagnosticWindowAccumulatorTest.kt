package com.solanasignal.app.data.telemetry

import com.solanasignal.app.data.pumpportal.TradeSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticWindowAccumulatorTest {
    @Test fun windowsMatchDocumentedDurations() {
        assertEquals(listOf(5, 10, 15, 30, 60, 120, 300), DiagnosticWindowAccumulator.WINDOWS)
    }

    @Test fun incompleteCoverageIsUnknownRatherThanZero() {
        val accumulator = DiagnosticWindowAccumulator()
        accumulator.beginCoverage(listOf("mint"), 10_000L)
        val fiveSeconds = accumulator.snapshot("mint", 14_000L).first()
        assertFalse(fiveSeconds.covered)
        assertNull(fiveSeconds.buyCount)
    }

    @Test fun completeQuietWindowHasKnownZeroCounts() {
        val accumulator = DiagnosticWindowAccumulator()
        accumulator.beginCoverage(listOf("mint"), 0L)
        val fiveSeconds = accumulator.snapshot("mint", 5_000L).first()
        assertTrue(fiveSeconds.covered)
        assertEquals(0, fiveSeconds.buyCount)
        assertEquals(0, fiveSeconds.sellCount)
        assertEquals(0.0, fiveSeconds.buyVolumeUsd!!, 0.0)
    }

    @Test fun liveEventsProduceBuyPressureAndVelocityWithoutChangingSignalInputs() {
        val accumulator = DiagnosticWindowAccumulator()
        accumulator.beginCoverage(listOf("mint"), 0L)
        accumulator.observe("mint", 1_000L, TradeSide.BUY, 1.0, 100.0, 10.0)
        accumulator.observe("mint", 4_000L, TradeSide.SELL, 1.1, 110.0, 5.0)
        val fiveSeconds = accumulator.snapshot("mint", 5_000L).first()
        assertEquals(1, fiveSeconds.buyCount)
        assertEquals(1, fiveSeconds.sellCount)
        assertEquals(50.0, fiveSeconds.buyPressurePct!!, 0.0)
        assertEquals(10.0, fiveSeconds.buyVolumeUsd!!, 0.0)
        assertTrue(fiveSeconds.priceVelocityPct!! > 0.0)
        assertEquals(2, fiveSeconds.sampleCount)
    }

    @Test fun stalePriceObservationsDoNotProduceMovementMetrics() {
        val accumulator = DiagnosticWindowAccumulator()
        accumulator.beginCoverage(listOf("mint"), 0L)
        accumulator.observe("mint", 50_000L, TradeSide.BUY, 1.0, 100.0, 10.0)
        accumulator.observe("mint", 80_000L, TradeSide.SELL, 2.0, 200.0, 5.0)
        val window = accumulator.snapshot("mint", 100_000L).first { it.windowSeconds == 60 }
        assertTrue(window.covered)
        assertNull(window.priceVelocityPct)
        assertNull(window.marketCapVelocityPct)
        assertNull(window.priceAccelerationPctPerSecond)
        assertNull(window.marketCapAccelerationPctPerSecond)
    }

    @Test fun insufficientPriceSamplesRemainUnknownRatherThanZeroMovement() {
        val single = DiagnosticWindowAccumulator()
        single.beginCoverage(listOf("single"), 0L)
        single.observe("single", 4_000L, TradeSide.BUY, 1.0, 100.0, 10.0)
        val singleWindow = single.snapshot("single", 5_000L).first()
        assertNull(singleWindow.priceVelocityPct)
        assertNull(singleWindow.marketCapVelocityPct)

        val halves = DiagnosticWindowAccumulator()
        halves.beginCoverage(listOf("halves"), 0L)
        halves.observe("halves", 1_000L, TradeSide.BUY, 1.0, 100.0, 10.0)
        halves.observe("halves", 3_000L, TradeSide.SELL, 1.1, 110.0, 5.0)
        val halfWindow = halves.snapshot("halves", 5_000L).first()
        assertTrue(halfWindow.priceVelocityPct != null)
        assertNull(halfWindow.priceAccelerationPctPerSecond)
    }

    @Test fun missingAmountsStayUnknownAndOverflowOnlyInvalidatesWindowsContainingLostEvents() {
        val accumulator = DiagnosticWindowAccumulator()
        accumulator.beginCoverage(listOf("mint"), 0L)
        accumulator.observe("mint", 1_000L, TradeSide.BUY, 1.0, 100.0, null)
        val window = accumulator.snapshot("mint", 5_000L).first()
        assertNull(window.buyVolumeUsd)

        repeat(600) { accumulator.observe("mint", 6_000L + it, TradeSide.BUY, 1.0, 100.0, 1.0) }
        val windows = accumulator.snapshot("mint", 60_000L)
        assertTrue(windows.first().covered) // all evicted events are outside the last 5 seconds
        assertFalse(windows.first { it.windowSeconds == 60 }.covered) // the 60s interval includes lost observations
    }
}
