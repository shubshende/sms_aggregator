package com.example.smsaggregator.data;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile TransactionDao _transactionDao;

  private volatile MerchantOverrideDao _merchantOverrideDao;

  private volatile BudgetDao _budgetDao;

  private volatile UserCategoryDao _userCategoryDao;

  private volatile TransactionSplitDao _transactionSplitDao;

  private volatile SubscriptionDao _subscriptionDao;

  private volatile CreditCardBillDao _creditCardBillDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(10) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount` REAL NOT NULL, `merchant` TEXT NOT NULL, `category` TEXT NOT NULL, `date` INTEGER NOT NULL, `type` TEXT NOT NULL, `source` TEXT NOT NULL, `rawSms` TEXT NOT NULL, `isIgnored` INTEGER NOT NULL, `isManual` INTEGER NOT NULL, `receiptPath` TEXT, `isTransfer` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_date_amount_merchant` ON `transactions` (`date`, `amount`, `merchant`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `merchant_overrides` (`merchantKey` TEXT NOT NULL, `category` TEXT NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`merchantKey`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `budgets` (`category` TEXT NOT NULL, `monthlyLimit` REAL NOT NULL, `rolloverAmount` REAL NOT NULL, `lastRolloverMonth` INTEGER NOT NULL, `lastRolloverYear` INTEGER NOT NULL, PRIMARY KEY(`category`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `user_categories` (`name` TEXT NOT NULL, `colorHex` TEXT NOT NULL, `iconName` TEXT NOT NULL, `isCustom` INTEGER NOT NULL, PRIMARY KEY(`name`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `transaction_splits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transactionId` INTEGER NOT NULL, `amount` REAL NOT NULL, `category` TEXT NOT NULL, `note` TEXT NOT NULL, FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_splits_transactionId` ON `transaction_splits` (`transactionId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `subscriptions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `merchant` TEXT NOT NULL, `amount` REAL NOT NULL, `category` TEXT NOT NULL, `frequencyDays` INTEGER NOT NULL, `lastDate` INTEGER NOT NULL, `nextExpectedDate` INTEGER NOT NULL, `isActive` INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_subscriptions_merchant_amount` ON `subscriptions` (`merchant`, `amount`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `credit_card_bills` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bankName` TEXT NOT NULL, `cardDigits` TEXT NOT NULL, `totalDue` REAL NOT NULL, `minDue` REAL NOT NULL, `dueDate` INTEGER NOT NULL, `isPaid` INTEGER NOT NULL, `rawSms` TEXT NOT NULL, `billGeneratedDate` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '5afb0f8c120889f03ac80d5c33dee9e3')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `transactions`");
        db.execSQL("DROP TABLE IF EXISTS `merchant_overrides`");
        db.execSQL("DROP TABLE IF EXISTS `budgets`");
        db.execSQL("DROP TABLE IF EXISTS `user_categories`");
        db.execSQL("DROP TABLE IF EXISTS `transaction_splits`");
        db.execSQL("DROP TABLE IF EXISTS `subscriptions`");
        db.execSQL("DROP TABLE IF EXISTS `credit_card_bills`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        db.execSQL("PRAGMA foreign_keys = ON");
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsTransactions = new HashMap<String, TableInfo.Column>(13);
        _columnsTransactions.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("amount", new TableInfo.Column("amount", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("merchant", new TableInfo.Column("merchant", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("category", new TableInfo.Column("category", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("date", new TableInfo.Column("date", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("type", new TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("source", new TableInfo.Column("source", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("rawSms", new TableInfo.Column("rawSms", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("isIgnored", new TableInfo.Column("isIgnored", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("isManual", new TableInfo.Column("isManual", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("receiptPath", new TableInfo.Column("receiptPath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("isTransfer", new TableInfo.Column("isTransfer", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTransactions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTransactions = new HashSet<TableInfo.Index>(1);
        _indicesTransactions.add(new TableInfo.Index("index_transactions_date_amount_merchant", true, Arrays.asList("date", "amount", "merchant"), Arrays.asList("ASC", "ASC", "ASC")));
        final TableInfo _infoTransactions = new TableInfo("transactions", _columnsTransactions, _foreignKeysTransactions, _indicesTransactions);
        final TableInfo _existingTransactions = TableInfo.read(db, "transactions");
        if (!_infoTransactions.equals(_existingTransactions)) {
          return new RoomOpenHelper.ValidationResult(false, "transactions(com.example.smsaggregator.data.Transaction).\n"
                  + " Expected:\n" + _infoTransactions + "\n"
                  + " Found:\n" + _existingTransactions);
        }
        final HashMap<String, TableInfo.Column> _columnsMerchantOverrides = new HashMap<String, TableInfo.Column>(3);
        _columnsMerchantOverrides.put("merchantKey", new TableInfo.Column("merchantKey", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMerchantOverrides.put("category", new TableInfo.Column("category", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMerchantOverrides.put("source", new TableInfo.Column("source", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysMerchantOverrides = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesMerchantOverrides = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoMerchantOverrides = new TableInfo("merchant_overrides", _columnsMerchantOverrides, _foreignKeysMerchantOverrides, _indicesMerchantOverrides);
        final TableInfo _existingMerchantOverrides = TableInfo.read(db, "merchant_overrides");
        if (!_infoMerchantOverrides.equals(_existingMerchantOverrides)) {
          return new RoomOpenHelper.ValidationResult(false, "merchant_overrides(com.example.smsaggregator.data.MerchantOverride).\n"
                  + " Expected:\n" + _infoMerchantOverrides + "\n"
                  + " Found:\n" + _existingMerchantOverrides);
        }
        final HashMap<String, TableInfo.Column> _columnsBudgets = new HashMap<String, TableInfo.Column>(5);
        _columnsBudgets.put("category", new TableInfo.Column("category", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBudgets.put("monthlyLimit", new TableInfo.Column("monthlyLimit", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBudgets.put("rolloverAmount", new TableInfo.Column("rolloverAmount", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBudgets.put("lastRolloverMonth", new TableInfo.Column("lastRolloverMonth", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBudgets.put("lastRolloverYear", new TableInfo.Column("lastRolloverYear", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysBudgets = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesBudgets = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoBudgets = new TableInfo("budgets", _columnsBudgets, _foreignKeysBudgets, _indicesBudgets);
        final TableInfo _existingBudgets = TableInfo.read(db, "budgets");
        if (!_infoBudgets.equals(_existingBudgets)) {
          return new RoomOpenHelper.ValidationResult(false, "budgets(com.example.smsaggregator.data.Budget).\n"
                  + " Expected:\n" + _infoBudgets + "\n"
                  + " Found:\n" + _existingBudgets);
        }
        final HashMap<String, TableInfo.Column> _columnsUserCategories = new HashMap<String, TableInfo.Column>(4);
        _columnsUserCategories.put("name", new TableInfo.Column("name", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserCategories.put("colorHex", new TableInfo.Column("colorHex", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserCategories.put("iconName", new TableInfo.Column("iconName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserCategories.put("isCustom", new TableInfo.Column("isCustom", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysUserCategories = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesUserCategories = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoUserCategories = new TableInfo("user_categories", _columnsUserCategories, _foreignKeysUserCategories, _indicesUserCategories);
        final TableInfo _existingUserCategories = TableInfo.read(db, "user_categories");
        if (!_infoUserCategories.equals(_existingUserCategories)) {
          return new RoomOpenHelper.ValidationResult(false, "user_categories(com.example.smsaggregator.data.UserCategory).\n"
                  + " Expected:\n" + _infoUserCategories + "\n"
                  + " Found:\n" + _existingUserCategories);
        }
        final HashMap<String, TableInfo.Column> _columnsTransactionSplits = new HashMap<String, TableInfo.Column>(5);
        _columnsTransactionSplits.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactionSplits.put("transactionId", new TableInfo.Column("transactionId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactionSplits.put("amount", new TableInfo.Column("amount", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactionSplits.put("category", new TableInfo.Column("category", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactionSplits.put("note", new TableInfo.Column("note", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTransactionSplits = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysTransactionSplits.add(new TableInfo.ForeignKey("transactions", "CASCADE", "NO ACTION", Arrays.asList("transactionId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesTransactionSplits = new HashSet<TableInfo.Index>(1);
        _indicesTransactionSplits.add(new TableInfo.Index("index_transaction_splits_transactionId", false, Arrays.asList("transactionId"), Arrays.asList("ASC")));
        final TableInfo _infoTransactionSplits = new TableInfo("transaction_splits", _columnsTransactionSplits, _foreignKeysTransactionSplits, _indicesTransactionSplits);
        final TableInfo _existingTransactionSplits = TableInfo.read(db, "transaction_splits");
        if (!_infoTransactionSplits.equals(_existingTransactionSplits)) {
          return new RoomOpenHelper.ValidationResult(false, "transaction_splits(com.example.smsaggregator.data.TransactionSplit).\n"
                  + " Expected:\n" + _infoTransactionSplits + "\n"
                  + " Found:\n" + _existingTransactionSplits);
        }
        final HashMap<String, TableInfo.Column> _columnsSubscriptions = new HashMap<String, TableInfo.Column>(8);
        _columnsSubscriptions.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSubscriptions.put("merchant", new TableInfo.Column("merchant", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSubscriptions.put("amount", new TableInfo.Column("amount", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSubscriptions.put("category", new TableInfo.Column("category", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSubscriptions.put("frequencyDays", new TableInfo.Column("frequencyDays", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSubscriptions.put("lastDate", new TableInfo.Column("lastDate", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSubscriptions.put("nextExpectedDate", new TableInfo.Column("nextExpectedDate", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSubscriptions.put("isActive", new TableInfo.Column("isActive", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSubscriptions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSubscriptions = new HashSet<TableInfo.Index>(1);
        _indicesSubscriptions.add(new TableInfo.Index("index_subscriptions_merchant_amount", true, Arrays.asList("merchant", "amount"), Arrays.asList("ASC", "ASC")));
        final TableInfo _infoSubscriptions = new TableInfo("subscriptions", _columnsSubscriptions, _foreignKeysSubscriptions, _indicesSubscriptions);
        final TableInfo _existingSubscriptions = TableInfo.read(db, "subscriptions");
        if (!_infoSubscriptions.equals(_existingSubscriptions)) {
          return new RoomOpenHelper.ValidationResult(false, "subscriptions(com.example.smsaggregator.data.Subscription).\n"
                  + " Expected:\n" + _infoSubscriptions + "\n"
                  + " Found:\n" + _existingSubscriptions);
        }
        final HashMap<String, TableInfo.Column> _columnsCreditCardBills = new HashMap<String, TableInfo.Column>(9);
        _columnsCreditCardBills.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCreditCardBills.put("bankName", new TableInfo.Column("bankName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCreditCardBills.put("cardDigits", new TableInfo.Column("cardDigits", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCreditCardBills.put("totalDue", new TableInfo.Column("totalDue", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCreditCardBills.put("minDue", new TableInfo.Column("minDue", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCreditCardBills.put("dueDate", new TableInfo.Column("dueDate", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCreditCardBills.put("isPaid", new TableInfo.Column("isPaid", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCreditCardBills.put("rawSms", new TableInfo.Column("rawSms", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCreditCardBills.put("billGeneratedDate", new TableInfo.Column("billGeneratedDate", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCreditCardBills = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesCreditCardBills = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoCreditCardBills = new TableInfo("credit_card_bills", _columnsCreditCardBills, _foreignKeysCreditCardBills, _indicesCreditCardBills);
        final TableInfo _existingCreditCardBills = TableInfo.read(db, "credit_card_bills");
        if (!_infoCreditCardBills.equals(_existingCreditCardBills)) {
          return new RoomOpenHelper.ValidationResult(false, "credit_card_bills(com.example.smsaggregator.data.CreditCardBill).\n"
                  + " Expected:\n" + _infoCreditCardBills + "\n"
                  + " Found:\n" + _existingCreditCardBills);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "5afb0f8c120889f03ac80d5c33dee9e3", "35d4cb6060553e8d188324847ed5d02d");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "transactions","merchant_overrides","budgets","user_categories","transaction_splits","subscriptions","credit_card_bills");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    final boolean _supportsDeferForeignKeys = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
    try {
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = FALSE");
      }
      super.beginTransaction();
      if (_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA defer_foreign_keys = TRUE");
      }
      _db.execSQL("DELETE FROM `transactions`");
      _db.execSQL("DELETE FROM `merchant_overrides`");
      _db.execSQL("DELETE FROM `budgets`");
      _db.execSQL("DELETE FROM `user_categories`");
      _db.execSQL("DELETE FROM `transaction_splits`");
      _db.execSQL("DELETE FROM `subscriptions`");
      _db.execSQL("DELETE FROM `credit_card_bills`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = TRUE");
      }
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(TransactionDao.class, TransactionDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(MerchantOverrideDao.class, MerchantOverrideDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(BudgetDao.class, BudgetDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(UserCategoryDao.class, UserCategoryDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(TransactionSplitDao.class, TransactionSplitDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(SubscriptionDao.class, SubscriptionDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(CreditCardBillDao.class, CreditCardBillDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public TransactionDao transactionDao() {
    if (_transactionDao != null) {
      return _transactionDao;
    } else {
      synchronized(this) {
        if(_transactionDao == null) {
          _transactionDao = new TransactionDao_Impl(this);
        }
        return _transactionDao;
      }
    }
  }

  @Override
  public MerchantOverrideDao merchantOverrideDao() {
    if (_merchantOverrideDao != null) {
      return _merchantOverrideDao;
    } else {
      synchronized(this) {
        if(_merchantOverrideDao == null) {
          _merchantOverrideDao = new MerchantOverrideDao_Impl(this);
        }
        return _merchantOverrideDao;
      }
    }
  }

  @Override
  public BudgetDao budgetDao() {
    if (_budgetDao != null) {
      return _budgetDao;
    } else {
      synchronized(this) {
        if(_budgetDao == null) {
          _budgetDao = new BudgetDao_Impl(this);
        }
        return _budgetDao;
      }
    }
  }

  @Override
  public UserCategoryDao userCategoryDao() {
    if (_userCategoryDao != null) {
      return _userCategoryDao;
    } else {
      synchronized(this) {
        if(_userCategoryDao == null) {
          _userCategoryDao = new UserCategoryDao_Impl(this);
        }
        return _userCategoryDao;
      }
    }
  }

  @Override
  public TransactionSplitDao transactionSplitDao() {
    if (_transactionSplitDao != null) {
      return _transactionSplitDao;
    } else {
      synchronized(this) {
        if(_transactionSplitDao == null) {
          _transactionSplitDao = new TransactionSplitDao_Impl(this);
        }
        return _transactionSplitDao;
      }
    }
  }

  @Override
  public SubscriptionDao subscriptionDao() {
    if (_subscriptionDao != null) {
      return _subscriptionDao;
    } else {
      synchronized(this) {
        if(_subscriptionDao == null) {
          _subscriptionDao = new SubscriptionDao_Impl(this);
        }
        return _subscriptionDao;
      }
    }
  }

  @Override
  public CreditCardBillDao creditCardBillDao() {
    if (_creditCardBillDao != null) {
      return _creditCardBillDao;
    } else {
      synchronized(this) {
        if(_creditCardBillDao == null) {
          _creditCardBillDao = new CreditCardBillDao_Impl(this);
        }
        return _creditCardBillDao;
      }
    }
  }
}
