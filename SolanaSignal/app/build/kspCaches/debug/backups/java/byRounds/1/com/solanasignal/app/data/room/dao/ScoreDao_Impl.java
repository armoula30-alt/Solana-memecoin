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
import com.solanasignal.app.data.room.entities.ScoreEntity;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ScoreDao_Impl implements ScoreDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ScoreEntity> __insertionAdapterOfScoreEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteOlderThan;

  public ScoreDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfScoreEntity = new EntityInsertionAdapter<ScoreEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `scores` (`id`,`mint`,`timestamp`,`score`,`buyerPressure`,`volumePressure`,`volumeVelocity`,`priceMomentum`,`liquidity`,`holderDistribution`,`safety`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ScoreEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getMint());
        statement.bindLong(3, entity.getTimestamp());
        statement.bindLong(4, entity.getScore());
        if (entity.getBuyerPressure() == null) {
          statement.bindNull(5);
        } else {
          statement.bindDouble(5, entity.getBuyerPressure());
        }
        if (entity.getVolumePressure() == null) {
          statement.bindNull(6);
        } else {
          statement.bindDouble(6, entity.getVolumePressure());
        }
        if (entity.getVolumeVelocity() == null) {
          statement.bindNull(7);
        } else {
          statement.bindDouble(7, entity.getVolumeVelocity());
        }
        if (entity.getPriceMomentum() == null) {
          statement.bindNull(8);
        } else {
          statement.bindDouble(8, entity.getPriceMomentum());
        }
        if (entity.getLiquidity() == null) {
          statement.bindNull(9);
        } else {
          statement.bindDouble(9, entity.getLiquidity());
        }
        if (entity.getHolderDistribution() == null) {
          statement.bindNull(10);
        } else {
          statement.bindDouble(10, entity.getHolderDistribution());
        }
        if (entity.getSafety() == null) {
          statement.bindNull(11);
        } else {
          statement.bindDouble(11, entity.getSafety());
        }
      }
    };
    this.__preparedStmtOfDeleteOlderThan = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM scores WHERE timestamp < ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final ScoreEntity score, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfScoreEntity.insertAndReturnId(score);
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
  public Object latestForMint(final String mint,
      final Continuation<? super ScoreEntity> $completion) {
    final String _sql = "SELECT * FROM scores WHERE mint = ? ORDER BY timestamp DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, mint);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ScoreEntity>() {
      @Override
      @Nullable
      public ScoreEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMint = CursorUtil.getColumnIndexOrThrow(_cursor, "mint");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfScore = CursorUtil.getColumnIndexOrThrow(_cursor, "score");
          final int _cursorIndexOfBuyerPressure = CursorUtil.getColumnIndexOrThrow(_cursor, "buyerPressure");
          final int _cursorIndexOfVolumePressure = CursorUtil.getColumnIndexOrThrow(_cursor, "volumePressure");
          final int _cursorIndexOfVolumeVelocity = CursorUtil.getColumnIndexOrThrow(_cursor, "volumeVelocity");
          final int _cursorIndexOfPriceMomentum = CursorUtil.getColumnIndexOrThrow(_cursor, "priceMomentum");
          final int _cursorIndexOfLiquidity = CursorUtil.getColumnIndexOrThrow(_cursor, "liquidity");
          final int _cursorIndexOfHolderDistribution = CursorUtil.getColumnIndexOrThrow(_cursor, "holderDistribution");
          final int _cursorIndexOfSafety = CursorUtil.getColumnIndexOrThrow(_cursor, "safety");
          final ScoreEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpMint;
            _tmpMint = _cursor.getString(_cursorIndexOfMint);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final int _tmpScore;
            _tmpScore = _cursor.getInt(_cursorIndexOfScore);
            final Double _tmpBuyerPressure;
            if (_cursor.isNull(_cursorIndexOfBuyerPressure)) {
              _tmpBuyerPressure = null;
            } else {
              _tmpBuyerPressure = _cursor.getDouble(_cursorIndexOfBuyerPressure);
            }
            final Double _tmpVolumePressure;
            if (_cursor.isNull(_cursorIndexOfVolumePressure)) {
              _tmpVolumePressure = null;
            } else {
              _tmpVolumePressure = _cursor.getDouble(_cursorIndexOfVolumePressure);
            }
            final Double _tmpVolumeVelocity;
            if (_cursor.isNull(_cursorIndexOfVolumeVelocity)) {
              _tmpVolumeVelocity = null;
            } else {
              _tmpVolumeVelocity = _cursor.getDouble(_cursorIndexOfVolumeVelocity);
            }
            final Double _tmpPriceMomentum;
            if (_cursor.isNull(_cursorIndexOfPriceMomentum)) {
              _tmpPriceMomentum = null;
            } else {
              _tmpPriceMomentum = _cursor.getDouble(_cursorIndexOfPriceMomentum);
            }
            final Double _tmpLiquidity;
            if (_cursor.isNull(_cursorIndexOfLiquidity)) {
              _tmpLiquidity = null;
            } else {
              _tmpLiquidity = _cursor.getDouble(_cursorIndexOfLiquidity);
            }
            final Double _tmpHolderDistribution;
            if (_cursor.isNull(_cursorIndexOfHolderDistribution)) {
              _tmpHolderDistribution = null;
            } else {
              _tmpHolderDistribution = _cursor.getDouble(_cursorIndexOfHolderDistribution);
            }
            final Double _tmpSafety;
            if (_cursor.isNull(_cursorIndexOfSafety)) {
              _tmpSafety = null;
            } else {
              _tmpSafety = _cursor.getDouble(_cursorIndexOfSafety);
            }
            _result = new ScoreEntity(_tmpId,_tmpMint,_tmpTimestamp,_tmpScore,_tmpBuyerPressure,_tmpVolumePressure,_tmpVolumeVelocity,_tmpPriceMomentum,_tmpLiquidity,_tmpHolderDistribution,_tmpSafety);
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
