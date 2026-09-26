package com.solanasignal.app.data.room.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.solanasignal.app.data.room.entities.TradeEntity;
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

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class TradeDao_Impl implements TradeDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<TradeEntity> __insertionAdapterOfTradeEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteOlderThan;

  public TradeDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTradeEntity = new EntityInsertionAdapter<TradeEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR IGNORE INTO `trades` (`id`,`mint`,`dedupeKey`,`side`,`trader`,`amountUsd`,`priceUsd`,`timestamp`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TradeEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getMint());
        statement.bindString(3, entity.getDedupeKey());
        statement.bindString(4, entity.getSide());
        if (entity.getTrader() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getTrader());
        }
        if (entity.getAmountUsd() == null) {
          statement.bindNull(6);
        } else {
          statement.bindDouble(6, entity.getAmountUsd());
        }
        if (entity.getPriceUsd() == null) {
          statement.bindNull(7);
        } else {
          statement.bindDouble(7, entity.getPriceUsd());
        }
        statement.bindLong(8, entity.getTimestamp());
      }
    };
    this.__preparedStmtOfDeleteOlderThan = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM trades WHERE timestamp < ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final TradeEntity trade, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfTradeEntity.insertAndReturnId(trade);
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
  public Object existsByDedupeKey(final String dedupeKey,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM trades WHERE dedupeKey = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, dedupeKey);
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
  public Object getSince(final String mint, final long sinceEpochMs,
      final Continuation<? super List<TradeEntity>> $completion) {
    final String _sql = "SELECT * FROM trades WHERE mint = ? AND timestamp >= ? ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, mint);
    _argIndex = 2;
    _statement.bindLong(_argIndex, sinceEpochMs);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TradeEntity>>() {
      @Override
      @NonNull
      public List<TradeEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMint = CursorUtil.getColumnIndexOrThrow(_cursor, "mint");
          final int _cursorIndexOfDedupeKey = CursorUtil.getColumnIndexOrThrow(_cursor, "dedupeKey");
          final int _cursorIndexOfSide = CursorUtil.getColumnIndexOrThrow(_cursor, "side");
          final int _cursorIndexOfTrader = CursorUtil.getColumnIndexOrThrow(_cursor, "trader");
          final int _cursorIndexOfAmountUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "amountUsd");
          final int _cursorIndexOfPriceUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "priceUsd");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final List<TradeEntity> _result = new ArrayList<TradeEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TradeEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpMint;
            _tmpMint = _cursor.getString(_cursorIndexOfMint);
            final String _tmpDedupeKey;
            _tmpDedupeKey = _cursor.getString(_cursorIndexOfDedupeKey);
            final String _tmpSide;
            _tmpSide = _cursor.getString(_cursorIndexOfSide);
            final String _tmpTrader;
            if (_cursor.isNull(_cursorIndexOfTrader)) {
              _tmpTrader = null;
            } else {
              _tmpTrader = _cursor.getString(_cursorIndexOfTrader);
            }
            final Double _tmpAmountUsd;
            if (_cursor.isNull(_cursorIndexOfAmountUsd)) {
              _tmpAmountUsd = null;
            } else {
              _tmpAmountUsd = _cursor.getDouble(_cursorIndexOfAmountUsd);
            }
            final Double _tmpPriceUsd;
            if (_cursor.isNull(_cursorIndexOfPriceUsd)) {
              _tmpPriceUsd = null;
            } else {
              _tmpPriceUsd = _cursor.getDouble(_cursorIndexOfPriceUsd);
            }
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            _item = new TradeEntity(_tmpId,_tmpMint,_tmpDedupeKey,_tmpSide,_tmpTrader,_tmpAmountUsd,_tmpPriceUsd,_tmpTimestamp);
            _result.add(_item);
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
