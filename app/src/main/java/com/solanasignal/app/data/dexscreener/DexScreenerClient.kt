package com.solanasignal.app.data.dexscreener

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Read-only DexScreener enrichment for tokens discovered by PumpPortal.
 * PumpPortal remains the low-latency discovery/trade stream; DexScreener is
 * eventually consistent and may not index a brand-new token immediately.
 */
data class DexScreenerPairInfo(
    val pairAddress: String,
    val dexId: String?,
    val url: String?,
    val priceUsd: Double?,
    val liquidityUsd: Double?,
    val liquidityBase: Double?,
    val liquidityQuote: Double?,
    val marketCapUsd: Double?,
    val fdvUsd: Double?,
    val pairCreatedAtEpochMs: Long?,
    val volume5mUsd: Double?,
    val volume1hUsd: Double?,
    val volume6hUsd: Double?,
    val volume24hUsd: Double?,
    val buys5m: Int?,
    val sells5m: Int?,
    val buys1h: Int?,
    val sells1h: Int?,
    val priceChange5mPct: Double?,
    val priceChange1hPct: Double?,
    val priceChange6hPct: Double?,
    val priceChange24hPct: Double?,
    val activeBoosts: Int?,
    val imageUrl: String?,
    val description: String?,
    val websitesJson: String?,
    val socialsJson: String?
)

class DexScreenerClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val BASE_URL = "https://api.dexscreener.com/latest/dex/tokens/"
        const val MAX_ADDRESSES_PER_CALL = 30
    }

    /**
     * Returns the highest-liquidity Solana pair known for each requested mint.
     * Missing mints are normal for tokens created seconds ago.
     */
    suspend fun fetchBestPairs(mints: List<String>): Map<String, DexScreenerPairInfo> {
        if (mints.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, DexScreenerPairInfo>()
        mints.chunked(MAX_ADDRESSES_PER_CALL).forEach { chunk ->
            try {
                val request = Request.Builder()
                    .url(BASE_URL + chunk.joinToString(","))
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
                    parseAndMerge(body, result)
                }
            } catch (_: Exception) {
                // Keep the PumpPortal-derived snapshot when DexScreener is unavailable.
            }
        }
        return result
    }

    private fun parseAndMerge(body: String, result: MutableMap<String, DexScreenerPairInfo>) {
        val pairs = JSONObject(body).optJSONArray("pairs") ?: return
        for (i in 0 until pairs.length()) {
            val pair = pairs.optJSONObject(i) ?: continue
            val baseToken = pair.optJSONObject("baseToken") ?: continue
            val mint = baseToken.optStringOrNull("address") ?: continue
            val pairAddress = pair.optStringOrNull("pairAddress") ?: continue
            val liquidity = pair.optJSONObject("liquidity")
            val txns = pair.optJSONObject("txns")
            val volume = pair.optJSONObject("volume")
            val changes = pair.optJSONObject("priceChange")
            val info = DexScreenerPairInfo(
                pairAddress = pairAddress,
                dexId = pair.optStringOrNull("dexId"),
                url = pair.optStringOrNull("url"),
                priceUsd = pair.optStringOrNull("priceUsd")?.toDoubleOrNull(),
                liquidityUsd = liquidity?.optDoubleOrNull("usd"),
                liquidityBase = liquidity?.optDoubleOrNull("base"),
                liquidityQuote = liquidity?.optDoubleOrNull("quote"),
                marketCapUsd = pair.optDoubleOrNull("marketCap"),
                fdvUsd = pair.optDoubleOrNull("fdv"),
                pairCreatedAtEpochMs = pair.optLongOrNull("pairCreatedAt"),
                volume5mUsd = volume?.optDoubleOrNull("m5"),
                volume1hUsd = volume?.optDoubleOrNull("h1"),
                volume6hUsd = volume?.optDoubleOrNull("h6"),
                volume24hUsd = volume?.optDoubleOrNull("h24"),
                buys5m = txns?.optJSONObject("m5")?.optIntOrNull("buys"),
                sells5m = txns?.optJSONObject("m5")?.optIntOrNull("sells"),
                buys1h = txns?.optJSONObject("h1")?.optIntOrNull("buys"),
                sells1h = txns?.optJSONObject("h1")?.optIntOrNull("sells"),
                priceChange5mPct = changes?.optDoubleOrNull("m5"),
                priceChange1hPct = changes?.optDoubleOrNull("h1"),
                priceChange6hPct = changes?.optDoubleOrNull("h6"),
                priceChange24hPct = changes?.optDoubleOrNull("h24"),
                activeBoosts = pair.optJSONObject("boosts")?.optIntOrNull("active"),
                imageUrl = pair.optJSONObject("info")?.optStringOrNull("imageUrl"),
                description = pair.optJSONObject("info")?.optStringOrNull("description"),
                websitesJson = pair.optJSONObject("info")?.optJSONArray("websites")?.toString(),
                socialsJson = pair.optJSONObject("info")?.optJSONArray("socials")?.toString()
            )
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

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key).takeIf { it > 0L } else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null
}
