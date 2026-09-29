package com.solanasignal.app.domain.paper

import androidx.room.withTransaction
import com.solanasignal.app.data.room.AppDatabase
import com.solanasignal.app.data.room.entities.PaperPortfolioEntity
import com.solanasignal.app.data.room.entities.PaperPositionEntity
import com.solanasignal.app.data.room.entities.PaperTradeEntity
import kotlin.math.max
import kotlin.math.min

sealed class PaperTradeResult {
    data class Success(val tradeId: Long, val fillPriceUsd: Double, val quantity: Double, val feeUsd: Double) : PaperTradeResult()
    data class Rejected(val reason: String) : PaperTradeResult()
}

/** Non-custodial simulator. This class has no wallet, RPC signing, or transaction APIs. */
class PaperTradingEngine(
    private val db: AppDatabase,
    private val feeRate: Double = 0.003,
    private val maxSlippageRate: Double = 0.15
) {
    suspend fun buy(mint: String, symbol: String?, amountUsd: Double, marketPriceUsd: Double, liquidityUsd: Double?, marketCapUsd: Double?, reason: String = "Manual simulated entry", now: Long = System.currentTimeMillis()): PaperTradeResult {
        if (amountUsd <= 0.0 || marketPriceUsd <= 0.0) return PaperTradeResult.Rejected("Amount and market price must be positive")
        return db.withTransaction {
            val portfolio = db.paperTradingDao().portfolio() ?: PaperPortfolioEntity(updatedAt = now).also { db.paperTradingDao().savePortfolio(it) }
            if (portfolio.cashUsd < amountUsd) return@withTransaction PaperTradeResult.Rejected("Insufficient virtual cash")
            val slippageRate = slippage(amountUsd, liquidityUsd)
            val fill = marketPriceUsd * (1.0 + slippageRate)
            val fee = amountUsd * feeRate
            val quantity = (amountUsd - fee) / fill
            val old = db.paperTradingDao().position(mint)
            val totalQuantity = (old?.quantity ?: 0.0) + quantity
            val invested = (old?.investedUsd ?: 0.0) + amountUsd
            val average = invested / totalQuantity
            db.paperTradingDao().savePosition(PaperPositionEntity(mint, symbol, totalQuantity, average, old?.entryMarketCapUsd ?: marketCapUsd, fill, marketCapUsd, invested, old?.realizedPnlUsd ?: 0.0, old?.openedAt ?: now, now))
            db.paperTradingDao().savePortfolio(portfolio.copy(cashUsd = portfolio.cashUsd - amountUsd, updatedAt = now))
            val id = db.paperTradingDao().insertTrade(PaperTradeEntity(mint = mint, symbol = symbol, side = "PAPER_BUY", timestamp = now, requestedUsd = amountUsd, quantity = quantity, marketPriceUsd = marketPriceUsd, fillPriceUsd = fill, feeUsd = fee, slippageUsd = (fill - marketPriceUsd) * quantity, reason = reason))
            PaperTradeResult.Success(id, fill, quantity, fee)
        }
    }

    suspend fun sell(mint: String, symbol: String?, quantity: Double, marketPriceUsd: Double, liquidityUsd: Double?, marketCapUsd: Double?, reason: String = "Manual simulated exit", now: Long = System.currentTimeMillis()): PaperTradeResult {
        if (quantity <= 0.0 || marketPriceUsd <= 0.0) return PaperTradeResult.Rejected("Quantity and market price must be positive")
        return db.withTransaction {
            val position = db.paperTradingDao().position(mint) ?: return@withTransaction PaperTradeResult.Rejected("No virtual position")
            if (quantity > position.quantity) return@withTransaction PaperTradeResult.Rejected("Quantity exceeds virtual position")
            val portfolio = db.paperTradingDao().portfolio() ?: PaperPortfolioEntity(updatedAt = now)
            val proceedsGross = quantity * marketPriceUsd
            val slippageRate = slippage(proceedsGross, liquidityUsd)
            val fill = marketPriceUsd * (1.0 - slippageRate)
            val proceeds = quantity * fill
            val fee = proceeds * feeRate
            val costBasis = position.averageEntryPriceUsd * quantity
            val pnl = proceeds - fee - costBasis
            val remaining = position.quantity - quantity
            if (remaining <= 1e-12) db.paperTradingDao().deletePosition(mint)
            else db.paperTradingDao().savePosition(position.copy(quantity = remaining, investedUsd = position.averageEntryPriceUsd * remaining, currentPriceUsd = fill, currentMarketCapUsd = marketCapUsd, realizedPnlUsd = position.realizedPnlUsd + pnl, updatedAt = now))
            db.paperTradingDao().savePortfolio(portfolio.copy(cashUsd = portfolio.cashUsd + proceeds - fee, realizedPnlUsd = portfolio.realizedPnlUsd + pnl, updatedAt = now))
            val id = db.paperTradingDao().insertTrade(PaperTradeEntity(mint = mint, symbol = symbol, side = "PAPER_SELL", timestamp = now, requestedUsd = proceedsGross, quantity = quantity, marketPriceUsd = marketPriceUsd, fillPriceUsd = fill, feeUsd = fee, slippageUsd = (marketPriceUsd - fill) * quantity, realizedPnlUsd = pnl, reason = reason))
            PaperTradeResult.Success(id, fill, quantity, fee)
        }
    }

    suspend fun reset(now: Long = System.currentTimeMillis()) = db.withTransaction {
        db.paperTradingDao().clearPositions()
        db.paperTradingDao().clearTrades()
        db.paperTradingDao().savePortfolio(PaperPortfolioEntity(updatedAt = now))
    }

    private fun slippage(notionalUsd: Double, liquidityUsd: Double?): Double {
        val liquidity = liquidityUsd?.takeIf { it > 0.0 } ?: return maxSlippageRate
        return min(maxSlippageRate, (notionalUsd / liquidity) * 0.5)
    }
}
