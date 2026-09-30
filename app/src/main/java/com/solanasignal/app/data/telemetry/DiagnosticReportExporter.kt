package com.solanasignal.app.data.telemetry

import com.solanasignal.app.data.room.AppDatabase
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter

class DiagnosticReportExporter(private val db: AppDatabase) {
    suspend fun writeCurrentSession(
        sessionId: String,
        output: OutputStream,
        runtime: DiagnosticRuntimeSnapshot? = null
    ): Long = withContext(Dispatchers.IO) {
        val dao = db.diagnosticDao()
        val session = dao.session(sessionId) ?: error("Diagnostic session is not available yet")
        val count = dao.countEvents(sessionId)
        val typeCounts = dao.countsByEventType(sessionId).associate { it.groupKey to it.count }
        val sourceCounts = dao.countsByDataSource(sessionId).associate { it.groupKey to it.count }
        val componentCounts = dao.countsByComponent(sessionId).associate { it.groupKey to it.count }
        val header = mapOf(
            "schemaVersion" to 1,
            "session" to mapOf(
                "sessionId" to session.sessionId,
                "startedAtMs" to session.startedAtMs,
                "endedAtMs" to session.endedAtMs,
                "endReason" to session.endReason,
                "appVersion" to session.appVersion,
                "buildVersion" to session.buildVersion,
                "initialDataSource" to session.initialDataSource,
                "eventCount" to count,
                "errorCount" to dao.countSeverity(sessionId, "ERROR"),
                "warningCount" to dao.countSeverity(sessionId, "WARN"),
                "malformedCount" to dao.countType(sessionId, "MALFORMED_EVENT"),
                "unknownCount" to dao.countType(sessionId, "UNKNOWN_EVENT"),
                "firstEventAtMs" to dao.firstEventAt(sessionId),
                "lastEventAtMs" to dao.lastEventAt(sessionId),
                "eventTypeCounts" to typeCounts,
                "sourceCounts" to sourceCounts,
                "componentCounts" to componentCounts,
                "connectionStats" to mapOf(
                    "connectionEvents" to (typeCounts["CONNECTION"] ?: 0L),
                    "stateTransitions" to (typeCounts["CONNECTION_STATE"] ?: 0L),
                    "reconnectAttempts" to (typeCounts["RECONNECT"] ?: 0L),
                    "subscriptionCommands" to (typeCounts["SUBSCRIPTION"] ?: 0L),
                    "subscriptionAcks" to (typeCounts["SUBSCRIPTION_ACK"] ?: 0L),
                    "subscriptionFailures" to ((typeCounts["SUBSCRIPTION_ERROR"] ?: 0L) + (typeCounts["SUBSCRIPTION_REJECTED"] ?: 0L))
                ),
                "scannerActivity" to mapOf(
                    "entered" to (typeCounts["SCANNER_CANDIDATE_ENTERED"] ?: 0L),
                    "rankChanges" to (typeCounts["RANK_CHANGED"] ?: 0L),
                    "exited" to (typeCounts["SCANNER_CANDIDATE_EXITED"] ?: 0L)
                ),
                "signalActivity" to mapOf(
                    "decisions" to (typeCounts["SIGNAL_DECISION"] ?: 0L),
                    "emitted" to (typeCounts["SIGNAL_EMITTED"] ?: 0L)
                ),
                "paperActivity" to mapOf(
                    "fills" to (typeCounts["PAPER_FILL"] ?: 0L),
                    "rejected" to (typeCounts["PAPER_REJECTED"] ?: 0L),
                    "portfolioSnapshots" to (typeCounts["PORTFOLIO_SNAPSHOT"] ?: 0L)
                ),
                "dataQualityCounts" to typeCounts.filterKeys {
                    it in setOf("MISSING_MARKET_FIELDS", "INVALID_NUMERIC_VALUE", "DUPLICATE_EVENT", "OUT_OF_ORDER", "SOURCE_TIMESTAMP_GAP", "EXCESSIVE_LATENCY", "FUTURE_SOURCE_TIMESTAMP", "STALE_MARKET_DATA")
                },
                "runtimeAtExport" to runtime?.let { snapshot -> mapOf(
                    "dataSource" to snapshot.dataSource, "connectionState" to snapshot.connectionState,
                    "eventsPerSecond" to snapshot.eventsPerSecond, "incomingEvents" to snapshot.incomingEvents,
                    "malformedEvents" to snapshot.malformedEvents, "unknownEvents" to snapshot.unknownEvents,
                    "errors" to snapshot.errors, "warnings" to snapshot.warnings,
                    "activeTokens" to snapshot.activeTokens, "subscriptions" to snapshot.subscriptions,
                    "staleTokens" to snapshot.staleTokens, "lastEventReceivedAtMs" to snapshot.lastEventReceivedAtMs,
                    "eventLatencyP50Ms" to snapshot.eventLatencyP50Ms, "eventLatencyP95Ms" to snapshot.eventLatencyP95Ms,
                    "eventLatencyMaxMs" to snapshot.eventLatencyMaxMs, "droppedLogCount" to snapshot.droppedLogCount,
                    "writerFailureCount" to snapshot.writerFailureCount
                ) }
            ),
            "eventEncoding" to "events array: stored event, receive timestamps; null values mean unavailable, never imputed"
        )
        val writer = BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8))
        val prefix = DiagnosticJson.stringify(header)
        writer.write(prefix.dropLast(1))
        writer.write(",\"events\":[")
        var afterId = 0L
        var first = true
        while (true) {
            val page = dao.eventsAfter(sessionId, afterId, 200)
            if (page.isEmpty()) break
            for (event in page) {
                if (!first) writer.write(",")
                first = false
                writer.write(DiagnosticJson.stringify(mapOf(
                    "id" to event.id,
                    "eventTimestampMs" to event.eventTimestampMs,
                    "receivedAtMs" to event.receivedAtMs,
                    "eventLatencyMs" to event.eventLatencyMs,
                    "component" to event.component,
                    "eventType" to event.eventType,
                    "tokenAddress" to event.tokenAddress,
                    "severity" to event.severity,
                    "message" to event.message,
                    "dataSource" to event.dataSource,
                    "metadata" to parseMetadata(event.metadataJson)
                )))
                afterId = event.id
            }
            if (page.size < 200) break
        }
        writer.write("]}")
        writer.flush()
        count
    }

    private fun parseMetadata(json: String): Map<String, Any?> = runCatching { JSONObject(json).toValueMap() }
        .getOrElse { mapOf("unparsedMetadata" to DiagnosticJson.redactText(json).take(4_096)) }

    private fun JSONObject.toValueMap(): Map<String, Any?> = buildMap {
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = opt(key)
            put(key, when (value) {
                null, JSONObject.NULL -> null
                is JSONObject -> value.toValueMap()
                is JSONArray -> (0 until value.length()).map { index ->
                    when (val item = value.opt(index)) {
                        null, JSONObject.NULL -> null
                        is JSONObject -> item.toValueMap()
                        is JSONArray -> item.toString()
                        else -> item
                    }
                }
                else -> value
            })
        }
    }
}
