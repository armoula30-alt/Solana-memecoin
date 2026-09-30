package com.solanasignal.app.domain.scanner

import com.solanasignal.app.data.settings.FilterConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialFilterEvaluatorTest {
    private val config = FilterConfig(
        maxTokenAgeSeconds = 300,
        minMarketCapUsd = 10_000.0,
        requireBuyersGtSellers = true,
        requireBuyVolumeGtSellVolume = true
    )

    private fun input(
        age: Long? = 60,
        marketCap: Double? = 20_000.0,
        buys: Int? = 8,
        sells: Int? = 3,
        buyVolume: Double? = 800.0,
        sellVolume: Double? = 300.0
    ) = InitialFilterInput(age, marketCap, buys, sells, buyVolume, sellVolume)

    @Test
    fun passingTokenIsEligibleForTrackingAndSubscription() {
        val decision = InitialFilterEvaluator.evaluate(input(), config)
        assertEquals(InitialFilterStatus.PASS, decision.status)
        assertTrue(decision.canTrack)
    }

    @Test
    fun marketCapFailureRejects() {
        val decision = InitialFilterEvaluator.evaluate(input(marketCap = 9_999.0), config)
        assertEquals(InitialFilterStatus.REJECT, decision.status)
        assertTrue(decision.details.any { it.name == "minMarketCapUsd" && it.status == InitialFilterStatus.REJECT })
    }

    @Test
    fun buyerSellerFailureRejects() {
        val decision = InitialFilterEvaluator.evaluate(input(buys = 2, sells = 3), config)
        assertEquals(InitialFilterStatus.REJECT, decision.status)
        assertTrue(decision.details.any { it.name == "requireBuyersGtSellers" && it.status == InitialFilterStatus.REJECT })
    }

    @Test
    fun buyVolumeFailureRejects() {
        val decision = InitialFilterEvaluator.evaluate(input(buyVolume = 100.0, sellVolume = 200.0), config)
        assertEquals(InitialFilterStatus.REJECT, decision.status)
        assertTrue(decision.details.any { it.name == "requireBuyVolumeGtSellVolume" && it.status == InitialFilterStatus.REJECT })
    }

    @Test
    fun missingRequiredValueIsUnknownAndNotEligible() {
        val decision = InitialFilterEvaluator.evaluate(input(buys = null, sells = null), config)
        assertEquals(InitialFilterStatus.UNKNOWN, decision.status)
        assertTrue(!decision.canTrack)
    }

    @Test
    fun rejectedTokenNeverReachesSubscriptionGate() {
        val requestedMints = mutableListOf<String>()
        val decision = InitialFilterEvaluator.evaluate(input(marketCap = 1.0), config)
        if (decision.canTrack) requestedMints += "REJECTED_MINT"
        assertTrue(requestedMints.isEmpty())
    }

    @Test
    fun passingTokenReachesSubscriptionGate() {
        val requestedMints = mutableListOf<String>()
        val decision = InitialFilterEvaluator.evaluate(input(), config)
        if (decision.canTrack) requestedMints += "APPROVED_MINT"
        assertEquals(listOf("APPROVED_MINT"), requestedMints)
    }

    @Test
    fun reconnectSetContainsOnlyPreviouslyApprovedCandidates() {
        val approved = mutableSetOf<String>()
        val rejected = InitialFilterEvaluator.evaluate(input(marketCap = 1.0), config)
        val passing = InitialFilterEvaluator.evaluate(input(), config)
        if (rejected.canTrack) approved += "REJECTED_MINT"
        if (passing.canTrack) approved += "APPROVED_MINT"
        val restoredOnReconnect = approved.toList()
        assertEquals(listOf("APPROVED_MINT"), restoredOnReconnect)
    }
}
