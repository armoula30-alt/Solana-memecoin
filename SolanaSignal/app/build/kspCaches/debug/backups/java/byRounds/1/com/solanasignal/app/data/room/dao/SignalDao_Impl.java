package com.solanasignal.app.data.room.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.solanasignal.app.data.room.entities.SignalEntity;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class SignalDao_Impl implements SignalDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<SignalEntity> __insertionAdapterOfSignalEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteOlderThan;

  public SignalDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfSignalEntity = new EntityInsertionAdapter<SignalEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `signals` (`id`,`mint`,`symbol`,`timestamp`,`signalType`,`score`,`reasonsJson`,`marketCapUsd`,`liquidityUsd`,`buyers`,`sellers`,`buyVolumeUsd`,`sellVolumeUsd`,`priceUsd`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SignalEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getMint());
        if (entity.getSymbol() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getSymbol());
        }
        statement.bindLong(4, entity.getTimestamp());
        statement.bindString(5, entity.getSignalType());
        statement.bindLong(6, entity.getScore());
        statement.bindString(7, entity.getReasonsJson());
        if (entity.getMarketCapUsd() == null) {
          statement.bindNull(8);
        } else {
          statement.bindDouble(8, entity.getMarketCapUsd());
        }
        if (entity.getLiquidityUsd() == null) {
          statement.bindNull(9);
        } else {
          statement.bindDouble(9, entity.getLiquidityUsd());
        }
        statement.bindLong(10, entity.getBuyers());
        statement.bindLong(11, entity.getSellers());
        statement.bindDouble(12, entity.getBuyVolumeUsd());
        statement.bindDouble(13, entity.getSellVolumeUsd());
        if (entity.getPriceUsd() == null) {
          statement.bindNull(14);
        } else {
          statement.bindDouble(14, entity.getPriceUsd());
        }
      }
    };
    this.__preparedStmtOfDeleteOlderThan = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM signals WHERE timestamp < ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final SignalEntity signal, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfSignalEntity.insertAndReturnId(signal);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteOlderThan(final long cutoff, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteOlderThan.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, cutoff);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteOlderThan.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<SignalEntity>> observeAll() {
    final String _sql = "SELECT * FROM signals ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"signals"}, new Callable<List<SignalEntity>>() {
      @Override
      @NonNull
      public List<SignalEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMint = CursorUtil.getColumnIndexOrThrow(_cursor, "mint");
          final int _cursorIndexOfSymbol = CursorUtil.getColumnIndexOrThrow(_cursor, "symbol");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfSignalType = CursorUtil.getColumnIndexOrThrow(_cursor, "signalType");
          final int _cursorIndexOfScore = CursorUtil.getColumnIndexOrThrow(_cursor, "score");
          final int _cursorIndexOfReasonsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "reasonsJson");
          final int _cursorIndexOfMarketCapUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "marketCapUsd");
          final int _cursorIndexOfLiquidityUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "liquidityUsd");
          final int _cursorIndexOfBuyers = CursorUtil.getColumnIndexOrThrow(_cursor, "buyers");
          final int _cursorIndexOfSellers = CursorUtil.getColumnIndexOrThrow(_cursor, "sellers");
          final int _cursorIndexOfBuyVolumeUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "buyVolumeUsd");
          final int _cursorIndexOfSellVolumeUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "sellVolumeUsd");
          final int _cursorIndexOfPriceUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "priceUsd");
          final List<SignalEntity> _result = new ArrayList<SignalEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SignalEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpMint;
            _tmpMint = _cursor.getString(_cursorIndexOfMint);
            final String _tmpSymbol;
            if (_cursor.isNull(_cursorIndexOfSymbol)) {
              _tmpSymbol = null;
            } else {
              _tmpSymbol = _cursor.getString(_cursorIndexOfSymbol);
            }
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpSignalType;
            _tmpSignalType = _cursor.getString(_cursorIndexOfSignalType);
            final int _tmpScore;
            _tmpScore = _cursor.getInt(_cursorIndexOfScore);
            final String _tmpReasonsJson;
            _tmpReasonsJson = _cursor.getString(_cursorIndexOfReasonsJson);
            final Double _tmpMarketCapUsd;
            if (_cursor.isNull(_cursorIndexOfMarketCapUsd)) {
              _tmpMarketCapUsd = null;
            } else {
              _tmpMarketCapUsd = _cursor.getDouble(_cursorIndexOfMarketCapUsd);
            }
            final Double _tmpLiquidityUsd;
            if (_cursor.isNull(_cursorIndexOfLiquidityUsd)) {
              _tmpLiquidityUsd = null;
            } else {
              _tmpLiquidityUsd = _cursor.getDouble(_cursorIndexOfLiquidityUsd);
            }
            final int _tmpBuyers;
            _tmpBuyers = _cursor.getInt(_cursorIndexOfBuyers);
            final int _tmpSellers;
            _tmpSellers = _cursor.getInt(_cursorIndexOfSellers);
            final double _tmpBuyVolumeUsd;
            _tmpBuyVolumeUsd = _cursor.getDouble(_cursorIndexOfBuyVolumeUsd);
            final double _tmpSellVolumeUsd;
            _tmpSellVolumeUsd = _cursor.getDouble(_cursorIndexOfSellVolumeUsd);
            final Double _tmpPriceUsd;
            if (_cursor.isNull(_cursorIndexOfPriceUsd)) {
              _tmpPriceUsd = null;
            } else {
              _tmpPriceUsd = _cursor.getDouble(_cursorIndexOfPriceUsd);
            }
            _item = new SignalEntity(_tmpId,_tmpMint,_tmpSymbol,_tmpTimestamp,_tmpSignalType,_tmpScore,_tmpReasonsJson,_tmpMarketCapUsd,_tmpLiquidityUsd,_tmpBuyers,_tmpSellers,_tmpBuyVolumeUsd,_tmpSellVolumeUsd,_tmpPriceUsd);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<SignalEntity>> observeByType(final String type) {
    final String _sql = "SELECT * FROM signals WHERE signalType = ? ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, type);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"signals"}, new Callable<List<SignalEntity>>() {
      @Override
      @NonNull
      public List<SignalEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMint = CursorUtil.getColumnIndexOrThrow(_cursor, "mint");
          final int _cursorIndexOfSymbol = CursorUtil.getColumnIndexOrThrow(_cursor, "symbol");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfSignalType = CursorUtil.getColumnIndexOrThrow(_cursor, "signalType");
          final int _cursorIndexOfScore = CursorUtil.getColumnIndexOrThrow(_cursor, "score");
          final int _cursorIndexOfReasonsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "reasonsJson");
          final int _cursorIndexOfMarketCapUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "marketCapUsd");
          final int _cursorIndexOfLiquidityUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "liquidityUsd");
          final int _cursorIndexOfBuyers = CursorUtil.getColumnIndexOrThrow(_cursor, "buyers");
          final int _cursorIndexOfSellers = CursorUtil.getColumnIndexOrThrow(_cursor, "sellers");
          final int _cursorIndexOfBuyVolumeUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "buyVolumeUsd");
          final int _cursorIndexOfSellVolumeUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "sellVolumeUsd");
          final int _cursorIndexOfPriceUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "priceUsd");
          final List<SignalEntity> _result = new ArrayList<SignalEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SignalEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpMint;
            _tmpMint = _cursor.getString(_cursorIndexOfMint);
            final String _tmpSymbol;
            if (_cursor.isNull(_cursorIndexOfSymbol)) {
              _tmpSymbol = null;
            } else {
              _tmpSymbol = _cursor.getString(_cursorIndexOfSymbol);
            }
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpSignalType;
            _tmpSignalType = _cursor.getString(_cursorIndexOfSignalType);
            final int _tmpScore;
            _tmpScore = _cursor.getInt(_cursorIndexOfScore);
            final String _tmpReasonsJson;
            _tmpReasonsJson = _cursor.getString(_cursorIndexOfReasonsJson);
            final Double _tmpMarketCapUsd;
            if (_cursor.isNull(_cursorIndexOfMarketCapUsd)) {
              _tmpMarketCapUsd = null;
            } else {
              _tmpMarketCapUsd = _cursor.getDouble(_cursorIndexOfMarketCapUsd);
            }
            final Double _tmpLiquidityUsd;
            if (_cursor.isNull(_cursorIndexOfLiquidityUsd)) {
              _tmpLiquidityUsd = null;
            } else {
              _tmpLiquidityUsd = _cursor.getDouble(_cursorIndexOfLiquidityUsd);
            }
            final int _tmpBuyers;
            _tmpBuyers = _cursor.getInt(_cursorIndexOfBuyers);
            final int _tmpSellers;
            _tmpSellers = _cursor.getInt(_cursorIndexOfSellers);
            final double _tmpBuyVolumeUsd;
            _tmpBuyVolumeUsd = _cursor.getDouble(_cursorIndexOfBuyVolumeUsd);
            final double _tmpSellVolumeUsd;
            _tmpSellVolumeUsd = _cursor.getDouble(_cursorIndexOfSellVolumeUsd);
            final Double _tmpPriceUsd;
            if (_cursor.isNull(_cursorIndexOfPriceUsd)) {
              _tmpPriceUsd = null;
            } else {
              _tmpPriceUsd = _cursor.getDouble(_cursorIndexOfPriceUsd);
            }
            _item = new SignalEntity(_tmpId,_tmpMint,_tmpSymbol,_tmpTimestamp,_tmpSignalType,_tmpScore,_tmpReasonsJson,_tmpMarketCapUsd,_tmpLiquidityUsd,_tmpBuyers,_tmpSellers,_tmpBuyVolumeUsd,_tmpSellVolumeUsd,_tmpPriceUsd);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object latestForMint(final String mint,
      final Continuation<? super SignalEntity> $completion) {
    final String _sql = "SELECT * FROM signals WHERE mint = ? ORDER BY timestamp DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, mint);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<SignalEntity>() {
      @Override
      @Nullable
      public SignalEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMint = CursorUtil.getColumnIndexOrThrow(_cursor, "mint");
          final int _cursorIndexOfSymbol = CursorUtil.getColumnIndexOrThrow(_cursor, "symbol");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfSignalType = CursorUtil.getColumnIndexOrThrow(_cursor, "signalType");
          final int _cursorIndexOfScore = CursorUtil.getColumnIndexOrThrow(_cursor, "score");
          final int _cursorIndexOfReasonsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "reasonsJson");
          final int _cursorIndexOfMarketCapUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "marketCapUsd");
          final int _cursorIndexOfLiquidityUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "liquidityUsd");
          final int _cursorIndexOfBuyers = CursorUtil.getColumnIndexOrThrow(_cursor, "buyers");
          final int _cursorIndexOfSellers = CursorUtil.getColumnIndexOrThrow(_cursor, "sellers");
          final int _cursorIndexOfBuyVolumeUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "buyVolumeUsd");
          final int _cursorIndexOfSellVolumeUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "sellVolumeUsd");
          final int _cursorIndexOfPriceUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "priceUsd");
          final SignalEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpMint;
            _tmpMint = _cursor.getString(_cursorIndexOfMint);
            final String _tmpSymbol;
            if (_cursor.isNull(_cursorIndexOfSymbol)) {
              _tmpSymbol = null;
            } else {
              _tmpSymbol = _cursor.getString(_cursorIndexOfSymbol);
            }
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpSignalType;
            _tmpSignalType = _cursor.getString(_cursorIndexOfSignalType);
            final int _tmpScore;
            _tmpScore = _cursor.getInt(_cursorIndexOfScore);
            final String _tmpReasonsJson;
            _tmpReasonsJson = _cursor.getString(_cursorIndexOfReasonsJson);
            final Double _tmpMarketCapUsd;
            if (_cursor.isNull(_cursorIndexOfMarketCapUsd)) {
              _tmpMarketCapUsd = null;
            } else {
              _tmpMarketCapUsd = _cursor.getDouble(_cursorIndexOfMarketCapUsd);
            }
            final Double _tmpLiquidityUsd;
            if (_cursor.isNull(_cursorIndexOfLiquidityUsd)) {
              _tmpLiquidityUsd = null;
            } else {
              _tmpLiquidityUsd = _cursor.getDouble(_cursorIndexOfLiquidityUsd);
            }
            final int _tmpBuyers;
            _tmpBuyers = _cursor.getInt(_cursorIndexOfBuyers);
            final int _tmpSellers;
            _tmpSellers = _cursor.getInt(_cursorIndexOfSellers);
            final double _tmpBuyVolumeUsd;
            _tmpBuyVolumeUsd = _cursor.getDouble(_cursorIndexOfBuyVolumeUsd);
            final double _tmpSellVolumeUsd;
            _tmpSellVolumeUsd = _cursor.getDouble(_cursorIndexOfSellVolumeUsd);
            final Double _tmpPriceUsd;
            if (_cursor.isNull(_cursorIndexOfPriceUsd)) {
              _tmpPriceUsd = null;
            } else {
              _tmpPriceUsd = _cursor.getDouble(_cursorIndexOfPriceUsd);
            }
            _result = new SignalEntity(_tmpId,_tmpMint,_tmpSymbol,_tmpTimestamp,_tmpSignalType,_tmpScore,_tmpReasonsJson,_tmpMarketCapUsd,_tmpLiquidityUsd,_tmpBuyers,_tmpSellers,_tmpBuyVolumeUsd,_tmpSellVolumeUsd,_tmpPriceUsd);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object countSince(final long sinceEpochMs,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM signals WHERE timestamp >= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, sinceEpochMs);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object countSinceByType(final long sinceEpochMs, final String type,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM signals WHERE timestamp >= ? AND signalType = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, sinceEpochMs);
    _argIndex = 2;
    _statement.bindString(_argIndex, type);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object averageScoreSince(final long sinceEpochMs,
      final Continuation<? super Double> $completion) {
    final String _sql = "SELECT AVG(score) FROM signals WHERE timestamp >= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, sinceEpochMs);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Double>() {
      @Override
      @Nullable
      public Double call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Double _result;
          if (_cursor.moveToFirst()) {
            final Double _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getDouble(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object maxScoreSince(final long sinceEpochMs,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT MAX(score) FROM signals WHERE timestamp >= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, sinceEpochMs);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @Nullable
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final Integer _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
