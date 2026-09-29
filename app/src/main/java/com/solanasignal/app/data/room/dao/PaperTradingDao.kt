package com.solanasignal.app.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.solanasignal.app.data.room.entities.PaperPortfolioEntity
import com.solanasignal.app.data.room.entities.PaperPositionEntity
import com.solanasignal.app.data.room.entities.PaperTradeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaperTradingDao {
    @Query("SELECT * FROM paper_portfolio WHERE id = 1")
    suspend fun portfolio(): PaperPortfolioEntity?

    @Query("SELECT * FROM paper_portfolio WHERE id = 1")
    fun observePortfolio(): Flow<PaperPortfolioEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePortfolio(value: PaperPortfolioEntity)

    @Query("SELECT * FROM paper_positions ORDER BY updatedAt DESC")
    fun observePositions(): Flow<List<PaperPositionEntity>>

    @Query("SELECT * FROM paper_positions WHERE mint = :mint")
    suspend fun position(mint: String): PaperPositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePosition(value: PaperPositionEntity)

    @Query("DELETE FROM paper_positions WHERE mint = :mint")
    suspend fun deletePosition(mint: String)

    @Query("SELECT * FROM paper_trades ORDER BY timestamp DESC")
    fun observeTrades(): Flow<List<PaperTradeEntity>>

    @Insert
    suspend fun insertTrade(value: PaperTradeEntity): Long

    @Query("DELETE FROM paper_positions")
    suspend fun clearPositions()

    @Query("DELETE FROM paper_trades")
    suspend fun clearTrades()
}
