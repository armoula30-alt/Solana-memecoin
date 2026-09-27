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
    val summary: String?,
    val positiveFactors: List<String>,
    val negativeFactors: List<String>,
    val redFlags: List<String>,
    val contradictions: List<String>,
    val missingData: List<String>,
    val recommendedMonitoring: List<String>,
    val shouldNotify: Boolean?
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
        pair: DexScreenerPairInfo,
        dataQualityScore: Int
    ): CodeCraftAnalysis? {
        val facts = JSONObject().apply {
            put("token", JSONObject().apply {
                put("mint", token.mint)
                put("symbol", token.symbol ?: JSONObject.NULL)
                put("name", token.name ?: JSONObject.NULL)
                put("ageSeconds", ((System.currentTimeMillis() - token.firstSeenAtEpochMs) / 1000L).coerceAtLeast(0L))
            })
            put("market", JSONObject().apply {
                put("pairAddress", pair.pairAddress)
                put("dex", pair.dexId ?: JSONObject.NULL)
                put("priceUsd", pair.priceUsd ?: JSONObject.NULL)
                put("marketCapUsd", pair.marketCapUsd ?: token.marketCapUsd ?: JSONObject.NULL)
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
            })
            put("metadata", JSONObject().apply {
                put("description", pair.description ?: JSONObject.NULL)
                put("websites", pair.websitesJson ?: JSONObject.NULL)
                put("socials", pair.socialsJson ?: JSONObject.NULL)
            })
            put("holders", JSONObject.NULL)
            put("deployer", JSONObject.NULL)
            put("manipulation", JSONObject.NULL)
            put("dataQualityScore", dataQualityScore)
        }
        val prompt = """
            You are an analytical assistant for a real-time Solana memecoin monitoring application.
            You do not execute trades, control wallets, or guarantee returns. Reason only from the supplied JSON.
            If a field is null or UNKNOWN, treat it as UNKNOWN. Never invent blockchain facts.
            Distinguish momentum from safety and opportunity from risk. Explain contradictions.
            Do not recommend automatic trading. Lower confidence when data is incomplete, the token is very young,
            liquidity is insufficient, or holder/deployer analysis is unavailable.

            Return ONLY strict JSON with exactly these fields:
            classification, confidence, riskLevel, summary, positiveFactors, negativeFactors, riskFlags,
            contradictions, missingData, keyMetrics, recommendedMonitoring, shouldNotify.
            classification must be one of WATCH, EARLY_MOMENTUM, STRONG_MOMENTUM, EXTREME_MOMENTUM,
            WEAKENING, INVALIDATED, AVOID, INSUFFICIENT_DATA.
            confidence is an integer 0-100. riskLevel is LOW, MEDIUM, HIGH, CRITICAL, or UNKNOWN.
            All list fields must be arrays of short strings. shouldNotify must be boolean.

            Structured facts:
            $facts
        """.trimIndent()
        val requestJson = JSONObject().apply {
            put("model", model)
            put("temperature", 0.1)
            put("max_tokens", 700)
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
        val allowed = setOf(
            "WATCH", "EARLY_MOMENTUM", "STRONG_MOMENTUM", "EXTREME_MOMENTUM",
            "WEAKENING", "INVALIDATED", "AVOID", "INSUFFICIENT_DATA"
        )
        val decision = json.optString("classification").uppercase().takeIf { it in allowed } ?: return null
        val risk = json.optString("riskLevel").uppercase()
            .takeIf { it in setOf("LOW", "MEDIUM", "HIGH", "CRITICAL", "UNKNOWN") }
        return CodeCraftAnalysis(
            decision = decision,
            confidence = json.optInt("confidence", -1).takeIf { it in 0..100 },
            risk = risk,
            summary = json.optString("summary").takeIf { it.isNotBlank() },
            positiveFactors = json.optJSONArray("positiveFactors").toStringList(),
            negativeFactors = json.optJSONArray("negativeFactors").toStringList(),
            redFlags = json.optJSONArray("riskFlags").toStringList(),
            contradictions = json.optJSONArray("contradictions").toStringList(),
            missingData = json.optJSONArray("missingData").toStringList(),
            recommendedMonitoring = json.optJSONArray("recommendedMonitoring").toStringList(),
            shouldNotify = if (json.has("shouldNotify")) json.optBoolean("shouldNotify") else null
        )
    }

    private fun JSONArray?.toStringList(): List<String> = if (this == null) emptyList() else {
        (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }.take(10)
    }
}
