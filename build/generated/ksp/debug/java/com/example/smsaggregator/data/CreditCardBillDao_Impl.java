package com.example.smsaggregator.data;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
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
public final class CreditCardBillDao_Impl implements CreditCardBillDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<CreditCardBill> __insertionAdapterOfCreditCardBill;

  private final EntityDeletionOrUpdateAdapter<CreditCardBill> __updateAdapterOfCreditCardBill;

  private final SharedSQLiteStatement __preparedStmtOfMarkAsPaid;

  private final SharedSQLiteStatement __preparedStmtOfMarkAsPaidById;

  public CreditCardBillDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfCreditCardBill = new EntityInsertionAdapter<CreditCardBill>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `credit_card_bills` (`id`,`bankName`,`cardDigits`,`totalDue`,`minDue`,`dueDate`,`isPaid`,`rawSms`,`billGeneratedDate`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CreditCardBill entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getBankName());
        statement.bindString(3, entity.getCardDigits());
        statement.bindDouble(4, entity.getTotalDue());
        statement.bindDouble(5, entity.getMinDue());
        statement.bindLong(6, entity.getDueDate());
        final int _tmp = entity.isPaid() ? 1 : 0;
        statement.bindLong(7, _tmp);
        statement.bindString(8, entity.getRawSms());
        statement.bindLong(9, entity.getBillGeneratedDate());
      }
    };
    this.__updateAdapterOfCreditCardBill = new EntityDeletionOrUpdateAdapter<CreditCardBill>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `credit_card_bills` SET `id` = ?,`bankName` = ?,`cardDigits` = ?,`totalDue` = ?,`minDue` = ?,`dueDate` = ?,`isPaid` = ?,`rawSms` = ?,`billGeneratedDate` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CreditCardBill entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getBankName());
        statement.bindString(3, entity.getCardDigits());
        statement.bindDouble(4, entity.getTotalDue());
        statement.bindDouble(5, entity.getMinDue());
        statement.bindLong(6, entity.getDueDate());
        final int _tmp = entity.isPaid() ? 1 : 0;
        statement.bindLong(7, _tmp);
        statement.bindString(8, entity.getRawSms());
        statement.bindLong(9, entity.getBillGeneratedDate());
        statement.bindLong(10, entity.getId());
      }
    };
    this.__preparedStmtOfMarkAsPaid = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE credit_card_bills SET isPaid = 1 WHERE bankName = ? AND cardDigits = ? AND isPaid = 0";
        return _query;
      }
    };
    this.__preparedStmtOfMarkAsPaidById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE credit_card_bills SET isPaid = 1 WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertBill(final CreditCardBill bill,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfCreditCardBill.insert(bill);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateBill(final CreditCardBill bill,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfCreditCardBill.handle(bill);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object markAsPaid(final String bankName, final String cardDigits,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfMarkAsPaid.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, bankName);
        _argIndex = 2;
        _stmt.bindString(_argIndex, cardDigits);
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
          __preparedStmtOfMarkAsPaid.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object markAsPaidById(final long billId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfMarkAsPaidById.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, billId);
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
          __preparedStmtOfMarkAsPaidById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<CreditCardBill>> getAllBills() {
    final String _sql = "SELECT * FROM credit_card_bills ORDER BY dueDate ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"credit_card_bills"}, new Callable<List<CreditCardBill>>() {
      @Override
      @NonNull
      public List<CreditCardBill> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfBankName = CursorUtil.getColumnIndexOrThrow(_cursor, "bankName");
          final int _cursorIndexOfCardDigits = CursorUtil.getColumnIndexOrThrow(_cursor, "cardDigits");
          final int _cursorIndexOfTotalDue = CursorUtil.getColumnIndexOrThrow(_cursor, "totalDue");
          final int _cursorIndexOfMinDue = CursorUtil.getColumnIndexOrThrow(_cursor, "minDue");
          final int _cursorIndexOfDueDate = CursorUtil.getColumnIndexOrThrow(_cursor, "dueDate");
          final int _cursorIndexOfIsPaid = CursorUtil.getColumnIndexOrThrow(_cursor, "isPaid");
          final int _cursorIndexOfRawSms = CursorUtil.getColumnIndexOrThrow(_cursor, "rawSms");
          final int _cursorIndexOfBillGeneratedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "billGeneratedDate");
          final List<CreditCardBill> _result = new ArrayList<CreditCardBill>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CreditCardBill _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpBankName;
            _tmpBankName = _cursor.getString(_cursorIndexOfBankName);
            final String _tmpCardDigits;
            _tmpCardDigits = _cursor.getString(_cursorIndexOfCardDigits);
            final double _tmpTotalDue;
            _tmpTotalDue = _cursor.getDouble(_cursorIndexOfTotalDue);
            final double _tmpMinDue;
            _tmpMinDue = _cursor.getDouble(_cursorIndexOfMinDue);
            final long _tmpDueDate;
            _tmpDueDate = _cursor.getLong(_cursorIndexOfDueDate);
            final boolean _tmpIsPaid;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsPaid);
            _tmpIsPaid = _tmp != 0;
            final String _tmpRawSms;
            _tmpRawSms = _cursor.getString(_cursorIndexOfRawSms);
            final long _tmpBillGeneratedDate;
            _tmpBillGeneratedDate = _cursor.getLong(_cursorIndexOfBillGeneratedDate);
            _item = new CreditCardBill(_tmpId,_tmpBankName,_tmpCardDigits,_tmpTotalDue,_tmpMinDue,_tmpDueDate,_tmpIsPaid,_tmpRawSms,_tmpBillGeneratedDate);
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
  public Flow<List<CreditCardBill>> getUnpaidBills() {
    final String _sql = "SELECT * FROM credit_card_bills WHERE isPaid = 0 ORDER BY dueDate ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"credit_card_bills"}, new Callable<List<CreditCardBill>>() {
      @Override
      @NonNull
      public List<CreditCardBill> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfBankName = CursorUtil.getColumnIndexOrThrow(_cursor, "bankName");
          final int _cursorIndexOfCardDigits = CursorUtil.getColumnIndexOrThrow(_cursor, "cardDigits");
          final int _cursorIndexOfTotalDue = CursorUtil.getColumnIndexOrThrow(_cursor, "totalDue");
          final int _cursorIndexOfMinDue = CursorUtil.getColumnIndexOrThrow(_cursor, "minDue");
          final int _cursorIndexOfDueDate = CursorUtil.getColumnIndexOrThrow(_cursor, "dueDate");
          final int _cursorIndexOfIsPaid = CursorUtil.getColumnIndexOrThrow(_cursor, "isPaid");
          final int _cursorIndexOfRawSms = CursorUtil.getColumnIndexOrThrow(_cursor, "rawSms");
          final int _cursorIndexOfBillGeneratedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "billGeneratedDate");
          final List<CreditCardBill> _result = new ArrayList<CreditCardBill>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CreditCardBill _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpBankName;
            _tmpBankName = _cursor.getString(_cursorIndexOfBankName);
            final String _tmpCardDigits;
            _tmpCardDigits = _cursor.getString(_cursorIndexOfCardDigits);
            final double _tmpTotalDue;
            _tmpTotalDue = _cursor.getDouble(_cursorIndexOfTotalDue);
            final double _tmpMinDue;
            _tmpMinDue = _cursor.getDouble(_cursorIndexOfMinDue);
            final long _tmpDueDate;
            _tmpDueDate = _cursor.getLong(_cursorIndexOfDueDate);
            final boolean _tmpIsPaid;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsPaid);
            _tmpIsPaid = _tmp != 0;
            final String _tmpRawSms;
            _tmpRawSms = _cursor.getString(_cursorIndexOfRawSms);
            final long _tmpBillGeneratedDate;
            _tmpBillGeneratedDate = _cursor.getLong(_cursorIndexOfBillGeneratedDate);
            _item = new CreditCardBill(_tmpId,_tmpBankName,_tmpCardDigits,_tmpTotalDue,_tmpMinDue,_tmpDueDate,_tmpIsPaid,_tmpRawSms,_tmpBillGeneratedDate);
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
  public Object findDuplicateBill(final String bankName, final String cardDigits,
      final double totalDue, final long dueDate,
      final Continuation<? super CreditCardBill> $completion) {
    final String _sql = "SELECT * FROM credit_card_bills WHERE bankName = ? AND cardDigits = ? AND totalDue = ? AND dueDate = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 4);
    int _argIndex = 1;
    _statement.bindString(_argIndex, bankName);
    _argIndex = 2;
    _statement.bindString(_argIndex, cardDigits);
    _argIndex = 3;
    _statement.bindDouble(_argIndex, totalDue);
    _argIndex = 4;
    _statement.bindLong(_argIndex, dueDate);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<CreditCardBill>() {
      @Override
      @Nullable
      public CreditCardBill call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfBankName = CursorUtil.getColumnIndexOrThrow(_cursor, "bankName");
          final int _cursorIndexOfCardDigits = CursorUtil.getColumnIndexOrThrow(_cursor, "cardDigits");
          final int _cursorIndexOfTotalDue = CursorUtil.getColumnIndexOrThrow(_cursor, "totalDue");
          final int _cursorIndexOfMinDue = CursorUtil.getColumnIndexOrThrow(_cursor, "minDue");
          final int _cursorIndexOfDueDate = CursorUtil.getColumnIndexOrThrow(_cursor, "dueDate");
          final int _cursorIndexOfIsPaid = CursorUtil.getColumnIndexOrThrow(_cursor, "isPaid");
          final int _cursorIndexOfRawSms = CursorUtil.getColumnIndexOrThrow(_cursor, "rawSms");
          final int _cursorIndexOfBillGeneratedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "billGeneratedDate");
          final CreditCardBill _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpBankName;
            _tmpBankName = _cursor.getString(_cursorIndexOfBankName);
            final String _tmpCardDigits;
            _tmpCardDigits = _cursor.getString(_cursorIndexOfCardDigits);
            final double _tmpTotalDue;
            _tmpTotalDue = _cursor.getDouble(_cursorIndexOfTotalDue);
            final double _tmpMinDue;
            _tmpMinDue = _cursor.getDouble(_cursorIndexOfMinDue);
            final long _tmpDueDate;
            _tmpDueDate = _cursor.getLong(_cursorIndexOfDueDate);
            final boolean _tmpIsPaid;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsPaid);
            _tmpIsPaid = _tmp != 0;
            final String _tmpRawSms;
            _tmpRawSms = _cursor.getString(_cursorIndexOfRawSms);
            final long _tmpBillGeneratedDate;
            _tmpBillGeneratedDate = _cursor.getLong(_cursorIndexOfBillGeneratedDate);
            _result = new CreditCardBill(_tmpId,_tmpBankName,_tmpCardDigits,_tmpTotalDue,_tmpMinDue,_tmpDueDate,_tmpIsPaid,_tmpRawSms,_tmpBillGeneratedDate);
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
