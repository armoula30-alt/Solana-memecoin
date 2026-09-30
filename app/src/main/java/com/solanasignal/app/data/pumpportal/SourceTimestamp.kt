package com.solanasignal.app.data.pumpportal

import org.json.JSONObject

/** Supports documented/observed epoch-second or epoch-millisecond timestamps only. */
internal fun JSONObject.sourceTimestampEpochMs(): Long? {
    val key = listOf("timestamp", "blockTime", "blockTimestamp").firstOrNull { has(it) && !isNull(it) } ?: return null
    val value = optDouble(key, Double.NaN)
    if (!value.isFinite() || value <= 0.0) return null
    return when {
        value >= 1_000_000_000_000.0 -> value.toLong()
        value >= 1_000_000_000.0 -> (value * 1_000.0).toLong()
        else -> null
    }
}
