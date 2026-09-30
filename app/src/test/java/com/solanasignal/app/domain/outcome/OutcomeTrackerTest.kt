package com.solanasignal.app.domain.outcome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutcomeTrackerTest {
    @Test
    fun futurePointsOnlyAreUsedAtRequiredHorizon() {
        val signal = OutcomeObservation(1_000L, 10.0, 100.0, 20.0)
        val result = OutcomeTracker.checkpoints(signal, listOf(
            FutureMarketPoint(500L, 99.0, 99.0, 99.0),
            FutureMarketPoint(31_000L, 12.0, 120.0, 21.0)
        ))
        assertEquals(30, result.first().horizonSeconds)
        assertEquals(20.0, result.first().returnPct!!, 0.0001)
        assertNull(result[1].returnPct)
    }
}
