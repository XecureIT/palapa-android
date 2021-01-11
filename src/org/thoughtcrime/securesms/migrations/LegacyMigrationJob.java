package org.thoughtcrime.securesms.migrations;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.contacts.avatars.ContactColorsLegacy;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase.Reader;
import org.thoughtcrime.securesms.database.PushDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob;
import org.thoughtcrime.securesms.jobs.CreateSignedPreKeyJob;
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob;
import org.thoughtcrime.securesms.jobs.PushDecryptJob;
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.util.FileUtils;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.VersionTracker;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Represents all of the migrations that used to take place in {@link ApplicationMigrationActivity}
 * (previously known as DatabaseUpgradeActivity). This job should *never* have new versions or
 * migrations added to it. Instead, create a new {@link MigrationJob} and place it in
 * {@link ApplicationMigrations}.
 */
public class LegacyMigrationJob extends MigrationJob {
  
  public static final String KEY = "LegacyMigrationJob";
  
  private static final String TAG = Log.tag(LegacyMigrationJob.class);

  public  static final int NO_MORE_KEY_EXCHANGE_PREFIX_VERSION  = 46;
  public  static final int MMS_BODY_VERSION                     = 46;
  public  static final int TOFU_IDENTITIES_VERSION              = 50;
  private static final int CURVE25519_VERSION                   = 63;
  public  static final int ASYMMETRIC_MASTER_SECRET_FIX_VERSION = 73;
  private static final int NO_V1_VERSION                        = 83;
  private static final int SIGNED_PREKEY_VERSION                = 83;
  private static final int NO_DECRYPT_QUEUE_VERSION             = 113;
  private static final int PUSH_DECRYPT_SERIAL_ID_VERSION       = 131;
  private static final int MIGRATE_SESSION_PLAINTEXT            = 136;
  private static final int CONTACTS_ACCOUNT_VERSION             = 136;
  private static final int MEDIA_DOWNLOAD_CONTROLS_VERSION      = 151;
  private static final int REDPHONE_SUPPORT_VERSION             = 157;
  private static final int NO_MORE_CANONICAL_DB_VERSION         = 276;
  private static final int PROFILES                             = 289;
  private static final int SCREENSHOTS                          = 300;
  private static final int PERSISTENT_BLOBS                     = 317;
  private static final int INTERNALIZE_CONTACTS                 = 317;
  public  static final int SQLCIPHER                            = 334;
  private static final int SQLCIPHER_COMPLETE                   = 352;
  private static final int REMOVE_JOURNAL                       = 353;
  private static final int REMOVE_CACHE                         = 354;
  private static final int FULL_TEXT_SEARCH                     = 358;
  private static final int BAD_IMPORT_CLEANUP                   = 373;
  private static final int IMAGE_CACHE_CLEANUP                  = 406;
  private static final int WORKMANAGER_MIGRATION                = 408;
  private static final int COLOR_MIGRATION                      = 412;
  private static final int UNIDENTIFIED_DELIVERY                = 422;
  private static final int SIGNALING_KEY_DEPRECATION            = 447;
  private static final int CONVERSATION_SEARCH                  = 455;


  public LegacyMigrationJob() {
    this(new Parameters.Builder().build());
  }
  
  private LegacyMigrationJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public boolean isUiBlocking() {
    return true;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }
  
