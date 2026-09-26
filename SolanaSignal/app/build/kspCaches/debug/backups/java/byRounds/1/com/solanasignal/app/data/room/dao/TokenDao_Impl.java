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
import com.solanasignal.app.data.room.entities.TokenEntity;
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
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class TokenDao_Impl implements TokenDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<TokenEntity> __insertionAdapterOfTokenEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteOlderThan;

  public TokenDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTokenEntity = new EntityInsertionAdapter<TokenEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `tokens` (`mint`,`name`,`symbol`,`creator`,`uri`,`poolAddress`,`createdAtEpochMs`,`firstSeenAtEpochMs`,`marketCapSol`,`liquiditySol`,`marketCapUsd`,`liquidityUsd`,`lastPriceUsd`,`buyers5m`,`sellers5m`,`buyVolume5mUsd`,`sellVolume5mUsd`,`lifecycle`,`source`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TokenEntity entity) {
        statement.bindString(1, entity.getMint());
        if (entity.getName() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getName());
        }
        if (entity.getSymbol() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getSymbol());
        }
        if (entity.getCreator() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getCreator());
        }
        if (entity.getUri() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getUri());
        }
        if (entity.getPoolAddress() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getPoolAddress());
        }
        if (entity.getCreatedAtEpochMs() == null) {
          statement.bindNull(7);
        } else {
          statement.bindLong(7, entity.getCreatedAtEpochMs());
        }
        statement.bindLong(8, entity.getFirstSeenAtEpochMs());
        if (entity.getMarketCapSol() == null) {
          statement.bindNull(9);
        } else {
          statement.bindDouble(9, entity.getMarketCapSol());
        }
        if (entity.getLiquiditySol() == null) {
          statement.bindNull(10);
        } else {
          statement.bindDouble(10, entity.getLiquiditySol());
        }
        if (entity.getMarketCapUsd() == null) {
          statement.bindNull(11);
        } else {
          statement.bindDouble(11, entity.getMarketCapUsd());
        }
        if (entity.getLiquidityUsd() == null) {
          statement.bindNull(12);
        } else {
          statement.bindDouble(12, entity.getLiquidityUsd());
        }
        if (entity.getLastPriceUsd() == null) {
          statement.bindNull(13);
        } else {
          statement.bindDouble(13, entity.getLastPriceUsd());
        }
        statement.bindLong(14, entity.getBuyers5m());
        statement.bindLong(15, entity.getSellers5m());
        statement.bindDouble(16, entity.getBuyVolume5mUsd());
        statement.bindDouble(17, entity.getSellVolume5mUsd());
        statement.bindString(18, entity.getLifecycle());
        statement.bindString(19, entity.getSource());
      }
    };
    this.__preparedStmtOfDeleteOlderThan = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM tokens WHERE firstSeenAtEpochMs < ?";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final TokenEntity token, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfTokenEntity.insert(token);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
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
  public Object getByMint(final String mint, final Continuation<? super TokenEntity> $completion) {
    final String _sql = "SELECT * FROM tokens WHERE mint = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, mint);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<TokenEntity>() {
      @Override
      @Nullable
      public TokenEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfMint = CursorUtil.getColumnIndexOrThrow(_cursor, "mint");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfSymbol = CursorUtil.getColumnIndexOrThrow(_cursor, "symbol");
          final int _cursorIndexOfCreator = CursorUtil.getColumnIndexOrThrow(_cursor, "creator");
          final int _cursorIndexOfUri = CursorUtil.getColumnIndexOrThrow(_cursor, "uri");
          final int _cursorIndexOfPoolAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "poolAddress");
          final int _cursorIndexOfCreatedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtEpochMs");
          final int _cursorIndexOfFirstSeenAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "firstSeenAtEpochMs");
          final int _cursorIndexOfMarketCapSol = CursorUtil.getColumnIndexOrThrow(_cursor, "marketCapSol");
          final int _cursorIndexOfLiquiditySol = CursorUtil.getColumnIndexOrThrow(_cursor, "liquiditySol");
          final int _cursorIndexOfMarketCapUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "marketCapUsd");
          final int _cursorIndexOfLiquidityUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "liquidityUsd");
          final int _cursorIndexOfLastPriceUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "lastPriceUsd");
          final int _cursorIndexOfBuyers5m = CursorUtil.getColumnIndexOrThrow(_cursor, "buyers5m");
          final int _cursorIndexOfSellers5m = CursorUtil.getColumnIndexOrThrow(_cursor, "sellers5m");
          final int _cursorIndexOfBuyVolume5mUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "buyVolume5mUsd");
          final int _cursorIndexOfSellVolume5mUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "sellVolume5mUsd");
          final int _cursorIndexOfLifecycle = CursorUtil.getColumnIndexOrThrow(_cursor, "lifecycle");
          final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
          final TokenEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpMint;
            _tmpMint = _cursor.getString(_cursorIndexOfMint);
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final String _tmpSymbol;
            if (_cursor.isNull(_cursorIndexOfSymbol)) {
              _tmpSymbol = null;
            } else {
              _tmpSymbol = _cursor.getString(_cursorIndexOfSymbol);
            }
            final String _tmpCreator;
            if (_cursor.isNull(_cursorIndexOfCreator)) {
              _tmpCreator = null;
            } else {
              _tmpCreator = _cursor.getString(_cursorIndexOfCreator);
            }
            final String _tmpUri;
            if (_cursor.isNull(_cursorIndexOfUri)) {
              _tmpUri = null;
            } else {
              _tmpUri = _cursor.getString(_cursorIndexOfUri);
            }
            final String _tmpPoolAddress;
            if (_cursor.isNull(_cursorIndexOfPoolAddress)) {
              _tmpPoolAddress = null;
            } else {
              _tmpPoolAddress = _cursor.getString(_cursorIndexOfPoolAddress);
            }
            final Long _tmpCreatedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfCreatedAtEpochMs)) {
              _tmpCreatedAtEpochMs = null;
            } else {
              _tmpCreatedAtEpochMs = _cursor.getLong(_cursorIndexOfCreatedAtEpochMs);
            }
            final long _tmpFirstSeenAtEpochMs;
            _tmpFirstSeenAtEpochMs = _cursor.getLong(_cursorIndexOfFirstSeenAtEpochMs);
            final Double _tmpMarketCapSol;
            if (_cursor.isNull(_cursorIndexOfMarketCapSol)) {
              _tmpMarketCapSol = null;
            } else {
              _tmpMarketCapSol = _cursor.getDouble(_cursorIndexOfMarketCapSol);
            }
            final Double _tmpLiquiditySol;
            if (_cursor.isNull(_cursorIndexOfLiquiditySol)) {
              _tmpLiquiditySol = null;
            } else {
              _tmpLiquiditySol = _cursor.getDouble(_cursorIndexOfLiquiditySol);
            }
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
            final Double _tmpLastPriceUsd;
            if (_cursor.isNull(_cursorIndexOfLastPriceUsd)) {
              _tmpLastPriceUsd = null;
            } else {
              _tmpLastPriceUsd = _cursor.getDouble(_cursorIndexOfLastPriceUsd);
            }
            final int _tmpBuyers5m;
            _tmpBuyers5m = _cursor.getInt(_cursorIndexOfBuyers5m);
            final int _tmpSellers5m;
            _tmpSellers5m = _cursor.getInt(_cursorIndexOfSellers5m);
            final double _tmpBuyVolume5mUsd;
            _tmpBuyVolume5mUsd = _cursor.getDouble(_cursorIndexOfBuyVolume5mUsd);
            final double _tmpSellVolume5mUsd;
            _tmpSellVolume5mUsd = _cursor.getDouble(_cursorIndexOfSellVolume5mUsd);
            final String _tmpLifecycle;
            _tmpLifecycle = _cursor.getString(_cursorIndexOfLifecycle);
            final String _tmpSource;
            _tmpSource = _cursor.getString(_cursorIndexOfSource);
            _result = new TokenEntity(_tmpMint,_tmpName,_tmpSymbol,_tmpCreator,_tmpUri,_tmpPoolAddress,_tmpCreatedAtEpochMs,_tmpFirstSeenAtEpochMs,_tmpMarketCapSol,_tmpLiquiditySol,_tmpMarketCapUsd,_tmpLiquidityUsd,_tmpLastPriceUsd,_tmpBuyers5m,_tmpSellers5m,_tmpBuyVolume5mUsd,_tmpSellVolume5mUsd,_tmpLifecycle,_tmpSource);
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
  public Flow<List<TokenEntity>> observeAll() {
    final String _sql = "SELECT * FROM tokens ORDER BY firstSeenAtEpochMs DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"tokens"}, new Callable<List<TokenEntity>>() {
      @Override
      @NonNull
      public List<TokenEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfMint = CursorUtil.getColumnIndexOrThrow(_cursor, "mint");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfSymbol = CursorUtil.getColumnIndexOrThrow(_cursor, "symbol");
          final int _cursorIndexOfCreator = CursorUtil.getColumnIndexOrThrow(_cursor, "creator");
          final int _cursorIndexOfUri = CursorUtil.getColumnIndexOrThrow(_cursor, "uri");
          final int _cursorIndexOfPoolAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "poolAddress");
          final int _cursorIndexOfCreatedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtEpochMs");
          final int _cursorIndexOfFirstSeenAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "firstSeenAtEpochMs");
          final int _cursorIndexOfMarketCapSol = CursorUtil.getColumnIndexOrThrow(_cursor, "marketCapSol");
          final int _cursorIndexOfLiquiditySol = CursorUtil.getColumnIndexOrThrow(_cursor, "liquiditySol");
          final int _cursorIndexOfMarketCapUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "marketCapUsd");
          final int _cursorIndexOfLiquidityUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "liquidityUsd");
          final int _cursorIndexOfLastPriceUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "lastPriceUsd");
          final int _cursorIndexOfBuyers5m = CursorUtil.getColumnIndexOrThrow(_cursor, "buyers5m");
          final int _cursorIndexOfSellers5m = CursorUtil.getColumnIndexOrThrow(_cursor, "sellers5m");
          final int _cursorIndexOfBuyVolume5mUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "buyVolume5mUsd");
          final int _cursorIndexOfSellVolume5mUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "sellVolume5mUsd");
          final int _cursorIndexOfLifecycle = CursorUtil.getColumnIndexOrThrow(_cursor, "lifecycle");
          final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
          final List<TokenEntity> _result = new ArrayList<TokenEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TokenEntity _item;
            final String _tmpMint;
            _tmpMint = _cursor.getString(_cursorIndexOfMint);
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final String _tmpSymbol;
            if (_cursor.isNull(_cursorIndexOfSymbol)) {
              _tmpSymbol = null;
            } else {
              _tmpSymbol = _cursor.getString(_cursorIndexOfSymbol);
            }
            final String _tmpCreator;
            if (_cursor.isNull(_cursorIndexOfCreator)) {
              _tmpCreator = null;
            } else {
              _tmpCreator = _cursor.getString(_cursorIndexOfCreator);
            }
            final String _tmpUri;
            if (_cursor.isNull(_cursorIndexOfUri)) {
              _tmpUri = null;
            } else {
              _tmpUri = _cursor.getString(_cursorIndexOfUri);
            }
            final String _tmpPoolAddress;
            if (_cursor.isNull(_cursorIndexOfPoolAddress)) {
              _tmpPoolAddress = null;
            } else {
              _tmpPoolAddress = _cursor.getString(_cursorIndexOfPoolAddress);
            }
            final Long _tmpCreatedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfCreatedAtEpochMs)) {
              _tmpCreatedAtEpochMs = null;
            } else {
              _tmpCreatedAtEpochMs = _cursor.getLong(_cursorIndexOfCreatedAtEpochMs);
            }
            final long _tmpFirstSeenAtEpochMs;
            _tmpFirstSeenAtEpochMs = _cursor.getLong(_cursorIndexOfFirstSeenAtEpochMs);
            final Double _tmpMarketCapSol;
            if (_cursor.isNull(_cursorIndexOfMarketCapSol)) {
              _tmpMarketCapSol = null;
            } else {
              _tmpMarketCapSol = _cursor.getDouble(_cursorIndexOfMarketCapSol);
            }
            final Double _tmpLiquiditySol;
            if (_cursor.isNull(_cursorIndexOfLiquiditySol)) {
              _tmpLiquiditySol = null;
            } else {
              _tmpLiquiditySol = _cursor.getDouble(_cursorIndexOfLiquiditySol);
            }
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
            final Double _tmpLastPriceUsd;
            if (_cursor.isNull(_cursorIndexOfLastPriceUsd)) {
              _tmpLastPriceUsd = null;
            } else {
              _tmpLastPriceUsd = _cursor.getDouble(_cursorIndexOfLastPriceUsd);
            }
            final int _tmpBuyers5m;
            _tmpBuyers5m = _cursor.getInt(_cursorIndexOfBuyers5m);
            final int _tmpSellers5m;
            _tmpSellers5m = _cursor.getInt(_cursorIndexOfSellers5m);
            final double _tmpBuyVolume5mUsd;
            _tmpBuyVolume5mUsd = _cursor.getDouble(_cursorIndexOfBuyVolume5mUsd);
            final double _tmpSellVolume5mUsd;
            _tmpSellVolume5mUsd = _cursor.getDouble(_cursorIndexOfSellVolume5mUsd);
            final String _tmpLifecycle;
            _tmpLifecycle = _cursor.getString(_cursorIndexOfLifecycle);
            final String _tmpSource;
            _tmpSource = _cursor.getString(_cursorIndexOfSource);
            _item = new TokenEntity(_tmpMint,_tmpName,_tmpSymbol,_tmpCreator,_tmpUri,_tmpPoolAddress,_tmpCreatedAtEpochMs,_tmpFirstSeenAtEpochMs,_tmpMarketCapSol,_tmpLiquiditySol,_tmpMarketCapUsd,_tmpLiquidityUsd,_tmpLastPriceUsd,_tmpBuyers5m,_tmpSellers5m,_tmpBuyVolume5mUsd,_tmpSellVolume5mUsd,_tmpLifecycle,_tmpSource);
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
  public Object getByLifecycle(final String lifecycle,
      final Continuation<? super List<TokenEntity>> $completion) {
    final String _sql = "SELECT * FROM tokens WHERE lifecycle = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, lifecycle);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TokenEntity>>() {
      @Override
      @NonNull
      public List<TokenEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfMint = CursorUtil.getColumnIndexOrThrow(_cursor, "mint");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfSymbol = CursorUtil.getColumnIndexOrThrow(_cursor, "symbol");
          final int _cursorIndexOfCreator = CursorUtil.getColumnIndexOrThrow(_cursor, "creator");
          final int _cursorIndexOfUri = CursorUtil.getColumnIndexOrThrow(_cursor, "uri");
          final int _cursorIndexOfPoolAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "poolAddress");
          final int _cursorIndexOfCreatedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtEpochMs");
          final int _cursorIndexOfFirstSeenAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "firstSeenAtEpochMs");
          final int _cursorIndexOfMarketCapSol = CursorUtil.getColumnIndexOrThrow(_cursor, "marketCapSol");
          final int _cursorIndexOfLiquiditySol = CursorUtil.getColumnIndexOrThrow(_cursor, "liquiditySol");
          final int _cursorIndexOfMarketCapUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "marketCapUsd");
          final int _cursorIndexOfLiquidityUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "liquidityUsd");
          final int _cursorIndexOfLastPriceUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "lastPriceUsd");
          final int _cursorIndexOfBuyers5m = CursorUtil.getColumnIndexOrThrow(_cursor, "buyers5m");
          final int _cursorIndexOfSellers5m = CursorUtil.getColumnIndexOrThrow(_cursor, "sellers5m");
          final int _cursorIndexOfBuyVolume5mUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "buyVolume5mUsd");
          final int _cursorIndexOfSellVolume5mUsd = CursorUtil.getColumnIndexOrThrow(_cursor, "sellVolume5mUsd");
          final int _cursorIndexOfLifecycle = CursorUtil.getColumnIndexOrThrow(_cursor, "lifecycle");
          final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
          final List<TokenEntity> _result = new ArrayList<TokenEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TokenEntity _item;
            final String _tmpMint;
            _tmpMint = _cursor.getString(_cursorIndexOfMint);
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final String _tmpSymbol;
            if (_cursor.isNull(_cursorIndexOfSymbol)) {
              _tmpSymbol = null;
            } else {
              _tmpSymbol = _cursor.getString(_cursorIndexOfSymbol);
            }
            final String _tmpCreator;
            if (_cursor.isNull(_cursorIndexOfCreator)) {
              _tmpCreator = null;
            } else {
              _tmpCreator = _cursor.getString(_cursorIndexOfCreator);
            }
            final String _tmpUri;
            if (_cursor.isNull(_cursorIndexOfUri)) {
              _tmpUri = null;
            } else {
              _tmpUri = _cursor.getString(_cursorIndexOfUri);
            }
            final String _tmpPoolAddress;
            if (_cursor.isNull(_cursorIndexOfPoolAddress)) {
              _tmpPoolAddress = null;
            } else {
              _tmpPoolAddress = _cursor.getString(_cursorIndexOfPoolAddress);
            }
            final Long _tmpCreatedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfCreatedAtEpochMs)) {
              _tmpCreatedAtEpochMs = null;
            } else {
              _tmpCreatedAtEpochMs = _cursor.getLong(_cursorIndexOfCreatedAtEpochMs);
            }
            final long _tmpFirstSeenAtEpochMs;
            _tmpFirstSeenAtEpochMs = _cursor.getLong(_cursorIndexOfFirstSeenAtEpochMs);
            final Double _tmpMarketCapSol;
            if (_cursor.isNull(_cursorIndexOfMarketCapSol)) {
              _tmpMarketCapSol = null;
            } else {
              _tmpMarketCapSol = _cursor.getDouble(_cursorIndexOfMarketCapSol);
            }
            final Double _tmpLiquiditySol;
            if (_cursor.isNull(_cursorIndexOfLiquiditySol)) {
              _tmpLiquiditySol = null;
            } else {
              _tmpLiquiditySol = _cursor.getDouble(_cursorIndexOfLiquiditySol);
            }
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
            final Double _tmpLastPriceUsd;
            if (_cursor.isNull(_cursorIndexOfLastPriceUsd)) {
              _tmpLastPriceUsd = null;
            } else {
              _tmpLastPriceUsd = _cursor.getDouble(_cursorIndexOfLastPriceUsd);
            }
            final int _tmpBuyers5m;
            _tmpBuyers5m = _cursor.getInt(_cursorIndexOfBuyers5m);
            final int _tmpSellers5m;
            _tmpSellers5m = _cursor.getInt(_cursorIndexOfSellers5m);
            final double _tmpBuyVolume5mUsd;
            _tmpBuyVolume5mUsd = _cursor.getDouble(_cursorIndexOfBuyVolume5mUsd);
            final double _tmpSellVolume5mUsd;
            _tmpSellVolume5mUsd = _cursor.getDouble(_cursorIndexOfSellVolume5mUsd);
            final String _tmpLifecycle;
            _tmpLifecycle = _cursor.getString(_cursorIndexOfLifecycle);
            final String _tmpSource;
            _tmpSource = _cursor.getString(_cursorIndexOfSource);
            _item = new TokenEntity(_tmpMint,_tmpName,_tmpSymbol,_tmpCreator,_tmpUri,_tmpPoolAddress,_tmpCreatedAtEpochMs,_tmpFirstSeenAtEpochMs,_tmpMarketCapSol,_tmpLiquiditySol,_tmpMarketCapUsd,_tmpLiquidityUsd,_tmpLastPriceUsd,_tmpBuyers5m,_tmpSellers5m,_tmpBuyVolume5mUsd,_tmpSellVolume5mUsd,_tmpLifecycle,_tmpSource);
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
