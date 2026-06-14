package com.example.smsaggregator.data;

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
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class SubscriptionDao_Impl implements SubscriptionDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Subscription> __insertionAdapterOfSubscription;

  private final SharedSQLiteStatement __preparedStmtOfDeleteSubscription;

  public SubscriptionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfSubscription = new EntityInsertionAdapter<Subscription>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `subscriptions` (`id`,`merchant`,`amount`,`category`,`frequencyDays`,`lastDate`,`nextExpectedDate`,`isActive`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Subscription entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getMerchant());
        statement.bindDouble(3, entity.getAmount());
        statement.bindString(4, entity.getCategory());
        statement.bindLong(5, entity.getFrequencyDays());
        statement.bindLong(6, entity.getLastDate());
        statement.bindLong(7, entity.getNextExpectedDate());
        final int _tmp = entity.isActive() ? 1 : 0;
        statement.bindLong(8, _tmp);
      }
    };
    this.__preparedStmtOfDeleteSubscription = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM subscriptions WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertSubscription(final Subscription subscription,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfSubscription.insert(subscription);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteSubscription(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteSubscription.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
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
          __preparedStmtOfDeleteSubscription.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Subscription>> getActiveSubscriptions() {
    final String _sql = "SELECT * FROM subscriptions WHERE isActive = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"subscriptions"}, new Callable<List<Subscription>>() {
      @Override
      @NonNull
      public List<Subscription> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMerchant = CursorUtil.getColumnIndexOrThrow(_cursor, "merchant");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfFrequencyDays = CursorUtil.getColumnIndexOrThrow(_cursor, "frequencyDays");
          final int _cursorIndexOfLastDate = CursorUtil.getColumnIndexOrThrow(_cursor, "lastDate");
          final int _cursorIndexOfNextExpectedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "nextExpectedDate");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final List<Subscription> _result = new ArrayList<Subscription>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Subscription _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpMerchant;
            _tmpMerchant = _cursor.getString(_cursorIndexOfMerchant);
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final int _tmpFrequencyDays;
            _tmpFrequencyDays = _cursor.getInt(_cursorIndexOfFrequencyDays);
            final long _tmpLastDate;
            _tmpLastDate = _cursor.getLong(_cursorIndexOfLastDate);
            final long _tmpNextExpectedDate;
            _tmpNextExpectedDate = _cursor.getLong(_cursorIndexOfNextExpectedDate);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            _item = new Subscription(_tmpId,_tmpMerchant,_tmpAmount,_tmpCategory,_tmpFrequencyDays,_tmpLastDate,_tmpNextExpectedDate,_tmpIsActive);
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
  public Object getActiveSubscriptionsList(
      final Continuation<? super List<Subscription>> $completion) {
    final String _sql = "SELECT * FROM subscriptions WHERE isActive = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Subscription>>() {
      @Override
      @NonNull
      public List<Subscription> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMerchant = CursorUtil.getColumnIndexOrThrow(_cursor, "merchant");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfFrequencyDays = CursorUtil.getColumnIndexOrThrow(_cursor, "frequencyDays");
          final int _cursorIndexOfLastDate = CursorUtil.getColumnIndexOrThrow(_cursor, "lastDate");
          final int _cursorIndexOfNextExpectedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "nextExpectedDate");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final List<Subscription> _result = new ArrayList<Subscription>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Subscription _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpMerchant;
            _tmpMerchant = _cursor.getString(_cursorIndexOfMerchant);
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final int _tmpFrequencyDays;
            _tmpFrequencyDays = _cursor.getInt(_cursorIndexOfFrequencyDays);
            final long _tmpLastDate;
            _tmpLastDate = _cursor.getLong(_cursorIndexOfLastDate);
            final long _tmpNextExpectedDate;
            _tmpNextExpectedDate = _cursor.getLong(_cursorIndexOfNextExpectedDate);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            _item = new Subscription(_tmpId,_tmpMerchant,_tmpAmount,_tmpCategory,_tmpFrequencyDays,_tmpLastDate,_tmpNextExpectedDate,_tmpIsActive);
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
