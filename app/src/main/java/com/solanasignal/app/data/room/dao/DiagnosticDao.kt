package com.solanasignal.app.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.solanasignal.app.data.room.entities.DiagnosticEventEntity
import com.solanasignal.app.data.room.entities.DiagnosticSessionEntity
import kotlinx.coroutines.flow.Flow

data class DiagnosticGroupCount(val groupKey: String, val count: Long)

@Dao
interface DiagnosticDao {
    @Upsert
    suspend fun upsertSession(session: DiagnosticSessionEntity)

    @Query("UPDATE diagnostic_sessions SET endedAtMs = :endedAtMs, endReason = :reason WHERE endedAtMs IS NULL")
    suspend fun closeOpenSessions(endedAtMs: Long, reason: String)

    @Query("UPDATE diagnostic_sessions SET endedAtMs = :endedAtMs, endReason = :reason WHERE sessionId = :sessionId")
    suspend fun endSession(sessionId: String, endedAtMs: Long, reason: String)

    @Query("SELECT * FROM diagnostic_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun session(sessionId: String): DiagnosticSessionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBatch(events: List<DiagnosticEventEntity>): List<Long>

    @Query("SELECT * FROM diagnostic_events WHERE sessionId = :sessionId AND id > :afterId ORDER BY id ASC LIMIT :limit")
    suspend fun eventsAfter(sessionId: String, afterId: Long, limit: Int): List<DiagnosticEventEntity>

    @Query("SELECT * FROM diagnostic_events WHERE sessionId = :sessionId ORDER BY id DESC LIMIT :limit")
    fun observeRecent(sessionId: String, limit: Int = 50): Flow<List<DiagnosticEventEntity>>

    @Query("SELECT COUNT(*) FROM diagnostic_events WHERE sessionId = :sessionId")
    suspend fun countEvents(sessionId: String): Long

    @Query("SELECT eventType AS groupKey, COUNT(*) AS count FROM diagnostic_events WHERE sessionId = :sessionId GROUP BY eventType")
    suspend fun countsByEventType(sessionId: String): List<DiagnosticGroupCount>

    @Query("SELECT dataSource AS groupKey, COUNT(*) AS count FROM diagnostic_events WHERE sessionId = :sessionId AND dataSource IS NOT NULL GROUP BY dataSource")
    suspend fun countsByDataSource(sessionId: String): List<DiagnosticGroupCount>

    @Query("SELECT component AS groupKey, COUNT(*) AS count FROM diagnostic_events WHERE sessionId = :sessionId GROUP BY component")
    suspend fun countsByComponent(sessionId: String): List<DiagnosticGroupCount>

    @Query("SELECT COUNT(*) FROM diagnostic_events WHERE sessionId = :sessionId AND severity = :severity")
    suspend fun countSeverity(sessionId: String, severity: String): Long

    @Query("SELECT COUNT(*) FROM diagnostic_events WHERE sessionId = :sessionId AND eventType = :eventType")
    suspend fun countType(sessionId: String, eventType: String): Long

    @Query("SELECT MIN(receivedAtMs) FROM diagnostic_events WHERE sessionId = :sessionId")
    suspend fun firstEventAt(sessionId: String): Long?

    @Query("SELECT MAX(receivedAtMs) FROM diagnostic_events WHERE sessionId = :sessionId")
    suspend fun lastEventAt(sessionId: String): Long?

    @Query("DELETE FROM diagnostic_events WHERE receivedAtMs < :cutoffMs")
    suspend fun deleteEventsBefore(cutoffMs: Long): Int

    @Query("DELETE FROM diagnostic_events WHERE id NOT IN (SELECT id FROM diagnostic_events ORDER BY id DESC LIMIT :keepRows)")
    suspend fun retainNewestEvents(keepRows: Int): Int

    @Query("DELETE FROM diagnostic_sessions WHERE startedAtMs < :cutoffMs AND endedAtMs IS NOT NULL")
    suspend fun deleteSessionsBefore(cutoffMs: Long): Int
}
