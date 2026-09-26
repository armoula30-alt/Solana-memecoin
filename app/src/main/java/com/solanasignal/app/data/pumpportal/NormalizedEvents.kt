package com.solanasignal.app.data.pumpportal

/**
 * Internal normalized models. The rest of the app (metrics/scoring/safety/signals/UI)
 * only ever consumes these - never raw PumpPortal JSON. This isolates the app from
 * upstream API changes (spec #39, #40).
 *
 * IMPORTANT: PumpPortal reports on-chain quantities in SOL, not USD (confirmed
 * fields: marketCapSol, vSolInBondingCurve, vTokensInBondingCurve, solAmount,
 * tokenAmount - see PumpPortalEventParser.kt for sources). USD conversion happens
 * one layer up, in ScannerOrchestrator, using SolPriceProvider - never invented here.
 */

data class NormalizedTokenCreatedEvent(
    val mint: String,
    val name: String?,
    val symbol: String?,
    val creator: String?,
    val uri: String?,
    val createdAtEpochMs: Long?,
    val marketCapSol: Double?,
    val vSolInBondingCurve: Double?,       // SOL reserve in the bonding curve -> used as liquidity proxy
    val vTokensInBondingCurve: Double?,
    val bondingCurveKey: String?,          // pool identifier - used to deep-link into Photon
    val receivedAtEpochMs: Long
)

data class NormalizedMigrationEvent(
    val mint: String,
    val migratedAtEpochMs: Long,
    val raw: Map<String, Any?>
)

data class NormalizedTradeEvent(
    val mint: String,
    val signature: String?,
    val side: TradeSide,
    val trader: String?,
    val solAmount: Double?,                // trade size in SOL
    val tokenAmount: Double?,
    val vSolInBondingCurve: Double?,
    val vTokensInBondingCurve: Double?,
    val marketCapSol: Double?,
    val timestampEpochMs: Long
) {
    /** price in SOL per token, derived from bonding-curve reserves; null if reserves not reported. */
    val priceSol: Double?
        get() = if (vSolInBondingCurve != null && vTokensInBondingCurve != null && vTokensInBondingCurve > 0)
            vSolInBondingCurve / vTokensInBondingCurve else null

    /**
     * Duplicate protection (spec #12): prefer the real signature. If PumpPortal
     * does not supply one for this event type, fall back to a *documented*
     * deterministic key built only from fields actually present on the event -
     * never a random or invented id.
     */
    fun dedupeKey(): String =
        signature ?: "fallback:$mint:${trader ?: "?"}:${solAmount ?: "?"}:${tokenAmount ?: "?"}:$timestampEpochMs"
}

enum class TradeSide { BUY, SELL }

enum class ConnectionState { CONNECTING, CONNECTED, DEGRADED, RECONNECTING, DISCONNECTED }
