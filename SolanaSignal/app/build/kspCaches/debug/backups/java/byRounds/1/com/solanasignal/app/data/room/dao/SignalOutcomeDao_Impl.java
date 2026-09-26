package com.solanasignal.app.data.room.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.solanasignal.app.data.room.entities.SignalOutcomeEntity;
import java.lang.Class;
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
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class SignalOutcomeDao_Impl implements SignalOutcomeDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<SignalOutcomeEntity> __insertionAdapterOfSignalOutcomeEntity;

  public SignalOutcomeDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfSignalOutcomeEntity = new EntityInsertionAdapter<SignalOutcomeEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `signal_outcomes` (`id`,`signalId`,`mint`,`entryPriceUsd`,`checkTimestamp`,`hypotheticalChangePct`,`elapsedSeconds`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SignalOutcomeEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getSignalId());
        statement.bindString(3, entity.getMint());
        statement.bindDouble(4, entity.getEntryPriceUsd());
        statement.bindLong(5, entity.getCheckTimestamp());
        statement.bindDouble(6, entity.getHypotheticalChangePct());
        statement.bindLong(7, entity.getElapsedSeconds());
      }
    };
  }

  @Override
  public Object insert(final SignalOutcomeEntity outcome,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfSignalOutcomeEntity.insertAndReturnId(outcome);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object forSignal(final long signalId,
      final Continuation<? super List<SignalOutcomeEntity>> $completion) {
    final String _sql = "SELECT * FROM signal_outcomes WHERE signalId = ? ORDER BY checkTimestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, signalId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<SignalOutcomeEntity>>() {
      @Override
      @NonNull
      public List<SignalOutcomeEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSignalId = CursorUtil.getColumnIndexOrThrow(_cursor, "signalId");
          final int _cursorIndexOfMint = CursorUtil.getColumnIndexOrThrow(_cursor, "mint");
          final int _cursorIndexOfEntryPriceUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "entryPriceUsd");
          final int _cursorIndexOfCheckTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "checkTimestamp");
          final int _cursorIndexOfHypotheticalChangePct = CursorUtil.getColumnIndexOrThrow(_cursor, "hypotheticalChangePct");
          final int _cursorIndexOfElapsedSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "elapsedSeconds");
          final List<SignalOutcomeEntity> _result = new ArrayList<SignalOutcomeEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SignalOutcomeEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpSignalId;
            _tmpSignalId = _cursor.getLong(_cursorIndexOfSignalId);
            final String _tmpMint;
            _tmpMint = _cursor.getString(_cursorIndexOfMint);
            final double _tmpEntryPriceUsd;
            _tmpEntryPriceUsd = _cursor.getDouble(_cursorIndexOfEntryPriceUsd);
            final long _tmpCheckTimestamp;
            _tmpCheckTimestamp = _cursor.getLong(_cursorIndexOfCheckTimestamp);
            final double _tmpHypotheticalChangePct;
            _tmpHypotheticalChangePct = _cursor.getDouble(_cursorIndexOfHypotheticalChangePct);
            final int _tmpElapsedSeconds;
            _tmpElapsedSeconds = _cursor.getInt(_cursorIndexOfElapsedSeconds);
            _item = new SignalOutcomeEntity(_tmpId,_tmpSignalId,_tmpMint,_tmpEntryPriceUsd,_tmpCheckTimestamp,_tmpHypotheticalChangePct,_tmpElapsedSeconds);
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
