package com.solanasignal.app.data.telemetry

import com.solanasignal.app.data.room.AppDatabase
import com.solanasignal.app.data.room.entities.DiagnosticEventEntity
import com.solanasignal.app.data.room.entities.DiagnosticSessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

private data class PendingDiagnosticEvent(
    val sessionId: String,
    val eventTimestampMs: Long?,
    val receivedAtMs: Long,
    val eventLatencyMs: Long?,
    val component: String,
    val eventType: String,
    val tokenAddress: String?,
    val severity: String,
    val message: String,
    val dataSource: String?,
    val metadata: Map<String, Any?>,
    val rawFrame: String?,
    val rawFrameOriginalLength: Int?
)

/** Buffered, bounded diagnostics writer. record() never waits for Room or disk. */
class StructuredTelemetryLogger(private val db: AppDatabase) {
    companion object {
        private const val QUEUE_CAPACITY = 2_048
        private const val MAX_ROWS = 25_000
        private const val BATCH_SIZE = 128
        private const val FLUSH_WAIT_MS = 300L
        private const val MAX_LATENCY_SAMPLES = 2_000
        private const val MAX_RAW_FRAME_CHARS = 2_048
        private const val EXCESSIVE_LATENCY_MS = 5_000L
        private const val FUTURE_TIMESTAMP_TOLERANCE_MS = 5_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<PendingDiagnosticEvent>(capacity = QUEUE_CAPACITY)
    private val dropped = AtomicLong(0)
    private val writerFailures = AtomicLong(0)
    private val latencySamples = ArrayDeque<Long>()
    private val inboundTimes = ArrayDeque<Long>()
    private var latencyStatsUpdatedAtMs = Long.MIN_VALUE
    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId.asStateFlow()
    private val sessionReady = CompletableDeferred<Unit>()
    private val _runtime = MutableStateFlow(DiagnosticRuntimeSnapshot())
    val runtime: StateFlow<DiagnosticRuntimeSnapshot> = _runtime.asStateFlow()

    init { scope.launch { writerLoop() } }

    fun startSession(appVersion: String, buildVersion: Long, initialSource: String, nowMs: Long = System.currentTimeMillis()): String {
        val id = UUID.randomUUID().toString()
        _sessionId.value = id
        synchronized(this) {
            latencySamples.clear()
            inboundTimes.clear()
            latencyStatsUpdatedAtMs = Long.MIN_VALUE
            _runtime.value = DiagnosticRuntimeSnapshot(sessionId = id, dataSource = initialSource)
        }
        scope.launch {
            try {
                db.diagnosticDao().closeOpenSessions(nowMs, "process_restarted")
                db.diagnosticDao().upsertSession(
                    DiagnosticSessionEntity(
                        sessionId = id,
                        startedAtMs = nowMs,
                        appVersion = appVersion,
                        buildVersion = buildVersion,
                        initialDataSource = initialSource
                    )
                )
                sessionReady.complete(Unit)
                record(
                    component = "APP",
                    eventType = "SESSION_STARTED",
                    severity = DiagnosticSeverity.INFO,
                    message = "Diagnostic session started",
                    dataSource = initialSource,
                    receivedAtMs = nowMs,
                    metadata = mapOf("schemaVersion" to 1, "appVersion" to appVersion, "buildVersion" to buildVersion)
                )
            } catch (_: Exception) {
                writerFailure()
                sessionReady.complete(Unit)
            }
        }
        return id
    }

    fun endSession(reason: String, atMs: Long = System.currentTimeMillis()) {
        val id = _sessionId.value ?: return
        record("APP", "SESSION_ENDED", DiagnosticSeverity.INFO, "Diagnostic session ended", receivedAtMs = atMs, metadata = mapOf("reason" to reason))
        scope.launch { runCatching { db.diagnosticDao().endSession(id, atMs, reason) }.onFailure { writerFailure() } }
    }

    fun setTraceEnabled(enabled: Boolean) { traceEnabled = enabled }
    @Volatile private var traceEnabled: Boolean = false

    fun record(
        component: String,
        eventType: String,
        severity: DiagnosticSeverity,
        message: String,
        tokenAddress: String? = null,
        dataSource: String? = null,
        eventTimestampMs: Long? = null,
        receivedAtMs: Long = System.currentTimeMillis(),
        metadata: Map<String, Any?> = emptyMap(),
        rawFrame: String? = null,
        incomingEvent: Boolean = false
    ) {
        if (severity == DiagnosticSeverity.TRACE && !traceEnabled) return
        val sid = _sessionId.value ?: return
        val latency = eventTimestampMs?.let { receivedAtMs - it }?.takeIf { it >= 0L }
        val pending = PendingDiagnosticEvent(
            sessionId = sid,
            eventTimestampMs = eventTimestampMs,
            receivedAtMs = receivedAtMs,
            eventLatencyMs = latency,
            component = component.take(80),
            eventType = eventType.take(100),
            tokenAddress = tokenAddress?.take(128),
            severity = severity.name,
            message = message.take(1_000),
            dataSource = dataSource?.take(32),
            metadata = LinkedHashMap<String, Any?>().apply {
                put("schemaVersion", 1)
                putAll(metadata)
            },
            rawFrame = rawFrame?.take(MAX_RAW_FRAME_CHARS),
            rawFrameOriginalLength = rawFrame?.length
        )
        var refreshLatencyStats = false
        synchronized(this) {
            val old = _runtime.value
            if (incomingEvent) {
                inboundTimes.addLast(receivedAtMs)
                while (inboundTimes.isNotEmpty() && receivedAtMs - inboundTimes.first() > 1_000L) inboundTimes.removeFirst()
            }
            if (incomingEvent && latency != null) {
                latencySamples.addLast(latency)
                while (latencySamples.size > MAX_LATENCY_SAMPLES) latencySamples.removeFirst()
                if (latencyStatsUpdatedAtMs == Long.MIN_VALUE || receivedAtMs - latencyStatsUpdatedAtMs >= 1_000L || receivedAtMs < latencyStatsUpdatedAtMs) {
                    latencyStatsUpdatedAtMs = receivedAtMs
                    refreshLatencyStats = true
                }
            }
            _runtime.value = old.copy(
                sessionId = sid,
                dataSource = dataSource ?: old.dataSource,
                eventsPerSecond = inboundTimes.size,
                incomingEvents = old.incomingEvents + if (incomingEvent) 1 else 0,
                malformedEvents = old.malformedEvents + if (eventType == "MALFORMED_EVENT") 1 else 0,
                unknownEvents = old.unknownEvents + if (eventType == "UNKNOWN_EVENT") 1 else 0,
                errors = old.errors + if (severity == DiagnosticSeverity.ERROR) 1 else 0,
                warnings = old.warnings + if (severity == DiagnosticSeverity.WARN) 1 else 0,
                lastEventReceivedAtMs = receivedAtMs,
                eventLatencyP50Ms = if (refreshLatencyStats) percentile(latencySamples, 0.50) else old.eventLatencyP50Ms,
                eventLatencyP95Ms = if (refreshLatencyStats) percentile(latencySamples, 0.95) else old.eventLatencyP95Ms,
                eventLatencyMaxMs = if (refreshLatencyStats) latencySamples.maxOrNull() else old.eventLatencyMaxMs,
                droppedLogCount = dropped.get(),
                writerFailureCount = writerFailures.get()
            )
        }
        if (queue.trySend(pending).isFailure) {
            val count = dropped.incrementAndGet()
            _runtime.value = _runtime.value.copy(droppedLogCount = count)
        }
    }

    fun updateRuntime(
        dataSource: String,
        connectionState: String,
        activeTokens: Int,
        subscriptions: Int,
        staleTokens: Int
    ) {
        _runtime.value = _runtime.value.copy(
            dataSource = dataSource,
            connectionState = connectionState,
            activeTokens = activeTokens,
            subscriptions = subscriptions,
            staleTokens = staleTokens,
            droppedLogCount = dropped.get(),
            writerFailureCount = writerFailures.get()
        )
    }

    fun recordMarketEvent(
        source: String,
        eventType: String,
        tokenAddress: String?,
        eventTimestampMs: Long?,
        receivedAtMs: Long,
        metadata: Map<String, Any?>,
        rawFrame: String?
    ) {
        val severity = when (eventType) {
            "MALFORMED_EVENT" -> DiagnosticSeverity.ERROR
            "UNKNOWN_EVENT", "PROVIDER_ERROR" -> DiagnosticSeverity.WARN
            else -> DiagnosticSeverity.INFO
        }
        record(
            component = "MARKET_FEED",
            eventType = eventType,
            severity = severity,
            message = eventType.replace('_', ' ').lowercase(),
            tokenAddress = tokenAddress,
            dataSource = source,
            eventTimestampMs = eventTimestampMs,
            receivedAtMs = receivedAtMs,
            metadata = metadata,
            rawFrame = rawFrame.takeIf { traceEnabled },
            incomingEvent = true
        )
        val latencyMs = eventTimestampMs?.let { receivedAtMs - it }
        if (latencyMs != null && latencyMs > EXCESSIVE_LATENCY_MS) record(
            component = "DATA_QUALITY", eventType = "EXCESSIVE_LATENCY", severity = DiagnosticSeverity.WARN,
            message = "Provider event exceeded the latency warning threshold", tokenAddress = tokenAddress,
            dataSource = source, eventTimestampMs = eventTimestampMs, receivedAtMs = receivedAtMs,
            metadata = mapOf("eventLatencyMs" to latencyMs, "thresholdMs" to EXCESSIVE_LATENCY_MS, "relatedEventType" to eventType)
        )
        if (eventTimestampMs != null && eventTimestampMs - receivedAtMs > FUTURE_TIMESTAMP_TOLERANCE_MS) record(
            component = "DATA_QUALITY", eventType = "FUTURE_SOURCE_TIMESTAMP", severity = DiagnosticSeverity.WARN,
            message = "Provider event timestamp is materially in the future", tokenAddress = tokenAddress,
            dataSource = source, eventTimestampMs = eventTimestampMs, receivedAtMs = receivedAtMs,
            metadata = mapOf("futureOffsetMs" to (eventTimestampMs - receivedAtMs), "toleranceMs" to FUTURE_TIMESTAMP_TOLERANCE_MS)
        )
    }

    private suspend fun writerLoop() {
        val batch = ArrayList<PendingDiagnosticEvent>(BATCH_SIZE)
        while (scope.isActive) {
            val first = queue.receiveCatching().getOrNull() ?: break
            batch.add(first)
            val until = System.currentTimeMillis() + FLUSH_WAIT_MS
            while (batch.size < BATCH_SIZE) {
                val remaining = until - System.currentTimeMillis()
                if (remaining <= 0L) break
                val next = withTimeoutOrNull(remaining) { queue.receive() } ?: break
                batch.add(next)
            }
            try {
                sessionReady.await()
                db.diagnosticDao().insertBatch(batch.map { it.toEntity() })
                db.diagnosticDao().retainNewestEvents(MAX_ROWS)
            } catch (_: Exception) {
                writerFailure()
            } finally {
                batch.clear()
            }
        }
    }

    private fun writerFailure() {
        val count = writerFailures.incrementAndGet()
        _runtime.value = _runtime.value.copy(writerFailureCount = count)
    }

    private fun boundedMetadataJson(metadata: Map<String, Any?>): String {
        val encoded = DiagnosticJson.stringify(metadata)
        if (encoded.length <= 4_096) return encoded
        return DiagnosticJson.stringify(mapOf(
            "schemaVersion" to 1,
            "metadataTruncated" to true,
            "originalLength" to encoded.length,
            "preview" to encoded.take(3_000)
        ))
    }

    private fun PendingDiagnosticEvent.toEntity(): DiagnosticEventEntity {
        val safeMetadata = LinkedHashMap(metadata)
        rawFrame?.let { safeMetadata["rawFrame"] = DiagnosticJson.safeRawFrame(it, rawFrameOriginalLength ?: it.length) }
        return DiagnosticEventEntity(
            sessionId = sessionId,
            eventTimestampMs = eventTimestampMs,
            receivedAtMs = receivedAtMs,
            eventLatencyMs = eventLatencyMs,
            component = component,
            eventType = eventType,
            tokenAddress = tokenAddress,
            severity = severity,
            message = DiagnosticJson.redactText(message),
            dataSource = dataSource,
            metadataJson = boundedMetadataJson(safeMetadata)
        )
    }

    private fun percentile(values: Collection<Long>, p: Double): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val index = (ceil(p * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }
}