  @Override
  void performMigration() throws RetryLaterException {
    Log.i("DatabaseUpgradeActivity", "Running background upgrade..");
    int          lastSeenVersion = VersionTracker.getLastSeenVersion(context);
    MasterSecret masterSecret    = KeyCachingService.getMasterSecret(context);
    
    if (lastSeenVersion < SQLCIPHER && masterSecret != null) {
      DatabaseFactory.getInstance(context).onApplicationLevelUpgrade(context, masterSecret, lastSeenVersion, (progress, total) -> {
        Log.i(TAG, "onApplicationLevelUpgrade: " + progress + "/" + total);
      });
    } else if (lastSeenVersion < SQLCIPHER) {
      throw new RetryLaterException();
    }

    if (lastSeenVersion < CURVE25519_VERSION) {
      IdentityKeyUtil.migrateIdentityKeys(context, masterSecret);
    }

    if (lastSeenVersion < NO_V1_VERSION) {
      File v1sessions = new File(context.getFilesDir(), "sessions");

      if (v1sessions.exists() && v1sessions.isDirectory()) {
        File[] contents = v1sessions.listFiles();

        if (contents != null) {
          for (File session : contents) {
            session.delete();
          }
        }

        v1sessions.delete();
      }
    }

    if (lastSeenVersion < SIGNED_PREKEY_VERSION) {
      ApplicationDependencies.getJobManager().add(new CreateSignedPreKeyJob(context));
    }

    if (lastSeenVersion < NO_DECRYPT_QUEUE_VERSION) {
      scheduleMessagesInPushDatabase(context);
    }

    if (lastSeenVersion < PUSH_DECRYPT_SERIAL_ID_VERSION) {
      scheduleMessagesInPushDatabase(context);
    }

    if (lastSeenVersion < MIGRATE_SESSION_PLAINTEXT) {
//        new TextSecureSessionStore(context, masterSecret).migrateSessions();
//        new TextSecurePreKeyStore(context, masterSecret).migrateRecords();

      IdentityKeyUtil.migrateIdentityKeys(context, masterSecret);
      scheduleMessagesInPushDatabase(context);;
    }

    if (lastSeenVersion < CONTACTS_ACCOUNT_VERSION) {
      ApplicationDependencies.getJobManager().add(new DirectoryRefreshJob(false));
    }

    if (lastSeenVersion < MEDIA_DOWNLOAD_CONTROLS_VERSION) {
      schedulePendingIncomingParts(context);
    }

    if (lastSeenVersion < REDPHONE_SUPPORT_VERSION) {
      ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
      ApplicationDependencies.getJobManager().add(new DirectoryRefreshJob(false));
    }

    if (lastSeenVersion < PROFILES) {
      ApplicationDependencies.getJobManager().add(new DirectoryRefreshJob(false));
    }

    if (lastSeenVersion < SCREENSHOTS) {
      boolean screenSecurity = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(TextSecurePreferences.SCREEN_SECURITY_PREF, true);
      TextSecurePreferences.setScreenSecurityEnabled(context, screenSecurity);
    }

    if (lastSeenVersion < PERSISTENT_BLOBS) {
      File externalDir = context.getExternalFilesDir(null);

      if (externalDir != null && externalDir.isDirectory() && externalDir.exists()) {
        for (File blob : externalDir.listFiles()) {
          if (blob.exists() && blob.isFile()) blob.delete();
        }
      }
    }

    if (lastSeenVersion < INTERNALIZE_CONTACTS) {
      if (TextSecurePreferences.isPushRegistered(context)) {
        TextSecurePreferences.setHasSuccessfullyRetrievedDirectory(context, true);
      }
    }

    if (lastSeenVersion < SQLCIPHER) {
      scheduleMessagesInPushDatabase(context);
    }

    if (lastSeenVersion < SQLCIPHER_COMPLETE) {
      File file = context.getDatabasePath("messages.db");
      if (file != null && file.exists()) file.delete();
    }

    if (lastSeenVersion < REMOVE_JOURNAL) {
      File file = context.getDatabasePath("messages.db-journal");
      if (file != null && file.exists()) file.delete();
    }

    if (lastSeenVersion < REMOVE_CACHE) {
      try {
        FileUtils.deleteDirectoryContents(context.getCacheDir());
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    }

    if (lastSeenVersion < IMAGE_CACHE_CLEANUP) {
      try {
        FileUtils.deleteDirectoryContents(context.getExternalCacheDir());
        GlideApp.get(context).clearDiskCache();
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    }

    // This migration became unnecessary after switching away from WorkManager
//      if (lastSeenVersion < WORKMANAGER_MIGRATION) {
//        Log.i(TAG, "Beginning migration of existing jobs to WorkManager");
//
//        JobManager        jobManager = ApplicationContext.getInstance(getApplicationContext()).getJobManager();
//        PersistentStorage storage    = new PersistentStorage(getApplicationContext(), "TextSecureJobs", new JavaJobSerializer());
//
//        for (Job job : storage.getAllUnencrypted()) {
//          jobManager.add(job);
//          Log.i(TAG, "Migrated job with class '" + job.getClass().getSimpleName() + "' to run on new JobManager.");
//        }
//      }

    if (lastSeenVersion < COLOR_MIGRATION) {
      long startTime = System.currentTimeMillis();
      DatabaseFactory.getRecipientDatabase(context).updateSystemContactColors((name, color) -> {
        if (color != null) {
          try {
            return MaterialColor.fromSerialized(color);
          } catch (MaterialColor.UnknownColorException e) {
            Log.w(TAG, "Encountered an unknown color during legacy color migration.", e);
            return ContactColorsLegacy.generateFor(name);
          }
        }
        return ContactColorsLegacy.generateFor(name);
      });
      Log.i(TAG, "Color migration took " + (System.currentTimeMillis() - startTime) + " ms");
    }

    if (lastSeenVersion < UNIDENTIFIED_DELIVERY) {
      if (TextSecurePreferences.isMultiDevice(context)) {
        Log.i(TAG, "MultiDevice: Disabling UD (will be re-enabled if possible after pending refresh).");
        TextSecurePreferences.setIsUnidentifiedDeliveryEnabled(context, false);
      }

      Log.i(TAG, "Scheduling UD attributes refresh.");
      ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
    }

    if (lastSeenVersion < SIGNALING_KEY_DEPRECATION) {
      Log.i(TAG, "Scheduling a RefreshAttributesJob to remove the signaling key remotely.");
      ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
    }
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return e instanceof RetryLaterException;
  }

  private void schedulePendingIncomingParts(Context context) {
    final AttachmentDatabase       attachmentDb       = DatabaseFactory.getAttachmentDatabase(context);
    final MmsDatabase              mmsDb              = DatabaseFactory.getMmsDatabase(context);
    final List<DatabaseAttachment> pendingAttachments = DatabaseFactory.getAttachmentDatabase(context).getPendingAttachments();

    Log.i(TAG, pendingAttachments.size() + " pending parts.");
    for (DatabaseAttachment attachment : pendingAttachments) {
      final Reader        reader = mmsDb.readerFor(mmsDb.getMessage(attachment.getMmsId()));
      final MessageRecord record = reader.getNext();

      if (attachment.hasData()) {
        Log.i(TAG, "corrected a pending media part " + attachment.getAttachmentId() + "that already had data.");
        attachmentDb.setTransferState(attachment.getMmsId(), attachment.getAttachmentId(), AttachmentDatabase.TRANSFER_PROGRESS_DONE);
      } else if (record != null && !record.isOutgoing() && record.isPush()) {
        Log.i(TAG, "queuing new attachment download job for incoming push part " + attachment.getAttachmentId() + ".");
        ApplicationDependencies.getJobManager().add(new AttachmentDownloadJob(attachment.getMmsId(), attachment.getAttachmentId(), false));
      }
      reader.close();
    }
  }

  private void scheduleMessagesInPushDatabase(Context context) {
    PushDatabase pushDatabase = DatabaseFactory.getPushDatabase(context);
    Cursor       pushReader   = null;

    try {
      pushReader = pushDatabase.getPending();

      while (pushReader != null && pushReader.moveToNext()) {
        ApplicationDependencies.getJobManager().add(new PushDecryptJob(context,
                                                                       pushReader.getLong(pushReader.getColumnIndexOrThrow(PushDatabase.ID))));
      }
    } finally {
      if (pushReader != null)
        pushReader.close();
    }
  }

  public interface DatabaseUpgradeListener {
    void setProgress(int progress, int total);
  }

  public static final class Factory implements Job.Factory<LegacyMigrationJob> {
    @Override
    public @NonNull LegacyMigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new LegacyMigrationJob(parameters);
    }
  }
}
