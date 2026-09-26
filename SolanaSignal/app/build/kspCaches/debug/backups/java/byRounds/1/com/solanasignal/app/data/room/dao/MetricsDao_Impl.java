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
import com.solanasignal.app.data.room.entities.MetricsSnapshotEntity;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
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
public final class MetricsDao_Impl implements MetricsDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<MetricsSnapshotEntity> __insertionAdapterOfMetricsSnapshotEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteOlderThan;

  public MetricsDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfMetricsSnapshotEntity = new EntityInsertionAdapter<MetricsSnapshotEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `metrics` (`id`,`mint`,`timestamp`,`windowSeconds`,`totalTrades`,`buys`,`sells`,`uniqueBuyers`,`uniqueSellers`,`buyVolumeUsd`,`sellVolumeUsd`,`avgBuySizeUsd`,`avgSellSizeUsd`,`largestBuyUsd`,`largestSellUsd`,`latestPriceUsd`,`priceChangePct`,`volumeVelocity`,`buyerVelocity`,`sellerVelocity`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final MetricsSnapshotEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getMint());
        statement.bindLong(3, entity.getTimestamp());
        statement.bindLong(4, entity.getWindowSeconds());
        statement.bindLong(5, entity.getTotalTrades());
        statement.bindLong(6, entity.getBuys());
        statement.bindLong(7, entity.getSells());
        statement.bindLong(8, entity.getUniqueBuyers());
        statement.bindLong(9, entity.getUniqueSellers());
        statement.bindDouble(10, entity.getBuyVolumeUsd());
        statement.bindDouble(11, entity.getSellVolumeUsd());
        statement.bindDouble(12, entity.getAvgBuySizeUsd());
        statement.bindDouble(13, entity.getAvgSellSizeUsd());
        statement.bindDouble(14, entity.getLargestBuyUsd());
        statement.bindDouble(15, entity.getLargestSellUsd());
        if (entity.getLatestPriceUsd() == null) {
          statement.bindNull(16);
        } else {
          statement.bindDouble(16, entity.getLatestPriceUsd());
        }
        if (entity.getPriceChangePct() == null) {
          statement.bindNull(17);
        } else {
          statement.bindDouble(17, entity.getPriceChangePct());
        }
        if (entity.getVolumeVelocity() == null) {
          statement.bindNull(18);
        } else {
          statement.bindDouble(18, entity.getVolumeVelocity());
        }
        if (entity.getBuyerVelocity() == null) {
          statement.bindNull(19);
        } else {
          statement.bindDouble(19, entity.getBuyerVelocity());
        }
        if (entity.getSellerVelocity() == null) {
          statement.bindNull(20);
        } else {
          statement.bindDouble(20, entity.getSellerVelocity());
        }
      }
    };
    this.__preparedStmtOfDeleteOlderThan = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM metrics WHERE timestamp < ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final MetricsSnapshotEntity snapshot,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfMetricsSnapshotEntity.insertAndReturnId(snapshot);
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
  public Object recentForMint(final String mint, final int limit,
      final Continuation<? super List<MetricsSnapshotEntity>> $completion) {
    final String _sql = "SELECT * FROM metrics WHERE mint = ? ORDER BY timestamp DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, mint);
    _argIndex = 2;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<MetricsSnapshotEntity>>() {
      @Override
      @NonNull
      public List<MetricsSnapshotEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMint = CursorUtil.getColumnIndexOrThrow(_cursor, "mint");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfWindowSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "windowSeconds");
          final int _cursorIndexOfTotalTrades = CursorUtil.getColumnIndexOrThrow(_cursor, "totalTrades");
          final int _cursorIndexOfBuys = CursorUtil.getColumnIndexOrThrow(_cursor, "buys");
          final int _cursorIndexOfSells = CursorUtil.getColumnIndexOrThrow(_cursor, "sells");
          final int _cursorIndexOfUniqueBuyers = CursorUtil.getColumnIndexOrThrow(_cursor, "uniqueBuyers");
          final int _cursorIndexOfUniqueSellers = CursorUtil.getColumnIndexOrThrow(_cursor, "uniqueSellers");
          final int _cursorIndexOfBuyVolumeUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "buyVolumeUsd");
          final int _cursorIndexOfSellVolumeUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "sellVolumeUsd");
          final int _cursorIndexOfAvgBuySizeUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "avgBuySizeUsd");
          final int _cursorIndexOfAvgSellSizeUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "avgSellSizeUsd");
          final int _cursorIndexOfLargestBuyUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "largestBuyUsd");
          final int _cursorIndexOfLargestSellUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "largestSellUsd");
          final int _cursorIndexOfLatestPriceUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "latestPriceUsd");
          final int _cursorIndexOfPriceChangePct = CursorUtil.getColumnIndexOrThrow(_cursor, "priceChangePct");
          final int _cursorIndexOfVolumeVelocity = CursorUtil.getColumnIndexOrThrow(_cursor, "volumeVelocity");
          final int _cursorIndexOfBuyerVelocity = CursorUtil.getColumnIndexOrThrow(_cursor, "buyerVelocity");
          final int _cursorIndexOfSellerVelocity = CursorUtil.getColumnIndexOrThrow(_cursor, "sellerVelocity");
          final List<MetricsSnapshotEntity> _result = new ArrayList<MetricsSnapshotEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MetricsSnapshotEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpMint;
            _tmpMint = _cursor.getString(_cursorIndexOfMint);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final int _tmpWindowSeconds;
            _tmpWindowSeconds = _cursor.getInt(_cursorIndexOfWindowSeconds);
            final int _tmpTotalTrades;
            _tmpTotalTrades = _cursor.getInt(_cursorIndexOfTotalTrades);
            final int _tmpBuys;
            _tmpBuys = _cursor.getInt(_cursorIndexOfBuys);
            final int _tmpSells;
            _tmpSells = _cursor.getInt(_cursorIndexOfSells);
            final int _tmpUniqueBuyers;
            _tmpUniqueBuyers = _cursor.getInt(_cursorIndexOfUniqueBuyers);
            final int _tmpUniqueSellers;
            _tmpUniqueSellers = _cursor.getInt(_cursorIndexOfUniqueSellers);
            final double _tmpBuyVolumeUsd;
            _tmpBuyVolumeUsd = _cursor.getDouble(_cursorIndexOfBuyVolumeUsd);
            final double _tmpSellVolumeUsd;
            _tmpSellVolumeUsd = _cursor.getDouble(_cursorIndexOfSellVolumeUsd);
            final double _tmpAvgBuySizeUsd;
            _tmpAvgBuySizeUsd = _cursor.getDouble(_cursorIndexOfAvgBuySizeUsd);
            final double _tmpAvgSellSizeUsd;
            _tmpAvgSellSizeUsd = _cursor.getDouble(_cursorIndexOfAvgSellSizeUsd);
            final double _tmpLargestBuyUsd;
            _tmpLargestBuyUsd = _cursor.getDouble(_cursorIndexOfLargestBuyUsd);
            final double _tmpLargestSellUsd;
            _tmpLargestSellUsd = _cursor.getDouble(_cursorIndexOfLargestSellUsd);
            final Double _tmpLatestPriceUsd;
            if (_cursor.isNull(_cursorIndexOfLatestPriceUsd)) {
              _tmpLatestPriceUsd = null;
            } else {
              _tmpLatestPriceUsd = _cursor.getDouble(_cursorIndexOfLatestPriceUsd);
            }
            final Double _tmpPriceChangePct;
            if (_cursor.isNull(_cursorIndexOfPriceChangePct)) {
              _tmpPriceChangePct = null;
            } else {
              _tmpPriceChangePct = _cursor.getDouble(_cursorIndexOfPriceChangePct);
            }
            final Double _tmpVolumeVelocity;
            if (_cursor.isNull(_cursorIndexOfVolumeVelocity)) {
              _tmpVolumeVelocity = null;
            } else {
              _tmpVolumeVelocity = _cursor.getDouble(_cursorIndexOfVolumeVelocity);
            }
            final Double _tmpBuyerVelocity;
            if (_cursor.isNull(_cursorIndexOfBuyerVelocity)) {
              _tmpBuyerVelocity = null;
            } else {
              _tmpBuyerVelocity = _cursor.getDouble(_cursorIndexOfBuyerVelocity);
            }
            final Double _tmpSellerVelocity;
            if (_cursor.isNull(_cursorIndexOfSellerVelocity)) {
              _tmpSellerVelocity = null;
            } else {
              _tmpSellerVelocity = _cursor.getDouble(_cursorIndexOfSellerVelocity);
            }
            _item = new MetricsSnapshotEntity(_tmpId,_tmpMint,_tmpTimestamp,_tmpWindowSeconds,_tmpTotalTrades,_tmpBuys,_tmpSells,_tmpUniqueBuyers,_tmpUniqueSellers,_tmpBuyVolumeUsd,_tmpSellVolumeUsd,_tmpAvgBuySizeUsd,_tmpAvgSellSizeUsd,_tmpLargestBuyUsd,_tmpLargestSellUsd,_tmpLatestPriceUsd,_tmpPriceChangePct,_tmpVolumeVelocity,_tmpBuyerVelocity,_tmpSellerVelocity);
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
