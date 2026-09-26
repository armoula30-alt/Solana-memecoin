package com.solanasignal.app.data.pumpportal

/**
 * Internal normalized models. The rest of the app (metrics/scoring/safety/signals/UI)
 * only ever consumes these - never raw PumpPortal JSON. This isolates the app from
 * upstream API changes (spec #39, #40).
 *
 * IMPORTANT: fields are nullable when PumpPortal does not reliably provide them.
 * Parsers must never invent/fabricate a value for a missing field (spec #41).
 */

data class NormalizedTokenCreatedEvent(
    val mint: String,
    val name: String?,
    val symbol: String?,
    val creator: String?,
    val uri: String?,
    val createdAtEpochMs: Long?,
    val initialMarketCapUsd: Double?,
    val receivedAtEpochMs: Long
)

data class NormalizedMigrationEvent(
    val mint: String,
    val migratedAtEpochMs: Long,
    val raw: Map<String, Any?>
)

data class NormalizedTradeEvent(
    val mint: String,
    val signature: String?,          // official identifier when provided
    val side: TradeSide,
    val trader: String?,
    val amountUsd: Double?,
    val priceUsd: Double?,
    val timestampEpochMs: Long
) {
    /**
     * Duplicate protection (spec #12): prefer the real signature. If PumpPortal
     * does not supply one for this event type, fall back to a *documented*
     * deterministic key built only from fields actually present on the event -
     * never a random or invented id. This means two genuinely distinct trades
     * that happen to share mint+trader+price+timestampMs could collide; that
     * tradeoff is intentional and documented rather than risking false dedupe
     * against fabricated data.
     */
    fun dedupeKey(): String =
        signature ?: "fallback:$mint:${trader ?: "?"}:${priceUsd ?: "?"}:${amountUsd ?: "?"}:$timestampEpochMs"
}

enum class TradeSide { BUY, SELL }

enum class ConnectionState { CONNECTING, CONNECTED, DEGRADED, RECONNECTING, DISCONNECTED }
