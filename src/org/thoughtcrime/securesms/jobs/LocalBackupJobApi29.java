package org.thoughtcrime.securesms.jobs;


import android.net.Uri;
import androidx.annotation.NonNull;

import androidx.documentfile.provider.DocumentFile;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.backup.BackupPassphrase;
import org.thoughtcrime.securesms.backup.FullBackupExporter;
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.NoExternalStorageException;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.service.GenericForegroundService;
import org.thoughtcrime.securesms.service.NotificationController;
import org.thoughtcrime.securesms.util.BackupUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class LocalBackupJobApi29 extends BaseJob {

  public static final String KEY = "LocalBackupJobApi29";

  private static final String TAG = LocalBackupJobApi29.class.getSimpleName();

  public static final String TEMP_BACKUP_FILE_PREFIX = ".backup";
  public static final String TEMP_BACKUP_FILE_SUFFIX = ".tmp";

  LocalBackupJobApi29(@NonNull Job.Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws NoExternalStorageException, IOException {
    Log.i(TAG, "Executing backup job...");

    if (!BackupUtil.isUserSelectionRequired(context)) {
      throw new IOException("Wrong backup job!");
    }

    Uri backupDirectoryUri = TextSecurePreferences.getBackupDirectory(context);
    if (backupDirectoryUri == null || backupDirectoryUri.getPath() == null) {
      throw new IOException("Backup Directory has not been selected!");
    }

    try (NotificationController notification = GenericForegroundService.startForegroundTask(context,
            context.getString(R.string.LocalBackupJob_creating_backup),
            NotificationChannels.BACKUPS,
            R.drawable.ic_signal_backup))
    {
      notification.setIndeterminateProgress();

      String       backupPassword  = BackupPassphrase.get(context);
      DocumentFile backupDirectory = DocumentFile.fromTreeUri(context, backupDirectoryUri);
      String       timestamp       = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(new Date());
      String       fileName        = String.format(BuildConfig.SIGNAL_FILENAME_PREFIX + "-%s.backup", timestamp);

      if (backupDirectory == null || !backupDirectory.canWrite()) {
        throw new IOException("Cannot write to backup directory location.");
      }

      deleteOldTemporaryBackups(backupDirectory);

      if (backupDirectory.findFile(fileName) != null) {
        throw new IOException("Backup file already exists!");
      }

      String       temporaryName = String.format(Locale.US, "%s%s%s", TEMP_BACKUP_FILE_PREFIX, UUID.randomUUID(), TEMP_BACKUP_FILE_SUFFIX);
      DocumentFile temporaryFile = backupDirectory.createFile("application/octet-stream", temporaryName);

      if (temporaryFile == null) {
        throw new IOException("Failed to create temporary backup file.");
      }

      if (backupPassword == null) {
        throw new IOException("Backup password is null");
      }

      FullBackupExporter.export(context,
              AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret(),
              DatabaseFactory.getBackupDatabase(context),
              temporaryFile,
              backupPassword);

      if (!temporaryFile.renameTo(fileName)) {
        DocumentFile fileToCleanUp = backupDirectory.findFile(temporaryName);
        if (fileToCleanUp != null) {
          fileToCleanUp.delete();
        }
        throw new IOException("Renaming temporary backup file failed!");
      }

      BackupUtil.deleteOldBackups();
    }
  }

  private static void deleteOldTemporaryBackups(@NonNull DocumentFile backupDirectory) {
    for (DocumentFile file : backupDirectory.listFiles()) {
      if (file.isFile()) {
        String name = file.getName();
        if (name != null && name.startsWith(TEMP_BACKUP_FILE_PREFIX) && name.endsWith(TEMP_BACKUP_FILE_SUFFIX)) {
          if (file.delete()) {
            Log.w(TAG, "Deleted old temporary backup file");
          } else {
            Log.w(TAG, "Could not delete old temporary backup file");
          }
        }
      }
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return false;
  }

  @Override
  public void onCanceled() {
  }

  public static class Factory implements Job.Factory<LocalBackupJobApi29> {
    @Override
    public @NonNull LocalBackupJobApi29 create(@NonNull Parameters parameters, @NonNull Data data) {
      return new LocalBackupJobApi29(parameters);
    }
  }
}
