package com.solanasignal.app.data.pumpportal

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Every figure PumpPortal streams (marketCapSol, vSolInBondingCurve, solAmount, ...)
 * is denominated in SOL, not USD. To show the USD figures the spec/UI call for
 * ("Market Cap >= $10,000", "Liquidity $X"), the app needs a live SOL/USD price.
 *
 * Polls Binance's public ticker endpoint (no API key required) every 60s. If the
 * fetch fails, the last known price is kept and used; if no price has ever been
 * fetched, callers get null and must show "UNKNOWN" rather than guessing (same
 * "never fabricate" rule as everywhere else in this app).
 */
class SolPriceProvider {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _priceUsd = MutableStateFlow<Double?>(null)
    val priceUsd: StateFlow<Double?> = _priceUsd.asStateFlow()

    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                refreshOnce()
                delay(60_000)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    private suspend fun refreshOnce() {
        try {
            val request = Request.Builder()
                .url("https://api.binance.com/api/v3/ticker/price?symbol=SOLUSDT")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return
                    val json = JSONObject(body)
                    val price = json.optDouble("price", Double.NaN)
                    if (!price.isNaN()) _priceUsd.value = price
                }
            }
        } catch (_: Exception) {
            // Keep the last known price (if any); never fabricate a fallback value.
        }
    }
}
