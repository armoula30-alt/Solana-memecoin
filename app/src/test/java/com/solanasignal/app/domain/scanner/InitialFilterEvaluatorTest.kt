package com.solanasignal.app.domain.scanner

import com.solanasignal.app.data.settings.FilterConfig
import com.solanasignal.app.domain.metrics.WindowMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialFilterEvaluatorTest {
    private val config = FilterConfig(
        maxTokenAgeSeconds = 300,
        minMarketCapUsd = 10_000.0,
        requireBuyersGtSellers = true,
        requireBuyVolumeGtSellVolume = true
    )

    private fun input(age: Long? = 60, marketCap: Double? = 20_000.0) =
        InitialFilterInput(ageSeconds = age, marketCapUsd = marketCap)

    private fun metrics(
        trades: Int = 2,
        buyers: Int = 2,
        sellers: Int = 1,
        buyVolume: Double = 800.0,
        sellVolume: Double = 300.0
    ) = WindowMetrics(
        windowSeconds = 300,
        totalTrades = trades,
        buys = buyers,
        sells = sellers,
        uniqueBuyers = buyers,
        uniqueSellers = sellers,
        buyVolumeUsd = buyVolume,
        sellVolumeUsd = sellVolume,
        avgBuySizeUsd = 0.0,
        avgSellSizeUsd = 0.0,
        largestBuyUsd = 0.0,
        largestSellUsd = 0.0,
        latestPriceUsd = null,
        priceChangePct = null,
        volumeVelocity = null,
        buyerVelocity = null,
        sellerVelocity = null
    )

    @Test
    fun discoveryMarketCapPassesAndCandidateCanBeTracked() {
        val decision = InitialFilterEvaluator.evaluateDiscovery(input(), config)
        assertTrue(decision.canTrack)
        assertEquals(InitialFilterStatus.PASS, decision.status)
    }

    @Test
    fun buyersAndSellersAreLiveUnknownButDoNotRejectDiscovery() {
        val decision = InitialFilterEvaluator.evaluateDiscovery(input(), config)
        val detail = InitialFilterEvaluator.evaluateLive(metrics(trades = 0), config).details.single { it.name == "requireBuyersGtSellers" }
        assertEquals(InitialFilterStage.LIVE, detail.stage)
        assertEquals(InitialFilterStatus.UNKNOWN, detail.status)
        assertTrue(decision.canTrack)
    }

    @Test
    fun buyVolumeIsLiveUnknownButDoesNotRejectDiscovery() {
        val decision = InitialFilterEvaluator.evaluateDiscovery(input(), config)
        val detail = InitialFilterEvaluator.evaluateLive(metrics(trades = 0), config).details.single { it.name == "requireBuyVolumeGtSellVolume" }
        assertEquals(InitialFilterStage.LIVE, detail.stage)
        assertEquals(InitialFilterStatus.UNKNOWN, detail.status)
        assertTrue(decision.canTrack)
    }

    @Test
    fun missingCreatedAtDoesNotFabricateTokenAge() {
        val decision = InitialFilterEvaluator.evaluateDiscovery(input(age = null), config)
        val age = decision.details.single { it.name == "maxTokenAgeSeconds" }
        assertEquals(InitialFilterStatus.UNKNOWN, age.status)
        assertEquals("UNKNOWN", age.actualValue)
        assertTrue(age.reason.contains("not fabricated"))
        assertTrue(decision.canTrack)
    }

    @Test
    fun discoveryMarketCapFailureStillRejects() {
        val decision = InitialFilterEvaluator.evaluateDiscovery(input(marketCap = 9_999.0), config)
        assertEquals(InitialFilterStatus.REJECT, decision.status)
        assertFalse(decision.canTrack)
    }

    @Test
    fun liveFiltersEvaluateOnceMetricsExist() {
        val decision = InitialFilterEvaluator.evaluateLive(metrics(), config)
        assertEquals(InitialFilterStatus.PASS, decision.status)
        assertTrue(decision.details.all { it.stage == InitialFilterStage.LIVE })
    }

    @Test
    fun liveFiltersRejectWhenConfiguredConditionsFail() {
        val decision = InitialFilterEvaluator.evaluateLive(
            metrics(buyers = 1, sellers = 2, buyVolume = 100.0, sellVolume = 200.0), config
        )
        assertEquals(InitialFilterStatus.REJECT, decision.status)
    }

    @Test
    fun rejectedDiscoveryTokenNeverReachesSubscriptionGate() {
        val requestedMints = mutableListOf<String>()
        val decision = InitialFilterEvaluator.evaluateDiscovery(input(marketCap = 1.0), config)
        if (decision.canTrack) requestedMints += "REJECTED_MINT"
        assertTrue(requestedMints.isEmpty())
    }

    @Test
    fun approvedCandidateReachesSubscriptionGate() {
        val requestedMints = mutableListOf<String>()
        val decision = InitialFilterEvaluator.evaluateDiscovery(input(), config)
        if (decision.canTrack) requestedMints += "APPROVED_MINT"
        assertEquals(listOf("APPROVED_MINT"), requestedMints)
    }
}
