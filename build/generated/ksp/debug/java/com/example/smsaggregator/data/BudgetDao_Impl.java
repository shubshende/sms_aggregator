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
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class BudgetDao_Impl implements BudgetDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Budget> __insertionAdapterOfBudget;

  private final SharedSQLiteStatement __preparedStmtOfDeleteBudget;

  public BudgetDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfBudget = new EntityInsertionAdapter<Budget>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `budgets` (`category`,`monthlyLimit`,`rolloverAmount`,`lastRolloverMonth`,`lastRolloverYear`) VALUES (?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Budget entity) {
        statement.bindString(1, entity.getCategory());
        statement.bindDouble(2, entity.getMonthlyLimit());
        statement.bindDouble(3, entity.getRolloverAmount());
        statement.bindLong(4, entity.getLastRolloverMonth());
        statement.bindLong(5, entity.getLastRolloverYear());
      }
    };
    this.__preparedStmtOfDeleteBudget = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM budgets WHERE category = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertBudget(final Budget budget, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfBudget.insert(budget);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteBudget(final String category, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteBudget.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, category);
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
          __preparedStmtOfDeleteBudget.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Budget>> getAllBudgets() {
    final String _sql = "SELECT * FROM budgets";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"budgets"}, new Callable<List<Budget>>() {
      @Override
      @NonNull
      public List<Budget> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfMonthlyLimit = CursorUtil.getColumnIndexOrThrow(_cursor, "monthlyLimit");
          final int _cursorIndexOfRolloverAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "rolloverAmount");
          final int _cursorIndexOfLastRolloverMonth = CursorUtil.getColumnIndexOrThrow(_cursor, "lastRolloverMonth");
          final int _cursorIndexOfLastRolloverYear = CursorUtil.getColumnIndexOrThrow(_cursor, "lastRolloverYear");
          final List<Budget> _result = new ArrayList<Budget>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Budget _item;
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final double _tmpMonthlyLimit;
            _tmpMonthlyLimit = _cursor.getDouble(_cursorIndexOfMonthlyLimit);
            final double _tmpRolloverAmount;
            _tmpRolloverAmount = _cursor.getDouble(_cursorIndexOfRolloverAmount);
            final int _tmpLastRolloverMonth;
            _tmpLastRolloverMonth = _cursor.getInt(_cursorIndexOfLastRolloverMonth);
            final int _tmpLastRolloverYear;
            _tmpLastRolloverYear = _cursor.getInt(_cursorIndexOfLastRolloverYear);
            _item = new Budget(_tmpCategory,_tmpMonthlyLimit,_tmpRolloverAmount,_tmpLastRolloverMonth,_tmpLastRolloverYear);
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
  public Object getAllBudgetsList(final Continuation<? super List<Budget>> $completion) {
    final String _sql = "SELECT * FROM budgets";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Budget>>() {
      @Override
      @NonNull
      public List<Budget> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfMonthlyLimit = CursorUtil.getColumnIndexOrThrow(_cursor, "monthlyLimit");
          final int _cursorIndexOfRolloverAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "rolloverAmount");
          final int _cursorIndexOfLastRolloverMonth = CursorUtil.getColumnIndexOrThrow(_cursor, "lastRolloverMonth");
          final int _cursorIndexOfLastRolloverYear = CursorUtil.getColumnIndexOrThrow(_cursor, "lastRolloverYear");
          final List<Budget> _result = new ArrayList<Budget>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Budget _item;
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final double _tmpMonthlyLimit;
            _tmpMonthlyLimit = _cursor.getDouble(_cursorIndexOfMonthlyLimit);
            final double _tmpRolloverAmount;
            _tmpRolloverAmount = _cursor.getDouble(_cursorIndexOfRolloverAmount);
            final int _tmpLastRolloverMonth;
            _tmpLastRolloverMonth = _cursor.getInt(_cursorIndexOfLastRolloverMonth);
            final int _tmpLastRolloverYear;
            _tmpLastRolloverYear = _cursor.getInt(_cursorIndexOfLastRolloverYear);
            _item = new Budget(_tmpCategory,_tmpMonthlyLimit,_tmpRolloverAmount,_tmpLastRolloverMonth,_tmpLastRolloverYear);
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

  @Override
  public Object getBudgetForCategory(final String category,
      final Continuation<? super Budget> $completion) {
    final String _sql = "SELECT * FROM budgets WHERE category = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, category);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Budget>() {
      @Override
      @Nullable
      public Budget call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfMonthlyLimit = CursorUtil.getColumnIndexOrThrow(_cursor, "monthlyLimit");
          final int _cursorIndexOfRolloverAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "rolloverAmount");
          final int _cursorIndexOfLastRolloverMonth = CursorUtil.getColumnIndexOrThrow(_cursor, "lastRolloverMonth");
          final int _cursorIndexOfLastRolloverYear = CursorUtil.getColumnIndexOrThrow(_cursor, "lastRolloverYear");
          final Budget _result;
          if (_cursor.moveToFirst()) {
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final double _tmpMonthlyLimit;
            _tmpMonthlyLimit = _cursor.getDouble(_cursorIndexOfMonthlyLimit);
            final double _tmpRolloverAmount;
            _tmpRolloverAmount = _cursor.getDouble(_cursorIndexOfRolloverAmount);
            final int _tmpLastRolloverMonth;
            _tmpLastRolloverMonth = _cursor.getInt(_cursorIndexOfLastRolloverMonth);
            final int _tmpLastRolloverYear;
            _tmpLastRolloverYear = _cursor.getInt(_cursorIndexOfLastRolloverYear);
            _result = new Budget(_tmpCategory,_tmpMonthlyLimit,_tmpRolloverAmount,_tmpLastRolloverMonth,_tmpLastRolloverYear);
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
