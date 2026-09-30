package com.solanasignal.app.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.solanasignal.app.data.room.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MarketEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(event: MarketEventEntity): Long
    @Query("SELECT * FROM market_events WHERE mint = :mint ORDER BY timestamp ASC") suspend fun forMint(mint: String): List<MarketEventEntity>
    @Query("SELECT * FROM market_events ORDER BY timestamp ASC LIMIT :limit") suspend fun ordered(limit: Int = 10000): List<MarketEventEntity>
    @Query("DELETE FROM market_events WHERE timestamp < :cutoff") suspend fun deleteOlderThan(cutoff: Long)
}

@Dao
interface ABObservationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(observation: ABObservationEntity): Long
    @Query("SELECT * FROM ab_observations WHERE opportunityId = :opportunityId ORDER BY timestamp ASC") fun forOpportunity(opportunityId: String): Flow<List<ABObservationEntity>>
    @Query("SELECT * FROM ab_observations ORDER BY timestamp DESC LIMIT :limit") fun recent(limit: Int = 500): Flow<List<ABObservationEntity>>
    @Query("SELECT COUNT(*) FROM ab_observations WHERE engine = :engine") suspend fun countByEngine(engine: String): Int
    @Query("DELETE FROM ab_observations WHERE timestamp < :cutoff") suspend fun deleteOlderThan(cutoff: Long)
}

@Dao
interface ABOutcomeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(outcome: ABOutcomeEntity): Long
    @Query("SELECT * FROM ab_outcomes WHERE evaluationId = :evaluationId ORDER BY horizonSeconds ASC") suspend fun forEvaluation(evaluationId: String): List<ABOutcomeEntity>
    @Query("SELECT COUNT(*) FROM ab_outcomes WHERE returnPct IS NOT NULL") suspend fun completedCount(): Int
    @Query("DELETE FROM ab_outcomes WHERE checkTimestamp < :cutoff") suspend fun deleteOlderThan(cutoff: Long)
}

@Dao
interface ShadowPaperDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(entry: ShadowPaperEntryEntity): Long
    @Query("SELECT * FROM shadow_paper_entries WHERE status = 'OPEN' ORDER BY timestamp ASC") fun open(): Flow<List<ShadowPaperEntryEntity>>
    @Query("UPDATE shadow_paper_entries SET status = :status WHERE entryId = :entryId") suspend fun updateStatus(entryId: String, status: String)
    @Query("DELETE FROM shadow_paper_entries WHERE timestamp < :cutoff") suspend fun deleteOlderThan(cutoff: Long)
}

@Dao
interface DataQualityDao {
    @Insert suspend fun insert(row: DataQualityEntity): Long
    @Query("SELECT * FROM data_quality ORDER BY timestamp DESC LIMIT :limit") fun recent(limit: Int = 100): Flow<List<DataQualityEntity>>
    @Query("DELETE FROM data_quality WHERE timestamp < :cutoff") suspend fun deleteOlderThan(cutoff: Long)
}
