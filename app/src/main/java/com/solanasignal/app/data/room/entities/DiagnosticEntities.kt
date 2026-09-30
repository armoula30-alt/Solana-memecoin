package com.solanasignal.app.data.room.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "diagnostic_sessions")
data class DiagnosticSessionEntity(
    @PrimaryKey val sessionId: String,
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    val appVersion: String,
    val buildVersion: Long,
    val initialDataSource: String,
    val schemaVersion: Int = 1,
    val endReason: String? = null
)

@Entity(
    tableName = "diagnostic_events",
    indices = [
        Index(value = ["sessionId", "id"]),
        Index(value = ["sessionId", "receivedAtMs"]),
        Index(value = ["dataSource", "eventType", "receivedAtMs"]),
        Index(value = ["tokenAddress", "receivedAtMs"])
    ]
)
data class DiagnosticEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    /** Versioned structured metadata including a sanitized rawFrame when applicable. */
    val metadataJson: String
)
