package com.solanasignal.app.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 15,
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

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE paper_trades ADD COLUMN marketDataSource TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE paper_trades ADD COLUMN simulated INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE signal_outcomes ADD COLUMN maxGainPct REAL")
                db.execSQL("ALTER TABLE signal_outcomes ADD COLUMN maxDrawdownPct REAL")
                db.execSQL("ALTER TABLE signal_outcomes ADD COLUMN timeToPeakSeconds INTEGER")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_signal_outcomes_signalId_elapsedSeconds ON signal_outcomes(signalId, elapsedSeconds)")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "solana_signal.db"
                ).addMigrations(MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
                    .fallbackToDestructiveMigration() // legacy migrations remain destructive; 12 -> 13 preserves paper history
                 .build().also { instance = it }
            }
    }
}
