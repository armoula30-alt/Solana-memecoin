package com.solanasignal.app.data.pumpportal

/** Normalized app inputs; source-specific details remain available to the diagnostic recorder. */
data class NormalizedTokenCreatedEvent(
    val mint: String,
    val name: String?,
    val symbol: String?,
    val creator: String?,
    val uri: String?,
    val createdAtEpochMs: Long?,
    val marketCapSol: Double?,
    val vSolInBondingCurve: Double?,
    val vTokensInBondingCurve: Double?,
    val bondingCurveKey: String?,
    val receivedAtEpochMs: Long,
    val sourceTimestampEpochMs: Long? = null,
    val rawFrame: String? = null
)

data class NormalizedMigrationEvent(
    val mint: String,
    val migratedAtEpochMs: Long,
    val raw: Map<String, Any?>,
    val sourceTimestampEpochMs: Long? = null,
    val rawFrame: String? = null
)

data class NormalizedTradeEvent(
    val mint: String,
    val signature: String?,
    val side: TradeSide,
    val trader: String?,
    val solAmount: Double?,
    val tokenAmount: Double?,
    val vSolInBondingCurve: Double?,
    val vTokensInBondingCurve: Double?,
    val marketCapSol: Double?,
    /** Receive/processing time used by the existing metrics engine; not a claimed block timestamp. */
    val timestampEpochMs: Long,
    val sourceTimestampEpochMs: Long? = null,
    val rawFrame: String? = null,
    val priceSolOverride: Double? = null,
    val providerSource: String = "PUMPPORTAL"
) {
    val priceSol: Double?
        get() = priceSolOverride?.takeIf { it.isFinite() && it > 0.0 }
            ?: if (vSolInBondingCurve != null && vTokensInBondingCurve != null && vTokensInBondingCurve > 0.0)
                (vSolInBondingCurve / vTokensInBondingCurve).takeIf { it.isFinite() && it > 0.0 }
            else null

    fun dedupeKey(): String =
        signature ?: "fallback:$mint:${trader ?: "?"}:${solAmount ?: "?"}:${tokenAmount ?: "?"}:$timestampEpochMs"
}

enum class TradeSide { BUY, SELL }
enum class ConnectionState { CONNECTING, CONNECTED, DEGRADED, RECONNECTING, DISCONNECTED }
