package com.solanasignal.app.domain.scanner

import com.solanasignal.app.data.pumpportal.NormalizedTokenCreatedEvent
import com.solanasignal.app.data.pumpportal.NormalizedTradeEvent
import com.solanasignal.app.data.pumpportal.TradeSide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * MOCK_MODE (spec #49): simulates new-token, trade, and buyer/seller activity so the
 * app is fully demoable without a live PumpPortal connection or API key. Clearly
 * simulated data only - never mixed silently with real data (the UI must show a
 * MOCK MODE badge whenever this source is active).
 */
class MockEventSource(
    private val onTokenCreated: (NormalizedTokenCreatedEvent) -> Unit,
    private val onTrade: (NormalizedTradeEvent) -> Unit
) {
    private var job: Job? = null
    private val symbols = listOf("PEPE", "WIF", "BONK", "MOON", "FLOKI", "CHAD", "RIZZ", "NYAN")
    private val activeMints = mutableListOf<String>()

    fun start(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                delay(Random.nextLong(2000, 6000))
                if (Random.nextInt(100) < 30 || activeMints.isEmpty()) {
                    spawnToken()
                } else {
                    val mint = activeMints.random()
                    repeat(Random.nextInt(1, 4)) { spawnTrade(mint) }
                }
                if (activeMints.size > 25) activeMints.removeAt(0)
            }
        }
    }

    fun stop() {
        job?.cancel()
        activeMints.clear()
    }

    private fun spawnToken() {
        val mint = "MOCK" + Random.nextLong(100000, 999999)
        val symbol = symbols.random() + Random.nextInt(0, 99)
        activeMints.add(mint)
        onTokenCreated(
            NormalizedTokenCreatedEvent(
                mint = mint,
                name = "$symbol Token",
                symbol = symbol,
                creator = "MockCreator" + Random.nextInt(1000, 9999),
                uri = null,
                createdAtEpochMs = System.currentTimeMillis(),
                initialMarketCapUsd = Random.nextDouble(5_000.0, 30_000.0),
                receivedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    private fun spawnTrade(mint: String) {
        val side = if (Random.nextInt(100) < 60) TradeSide.BUY else TradeSide.SELL
        onTrade(
            NormalizedTradeEvent(
                mint = mint,
                signature = "mock-sig-${Random.nextLong()}",
                side = side,
                trader = "MockTrader" + Random.nextInt(1, 500),
                amountUsd = Random.nextDouble(20.0, 800.0),
                priceUsd = Random.nextDouble(0.00001, 0.01),
                timestampEpochMs = System.currentTimeMillis()
            )
        )
    }
}
