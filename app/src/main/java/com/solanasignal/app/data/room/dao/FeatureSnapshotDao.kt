package com.solanasignal.app.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.solanasignal.app.data.room.entities.TokenFeatureSnapshotEntity
import com.solanasignal.app.data.room.entities.TokenObservationEntity

@Dao
interface FeatureSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObservation(value: TokenObservationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(value: TokenFeatureSnapshotEntity): Long

    @Query("SELECT * FROM token_observations WHERE mint = :mint AND timestamp BETWEEN :from AND :to ORDER BY timestamp ASC")
    suspend fun observations(mint: String, from: Long, to: Long): List<TokenObservationEntity>

    @Query("SELECT * FROM token_feature_snapshots WHERE mint = :mint AND timestamp BETWEEN :from AND :to ORDER BY timestamp ASC")
    suspend fun snapshots(mint: String, from: Long, to: Long): List<TokenFeatureSnapshotEntity>

    @Query("DELETE FROM token_observations WHERE timestamp < :before")
    suspend fun deleteObservationsBefore(before: Long): Int

    @Query("DELETE FROM token_feature_snapshots WHERE timestamp < :before")
    suspend fun deleteSnapshotsBefore(before: Long): Int
}
