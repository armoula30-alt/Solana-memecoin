package com.solanasignal.app.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.solanasignal.app.data.room.dao.*
import com.solanasignal.app.data.room.entities.*

@Database(
    entities = [
        TokenEntity::class,
        TradeEntity::class,
        MetricsSnapshotEntity::class,
        ScoreEntity::class,
        SignalEntity::class,
        SignalOutcomeEntity::class,
        SignalTransitionEntity::class,
        TokenObservationEntity::class,
        TokenFeatureSnapshotEntity::class,
        PaperPortfolioEntity::class,
        PaperPositionEntity::class,
        PaperTradeEntity::class,
        PaperWatchlistEntity::class,
        SystemEventEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tokenDao(): TokenDao
    abstract fun tradeDao(): TradeDao
    abstract fun metricsDao(): MetricsDao
    abstract fun scoreDao(): ScoreDao
    abstract fun signalDao(): SignalDao
    abstract fun signalOutcomeDao(): SignalOutcomeDao
    abstract fun signalTransitionDao(): SignalTransitionDao
    abstract fun featureSnapshotDao(): FeatureSnapshotDao
    abstract fun paperTradingDao(): PaperTradingDao
    abstract fun systemEventDao(): SystemEventDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "solana_signal.db"
                ).fallbackToDestructiveMigration() // no users/data to preserve yet; add real migrations before release
                 .build().also { instance = it }
            }
    }
}
