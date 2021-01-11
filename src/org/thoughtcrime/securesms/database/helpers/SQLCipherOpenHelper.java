package org.thoughtcrime.securesms.database.helpers;


import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import androidx.annotation.NonNull;

import android.text.TextUtils;

import com.annimon.stream.Stream;
import com.bumptech.glide.Glide;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.thoughtcrime.securesms.contacts.sync.StorageSyncHelper;
import org.thoughtcrime.securesms.crypto.DatabaseSecret;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DraftDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.GroupReceiptDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.JobDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.OneTimePreKeyDatabase;
import org.thoughtcrime.securesms.database.PushDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SearchDatabase;
import org.thoughtcrime.securesms.database.SessionDatabase;
import org.thoughtcrime.securesms.database.SignedPreKeyDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.StickerDatabase;
import org.thoughtcrime.securesms.database.StorageKeyDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.RefreshPreKeysJob;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.SqlUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.io.File;
import java.util.List;

public class SQLCipherOpenHelper extends SQLiteOpenHelper {

  @SuppressWarnings("unused")
  private static final String TAG = SQLCipherOpenHelper.class.getSimpleName();

  private static final int RECIPIENT_CALL_RINGTONE_VERSION  = 2;
  private static final int MIGRATE_PREKEYS_VERSION          = 3;
  private static final int MIGRATE_SESSIONS_VERSION         = 4;
  private static final int NO_MORE_IMAGE_THUMBNAILS_VERSION = 5;
  private static final int ATTACHMENT_DIMENSIONS            = 6;
  private static final int QUOTED_REPLIES                   = 7;
  private static final int SHARED_CONTACTS                  = 8;
  private static final int FULL_TEXT_SEARCH                 = 9;
  private static final int BAD_IMPORT_CLEANUP               = 10;
  private static final int QUOTE_MISSING                    = 11;
  private static final int NOTIFICATION_CHANNELS            = 12;
  private static final int SECRET_SENDER                    = 13;
  private static final int ATTACHMENT_CAPTIONS              = 14;
  private static final int ATTACHMENT_CAPTIONS_FIX          = 15;
  private static final int PREVIEWS                         = 16;
  private static final int CONVERSATION_SEARCH              = 17;
  private static final int SELF_ATTACHMENT_CLEANUP          = 18;
  private static final int RECIPIENT_FORCE_SMS_SELECTION    = 19;
  private static final int JOBMANAGER_STRIKES_BACK          = 20;
  private static final int STICKERS                         = 21;
  private static final int REVEALABLE_MESSAGES              = 22;
  private static final int VIEW_ONCE_ONLY                   = 23;
  private static final int RECIPIENT_IDS                    = 24;
  private static final int RECIPIENT_SEARCH                 = 25;
  private static final int RECIPIENT_CLEANUP                = 26;
  private static final int MMS_RECIPIENT_CLEANUP            = 27;
  private static final int ATTACHMENT_HASHING               = 28;
  private static final int NOTIFICATION_RECIPIENT_IDS       = 29;
  private static final int BLUR_HASH                        = 30;
  private static final int MMS_RECIPIENT_CLEANUP_2          = 31;
  private static final int ATTACHMENT_TRANSFORM_PROPERTIES  = 32;
  private static final int ATTACHMENT_CLEAR_HASHES          = 33;
  private static final int ATTACHMENT_CLEAR_HASHES_2        = 34;
  private static final int UUIDS                            = 35;
  private static final int USERNAMES                        = 36;
  private static final int REACTIONS                        = 37;
  private static final int STORAGE_SERVICE                  = 38;
  private static final int REACTIONS_UNREAD_INDEX           = 39;

  private static final int    DATABASE_VERSION = 39;
  private static final String DATABASE_NAME    = "signal.db";

  private final Context        context;
  private final DatabaseSecret databaseSecret;

