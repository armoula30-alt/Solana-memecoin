package com.solanasignal.app.data.chart

import com.solanasignal.app.data.room.AppDatabase
import com.solanasignal.app.data.room.entities.TradeEntity


enum class ChartInterval(val seconds: Int, val label: String) {
    TEN_SECONDS(10, "10s"), THIRTY_SECONDS(30, "30s"), ONE_MINUTE(60, "1m"),
    THREE_MINUTES(180, "3m"), FIVE_MINUTES(300, "5m"), FIFTEEN_MINUTES(900, "15m"),
    THIRTY_MINUTES(1800, "30m"), ONE_HOUR(3600, "1h")
}

data class ChartCandle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val buyVolume: Double,
    val sellVolume: Double,
    val buyCount: Int,
    val sellCount: Int,
    val uniqueBuyers: Int,
    val uniqueSellers: Int
)

/** Builds OHLCV only from the app's normalized TradeEntity records. */
class ChartDataRepository(private val db: AppDatabase) {
    suspend fun loadHistory(
        mint: String,
        interval: ChartInterval = ChartInterval.ONE_MINUTE,
        lookbackMs: Long = 6 * 60 * 60 * 1000L
    ): List<ChartCandle> {
        val trades = db.tradeDao().getSince(mint, System.currentTimeMillis() - lookbackMs)
        return aggregate(trades, interval)
    }

    fun aggregate(trades: List<TradeEntity>, interval: ChartInterval): List<ChartCandle> {
        val intervalMs = interval.seconds * 1000L
        val buckets = linkedMapOf<Long, MutableCandle>()
        trades.asSequence()
            .filter { it.priceUsd != null && it.priceUsd > 0.0 }
            .sortedBy { it.timestamp }
            .forEach { trade ->
                val price = trade.priceUsd ?: return@forEach
                val bucket = (trade.timestamp / intervalMs) * intervalMs
                val candle = buckets.getOrPut(bucket) { MutableCandle(price) }
                candle.add(trade, price)
            }
        return buckets.map { (timestamp, candle) -> candle.freeze(timestamp) }
    }

    private class MutableCandle(firstPrice: Double) {
        var open = firstPrice
        var high = firstPrice
        var low = firstPrice
        var close = firstPrice
        var volume = 0.0
        var buyVolume = 0.0
        var sellVolume = 0.0
        var buyCount = 0
        var sellCount = 0
        val buyers = linkedSetOf<String>()
        val sellers = linkedSetOf<String>()

        fun add(trade: TradeEntity, price: Double) {
            high = maxOf(high, price)
            low = minOf(low, price)
            close = price
            volume += trade.amountUsd ?: 0.0
            if (trade.side == "BUY") {
                buyVolume += trade.amountUsd ?: 0.0
                buyCount++
                trade.trader?.let(buyers::add)
            } else if (trade.side == "SELL") {
                sellVolume += trade.amountUsd ?: 0.0
                sellCount++
                trade.trader?.let(sellers::add)
            }
        }

        fun freeze(timestamp: Long) = ChartCandle(
            timestamp, open, high, low, close, volume, buyVolume, sellVolume,
            buyCount, sellCount, buyers.size, sellers.size
        )
    }
}
