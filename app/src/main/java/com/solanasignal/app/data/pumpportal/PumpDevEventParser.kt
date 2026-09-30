package com.solanasignal.app.data.pumpportal

import org.json.JSONObject

/** PumpDev-specific adapter; all unknown frames are preserved for diagnostics, not silently coerced. */
object PumpDevEventParser {
    private const val WRAPPED_SOL_MINT = "So11111111111111111111111111111111111111112"

    fun parse(rawJson: String, receivedAtMs: Long): ParseResult = try {
        val obj = JSONObject(rawJson)
        val txType = obj.optString("txType", "").lowercase()
        if (txType in setOf("create", "complete", "create_pool", "migrate", "buy", "sell") && obj.optStringOrNull("mint") == null) {
            ParseResult.Malformed(rawJson, "missing mint")
        } else if (txType.isBlank() && obj.optString("type").isNotBlank()) ParseResult.Control(obj.optString("type"), rawJson)
        else when (txType) {
            "create" -> ParseResult.TokenCreated(
                NormalizedTokenCreatedEvent(
                    mint = obj.optStringOrNull("mint") ?: "UNKNOWN_MINT_$receivedAtMs",
                    name = obj.optStringOrNull("name"),
                    symbol = obj.optStringOrNull("symbol"),
                    creator = obj.optStringOrNull("traderPublicKey"),
                    uri = obj.optStringOrNull("uri"),
                    createdAtEpochMs = obj.sourceTimestampEpochMs(),
                    marketCapSol = obj.optDoubleOrNull("marketCapSol")
                        ?: obj.optDoubleOrNull("marketCapQuote").takeIf { obj.isSolQuote() },
                    vSolInBondingCurve = obj.optDoubleOrNull("vSolInBondingCurve"),
                    vTokensInBondingCurve = obj.optDoubleOrNull("vTokensInBondingCurve"),
                    bondingCurveKey = obj.optStringOrNull("bondingCurveKey"),
                    receivedAtEpochMs = receivedAtMs,
                    sourceTimestampEpochMs = obj.sourceTimestampEpochMs(),
                    rawFrame = rawJson
                )
            )
            "complete", "create_pool", "migrate" -> {
                val values = mutableMapOf<String, Any?>()
                obj.keys().forEach { key -> values[key] = obj.opt(key) }
                ParseResult.Migration(
                    NormalizedMigrationEvent(
                        mint = obj.optStringOrNull("mint") ?: "UNKNOWN_MINT_$receivedAtMs",
                        migratedAtEpochMs = receivedAtMs,
                        raw = values,
                        sourceTimestampEpochMs = obj.sourceTimestampEpochMs(),
                        rawFrame = rawJson
                    )
                )
            }
            "buy", "sell" -> {
                val solQuote = obj.isSolQuote()
                val poolPriceSol = if (solQuote) {
                    val quote = obj.optDoubleOrNull("poolEffectiveQuoteReservesUi")
                    val base = obj.optDoubleOrNull("poolBaseReservesUi")
                    if (quote != null && base != null && quote > 0.0 && base > 0.0) quote / base else null
                } else null
                ParseResult.Trade(
                    NormalizedTradeEvent(
                        mint = obj.optStringOrNull("mint") ?: "UNKNOWN_MINT_$receivedAtMs",
                        signature = obj.optStringOrNull("signature"),
                        side = if (txType == "sell") TradeSide.SELL else TradeSide.BUY,
                        trader = obj.optStringOrNull("traderPublicKey"),
                        solAmount = obj.optDoubleOrNull("solAmount")
                            ?: obj.optDoubleOrNull("quoteAmount").takeIf { solQuote },
                        tokenAmount = obj.optDoubleOrNull("tokenAmount"),
                        vSolInBondingCurve = obj.optDoubleOrNull("vSolInBondingCurve"),
                        vTokensInBondingCurve = obj.optDoubleOrNull("vTokensInBondingCurve"),
                        marketCapSol = obj.optDoubleOrNull("marketCapSol")
                            ?: obj.optDoubleOrNull("marketCapQuote").takeIf { solQuote },
                        timestampEpochMs = receivedAtMs,
                        sourceTimestampEpochMs = obj.sourceTimestampEpochMs(),
                        rawFrame = rawJson,
                        priceSolOverride = poolPriceSol,
                        providerSource = "PUMPDEV"
                    )
                )
            }
            else -> ParseResult.Unknown(obj.optString("txType", null), rawJson)
        }
    } catch (e: Exception) {
        ParseResult.Malformed(rawJson, e.javaClass.simpleName)
    }

    private fun JSONObject.isSolQuote(): Boolean =
        optStringOrNull("quoteMint") == WRAPPED_SOL_MINT || optStringOrNull("pairQuoteMint") == WRAPPED_SOL_MINT

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { it.isFinite() } else null
}
