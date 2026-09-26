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
 * app is fully demoable without a live PumpPortal connection or API key. Field shapes
 * mirror the real SOL-denominated PumpPortal payload (marketCapSol, vSolInBondingCurve,
 * solAmount, tokenAmount) so the same conversion/metrics/scoring code path runs in
 * both mock and live mode - clearly simulated data only, never mixed silently with
 * real data (the UI shows a MOCK MODE badge whenever this source is active).
 */
class MockEventSource(
    private val onTokenCreated: (NormalizedTokenCreatedEvent) -> Unit,
    private val onTrade: (NormalizedTradeEvent) -> Unit
) {
    private var job: Job? = null
    private val symbols = listOf("PEPE", "WIF", "BONK", "MOON", "FLOKI", "CHAD", "RIZZ", "NYAN")
    private val activeMints = mutableListOf<String>()
    // Track simple bonding-curve state per mock mint so reserves move realistically.
    private val vSol = mutableMapOf<String, Double>()
    private val vTokens = mutableMapOf<String, Double>()

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
        vSol.clear()
        vTokens.clear()
    }

    private fun spawnToken() {
        val mint = "MOCK" + Random.nextLong(100000, 999999)
        val symbol = symbols.random() + Random.nextInt(0, 99)
        activeMints.add(mint)
        val initialSol = Random.nextDouble(20.0, 40.0)      // typical pump.fun starting curve ~30 SOL
        val initialTokens = Random.nextDouble(900_000_000.0, 1_050_000_000.0)
        vSol[mint] = initialSol
        vTokens[mint] = initialTokens
        onTokenCreated(
            NormalizedTokenCreatedEvent(
                mint = mint,
                name = "$symbol Token",
                symbol = symbol,
                creator = "MockCreator" + Random.nextInt(1000, 9999),
                uri = null,
                createdAtEpochMs = System.currentTimeMillis(),
                marketCapSol = initialSol * 2.5,
                vSolInBondingCurve = initialSol,
                vTokensInBondingCurve = initialTokens,
                bondingCurveKey = "MockPool$mint",
                receivedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    private fun spawnTrade(mint: String) {
        val side = if (Random.nextInt(100) < 60) TradeSide.BUY else TradeSide.SELL
        val solAmount = Random.nextDouble(0.1, 4.0)
        var curveSol = vSol[mint] ?: 30.0
        var curveTokens = vTokens[mint] ?: 1_000_000_000.0
        val tokenAmount = if (curveTokens > 0) (solAmount / curveSol) * curveTokens * 0.98 else 0.0
        if (side == TradeSide.BUY) {
            curveSol += solAmount
            curveTokens -= tokenAmount
        } else {
            curveSol = (curveSol - solAmount).coerceAtLeast(0.5)
            curveTokens += tokenAmount
        }
        vSol[mint] = curveSol
        vTokens[mint] = curveTokens

        onTrade(
            NormalizedTradeEvent(
                mint = mint,
                signature = "mock-sig-${Random.nextLong()}",
                side = side,
                trader = "MockTrader" + Random.nextInt(1, 500),
                solAmount = solAmount,
                tokenAmount = tokenAmount,
                vSolInBondingCurve = curveSol,
                vTokensInBondingCurve = curveTokens,
                marketCapSol = curveSol * 2.5,
                timestampEpochMs = System.currentTimeMillis()
            )
        )
    }
}
