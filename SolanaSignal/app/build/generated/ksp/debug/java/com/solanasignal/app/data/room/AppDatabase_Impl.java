package com.solanasignal.app.data.room;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.solanasignal.app.data.room.dao.MetricsDao;
import com.solanasignal.app.data.room.dao.MetricsDao_Impl;
import com.solanasignal.app.data.room.dao.ScoreDao;
import com.solanasignal.app.data.room.dao.ScoreDao_Impl;
import com.solanasignal.app.data.room.dao.SignalDao;
import com.solanasignal.app.data.room.dao.SignalDao_Impl;
import com.solanasignal.app.data.room.dao.SignalOutcomeDao;
import com.solanasignal.app.data.room.dao.SignalOutcomeDao_Impl;
import com.solanasignal.app.data.room.dao.SystemEventDao;
import com.solanasignal.app.data.room.dao.SystemEventDao_Impl;
import com.solanasignal.app.data.room.dao.TokenDao;
import com.solanasignal.app.data.room.dao.TokenDao_Impl;
import com.solanasignal.app.data.room.dao.TradeDao;
import com.solanasignal.app.data.room.dao.TradeDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile TokenDao _tokenDao;

  private volatile TradeDao _tradeDao;

  private volatile MetricsDao _metricsDao;

  private volatile ScoreDao _scoreDao;

  private volatile SignalDao _signalDao;

  private volatile SignalOutcomeDao _signalOutcomeDao;

  private volatile SystemEventDao _systemEventDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(2) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `tokens` (`mint` TEXT NOT NULL, `name` TEXT, `symbol` TEXT, `creator` TEXT, `uri` TEXT, `poolAddress` TEXT, `createdAtEpochMs` INTEGER, `firstSeenAtEpochMs` INTEGER NOT NULL, `marketCapSol` REAL, `liquiditySol` REAL, `marketCapUsd` REAL, `liquidityUsd` REAL, `lastPriceUsd` REAL, `buyers5m` INTEGER NOT NULL, `sellers5m` INTEGER NOT NULL, `buyVolume5mUsd` REAL NOT NULL, `sellVolume5mUsd` REAL NOT NULL, `lifecycle` TEXT NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`mint`))");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tokens_mint` ON `tokens` (`mint`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `trades` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mint` TEXT NOT NULL, `dedupeKey` TEXT NOT NULL, `side` TEXT NOT NULL, `trader` TEXT, `amountUsd` REAL, `priceUsd` REAL, `timestamp` INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_trades_mint` ON `trades` (`mint`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_trades_timestamp` ON `trades` (`timestamp`)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_trades_dedupeKey` ON `trades` (`dedupeKey`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `metrics` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mint` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `windowSeconds` INTEGER NOT NULL, `totalTrades` INTEGER NOT NULL, `buys` INTEGER NOT NULL, `sells` INTEGER NOT NULL, `uniqueBuyers` INTEGER NOT NULL, `uniqueSellers` INTEGER NOT NULL, `buyVolumeUsd` REAL NOT NULL, `sellVolumeUsd` REAL NOT NULL, `avgBuySizeUsd` REAL NOT NULL, `avgSellSizeUsd` REAL NOT NULL, `largestBuyUsd` REAL NOT NULL, `largestSellUsd` REAL NOT NULL, `latestPriceUsd` REAL, `priceChangePct` REAL, `volumeVelocity` REAL, `buyerVelocity` REAL, `sellerVelocity` REAL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_metrics_mint` ON `metrics` (`mint`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_metrics_timestamp` ON `metrics` (`timestamp`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `scores` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mint` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `score` INTEGER NOT NULL, `buyerPressure` REAL, `volumePressure` REAL, `volumeVelocity` REAL, `priceMomentum` REAL, `liquidity` REAL, `holderDistribution` REAL, `safety` REAL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_scores_mint` ON `scores` (`mint`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_scores_timestamp` ON `scores` (`timestamp`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_scores_score` ON `scores` (`score`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `signals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mint` TEXT NOT NULL, `symbol` TEXT, `timestamp` INTEGER NOT NULL, `signalType` TEXT NOT NULL, `score` INTEGER NOT NULL, `reasonsJson` TEXT NOT NULL, `marketCapUsd` REAL, `liquidityUsd` REAL, `buyers` INTEGER NOT NULL, `sellers` INTEGER NOT NULL, `buyVolumeUsd` REAL NOT NULL, `sellVolumeUsd` REAL NOT NULL, `priceUsd` REAL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_signals_mint` ON `signals` (`mint`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_signals_timestamp` ON `signals` (`timestamp`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_signals_signalType` ON `signals` (`signalType`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_signals_score` ON `signals` (`score`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `signal_outcomes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `signalId` INTEGER NOT NULL, `mint` TEXT NOT NULL, `entryPriceUsd` REAL NOT NULL, `checkTimestamp` INTEGER NOT NULL, `hypotheticalChangePct` REAL NOT NULL, `elapsedSeconds` INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_signal_outcomes_signalId` ON `signal_outcomes` (`signalId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `system_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `category` TEXT NOT NULL, `message` TEXT NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_system_events_timestamp` ON `system_events` (`timestamp`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'd56be8ffeca03cd3c309e9ace7a7e186')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `tokens`");
        db.execSQL("DROP TABLE IF EXISTS `trades`");
        db.execSQL("DROP TABLE IF EXISTS `metrics`");
        db.execSQL("DROP TABLE IF EXISTS `scores`");
        db.execSQL("DROP TABLE IF EXISTS `signals`");
        db.execSQL("DROP TABLE IF EXISTS `signal_outcomes`");
        db.execSQL("DROP TABLE IF EXISTS `system_events`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsTokens = new HashMap<String, TableInfo.Column>(19);
        _columnsTokens.put("mint", new TableInfo.Column("mint", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("name", new TableInfo.Column("name", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("symbol", new TableInfo.Column("symbol", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("creator", new TableInfo.Column("creator", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("uri", new TableInfo.Column("uri", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("poolAddress", new TableInfo.Column("poolAddress", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("createdAtEpochMs", new TableInfo.Column("createdAtEpochMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("firstSeenAtEpochMs", new TableInfo.Column("firstSeenAtEpochMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("marketCapSol", new TableInfo.Column("marketCapSol", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("liquiditySol", new TableInfo.Column("liquiditySol", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("marketCapUsd", new TableInfo.Column("marketCapUsd", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("liquidityUsd", new TableInfo.Column("liquidityUsd", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("lastPriceUsd", new TableInfo.Column("lastPriceUsd", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("buyers5m", new TableInfo.Column("buyers5m", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("sellers5m", new TableInfo.Column("sellers5m", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("buyVolume5mUsd", new TableInfo.Column("buyVolume5mUsd", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("sellVolume5mUsd", new TableInfo.Column("sellVolume5mUsd", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("lifecycle", new TableInfo.Column("lifecycle", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("source", new TableInfo.Column("source", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTokens = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTokens = new HashSet<TableInfo.Index>(1);
        _indicesTokens.add(new TableInfo.Index("index_tokens_mint", true, Arrays.asList("mint"), Arrays.asList("ASC")));
        final TableInfo _infoTokens = new TableInfo("tokens", _columnsTokens, _foreignKeysTokens, _indicesTokens);
        final TableInfo _existingTokens = TableInfo.read(db, "tokens");
        if (!_infoTokens.equals(_existingTokens)) {
          return new RoomOpenHelper.ValidationResult(false, "tokens(com.solanasignal.app.data.room.entities.TokenEntity).\n"
                  + " Expected:\n" + _infoTokens + "\n"
                  + " Found:\n" + _existingTokens);
        }
        final HashMap<String, TableInfo.Column> _columnsTrades = new HashMap<String, TableInfo.Column>(8);
        _columnsTrades.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrades.put("mint", new TableInfo.Column("mint", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrades.put("dedupeKey", new TableInfo.Column("dedupeKey", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrades.put("side", new TableInfo.Column("side", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrades.put("trader", new TableInfo.Column("trader", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrades.put("amountUsd", new TableInfo.Column("amountUsd", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrades.put("priceUsd", new TableInfo.Column("priceUsd", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrades.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTrades = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTrades = new HashSet<TableInfo.Index>(3);
        _indicesTrades.add(new TableInfo.Index("index_trades_mint", false, Arrays.asList("mint"), Arrays.asList("ASC")));
        _indicesTrades.add(new TableInfo.Index("index_trades_timestamp", false, Arrays.asList("timestamp"), Arrays.asList("ASC")));
        _indicesTrades.add(new TableInfo.Index("index_trades_dedupeKey", true, Arrays.asList("dedupeKey"), Arrays.asList("ASC")));
        final TableInfo _infoTrades = new TableInfo("trades", _columnsTrades, _foreignKeysTrades, _indicesTrades);
        final TableInfo _existingTrades = TableInfo.read(db, "trades");
        if (!_infoTrades.equals(_existingTrades)) {
          return new RoomOpenHelper.ValidationResult(false, "trades(com.solanasignal.app.data.room.entities.TradeEntity).\n"
                  + " Expected:\n" + _infoTrades + "\n"
                  + " Found:\n" + _existingTrades);
        }
        final HashMap<String, TableInfo.Column> _columnsMetrics = new HashMap<String, TableInfo.Column>(20);
        _columnsMetrics.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMetrics.put("mint", new TableInfo.Column("mint", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMetrics.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMetrics.put("windowSeconds", new TableInfo.Column("windowSeconds", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMetrics.put("totalTrades", new TableInfo.Column("totalTrades", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMetrics.put("buys", new TableInfo.Column("buys", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMetrics.put("sells", new TableInfo.Column("sells", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMetrics.put("uniqueBuyers", new TableInfo.Column("uniqueBuyers", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMetrics.put("uniqueSellers", new TableInfo.Column("uniqueSellers", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMetrics.put("buyVolumeUsd", new TableInfo.Column("buyVolumeUsd", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMetrics.put("sellVolumeUsd", new TableInfo.Column("sellVolumeUsd", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMetrics.put("avgBuySizeUsd", new TableInfo.Column("avgBuySizeUsd", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMetrics.put("avgSellSizeUsd", new TableInfo.Column("avgSellSizeUsd", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMetrics.put("largestBuyUsd", new TableInfo.Column("largestBuyUsd", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMetrics.put("largestSellUsd", new TableInfo.Column("largestSellUsd", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMetrics.put("latestPriceUsd", new TableInfo.Column("latestPriceUsd", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMetrics.put("priceChangePct", new TableInfo.Column("priceChangePct", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMetrics.put("volumeVelocity", new TableInfo.Column("volumeVelocity", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMetrics.put("buyerVelocity", new TableInfo.Column("buyerVelocity", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMetrics.put("sellerVelocity", new TableInfo.Column("sellerVelocity", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysMetrics = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesMetrics = new HashSet<TableInfo.Index>(2);
        _indicesMetrics.add(new TableInfo.Index("index_metrics_mint", false, Arrays.asList("mint"), Arrays.asList("ASC")));
        _indicesMetrics.add(new TableInfo.Index("index_metrics_timestamp", false, Arrays.asList("timestamp"), Arrays.asList("ASC")));
        final TableInfo _infoMetrics = new TableInfo("metrics", _columnsMetrics, _foreignKeysMetrics, _indicesMetrics);
        final TableInfo _existingMetrics = TableInfo.read(db, "metrics");
        if (!_infoMetrics.equals(_existingMetrics)) {
          return new RoomOpenHelper.ValidationResult(false, "metrics(com.solanasignal.app.data.room.entities.MetricsSnapshotEntity).\n"
                  + " Expected:\n" + _infoMetrics + "\n"
                  + " Found:\n" + _existingMetrics);
        }
        final HashMap<String, TableInfo.Column> _columnsScores = new HashMap<String, TableInfo.Column>(11);
        _columnsScores.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScores.put("mint", new TableInfo.Column("mint", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScores.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScores.put("score", new TableInfo.Column("score", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScores.put("buyerPressure", new TableInfo.Column("buyerPressure", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScores.put("volumePressure", new TableInfo.Column("volumePressure", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScores.put("volumeVelocity", new TableInfo.Column("volumeVelocity", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScores.put("priceMomentum", new TableInfo.Column("priceMomentum", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScores.put("liquidity", new TableInfo.Column("liquidity", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScores.put("holderDistribution", new TableInfo.Column("holderDistribution", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScores.put("safety", new TableInfo.Column("safety", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysScores = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesScores = new HashSet<TableInfo.Index>(3);
        _indicesScores.add(new TableInfo.Index("index_scores_mint", false, Arrays.asList("mint"), Arrays.asList("ASC")));
        _indicesScores.add(new TableInfo.Index("index_scores_timestamp", false, Arrays.asList("timestamp"), Arrays.asList("ASC")));
        _indicesScores.add(new TableInfo.Index("index_scores_score", false, Arrays.asList("score"), Arrays.asList("ASC")));
        final TableInfo _infoScores = new TableInfo("scores", _columnsScores, _foreignKeysScores, _indicesScores);
        final TableInfo _existingScores = TableInfo.read(db, "scores");
        if (!_infoScores.equals(_existingScores)) {
          return new RoomOpenHelper.ValidationResult(false, "scores(com.solanasignal.app.data.room.entities.ScoreEntity).\n"
                  + " Expected:\n" + _infoScores + "\n"
                  + " Found:\n" + _existingScores);
        }
        final HashMap<String, TableInfo.Column> _columnsSignals = new HashMap<String, TableInfo.Column>(14);
        _columnsSignals.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSignals.put("mint", new TableInfo.Column("mint", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSignals.put("symbol", new TableInfo.Column("symbol", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSignals.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSignals.put("signalType", new TableInfo.Column("signalType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSignals.put("score", new TableInfo.Column("score", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSignals.put("reasonsJson", new TableInfo.Column("reasonsJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSignals.put("marketCapUsd", new TableInfo.Column("marketCapUsd", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSignals.put("liquidityUsd", new TableInfo.Column("liquidityUsd", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSignals.put("buyers", new TableInfo.Column("buyers", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSignals.put("sellers", new TableInfo.Column("sellers", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSignals.put("buyVolumeUsd", new TableInfo.Column("buyVolumeUsd", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSignals.put("sellVolumeUsd", new TableInfo.Column("sellVolumeUsd", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSignals.put("priceUsd", new TableInfo.Column("priceUsd", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSignals = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSignals = new HashSet<TableInfo.Index>(4);
        _indicesSignals.add(new TableInfo.Index("index_signals_mint", false, Arrays.asList("mint"), Arrays.asList("ASC")));
        _indicesSignals.add(new TableInfo.Index("index_signals_timestamp", false, Arrays.asList("timestamp"), Arrays.asList("ASC")));
        _indicesSignals.add(new TableInfo.Index("index_signals_signalType", false, Arrays.asList("signalType"), Arrays.asList("ASC")));
        _indicesSignals.add(new TableInfo.Index("index_signals_score", false, Arrays.asList("score"), Arrays.asList("ASC")));
        final TableInfo _infoSignals = new TableInfo("signals", _columnsSignals, _foreignKeysSignals, _indicesSignals);
        final TableInfo _existingSignals = TableInfo.read(db, "signals");
        if (!_infoSignals.equals(_existingSignals)) {
          return new RoomOpenHelper.ValidationResult(false, "signals(com.solanasignal.app.data.room.entities.SignalEntity).\n"
                  + " Expected:\n" + _infoSignals + "\n"
                  + " Found:\n" + _existingSignals);
        }
        final HashMap<String, TableInfo.Column> _columnsSignalOutcomes = new HashMap<String, TableInfo.Column>(7);
        _columnsSignalOutcomes.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSignalOutcomes.put("signalId", new TableInfo.Column("signalId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSignalOutcomes.put("mint", new TableInfo.Column("mint", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSignalOutcomes.put("entryPriceUsd", new TableInfo.Column("entryPriceUsd", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSignalOutcomes.put("checkTimestamp", new TableInfo.Column("checkTimestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSignalOutcomes.put("hypotheticalChangePct", new TableInfo.Column("hypotheticalChangePct", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSignalOutcomes.put("elapsedSeconds", new TableInfo.Column("elapsedSeconds", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSignalOutcomes = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSignalOutcomes = new HashSet<TableInfo.Index>(1);
        _indicesSignalOutcomes.add(new TableInfo.Index("index_signal_outcomes_signalId", false, Arrays.asList("signalId"), Arrays.asList("ASC")));
        final TableInfo _infoSignalOutcomes = new TableInfo("signal_outcomes", _columnsSignalOutcomes, _foreignKeysSignalOutcomes, _indicesSignalOutcomes);
        final TableInfo _existingSignalOutcomes = TableInfo.read(db, "signal_outcomes");
        if (!_infoSignalOutcomes.equals(_existingSignalOutcomes)) {
          return new RoomOpenHelper.ValidationResult(false, "signal_outcomes(com.solanasignal.app.data.room.entities.SignalOutcomeEntity).\n"
                  + " Expected:\n" + _infoSignalOutcomes + "\n"
                  + " Found:\n" + _existingSignalOutcomes);
        }
        final HashMap<String, TableInfo.Column> _columnsSystemEvents = new HashMap<String, TableInfo.Column>(4);
        _columnsSystemEvents.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSystemEvents.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSystemEvents.put("category", new TableInfo.Column("category", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSystemEvents.put("message", new TableInfo.Column("message", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSystemEvents = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSystemEvents = new HashSet<TableInfo.Index>(1);
        _indicesSystemEvents.add(new TableInfo.Index("index_system_events_timestamp", false, Arrays.asList("timestamp"), Arrays.asList("ASC")));
        final TableInfo _infoSystemEvents = new TableInfo("system_events", _columnsSystemEvents, _foreignKeysSystemEvents, _indicesSystemEvents);
        final TableInfo _existingSystemEvents = TableInfo.read(db, "system_events");
        if (!_infoSystemEvents.equals(_existingSystemEvents)) {
          return new RoomOpenHelper.ValidationResult(false, "system_events(com.solanasignal.app.data.room.entities.SystemEventEntity).\n"
                  + " Expected:\n" + _infoSystemEvents + "\n"
                  + " Found:\n" + _existingSystemEvents);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "d56be8ffeca03cd3c309e9ace7a7e186", "028dac26fe7428c54d884e20ba0d8ba0");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "tokens","trades","metrics","scores","signals","signal_outcomes","system_events");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `tokens`");
      _db.execSQL("DELETE FROM `trades`");
      _db.execSQL("DELETE FROM `metrics`");
      _db.execSQL("DELETE FROM `scores`");
      _db.execSQL("DELETE FROM `signals`");
      _db.execSQL("DELETE FROM `signal_outcomes`");
      _db.execSQL("DELETE FROM `system_events`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(TokenDao.class, TokenDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(TradeDao.class, TradeDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(MetricsDao.class, MetricsDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ScoreDao.class, ScoreDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(SignalDao.class, SignalDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(SignalOutcomeDao.class, SignalOutcomeDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(SystemEventDao.class, SystemEventDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public TokenDao tokenDao() {
    if (_tokenDao != null) {
      return _tokenDao;
    } else {
      synchronized(this) {
        if(_tokenDao == null) {
          _tokenDao = new TokenDao_Impl(this);
        }
        return _tokenDao;
      }
    }
  }

  @Override
  public TradeDao tradeDao() {
    if (_tradeDao != null) {
      return _tradeDao;
    } else {
      synchronized(this) {
        if(_tradeDao == null) {
          _tradeDao = new TradeDao_Impl(this);
        }
        return _tradeDao;
      }
    }
  }

  @Override
  public MetricsDao metricsDao() {
    if (_metricsDao != null) {
      return _metricsDao;
    } else {
      synchronized(this) {
        if(_metricsDao == null) {
          _metricsDao = new MetricsDao_Impl(this);
        }
        return _metricsDao;
      }
    }
  }

  @Override
  public ScoreDao scoreDao() {
    if (_scoreDao != null) {
      return _scoreDao;
    } else {
      synchronized(this) {
        if(_scoreDao == null) {
          _scoreDao = new ScoreDao_Impl(this);
        }
        return _scoreDao;
      }
    }
  }

  @Override
  public SignalDao signalDao() {
    if (_signalDao != null) {
      return _signalDao;
    } else {
      synchronized(this) {
        if(_signalDao == null) {
          _signalDao = new SignalDao_Impl(this);
        }
        return _signalDao;
      }
    }
  }

  @Override
  public SignalOutcomeDao signalOutcomeDao() {
    if (_signalOutcomeDao != null) {
      return _signalOutcomeDao;
    } else {
      synchronized(this) {
        if(_signalOutcomeDao == null) {
          _signalOutcomeDao = new SignalOutcomeDao_Impl(this);
        }
        return _signalOutcomeDao;
      }
    }
  }

  @Override
  public SystemEventDao systemEventDao() {
    if (_systemEventDao != null) {
      return _systemEventDao;
    } else {
      synchronized(this) {
        if(_systemEventDao == null) {
          _systemEventDao = new SystemEventDao_Impl(this);
        }
        return _systemEventDao;
      }
    }
  }
}