  public SQLCipherOpenHelper(@NonNull Context context, @NonNull DatabaseSecret databaseSecret) {
    super(context, DATABASE_NAME, null, DATABASE_VERSION, new SQLiteDatabaseHook() {
      @Override
      public void preKey(SQLiteDatabase db) {
        db.rawExecSQL("PRAGMA cipher_default_kdf_iter = 1;");
        db.rawExecSQL("PRAGMA cipher_default_page_size = 4096;");
      }

      @Override
      public void postKey(SQLiteDatabase db) {
        db.rawExecSQL("PRAGMA kdf_iter = '1';");
        db.rawExecSQL("PRAGMA cipher_page_size = 4096;");
      }
    });

    this.context        = context.getApplicationContext();
    this.databaseSecret = databaseSecret;
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(SmsDatabase.CREATE_TABLE);
    db.execSQL(MmsDatabase.CREATE_TABLE);
    db.execSQL(AttachmentDatabase.CREATE_TABLE);
    db.execSQL(ThreadDatabase.CREATE_TABLE);
    db.execSQL(IdentityDatabase.CREATE_TABLE);
    db.execSQL(DraftDatabase.CREATE_TABLE);
    db.execSQL(PushDatabase.CREATE_TABLE);
    db.execSQL(GroupDatabase.CREATE_TABLE);
    db.execSQL(RecipientDatabase.CREATE_TABLE);
    db.execSQL(GroupReceiptDatabase.CREATE_TABLE);
    db.execSQL(OneTimePreKeyDatabase.CREATE_TABLE);
    db.execSQL(SignedPreKeyDatabase.CREATE_TABLE);
    db.execSQL(SessionDatabase.CREATE_TABLE);
    db.execSQL(StickerDatabase.CREATE_TABLE);
    db.execSQL(StorageKeyDatabase.CREATE_TABLE);
    executeStatements(db, SearchDatabase.CREATE_TABLE);
    executeStatements(db, JobDatabase.CREATE_TABLE);

    executeStatements(db, RecipientDatabase.CREATE_INDEXS);
    executeStatements(db, SmsDatabase.CREATE_INDEXS);
    executeStatements(db, MmsDatabase.CREATE_INDEXS);
    executeStatements(db, AttachmentDatabase.CREATE_INDEXS);
    executeStatements(db, ThreadDatabase.CREATE_INDEXS);
    executeStatements(db, DraftDatabase.CREATE_INDEXS);
    executeStatements(db, GroupDatabase.CREATE_INDEXS);
    executeStatements(db, GroupReceiptDatabase.CREATE_INDEXES);
    executeStatements(db, StickerDatabase.CREATE_INDEXES);
    executeStatements(db, StorageKeyDatabase.CREATE_INDEXES);

    if (context.getDatabasePath(ClassicOpenHelper.NAME).exists()) {
      ClassicOpenHelper                      legacyHelper = new ClassicOpenHelper(context);
      android.database.sqlite.SQLiteDatabase legacyDb     = legacyHelper.getWritableDatabase();

      SQLCipherMigrationHelper.migratePlaintext(context, legacyDb, db);

      MasterSecret masterSecret = KeyCachingService.getMasterSecret(context);

      if (masterSecret != null) SQLCipherMigrationHelper.migrateCiphertext(context, masterSecret, legacyDb, db, null);
      else                      TextSecurePreferences.setNeedsSqlCipherMigration(context, true);

      if (!PreKeyMigrationHelper.migratePreKeys(context, db)) {
        ApplicationDependencies.getJobManager().add(new RefreshPreKeysJob());
      }

      SessionStoreMigrationHelper.migrateSessions(context, db);
      PreKeyMigrationHelper.cleanUpPreKeys(context);
    }
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    Log.i(TAG, "Upgrading database: " + oldVersion + ", " + newVersion);
    long startTime = System.currentTimeMillis();

    db.beginTransaction();

    try {

      if (oldVersion < RECIPIENT_CALL_RINGTONE_VERSION) {
        db.execSQL("ALTER TABLE recipient_preferences ADD COLUMN call_ringtone TEXT DEFAULT NULL");
        db.execSQL("ALTER TABLE recipient_preferences ADD COLUMN call_vibrate INTEGER DEFAULT " + RecipientDatabase.VibrateState.DEFAULT.getId());
      }

      if (oldVersion < MIGRATE_PREKEYS_VERSION) {
        db.execSQL("CREATE TABLE signed_prekeys (_id INTEGER PRIMARY KEY, key_id INTEGER UNIQUE, public_key TEXT NOT NULL, private_key TEXT NOT NULL, signature TEXT NOT NULL, timestamp INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE one_time_prekeys (_id INTEGER PRIMARY KEY, key_id INTEGER UNIQUE, public_key TEXT NOT NULL, private_key TEXT NOT NULL)");

        if (!PreKeyMigrationHelper.migratePreKeys(context, db)) {
          ApplicationDependencies.getJobManager().add(new RefreshPreKeysJob());
        }
      }

      if (oldVersion < MIGRATE_SESSIONS_VERSION) {
        db.execSQL("CREATE TABLE sessions (_id INTEGER PRIMARY KEY, address TEXT NOT NULL, device INTEGER NOT NULL, record BLOB NOT NULL, UNIQUE(address, device) ON CONFLICT REPLACE)");
        SessionStoreMigrationHelper.migrateSessions(context, db);
      }

      if (oldVersion < NO_MORE_IMAGE_THUMBNAILS_VERSION) {
        ContentValues update = new ContentValues();
        update.put("thumbnail", (String)null);
        update.put("aspect_ratio", (String)null);
        update.put("thumbnail_random", (String)null);

        try (Cursor cursor = db.query("part", new String[] {"_id", "ct", "thumbnail"}, "thumbnail IS NOT NULL", null, null, null, null)) {
          while (cursor != null && cursor.moveToNext()) {
            long   id          = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
            String contentType = cursor.getString(cursor.getColumnIndexOrThrow("ct"));

            if (contentType != null && !contentType.startsWith("video")) {
              String thumbnailPath = cursor.getString(cursor.getColumnIndexOrThrow("thumbnail"));
              File   thumbnailFile = new File(thumbnailPath);
              thumbnailFile.delete();

              db.update("part", update, "_id = ?", new String[] {String.valueOf(id)});
            }
          }
        }
      }

      if (oldVersion < ATTACHMENT_DIMENSIONS) {
        db.execSQL("ALTER TABLE part ADD COLUMN width INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE part ADD COLUMN height INTEGER DEFAULT 0");
      }

      if (oldVersion < QUOTED_REPLIES) {
        db.execSQL("ALTER TABLE mms ADD COLUMN quote_id INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE mms ADD COLUMN quote_author TEXT");
        db.execSQL("ALTER TABLE mms ADD COLUMN quote_body TEXT");
        db.execSQL("ALTER TABLE mms ADD COLUMN quote_attachment INTEGER DEFAULT -1");

        db.execSQL("ALTER TABLE part ADD COLUMN quote INTEGER DEFAULT 0");
      }

      if (oldVersion < SHARED_CONTACTS) {
        db.execSQL("ALTER TABLE mms ADD COLUMN shared_contacts TEXT");
      }

      if (oldVersion < FULL_TEXT_SEARCH) {
        db.execSQL("CREATE VIRTUAL TABLE sms_fts USING fts5(body, content=sms, content_rowid=_id)");
        db.execSQL("CREATE TRIGGER sms_ai AFTER INSERT ON sms BEGIN\n" +
                   "  INSERT INTO sms_fts(rowid, body) VALUES (new._id, new.body);\n" +
                   "END;");
        db.execSQL("CREATE TRIGGER sms_ad AFTER DELETE ON sms BEGIN\n" +
                   "  INSERT INTO sms_fts(sms_fts, rowid, body) VALUES('delete', old._id, old.body);\n" +
                   "END;\n");
        db.execSQL("CREATE TRIGGER sms_au AFTER UPDATE ON sms BEGIN\n" +
                   "  INSERT INTO sms_fts(sms_fts, rowid, body) VALUES('delete', old._id, old.body);\n" +
                   "  INSERT INTO sms_fts(rowid, body) VALUES(new._id, new.body);\n" +
                   "END;");

        db.execSQL("CREATE VIRTUAL TABLE mms_fts USING fts5(body, content=mms, content_rowid=_id)");
        db.execSQL("CREATE TRIGGER mms_ai AFTER INSERT ON mms BEGIN\n" +
                   "  INSERT INTO mms_fts(rowid, body) VALUES (new._id, new.body);\n" +
                   "END;");
        db.execSQL("CREATE TRIGGER mms_ad AFTER DELETE ON mms BEGIN\n" +
                   "  INSERT INTO mms_fts(mms_fts, rowid, body) VALUES('delete', old._id, old.body);\n" +
                   "END;\n");
        db.execSQL("CREATE TRIGGER mms_au AFTER UPDATE ON mms BEGIN\n" +
                   "  INSERT INTO mms_fts(mms_fts, rowid, body) VALUES('delete', old._id, old.body);\n" +
                   "  INSERT INTO mms_fts(rowid, body) VALUES(new._id, new.body);\n" +
                   "END;");

        Log.i(TAG, "Beginning to build search index.");
        long start = SystemClock.elapsedRealtime();

        db.execSQL("INSERT INTO sms_fts (rowid, body) SELECT _id, body FROM sms");

        long smsFinished = SystemClock.elapsedRealtime();
        Log.i(TAG, "Indexing SMS completed in " + (smsFinished - start) + " ms");

        db.execSQL("INSERT INTO mms_fts (rowid, body) SELECT _id, body FROM mms");

        long mmsFinished = SystemClock.elapsedRealtime();
        Log.i(TAG, "Indexing MMS completed in " + (mmsFinished - smsFinished) + " ms");
        Log.i(TAG, "Indexing finished. Total time: " + (mmsFinished - start) + " ms");
      }

      if (oldVersion < BAD_IMPORT_CLEANUP) {
        String trimmedCondition = " NOT IN (SELECT _id FROM mms)";

        db.delete("group_receipts", "mms_id" + trimmedCondition, null);

        String[] columns = new String[] { "_id", "unique_id", "_data", "thumbnail"};

        try (Cursor cursor = db.query("part", columns, "mid" + trimmedCondition, null, null, null, null)) {
          while (cursor != null && cursor.moveToNext()) {
            db.delete("part", "_id = ? AND unique_id = ?", new String[] { String.valueOf(cursor.getLong(0)), String.valueOf(cursor.getLong(1)) });

            String data      = cursor.getString(2);
            String thumbnail = cursor.getString(3);

            if (!TextUtils.isEmpty(data)) {
              new File(data).delete();
            }

            if (!TextUtils.isEmpty(thumbnail)) {
              new File(thumbnail).delete();
            }
          }
        }
      }

      // Note: This column only being checked due to upgrade issues as described in #8184
      if (oldVersion < QUOTE_MISSING && !SqlUtil.columnExists(db, "mms", "quote_missing")) {
        db.execSQL("ALTER TABLE mms ADD COLUMN quote_missing INTEGER DEFAULT 0");
      }

      // Note: The column only being checked due to upgrade issues as described in #8184
      if (oldVersion < NOTIFICATION_CHANNELS && !SqlUtil.columnExists(db, "recipient_preferences", "notification_channel")) {
        db.execSQL("ALTER TABLE recipient_preferences ADD COLUMN notification_channel TEXT DEFAULT NULL");
        NotificationChannels.create(context);

        try (Cursor cursor = db.rawQuery("SELECT recipient_ids, system_display_name, signal_profile_name, notification, vibrate FROM recipient_preferences WHERE notification NOT NULL OR vibrate != 0", null)) {
          while (cursor != null && cursor.moveToNext()) {
            String  rawAddress      = cursor.getString(cursor.getColumnIndexOrThrow("recipient_ids"));
            String  address         = PhoneNumberFormatter.get(context).format(rawAddress);
            String  systemName      = cursor.getString(cursor.getColumnIndexOrThrow("system_display_name"));
            String  profileName     = cursor.getString(cursor.getColumnIndexOrThrow("signal_profile_name"));
            String  messageSound    = cursor.getString(cursor.getColumnIndexOrThrow("notification"));
            Uri     messageSoundUri = messageSound != null ? Uri.parse(messageSound) : null;
            int     vibrateState    = cursor.getInt(cursor.getColumnIndexOrThrow("vibrate"));
            String  displayName     = NotificationChannels.getChannelDisplayNameFor(context, systemName, profileName, null, address);
            boolean vibrateEnabled  = vibrateState == 0 ? TextSecurePreferences.isNotificationVibrateEnabled(context) : vibrateState == 1;

            if (GroupUtil.isEncodedGroup(address)) {
              try(Cursor groupCursor = db.rawQuery("SELECT title FROM groups WHERE group_id = ?", new String[] { address })) {
                if (groupCursor != null && groupCursor.moveToFirst()) {
                  String title = groupCursor.getString(groupCursor.getColumnIndexOrThrow("title"));

                  if (!TextUtils.isEmpty(title)) {
                    displayName = title;
                  }
                }
              }
            }

            String channelId = NotificationChannels.createChannelFor(context, "contact_" + address + "_" + System.currentTimeMillis(), displayName, messageSoundUri, vibrateEnabled);

            ContentValues values = new ContentValues(1);
            values.put("notification_channel", channelId);
            db.update("recipient_preferences", values, "recipient_ids = ?", new String[] { rawAddress });
          }
        }
      }

      if (oldVersion < SECRET_SENDER) {
        db.execSQL("ALTER TABLE recipient_preferences ADD COLUMN unidentified_access_mode INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE push ADD COLUMN server_timestamp INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE push ADD COLUMN server_guid TEXT DEFAULT NULL");
        db.execSQL("ALTER TABLE group_receipts ADD COLUMN unidentified INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE mms ADD COLUMN unidentified INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE sms ADD COLUMN unidentified INTEGER DEFAULT 0");
      }

      if (oldVersion < ATTACHMENT_CAPTIONS) {
        db.execSQL("ALTER TABLE part ADD COLUMN caption TEXT DEFAULT NULL");
      }

      // 4.30.8 included a migration, but not a correct CREATE_TABLE statement, so we need to add
      // this column if it isn't present.
      if (oldVersion < ATTACHMENT_CAPTIONS_FIX) {
        if (!SqlUtil.columnExists(db, "part", "caption")) {
          db.execSQL("ALTER TABLE part ADD COLUMN caption TEXT DEFAULT NULL");
        }
      }

      if (oldVersion < PREVIEWS) {
        db.execSQL("ALTER TABLE mms ADD COLUMN previews TEXT");
      }

      if (oldVersion < CONVERSATION_SEARCH) {
        db.execSQL("DROP TABLE sms_fts");
        db.execSQL("DROP TABLE mms_fts");
        db.execSQL("DROP TRIGGER sms_ai");
        db.execSQL("DROP TRIGGER sms_au");
        db.execSQL("DROP TRIGGER sms_ad");
        db.execSQL("DROP TRIGGER mms_ai");
        db.execSQL("DROP TRIGGER mms_au");
        db.execSQL("DROP TRIGGER mms_ad");

        db.execSQL("CREATE VIRTUAL TABLE sms_fts USING fts5(body, thread_id UNINDEXED, content=sms, content_rowid=_id)");
        db.execSQL("CREATE TRIGGER sms_ai AFTER INSERT ON sms BEGIN\n" +
                   "  INSERT INTO sms_fts(rowid, body, thread_id) VALUES (new._id, new.body, new.thread_id);\n" +
                   "END;");
        db.execSQL("CREATE TRIGGER sms_ad AFTER DELETE ON sms BEGIN\n" +
                   "  INSERT INTO sms_fts(sms_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id);\n" +
                   "END;\n");
        db.execSQL("CREATE TRIGGER sms_au AFTER UPDATE ON sms BEGIN\n" +
                   "  INSERT INTO sms_fts(sms_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id);\n" +
                   "  INSERT INTO sms_fts(rowid, body, thread_id) VALUES(new._id, new.body, new.thread_id);\n" +
                   "END;");

        db.execSQL("CREATE VIRTUAL TABLE mms_fts USING fts5(body, thread_id UNINDEXED, content=mms, content_rowid=_id)");
        db.execSQL("CREATE TRIGGER mms_ai AFTER INSERT ON mms BEGIN\n" +
                   "  INSERT INTO mms_fts(rowid, body, thread_id) VALUES (new._id, new.body, new.thread_id);\n" +
                   "END;");
        db.execSQL("CREATE TRIGGER mms_ad AFTER DELETE ON mms BEGIN\n" +
                   "  INSERT INTO mms_fts(mms_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id);\n" +
                   "END;\n");
        db.execSQL("CREATE TRIGGER mms_au AFTER UPDATE ON mms BEGIN\n" +
                   "  INSERT INTO mms_fts(mms_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id);\n" +
                   "  INSERT INTO mms_fts(rowid, body, thread_id) VALUES(new._id, new.body, new.thread_id);\n" +
                   "END;");

        Log.i(TAG, "Beginning to build search index.");
        long start = SystemClock.elapsedRealtime();

        db.execSQL("INSERT INTO sms_fts (rowid, body, thread_id) SELECT _id, body, thread_id FROM sms");

        long smsFinished = SystemClock.elapsedRealtime();
        Log.i(TAG, "Indexing SMS completed in " + (smsFinished - start) + " ms");

        db.execSQL("INSERT INTO mms_fts (rowid, body, thread_id) SELECT _id, body, thread_id FROM mms");

        long mmsFinished = SystemClock.elapsedRealtime();
        Log.i(TAG, "Indexing MMS completed in " + (mmsFinished - smsFinished) + " ms");
        Log.i(TAG, "Indexing finished. Total time: " + (mmsFinished - start) + " ms");
      }

      if (oldVersion < SELF_ATTACHMENT_CLEANUP) {
        String localNumber = TextSecurePreferences.getLocalNumber(context);

        if (!TextUtils.isEmpty(localNumber)) {
          try (Cursor threadCursor = db.rawQuery("SELECT _id FROM thread WHERE recipient_ids = ?", new String[]{ localNumber })) {
            if (threadCursor != null && threadCursor.moveToFirst()) {
              long          threadId     = threadCursor.getLong(0);
              ContentValues updateValues = new ContentValues(1);

              updateValues.put("pending_push", 0);

              int count = db.update("part", updateValues, "mid IN (SELECT _id FROM mms WHERE thread_id = ?)", new String[]{ String.valueOf(threadId) });
              Log.i(TAG, "Updated " + count + " self-sent attachments.");
            }
          }
        }
      }

      if (oldVersion < RECIPIENT_FORCE_SMS_SELECTION) {
        db.execSQL("ALTER TABLE recipient_preferences ADD COLUMN force_sms_selection INTEGER DEFAULT 0");
      }

      if (oldVersion < JOBMANAGER_STRIKES_BACK) {
        db.execSQL("CREATE TABLE job_spec(_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                         "job_spec_id TEXT UNIQUE, " +
                                         "factory_key TEXT, " +
                                         "queue_key TEXT, " +
                                         "create_time INTEGER, " +
                                         "next_run_attempt_time INTEGER, " +
                                         "run_attempt INTEGER, " +
                                         "max_attempts INTEGER, " +
                                         "max_backoff INTEGER, " +
                                         "max_instances INTEGER, " +
                                         "lifespan INTEGER, " +
                                         "serialized_data TEXT, " +
                                         "is_running INTEGER)");

        db.execSQL("CREATE TABLE constraint_spec(_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                "job_spec_id TEXT, " +
                                                "factory_key TEXT, " +
                                                "UNIQUE(job_spec_id, factory_key))");

        db.execSQL("CREATE TABLE dependency_spec(_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                "job_spec_id TEXT, " +
                                                "depends_on_job_spec_id TEXT, " +
                                                "UNIQUE(job_spec_id, depends_on_job_spec_id))");
      }

      if (oldVersion < STICKERS) {
        db.execSQL("CREATE TABLE sticker (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                         "pack_id TEXT NOT NULL, " +
                                         "pack_key TEXT NOT NULL, " +
                                         "pack_title TEXT NOT NULL, " +
                                         "pack_author TEXT NOT NULL, " +
                                         "sticker_id INTEGER, " +
                                         "cover INTEGER, " +
                                         "emoji TEXT NOT NULL, " +
                                         "last_used INTEGER, " +
                                         "installed INTEGER," +
                                         "file_path TEXT NOT NULL, " +
                                         "file_length INTEGER, " +
                                         "file_random BLOB, " +
                                         "UNIQUE(pack_id, sticker_id, cover) ON CONFLICT IGNORE)");

        db.execSQL("CREATE INDEX IF NOT EXISTS sticker_pack_id_index ON sticker (pack_id);");
        db.execSQL("CREATE INDEX IF NOT EXISTS sticker_sticker_id_index ON sticker (sticker_id);");

        db.execSQL("ALTER TABLE part ADD COLUMN sticker_pack_id TEXT");
        db.execSQL("ALTER TABLE part ADD COLUMN sticker_pack_key TEXT");
        db.execSQL("ALTER TABLE part ADD COLUMN sticker_id INTEGER DEFAULT -1");
        db.execSQL("CREATE INDEX IF NOT EXISTS part_sticker_pack_id_index ON part (sticker_pack_id)");
      }

      if (oldVersion < REVEALABLE_MESSAGES) {
        db.execSQL("ALTER TABLE mms ADD COLUMN reveal_duration INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE mms ADD COLUMN reveal_start_time INTEGER DEFAULT 0");

        db.execSQL("ALTER TABLE thread ADD COLUMN snippet_content_type TEXT DEFAULT NULL");
        db.execSQL("ALTER TABLE thread ADD COLUMN snippet_extras TEXT DEFAULT NULL");
      }

      if (oldVersion < VIEW_ONCE_ONLY) {
        db.execSQL("UPDATE mms SET reveal_duration = 1 WHERE reveal_duration > 0");
        db.execSQL("UPDATE mms SET reveal_start_time = 0");
      }

      if (oldVersion < RECIPIENT_IDS) {
        RecipientIdMigrationHelper.execute(db);
      }

      if (oldVersion < RECIPIENT_SEARCH) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN system_phone_type INTEGER DEFAULT -1");

        String localNumber = TextSecurePreferences.getLocalNumber(context);
        if (!TextUtils.isEmpty(localNumber)) {
          try (Cursor cursor = db.query("recipient", null, "phone = ?", new String[] { localNumber }, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
              ContentValues values = new ContentValues();
              values.put("phone", localNumber);
              values.put("registered", 1);
              values.put("profile_sharing", 1);
              values.put("signal_profile_name", TextSecurePreferences.getProfileName(context));
              db.insert("recipient", null, values);
            } else {
              db.execSQL("UPDATE recipient SET registered = ?, profile_sharing = ?, signal_profile_name = ? WHERE phone = ?",
                         new String[] { "1", "1", TextSecurePreferences.getProfileName(context), localNumber });
            }
          }
        }
      }

      if (oldVersion < RECIPIENT_CLEANUP) {
        RecipientIdCleanupHelper.execute(db);
      }

      if (oldVersion < MMS_RECIPIENT_CLEANUP) {
        ContentValues values = new ContentValues(1);
        values.put("address", "-1");
        int count = db.update("mms", values, "address = ?", new String[] { "0" });
        Log.i(TAG, "MMS recipient cleanup updated " + count + " rows.");
      }

      if (oldVersion < ATTACHMENT_HASHING) {
        db.execSQL("ALTER TABLE part ADD COLUMN data_hash TEXT DEFAULT NULL");
        db.execSQL("CREATE INDEX IF NOT EXISTS part_data_hash_index ON part (data_hash)");
      }

      if (oldVersion < NOTIFICATION_RECIPIENT_IDS && Build.VERSION.SDK_INT >= 26) {
        NotificationManager       notificationManager = ServiceUtil.getNotificationManager(context);
        List<NotificationChannel> channels            = Stream.of(notificationManager.getNotificationChannels())
                                                              .filter(c -> c.getId().startsWith("contact_"))
                                                              .toList();

        Log.i(TAG, "Migrating " + channels.size() + " channels to use RecipientId's.");

        for (NotificationChannel oldChannel : channels) {
          notificationManager.deleteNotificationChannel(oldChannel.getId());

          int    startIndex = "contact_".length();
          int    endIndex   = oldChannel.getId().lastIndexOf("_");
          String address    = oldChannel.getId().substring(startIndex, endIndex);

          String recipientId;

          try (Cursor cursor = db.query("recipient", new String[] { "_id" }, "phone = ? OR email = ? OR group_id = ?", new String[] { address, address, address}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
              recipientId = cursor.getString(cursor.getColumnIndexOrThrow("_id"));
            } else {
              Log.w(TAG, "Couldn't find recipient for address: " + address);
              continue;
            }
          }

          String              newId      = "contact_" + recipientId + "_" + System.currentTimeMillis();
          NotificationChannel newChannel = new NotificationChannel(newId, oldChannel.getName(), oldChannel.getImportance());

          Log.i(TAG, "Updating channel ID from '" + oldChannel.getId() + "' to '" + newChannel.getId() + "'.");

          newChannel.setGroup(oldChannel.getGroup());
          newChannel.setSound(oldChannel.getSound(), oldChannel.getAudioAttributes());
          newChannel.setBypassDnd(oldChannel.canBypassDnd());
          newChannel.enableVibration(oldChannel.shouldVibrate());
          newChannel.setVibrationPattern(oldChannel.getVibrationPattern());
          newChannel.setLockscreenVisibility(oldChannel.getLockscreenVisibility());
          newChannel.setShowBadge(oldChannel.canShowBadge());
          newChannel.setLightColor(oldChannel.getLightColor());
          newChannel.enableLights(oldChannel.shouldShowLights());

          notificationManager.createNotificationChannel(newChannel);

          ContentValues contentValues = new ContentValues(1);
          contentValues.put("notification_channel", newChannel.getId());
          db.update("recipient", contentValues, "_id = ?", new String[] { recipientId });
        }
      }

      if (oldVersion < BLUR_HASH) {
        db.execSQL("ALTER TABLE part ADD COLUMN blur_hash TEXT DEFAULT NULL");
      }

      if (oldVersion < MMS_RECIPIENT_CLEANUP_2) {
        ContentValues values = new ContentValues(1);
        values.put("address", "-1");
        int count = db.update("mms", values, "address = ? OR address IS NULL", new String[] { "0" });
        Log.i(TAG, "MMS recipient cleanup 2 updated " + count + " rows.");
      }

      if (oldVersion < ATTACHMENT_TRANSFORM_PROPERTIES) {
        db.execSQL("ALTER TABLE part ADD COLUMN transform_properties TEXT DEFAULT NULL");
      }

      if (oldVersion < ATTACHMENT_CLEAR_HASHES) {
        db.execSQL("UPDATE part SET data_hash = null");
      }

      if (oldVersion < ATTACHMENT_CLEAR_HASHES_2) {
        db.execSQL("UPDATE part SET data_hash = null");
        Glide.get(context).clearDiskCache();
      }

      if (oldVersion < UUIDS) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN uuid_supported INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE push ADD COLUMN source_uuid TEXT DEFAULT NULL");
      }

