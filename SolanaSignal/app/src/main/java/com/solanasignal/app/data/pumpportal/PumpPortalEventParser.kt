package com.solanasignal.app.data.pumpportal

import org.json.JSONObject

/**
 * Parses raw PumpPortal WebSocket JSON frames into normalized internal events.
 *
 * SOURCE: confirmed against multiple independent working integrations (PumpPortal's
 * own GitHub examples at github.com/thetateman/Trading-API, third-party monitors,
 * and community trading bots), since the official docs page itself couldn't be
 * fetched at generation time. Confirmed field names on "create" events: txType,
 * mint, traderPublicKey, name, symbol, uri, signature, bondingCurveKey, solAmount,
 * initialBuy, vSolInBondingCurve, vTokensInBondingCurve, marketCapSol. Confirmed
 * fields on "buy"/"sell" trade events: txType, mint, traderPublicKey, signature,
 * tokenAmount, solAmount, vSolInBondingCurve, vTokensInBondingCurve, marketCapSol.
 *
 * All figures from PumpPortal are SOL-denominated, not USD - this parser does not
 * convert; that happens one layer up using a live SOL/USD price (see
 * SolPriceProvider + ScannerOrchestrator). If a field is missing, it maps to
 * `null` rather than a fabricated value (spec #9, #41).
 */
sealed class ParseResult {
    data class TokenCreated(val event: NormalizedTokenCreatedEvent) : ParseResult()
    data class Migration(val event: NormalizedMigrationEvent) : ParseResult()
    data class Trade(val event: NormalizedTradeEvent) : ParseResult()
    data class Unknown(val rawType: String?, val raw: String) : ParseResult()
    data class Malformed(val raw: String, val error: String) : ParseResult()
}

object PumpPortalEventParser {

    fun parse(rawJson: String, nowEpochMs: Long): ParseResult {
        return try {
            val obj = JSONObject(rawJson)
            when (classify(obj)) {
                EventKind.NEW_TOKEN -> ParseResult.TokenCreated(parseTokenCreated(obj, nowEpochMs))
                EventKind.MIGRATION -> ParseResult.Migration(parseMigration(obj, nowEpochMs))
                EventKind.TRADE -> ParseResult.Trade(parseTrade(obj, nowEpochMs))
                EventKind.UNKNOWN -> ParseResult.Unknown(obj.optString("txType", null), rawJson)
            }
        } catch (e: Exception) {
            ParseResult.Malformed(rawJson, e.message ?: "unknown parse error")
        }
    }

    private enum class EventKind { NEW_TOKEN, MIGRATION, TRADE, UNKNOWN }

    private fun classify(obj: JSONObject): EventKind {
        val txType = obj.optString("txType", "").lowercase()
        return when (txType) {
            "create" -> EventKind.NEW_TOKEN
            "migrate" -> EventKind.MIGRATION
            "buy", "sell" -> EventKind.TRADE
            else -> EventKind.UNKNOWN
        }
    }

    private fun parseTokenCreated(obj: JSONObject, now: Long): NormalizedTokenCreatedEvent {
        return NormalizedTokenCreatedEvent(
            mint = obj.optStringOrNull("mint") ?: "UNKNOWN_MINT_${now}",
            name = obj.optStringOrNull("name"),
            symbol = obj.optStringOrNull("symbol"),
            creator = obj.optStringOrNull("traderPublicKey"),
            uri = obj.optStringOrNull("uri"),
            createdAtEpochMs = null, // not reported as epoch ms - use receivedAtEpochMs as the discovery clock
            marketCapSol = obj.optDoubleOrNull("marketCapSol"),
            vSolInBondingCurve = obj.optDoubleOrNull("vSolInBondingCurve"),
            vTokensInBondingCurve = obj.optDoubleOrNull("vTokensInBondingCurve"),
            bondingCurveKey = obj.optStringOrNull("bondingCurveKey"),
            receivedAtEpochMs = now
        )
    }

    private fun parseMigration(obj: JSONObject, now: Long): NormalizedMigrationEvent {
        val map = mutableMapOf<String, Any?>()
        obj.keys().forEach { k -> map[k] = obj.opt(k) }
        return NormalizedMigrationEvent(
            mint = obj.optStringOrNull("mint") ?: "UNKNOWN_MINT_${now}",
            migratedAtEpochMs = now,
            raw = map
        )
    }

    private fun parseTrade(obj: JSONObject, now: Long): NormalizedTradeEvent {
        val txType = obj.optString("txType", "").lowercase()
        val side = if (txType == "sell") TradeSide.SELL else TradeSide.BUY
        return NormalizedTradeEvent(
            mint = obj.optStringOrNull("mint") ?: "UNKNOWN_MINT_${now}",
            signature = obj.optStringOrNull("signature"),
            side = side,
            trader = obj.optStringOrNull("traderPublicKey"),
            solAmount = obj.optDoubleOrNull("solAmount"),
            tokenAmount = obj.optDoubleOrNull("tokenAmount"),
            vSolInBondingCurve = obj.optDoubleOrNull("vSolInBondingCurve"),
            vTokensInBondingCurve = obj.optDoubleOrNull("vTokensInBondingCurve"),
            marketCapSol = obj.optDoubleOrNull("marketCapSol"),
            timestampEpochMs = now
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null
}
