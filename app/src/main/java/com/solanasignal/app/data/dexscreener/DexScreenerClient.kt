package com.solanasignal.app.data.dexscreener

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * DexScreener's public REST API (no key required): GET
 * https://api.dexscreener.com/latest/dex/tokens/{mint1},{mint2},...  (max 30 addresses/call)
 * Returns every known DEX pair for each address, with authoritative USD-denominated
 * figures (DexScreener computes these from on-chain pool reserves itself - this is
 * NOT us doing a SOL->USD conversion, it's DexScreener's own number). Confirmed
 * response shape (pairs[].pairAddress, .dexId, .priceUsd, .liquidity.usd, .marketCap,
 * .fdv) against multiple independent working integrations, since the official docs
 * page couldn't be fetched live at generation time - if DexScreener changes its
 * schema, this is the one place to update.
 *
 * A very new token (seconds old) usually isn't indexed yet - callers get an empty
 * result for it, never a fabricated one, and should keep using PumpPortal-derived
 * estimates until DexScreener catches up (typically well under a minute).
 */
data class DexScreenerPairInfo(
    val pairAddress: String,
    val dexId: String?,          // "pumpfun", "raydium", "pumpswap", ...
    val priceUsd: Double?,
    val liquidityUsd: Double?,
    val marketCapUsd: Double?,
    val fdvUsd: Double?
)

class DexScreenerClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val BASE_URL = "https://api.dexscreener.com/latest/dex/tokens/"
        const val MAX_ADDRESSES_PER_CALL = 30
        // DexScreener has no strictly documented limit; independent integrations
        // report treating ~300 req/min as a safe ceiling. We stay far under that
        // by polling every ~20s in batches of 30 (see ScannerOrchestrator).
    }

    /** Returns, for each mint that DexScreener knows about, its highest-liquidity pair. Missing mints = not indexed yet, not an error. */
    suspend fun fetchBestPairs(mints: List<String>): Map<String, DexScreenerPairInfo> {
        if (mints.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, DexScreenerPairInfo>()
        mints.chunked(MAX_ADDRESSES_PER_CALL).forEach { chunk ->
            try {
                val url = BASE_URL + chunk.joinToString(",")
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
                    parseAndMerge(body, result)
                }
            } catch (_: Exception) {
                // Network hiccup or this batch failed - skip it, keep whatever we already
                // have from previous batches/polls. Never fabricate a fallback value.
            }
        }
        return result
    }

    private fun parseAndMerge(body: String, result: MutableMap<String, DexScreenerPairInfo>) {
        val json = JSONObject(body)
        val pairs = json.optJSONArray("pairs") ?: return
        for (i in 0 until pairs.length()) {
            val pair = pairs.optJSONObject(i) ?: continue
            val baseToken = pair.optJSONObject("baseToken") ?: continue
            val mint = baseToken.optStringOrNull("address") ?: continue
            val pairAddress = pair.optStringOrNull("pairAddress") ?: continue
            val liquidityObj = pair.optJSONObject("liquidity")
            val info = DexScreenerPairInfo(
                pairAddress = pairAddress,
                dexId = pair.optStringOrNull("dexId"),
                priceUsd = pair.optStringOrNull("priceUsd")?.toDoubleOrNull(),
                liquidityUsd = liquidityObj?.optDoubleOrNull("usd"),
                marketCapUsd = pair.optDoubleOrNull("marketCap"),
                fdvUsd = pair.optDoubleOrNull("fdv")
            )
            // Keep the highest-liquidity pair per mint when a token has several (e.g. multiple DEXs).
            val existing = result[mint]
            if (existing == null || (info.liquidityUsd ?: 0.0) > (existing.liquidityUsd ?: 0.0)) {
                result[mint] = info
            }
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null
}
