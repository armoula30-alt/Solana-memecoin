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
        SystemEventEntity::class,
        DiagnosticSessionEntity::class,
        DiagnosticEventEntity::class
    ],
    version = 16,
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
    abstract fun diagnosticDao(): DiagnosticDao

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

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trades ADD COLUMN source TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE paper_trades ADD COLUMN signalTimestampMs INTEGER")
                db.execSQL("ALTER TABLE paper_trades ADD COLUMN holdingDurationMs INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS diagnostic_sessions (sessionId TEXT NOT NULL, startedAtMs INTEGER NOT NULL, endedAtMs INTEGER, appVersion TEXT NOT NULL, buildVersion INTEGER NOT NULL, initialDataSource TEXT NOT NULL, schemaVersion INTEGER NOT NULL, endReason TEXT, PRIMARY KEY(sessionId))")
                db.execSQL("CREATE TABLE IF NOT EXISTS diagnostic_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId TEXT NOT NULL, eventTimestampMs INTEGER, receivedAtMs INTEGER NOT NULL, eventLatencyMs INTEGER, component TEXT NOT NULL, eventType TEXT NOT NULL, tokenAddress TEXT, severity TEXT NOT NULL, message TEXT NOT NULL, dataSource TEXT, metadataJson TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_diagnostic_events_sessionId_id ON diagnostic_events(sessionId, id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_diagnostic_events_sessionId_receivedAtMs ON diagnostic_events(sessionId, receivedAtMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_diagnostic_events_dataSource_eventType_receivedAtMs ON diagnostic_events(dataSource, eventType, receivedAtMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_diagnostic_events_tokenAddress_receivedAtMs ON diagnostic_events(tokenAddress, receivedAtMs)")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "solana_signal.db"
                ).addMigrations(MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
                    .fallbackToDestructiveMigration() // legacy migrations remain destructive; 12 -> 13 preserves paper history
                 .build().also { instance = it }
            }
    }
}
