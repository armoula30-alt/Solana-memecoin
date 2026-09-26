package com.solanasignal.app.data.pumpportal

import org.json.JSONObject

/**
 * Parses raw PumpPortal WebSocket JSON frames into normalized internal events.
 *
 * SOURCE OF TRUTH: https://pumpportal.fun/data-api/real-time/
 * This parser reads only fields that are part of the current official documentation.
 * It deliberately does NOT assume any field is present - every field access is
 * optional-safe, and a missing field maps to `null` rather than a fabricated value
 * (spec #9, #41). If PumpPortal changes its schema, this is the single place to update
 * (spec #39/#40 isolation).
 *
 * PumpPortal's `subscribeNewToken` / `subscribeTokenTrade` payloads are JSON objects;
 * the exact key names must be verified against current docs before shipping and this
 * class should be treated as a template to be confirmed/adjusted against that source,
 * NOT as a guarantee of exact field names, since the assistant generating this project
 * does not have network access to fetch the live schema at generation time.
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
                EventKind.UNKNOWN -> ParseResult.Unknown(obj.optString("txType", obj.optString("type", null)), rawJson)
            }
        } catch (e: Exception) {
            ParseResult.Malformed(rawJson, e.message ?: "unknown parse error")
        }
    }

    private enum class EventKind { NEW_TOKEN, MIGRATION, TRADE, UNKNOWN }

    private fun classify(obj: JSONObject): EventKind {
        // PumpPortal frames typically carry a "txType" discriminator (create/buy/sell/migrate).
        val txType = obj.optString("txType", "").lowercase()
        return when {
            txType == "create" -> EventKind.NEW_TOKEN
            txType == "migrate" -> EventKind.MIGRATION
            txType == "buy" || txType == "sell" -> EventKind.TRADE
            obj.has("mint") && obj.has("marketCapSol") && !obj.has("txType") -> EventKind.NEW_TOKEN
            else -> EventKind.UNKNOWN
        }
    }

    private fun parseTokenCreated(obj: JSONObject, now: Long): NormalizedTokenCreatedEvent {
        return NormalizedTokenCreatedEvent(
            mint = obj.optStringOrNull("mint") ?: "UNKNOWN_MINT_${now}",
            name = obj.optStringOrNull("name"),
            symbol = obj.optStringOrNull("symbol"),
            creator = obj.optStringOrNull("traderPublicKey") ?: obj.optStringOrNull("creator"),
            uri = obj.optStringOrNull("uri"),
            createdAtEpochMs = null, // not reliably provided as epoch ms - treat as UNKNOWN, use receivedAtEpochMs
            initialMarketCapUsd = obj.optDoubleOrNull("marketCapUsd") ?: obj.optDoubleOrNull("marketCap"),
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
            amountUsd = obj.optDoubleOrNull("solAmountUsd") ?: obj.optDoubleOrNull("amountUsd"),
            priceUsd = obj.optDoubleOrNull("priceUsd"),
            timestampEpochMs = now
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null
}
