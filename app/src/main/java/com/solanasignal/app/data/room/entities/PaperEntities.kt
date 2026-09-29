package com.solanasignal.app.data.room.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "paper_portfolio")
data class PaperPortfolioEntity(
    @PrimaryKey val id: Int = 1,
    val initialCashUsd: Double = 1_000.0,
    val cashUsd: Double = 1_000.0,
    val realizedPnlUsd: Double = 0.0,
    val updatedAt: Long = 0L
)

@Entity(tableName = "paper_positions")
data class PaperPositionEntity(
    @PrimaryKey val mint: String,
    val symbol: String?,
    val quantity: Double,
    val averageEntryPriceUsd: Double,
    val entryMarketCapUsd: Double?,
    val currentPriceUsd: Double,
    val currentMarketCapUsd: Double?,
    val investedUsd: Double,
    val realizedPnlUsd: Double = 0.0,
    val openedAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "paper_trades", indices = [Index(value = ["mint"]), Index(value = ["timestamp"])])
data class PaperTradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mint: String,
    val symbol: String?,
    val side: String,
    val timestamp: Long,
    val requestedUsd: Double,
    val quantity: Double,
    val marketPriceUsd: Double,
    val fillPriceUsd: Double,
    val feeUsd: Double,
    val slippageUsd: Double,
    val realizedPnlUsd: Double? = null,
    val reason: String? = null
)
