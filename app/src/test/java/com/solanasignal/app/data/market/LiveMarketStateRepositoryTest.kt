package com.solanasignal.app.data.market

import com.solanasignal.app.data.pumpportal.ConnectionState
import com.solanasignal.app.data.pumpportal.TradeSide
import com.solanasignal.app.domain.scanner.LiveCandidateRanker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveMarketStateRepositoryTest {
    @Test
    fun missingRestTradeCountsRemainUnknownRatherThanZero() {
        val repository = LiveMarketStateRepository()
        repository.setConnectionState(ConnectionState.CONNECTED, nowMs = 1_000L)
        repository.updateRestSnapshot(
            mint = "mint-a", symbol = "A", name = "Alpha", priceUsd = 0.001,
            marketCapUsd = 100_000.0, liquidityUsd = 10_000.0, volumeUsd = null,
            nowMs = 2_000L
        )
        val state = repository.states.value.getValue("mint-a")
        assertNull(state.buyCount60s)
        assertNull(state.sellCount60s)
        assertEquals(MarketDataStatus.STALE, state.status)
        assertFalse(state.isFreshTradeQuote(2_000L, repository.staleAfterMs))
    }

    @Test
    fun streamQuoteWinsOverRestAndPaperEligibilityExpiresWithoutPriceFabrication() {
        val repository = LiveMarketStateRepository(staleAfterMs = 15_000L)
        repository.setConnectionState(ConnectionState.CONNECTED, nowMs = 1_000L)
        repository.beginTradeTracking(listOf("mint-a"), nowMs = 1_000L)
        repository.updateTrade(
            mint = "mint-a", symbol = "A", name = "Alpha", priceUsd = 0.001,
            marketCapUsd = 100_000.0, liquidityUsd = 10_000.0,
            tick = LiveTradeTick(1_000L, TradeSide.BUY, 0.001, 25.0, "wallet", "sig-1"), nowMs = 1_000L
        )
        val initial = repository.states.value.getValue("mint-a")
        assertEquals(MarketDataStatus.LIVE, initial.status)
        assertEquals(MarketDataSource.PUMPPORTAL_TRADE, initial.priceSource)
        assertTrue(initial.isFreshTradeQuote(1_000L, repository.staleAfterMs))
        assertNull(initial.buyCount60s)
        assertNull(initial.sellCount60s)

        repository.updateRestSnapshot(
            mint = "mint-a", symbol = "A", name = "Alpha", priceUsd = 0.002,
            marketCapUsd = 200_000.0, liquidityUsd = 20_000.0, volumeUsd = 50_000.0,
            nowMs = 2_000L
        )
        assertEquals(0.001, repository.states.value.getValue("mint-a").priceUsd!!, 0.0000001)

        val pointCount = repository.states.value.getValue("mint-a").points.size
        repository.refreshStatuses(nowMs = 20_000L)
        val stale = repository.states.value.getValue("mint-a")
        assertEquals(MarketDataStatus.STALE, stale.status)
        assertEquals(pointCount, stale.points.size)
        assertEquals(0.001, stale.priceUsd!!, 0.0000001)
        assertFalse(stale.isFreshTradeQuote(20_000L, repository.staleAfterMs))
    }

    @Test
    fun zeroTradeSideIsOnlyReportedAfterFullRollingWindowCoverage() {
        val repository = LiveMarketStateRepository()
        repository.setConnectionState(ConnectionState.CONNECTED, nowMs = 1_000L)
        repository.beginTradeTracking(listOf("mint-a"), nowMs = 1_000L)
        repository.updateTrade("mint-a", "A", "Alpha", 1.0, 100_000.0, 10_000.0,
            LiveTradeTick(1_000L, TradeSide.BUY, 1.0, 10.0, null, "sig-1"), nowMs = 1_000L)
        assertNull(repository.states.value.getValue("mint-a").sellCount60s)

        repository.updateTrade("mint-a", "A", "Alpha", 1.1, 110_000.0, 10_000.0,
            LiveTradeTick(61_000L, TradeSide.BUY, 1.1, 5.0, null, "sig-2"), nowMs = 61_000L)
        val covered = repository.states.value.getValue("mint-a")
        assertEquals(2, covered.buyCount60s)
        assertEquals(0, covered.sellCount60s)
    }

    @Test
    fun reconnectDoesNotMarkPreDisconnectQuoteLiveUntilNewTradeArrives() {
        val repository = LiveMarketStateRepository()
        repository.setConnectionState(ConnectionState.CONNECTED, nowMs = 1_000L)
        repository.beginTradeTracking(listOf("mint-a"), nowMs = 1_000L)
        repository.updateTrade(
            "mint-a", "A", "Alpha", 1.0, 100_000.0, 10_000.0,
            LiveTradeTick(1_000L, TradeSide.BUY, 1.0, 10.0, null, "sig-1"), nowMs = 1_000L
        )
        repository.setConnectionState(ConnectionState.DISCONNECTED, nowMs = 2_000L)
        assertEquals(MarketDataStatus.DISCONNECTED, repository.states.value.getValue("mint-a").status)
        repository.setConnectionState(ConnectionState.CONNECTED, nowMs = 3_000L)
        assertEquals(MarketDataStatus.STALE, repository.states.value.getValue("mint-a").status)
        repository.updateTrade(
            "mint-a", "A", "Alpha", 1.1, 110_000.0, 10_000.0,
            LiveTradeTick(3_010L, TradeSide.BUY, 1.1, 5.0, null, "sig-2"), nowMs = 3_010L
        )
        assertEquals(MarketDataStatus.LIVE, repository.states.value.getValue("mint-a").status)
    }

    @Test
    fun rankerUsesOnlyLiveStreamPointsAndRequiresObservedWindowCoverage() {
        val repository = LiveMarketStateRepository()
        repository.setConnectionState(ConnectionState.CONNECTED, nowMs = 1_000L)
        repository.beginTradeTracking(listOf("mint-a"), nowMs = 1_000L)
        repository.updateRestSnapshot("mint-a", "A", "Alpha", 1.0, 100_000.0, 10_000.0, null, 1_000L)
        assertNull(LiveCandidateRanker().rank(repository.states.value.getValue("mint-a"), 1_000L).score)

        repository.updateTrade("mint-a", "A", "Alpha", 1.0, 100_000.0, 10_000.0,
            LiveTradeTick(1_000L, TradeSide.BUY, 1.0, 10.0, null, "sig-1"), nowMs = 1_000L)
        repository.updateTrade("mint-a", "A", "Alpha", 1.2, 120_000.0, 10_000.0,
            LiveTradeTick(61_000L, TradeSide.BUY, 1.2, 20.0, null, "sig-2"), nowMs = 61_000L)
        val rank = LiveCandidateRanker().rank(repository.states.value.getValue("mint-a"), 61_000L)
        assertNotNull(rank.score)
        assertEquals("RISING", rank.state)
        assertTrue(rank.return60sPct!! > 0.0)
    }
}
