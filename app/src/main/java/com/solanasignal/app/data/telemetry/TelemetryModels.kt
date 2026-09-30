package com.solanasignal.app.data.telemetry

import java.util.Locale

/** Selects one live provider at a time; source identity is persisted with every observation. */
enum class MarketFeedProvider(
    val sourceId: String,
    val displayName: String,
    val websocketUrl: String,
    val requiresApiKey: Boolean,
    val maxLiveTokenSubscriptions: Int?
) {
    PUMPPORTAL("PUMPPORTAL", "PumpPortal", "wss://pumpportal.fun/api/data", true, null),
    PUMPDEV("PUMPDEV", "PumpDev", "wss://pumpdev.io/ws", false, 5)
}

enum class DiagnosticSeverity { ERROR, WARN, INFO, DEBUG, TRACE }

data class DiagnosticRuntimeSnapshot(
    val sessionId: String? = null,
    val dataSource: String = "UNKNOWN",
    val connectionState: String = "DISCONNECTED",
    val eventsPerSecond: Int = 0,
    val incomingEvents: Long = 0,
    val malformedEvents: Long = 0,
    val unknownEvents: Long = 0,
    val errors: Long = 0,
    val warnings: Long = 0,
    val activeTokens: Int = 0,
    val subscriptions: Int = 0,
    val staleTokens: Int = 0,
    val lastEventReceivedAtMs: Long? = null,
    val eventLatencyP50Ms: Long? = null,
    val eventLatencyP95Ms: Long? = null,
    val eventLatencyMaxMs: Long? = null,
    val droppedLogCount: Long = 0,
    val writerFailureCount: Long = 0
)

/** Stable JSON encoder for diagnostic metadata; unsupported/non-finite values remain null. */
object DiagnosticJson {
    private val secretKey = Regex("(?i)(api[-_]?key|private[-_]?key|secret|seed(?:[-_ ]?phrase)?|mnemonic|password|authorization|bearer)")
    private val apiKeyQuery = Regex("(?i)(api-key=)[^&\\s\\\"']+")
    private val secretJsonField = Regex("(?i)(\\\"[^\\\"]*(?:api[-_]?key|private[-_]?key|secret|seed(?:[-_ ]?phrase)?|mnemonic|password|authorization)[^\\\"]*\\\"\\s*:\\s*)\\\"(?:\\\\.|[^\\\"\\\\])*\\\"")
    private val identityJsonField = Regex("(?i)(\\\"(?:trader|traderPublicKey|wallet|walletAddress|owner|creator|creatorAddress)\\\"\\s*:\\s*)\\\"(?:\\\\.|[^\\\"\\\\])*\\\"")
    private const val MAX_RAW_CHARS = 2_048

    fun redactText(value: String): String {
        val queryRedacted = apiKeyQuery.replace(value) { "${it.groupValues[1]}[REDACTED]" }
        val secretsRedacted = secretJsonField.replace(queryRedacted) { "${it.groupValues[1]}\"[REDACTED]\"" }
        return identityJsonField.replace(secretsRedacted) { "${it.groupValues[1]}\"[REDACTED]\"" }
    }

    fun safeRawFrame(value: String, originalLength: Int = value.length): String {
        val clipped = value.take(MAX_RAW_CHARS)
        val redacted = redactText(clipped)
        return if (originalLength > MAX_RAW_CHARS) "$redacted…[TRUNCATED:$originalLength]" else redacted
    }

    fun stringify(value: Any?): String = buildString { appendValue(this, value, 0) }

    private fun appendValue(out: StringBuilder, value: Any?, depth: Int) {
        if (depth > 12) { out.append("null"); return }
        when (value) {
            null -> out.append("null")
            is String -> appendQuoted(out, redactText(value))
            is Boolean -> out.append(value)
            is Byte, is Short, is Int, is Long -> out.append(value)
            is Float -> if (value.isFinite()) out.append(value) else out.append("null")
            is Double -> if (value.isFinite()) out.append(value) else out.append("null")
            is Enum<*> -> appendQuoted(out, value.name)
            is Map<*, *> -> {
                out.append('{')
                var first = true
                value.entries.filter { it.key is String }.sortedBy { it.key.toString() }.forEach { (key, item) ->
                    val name = key.toString()
                    if (!first) out.append(',')
                    first = false
                    appendQuoted(out, name)
                    out.append(':')
                    if (secretKey.containsMatchIn(name) || isIdentityKey(name)) appendQuoted(out, "[REDACTED]") else appendValue(out, item, depth + 1)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                value.take(512).forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    appendValue(out, item, depth + 1)
                }
                out.append(']')
            }
            is Array<*> -> appendValue(out, value.asIterable(), depth + 1)
            else -> appendQuoted(out, redactText(value.toString()))
        }
    }

    private fun appendQuoted(out: StringBuilder, value: String) {
        out.append('"')
        value.forEach { ch ->
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (ch.code < 0x20) out.append(String.format(Locale.US, "\\u%04x", ch.code)) else out.append(ch)
            }
        }
        out.append('"')
    }

    private fun isIdentityKey(key: String): Boolean = key.equals("trader", true) ||
        key.equals("traderPublicKey", true) || key.equals("wallet", true) ||
        key.equals("walletAddress", true) || key.equals("owner", true) ||
        key.equals("creator", true) || key.equals("creatorAddress", true)
}
