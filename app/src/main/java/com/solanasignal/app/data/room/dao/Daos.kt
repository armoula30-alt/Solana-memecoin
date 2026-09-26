package com.solanasignal.app.data.room.dao

import androidx.room.*
import com.solanasignal.app.data.room.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TokenDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(token: TokenEntity)

    @Query("SELECT * FROM tokens WHERE mint = :mint")
    suspend fun getByMint(mint: String): TokenEntity?

    @Query("SELECT * FROM tokens ORDER BY firstSeenAtEpochMs DESC")
    fun observeAll(): Flow<List<TokenEntity>>

    @Query("SELECT * FROM tokens WHERE lifecycle = :lifecycle")
    suspend fun getByLifecycle(lifecycle: String): List<TokenEntity>

    @Query("DELETE FROM tokens WHERE firstSeenAtEpochMs < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

@Dao
interface TradeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(trade: TradeEntity): Long

    @Query("SELECT COUNT(*) FROM trades WHERE dedupeKey = :dedupeKey")
    suspend fun existsByDedupeKey(dedupeKey: String): Int

    @Query("SELECT * FROM trades WHERE mint = :mint AND timestamp >= :sinceEpochMs ORDER BY timestamp ASC")
    suspend fun getSince(mint: String, sinceEpochMs: Long): List<TradeEntity>

    @Query("DELETE FROM trades WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

@Dao
interface MetricsDao {
    @Insert
    suspend fun insert(snapshot: MetricsSnapshotEntity): Long

    @Query("SELECT * FROM metrics WHERE mint = :mint ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recentForMint(mint: String, limit: Int = 50): List<MetricsSnapshotEntity>

    @Query("DELETE FROM metrics WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

@Dao
interface ScoreDao {
    @Insert
    suspend fun insert(score: ScoreEntity): Long

    @Query("SELECT * FROM scores WHERE mint = :mint ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestForMint(mint: String): ScoreEntity?

    @Query("DELETE FROM scores WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

@Dao
interface SignalDao {
    @Insert
    suspend fun insert(signal: SignalEntity): Long

    @Query("SELECT * FROM signals ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<SignalEntity>>

    @Query("SELECT * FROM signals WHERE signalType = :type ORDER BY timestamp DESC")
    fun observeByType(type: String): Flow<List<SignalEntity>>

    @Query("SELECT * FROM signals WHERE mint = :mint ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestForMint(mint: String): SignalEntity?

    @Query("SELECT COUNT(*) FROM signals WHERE timestamp >= :sinceEpochMs")
    suspend fun countSince(sinceEpochMs: Long): Int

    @Query("SELECT COUNT(*) FROM signals WHERE timestamp >= :sinceEpochMs AND signalType = :type")
    suspend fun countSinceByType(sinceEpochMs: Long, type: String): Int

    @Query("SELECT AVG(score) FROM signals WHERE timestamp >= :sinceEpochMs")
    suspend fun averageScoreSince(sinceEpochMs: Long): Double?

    @Query("SELECT MAX(score) FROM signals WHERE timestamp >= :sinceEpochMs")
    suspend fun maxScoreSince(sinceEpochMs: Long): Int?

    @Query("DELETE FROM signals WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

@Dao
interface SignalOutcomeDao {
    @Insert
    suspend fun insert(outcome: SignalOutcomeEntity): Long

    @Query("SELECT * FROM signal_outcomes WHERE signalId = :signalId ORDER BY checkTimestamp ASC")
    suspend fun forSignal(signalId: Long): List<SignalOutcomeEntity>
}

@Dao
interface SystemEventDao {
    @Insert
    suspend fun insert(event: SystemEventEntity): Long

    @Query("SELECT * FROM system_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<SystemEventEntity>>

    @Query("DELETE FROM system_events WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
