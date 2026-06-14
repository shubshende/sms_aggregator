package com.example.smsaggregator.data;

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
import java.lang.Class;
import java.lang.Exception;
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
public final class MerchantOverrideDao_Impl implements MerchantOverrideDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<MerchantOverride> __insertionAdapterOfMerchantOverride;

  private final SharedSQLiteStatement __preparedStmtOfDeleteOverride;

  private final SharedSQLiteStatement __preparedStmtOfDeleteOverridesByType;

  private final SharedSQLiteStatement __preparedStmtOfUpdateCategoryName;

  public MerchantOverrideDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfMerchantOverride = new EntityInsertionAdapter<MerchantOverride>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `merchant_overrides` (`merchantKey`,`category`,`source`) VALUES (?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final MerchantOverride entity) {
        statement.bindString(1, entity.getMerchantKey());
        statement.bindString(2, entity.getCategory());
        statement.bindString(3, entity.getSource());
      }
    };
    this.__preparedStmtOfDeleteOverride = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM merchant_overrides WHERE merchantKey = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteOverridesByType = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM merchant_overrides WHERE source = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateCategoryName = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE merchant_overrides SET category = ? WHERE category = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertOverride(final MerchantOverride override,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfMerchantOverride.insert(override);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<MerchantOverride> overrides,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfMerchantOverride.insert(overrides);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteOverride(final String merchantKey,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteOverride.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, merchantKey);
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
          __preparedStmtOfDeleteOverride.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteOverridesByType(final String source,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteOverridesByType.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, source);
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
          __preparedStmtOfDeleteOverridesByType.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateCategoryName(final String oldCategory, final String newCategory,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateCategoryName.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, newCategory);
        _argIndex = 2;
        _stmt.bindString(_argIndex, oldCategory);
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
          __preparedStmtOfUpdateCategoryName.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getCategoryForMerchant(final String merchantKey,
      final Continuation<? super String> $completion) {
    final String _sql = "SELECT category FROM merchant_overrides WHERE merchantKey = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, merchantKey);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<String>() {
      @Override
      @Nullable
      public String call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final String _result;
          if (_cursor.moveToFirst()) {
            if (_cursor.isNull(0)) {
              _result = null;
            } else {
              _result = _cursor.getString(0);
            }
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
  public Object getAllOverrides(final Continuation<? super List<MerchantOverride>> $completion) {
    final String _sql = "SELECT * FROM merchant_overrides";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<MerchantOverride>>() {
      @Override
      @NonNull
      public List<MerchantOverride> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfMerchantKey = CursorUtil.getColumnIndexOrThrow(_cursor, "merchantKey");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
          final List<MerchantOverride> _result = new ArrayList<MerchantOverride>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MerchantOverride _item;
            final String _tmpMerchantKey;
            _tmpMerchantKey = _cursor.getString(_cursorIndexOfMerchantKey);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final String _tmpSource;
            _tmpSource = _cursor.getString(_cursorIndexOfSource);
            _item = new MerchantOverride(_tmpMerchantKey,_tmpCategory,_tmpSource);
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
