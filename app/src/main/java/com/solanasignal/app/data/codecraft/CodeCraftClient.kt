package com.solanasignal.app.data.codecraft

import com.solanasignal.app.data.dexscreener.DexScreenerPairInfo
import com.solanasignal.app.data.room.entities.TokenEntity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CodeCraftAnalysis(
    val decision: String,
    val confidence: Int?,
    val risk: String?,
    val reasons: List<String>,
    val redFlags: List<String>
)

class CodeCraftClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun analyze(
        apiKey: String,
        model: String,
        token: TokenEntity,
        pair: DexScreenerPairInfo
    ): CodeCraftAnalysis? {
        val facts = JSONObject().apply {
            put("mint", token.mint)
            put("symbol", token.symbol ?: JSONObject.NULL)
            put("name", token.name ?: JSONObject.NULL)
            put("pairAddress", pair.pairAddress)
            put("dex", pair.dexId ?: JSONObject.NULL)
            put("priceUsd", pair.priceUsd ?: JSONObject.NULL)
            put("marketCapUsd", pair.marketCapUsd ?: JSONObject.NULL)
            put("fdvUsd", pair.fdvUsd ?: JSONObject.NULL)
            put("liquidityUsd", pair.liquidityUsd ?: JSONObject.NULL)
            put("volume5mUsd", pair.volume5mUsd ?: JSONObject.NULL)
            put("volume1hUsd", pair.volume1hUsd ?: JSONObject.NULL)
            put("volume6hUsd", pair.volume6hUsd ?: JSONObject.NULL)
            put("volume24hUsd", pair.volume24hUsd ?: JSONObject.NULL)
            put("buys5m", pair.buys5m ?: JSONObject.NULL)
            put("sells5m", pair.sells5m ?: JSONObject.NULL)
            put("buys1h", pair.buys1h ?: JSONObject.NULL)
            put("sells1h", pair.sells1h ?: JSONObject.NULL)
            put("priceChange5mPct", pair.priceChange5mPct ?: JSONObject.NULL)
            put("priceChange1hPct", pair.priceChange1hPct ?: JSONObject.NULL)
            put("priceChange6hPct", pair.priceChange6hPct ?: JSONObject.NULL)
            put("priceChange24hPct", pair.priceChange24hPct ?: JSONObject.NULL)
            put("activeBoosts", pair.activeBoosts ?: JSONObject.NULL)
            put("description", pair.description ?: JSONObject.NULL)
            put("websites", pair.websitesJson ?: JSONObject.NULL)
            put("socials", pair.socialsJson ?: JSONObject.NULL)
        }
        val prompt = """
            You are a cautious Solana memecoin market analyst. Analyze only the supplied DexScreener snapshot.
            Do not claim certainty or predict guaranteed pumps. Missing fields are unknown, never positive evidence.
            Return ONLY valid JSON with exactly these fields: decision, confidence, risk, reasons, red_flags.
            decision must be one of BUY_CANDIDATE, WATCH, REJECTED. confidence is an integer 0-100.
            risk must be LOW, MEDIUM, or HIGH. reasons and red_flags must be arrays of short strings.
            A BUY_CANDIDATE is only allowed when buy/sell counts, liquidity, momentum and market cap support it;
            otherwise use WATCH or REJECTED. Treat a very young pair and low liquidity as high risk.

            DexScreener snapshot:
            ${facts}
        """.trimIndent()
        val requestJson = JSONObject().apply {
            put("model", model)
            put("temperature", 0.1)
            put("max_tokens", 500)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
        }
        val request = Request.Builder()
            .url("https://codecraftapi.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val content = JSONObject(response.body?.string().orEmpty())
                    .optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content") ?: return null
                parseAnalysis(content)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseAnalysis(content: String): CodeCraftAnalysis? {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val json = JSONObject(content.substring(start, end + 1))
        val decision = json.optString("decision").uppercase()
            .takeIf { it in setOf("BUY_CANDIDATE", "WATCH", "REJECTED") } ?: return null
        return CodeCraftAnalysis(
            decision = decision,
            confidence = json.optInt("confidence", -1).takeIf { it in 0..100 },
            risk = json.optString("risk").uppercase().takeIf { it in setOf("LOW", "MEDIUM", "HIGH") },
            reasons = json.optJSONArray("reasons").toStringList(),
            redFlags = json.optJSONArray("red_flags").toStringList()
        )
    }

    private fun JSONArray?.toStringList(): List<String> = if (this == null) emptyList() else {
        (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }.take(8)
    }
}