      if (oldVersion < USERNAMES) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN username TEXT DEFAULT NULL");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS recipient_username_index ON recipient (username)");
      }

      if (oldVersion < REACTIONS) {
        db.execSQL("ALTER TABLE sms ADD COLUMN reactions BLOB DEFAULT NULL");
        db.execSQL("ALTER TABLE mms ADD COLUMN reactions BLOB DEFAULT NULL");

        db.execSQL("ALTER TABLE sms ADD COLUMN reactions_unread INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE mms ADD COLUMN reactions_unread INTEGER DEFAULT 0");

        db.execSQL("ALTER TABLE sms ADD COLUMN reactions_last_seen INTEGER DEFAULT -1");
        db.execSQL("ALTER TABLE mms ADD COLUMN reactions_last_seen INTEGER DEFAULT -1");
      }

      if (oldVersion < STORAGE_SERVICE) {
        db.execSQL("CREATE TABLE storage_key (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                             "type INTEGER, " +
                                             "key TEXT UNIQUE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS storage_key_type_index ON storage_key (type)");

        db.execSQL("ALTER TABLE recipient ADD COLUMN system_info_pending INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE recipient ADD COLUMN storage_service_key TEXT DEFAULT NULL");
        db.execSQL("ALTER TABLE recipient ADD COLUMN dirty INTEGER DEFAULT 0");

        db.execSQL("CREATE UNIQUE INDEX recipient_storage_service_key ON recipient (storage_service_key)");
        db.execSQL("CREATE INDEX recipient_dirty_index ON recipient (dirty)");

        // TODO [greyson] Do this in a future DB migration
//        db.execSQL("UPDATE recipient SET dirty = 2 WHERE registered = 1");
//
//        try (Cursor cursor = db.rawQuery("SELECT _id FROM recipient WHERE registered = 1", null)) {
//          while (cursor != null && cursor.moveToNext()) {
//            String        id     = cursor.getString(cursor.getColumnIndexOrThrow("_id"));
//            ContentValues values = new ContentValues(1);
//
//            values.put("storage_service_key", Base64.encodeBytes(StorageSyncHelper.generateKey()));
//
//            db.update("recipient", values, "_id = ?", new String[] { id });
//          }
//        }
      }

      if (oldVersion < REACTIONS_UNREAD_INDEX) {
        db.execSQL("CREATE INDEX IF NOT EXISTS sms_reactions_unread_index ON sms (reactions_unread);");
        db.execSQL("CREATE INDEX IF NOT EXISTS mms_reactions_unread_index ON mms (reactions_unread);");
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    if (oldVersion < MIGRATE_PREKEYS_VERSION) {
      PreKeyMigrationHelper.cleanUpPreKeys(context);
    }

    Log.i(TAG, "Upgrade complete. Took " + (System.currentTimeMillis() - startTime) + " ms.");
  }

  public SQLiteDatabase getReadableDatabase() {
    return getReadableDatabase(databaseSecret.asString());
  }

  public SQLiteDatabase getWritableDatabase() {
    return getWritableDatabase(databaseSecret.asString());
  }

  public void markCurrent(SQLiteDatabase db) {
    db.setVersion(DATABASE_VERSION);
  }

  public static boolean databaseFileExists(@NonNull Context context) {
    return context.getDatabasePath(DATABASE_NAME).exists();
  }

  private void executeStatements(SQLiteDatabase db, String[] statements) {
    for (String statement : statements)
      db.execSQL(statement);
  }
}
