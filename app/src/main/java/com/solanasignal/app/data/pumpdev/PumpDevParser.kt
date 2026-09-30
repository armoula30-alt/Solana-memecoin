package com.solanasignal.app.data.pumpdev

import com.solanasignal.app.data.pumpportal.NormalizedTradeEvent
import com.solanasignal.app.data.pumpportal.TradeSide
import org.json.JSONArray
import org.json.JSONObject

/** Converts PumpDev read-only market events into the existing normalized trade model. */
object PumpDevParser {
    data class SubscriptionAck(val confirmed: Boolean, val keys: List<String>)
    data class ControlMessage(
        val type: String?,
        val method: String?,
        val code: String?,
        val message: String?,
        val status: String?,
        val tier: String?,
        val keys: List<String>,
        val isSubscriptionAck: Boolean,
        val isError: Boolean
    )

    fun parseTrade(rawJson: String, nowEpochMs: Long): NormalizedTradeEvent? {
        val obj = JSONObject(rawJson)
        val txType = obj.optString("txType", "").lowercase()
        if (txType != "buy" && txType != "sell") return null
        val mint = obj.optStringOrNull("mint") ?: return null
        val quoteMint = obj.optStringOrNull("quoteMint")
        val quoteAmount = obj.optDoubleOrNull("quoteAmount")
        val solAmount = obj.optDoubleOrNull("solAmount") ?: if (isNativeSol(quoteMint)) quoteAmount else null
        val marketCapQuote = obj.optDoubleOrNull("marketCapQuote")
        val marketCapSol = obj.optDoubleOrNull("marketCapSol") ?: if (isNativeSol(quoteMint)) marketCapQuote else null
        return NormalizedTradeEvent(
            mint = mint,
            signature = obj.optStringOrNull("signature"),
            side = if (txType == "buy") TradeSide.BUY else TradeSide.SELL,
            trader = obj.optStringOrNull("traderPublicKey"),
            solAmount = solAmount,
            tokenAmount = obj.optDoubleOrNull("tokenAmount"),
            vSolInBondingCurve = obj.optDoubleOrNull("vSolInBondingCurve"),
            vTokensInBondingCurve = obj.optDoubleOrNull("vTokensInBondingCurve"),
            marketCapSol = marketCapSol,
            timestampEpochMs = parseTimestamp(obj.opt("timestamp"), nowEpochMs),
            quoteAmount = quoteAmount,
            marketCapQuote = marketCapQuote,
            source = obj.optStringOrNull("source") ?: "PUMPDEV",
            pool = obj.optStringOrNull("pool"),
            quoteMint = quoteMint
        )
    }

    fun isTrade(rawJson: String): Boolean = runCatching {
        val type = JSONObject(rawJson).optString("txType", "").lowercase()
        type == "buy" || type == "sell"
    }.getOrDefault(false)

    fun parseSubscriptionAck(rawJson: String): SubscriptionAck? = runCatching {
        parseControlMessage(rawJson)?.takeIf { it.isSubscriptionAck }?.let {
            SubscriptionAck(confirmed = !it.isError, keys = it.keys)
        }
    }.getOrNull()

    fun parseControlMessage(rawJson: String): ControlMessage? = runCatching {
        val obj = JSONObject(rawJson)
        val type = obj.optStringOrNull("type")
        val method = obj.optStringOrNull("method")
        val code = obj.optStringOrNull("code")
        if (type == null && method == null && code == null) return@runCatching null
        val isError = type == "error" || type == "subscription_error" || code != null
        val isSubscription = method == "subscribeTokenTrade" ||
            (isError && obj.optJSONArray("keys") != null)
        ControlMessage(
            type = type,
            method = method,
            code = code,
            message = obj.optStringOrNull("message"),
            status = obj.optStringOrNull("status"),
            tier = obj.optStringOrNull("tier"),
            keys = obj.optJSONArray("keys")?.toStringList() ?: emptyList(),
            isSubscriptionAck = isSubscription,
            isError = isError
        )
    }.getOrNull()

    private fun parseTimestamp(value: Any?, fallbackMs: Long): Long = when (value) {
        is Number -> value.toLong().let { if (it < 100_000_000_000L) it * 1000L else it }
        is String -> value.toLongOrNull()?.let { if (it < 100_000_000_000L) it * 1000L else it } ?: fallbackMs
        else -> fallbackMs
    }

    private fun isNativeSol(quoteMint: String?): Boolean = quoteMint == SOL_MINT

    private fun JSONArray.toStringList(): List<String> = (0 until length()).mapNotNull { optString(it, null) }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

    private const val SOL_MINT = "So11111111111111111111111111111111111111112"
}
