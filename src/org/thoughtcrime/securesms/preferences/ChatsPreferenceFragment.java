package org.thoughtcrime.securesms.preferences;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.backup.BackupDialog;
import org.thoughtcrime.securesms.backup.FullBackupBase.BackupEvent;
import org.thoughtcrime.securesms.components.SwitchPreferenceCompat;
import org.thoughtcrime.securesms.database.NoExternalStorageException;
import org.thoughtcrime.securesms.jobs.LocalBackupJob;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.preferences.widgets.ProgressPreference;
import org.thoughtcrime.securesms.util.BackupUtil;
import org.thoughtcrime.securesms.util.StorageUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.*;

public class ChatsPreferenceFragment extends ListSummaryPreferenceFragment {

  private static final String TAG = ChatsPreferenceFragment.class.getSimpleName();

  private static final short CHOOSE_BACKUPS_LOCATION_REQUEST_CODE = 26212;

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    findPreference(TextSecurePreferences.MEDIA_DOWNLOAD_MOBILE_PREF)
        .setOnPreferenceChangeListener(new MediaDownloadChangeListener());
    findPreference(TextSecurePreferences.MEDIA_DOWNLOAD_WIFI_PREF)
        .setOnPreferenceChangeListener(new MediaDownloadChangeListener());
    findPreference(TextSecurePreferences.MEDIA_DOWNLOAD_ROAMING_PREF)
        .setOnPreferenceChangeListener(new MediaDownloadChangeListener());
    findPreference(TextSecurePreferences.MESSAGE_BODY_TEXT_SIZE_PREF)
        .setOnPreferenceChangeListener(new ListSummaryListener());

    findPreference(TextSecurePreferences.BACKUP_ENABLED)
        .setOnPreferenceClickListener(new BackupClickListener());
    findPreference(TextSecurePreferences.BACKUP_NOW)
        .setOnPreferenceClickListener(new BackupCreateListener());

    initializeListSummary((ListPreference) findPreference(TextSecurePreferences.MESSAGE_BODY_TEXT_SIZE_PREF));

    EventBus.getDefault().register(this);
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_chats);
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity)getActivity()).getSupportActionBar().setTitle(R.string.preferences__chats);
    setMediaDownloadSummaries();
    setBackupStatus();
    setBackupSummary();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    EventBus.getDefault().unregister(this);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (Build.VERSION.SDK_INT >= 29                             &&
            requestCode == CHOOSE_BACKUPS_LOCATION_REQUEST_CODE &&
            resultCode == Activity.RESULT_OK                    &&
            data != null                                        &&
            data.getData() != null)
    {
      BackupDialog.showEnableBackupDialog(requireContext(),
              data,
              this::setBackupsEnabled);
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onEvent(BackupEvent event) {
    ProgressPreference preference = (ProgressPreference)findPreference(TextSecurePreferences.BACKUP_NOW);

    if (event.getType() == BackupEvent.Type.PROGRESS) {
      preference.setEnabled(false);
      preference.setSummary(getString(R.string.ChatsPreferenceFragment_in_progress));
      preference.setProgress(event.getCount());
    } else if (event.getType() == BackupEvent.Type.FINISHED) {
      preference.setEnabled(true);
      preference.setProgressVisible(false);
      setBackupSummary();
    }
  }

  private void setBackupStatus() {
    if (TextSecurePreferences.isBackupEnabled(requireContext())) {
      if (BackupUtil.canUserAccessBackupDirectory(requireContext())) {
        setBackupsEnabled();
      } else {
        Log.w(TAG, "Cannot access backup directory. Disabling backups.");

        BackupUtil.disableBackups(requireContext());
        setBackupsDisabled();
      }
    } else {
      setBackupsDisabled();
    }
  }

  private void setBackupSummary() {
    findPreference(TextSecurePreferences.BACKUP_NOW)
        .setSummary(String.format(getString(R.string.ChatsPreferenceFragment_last_backup_s), BackupUtil.getLastBackupTime(getContext(), Locale.getDefault())));
  }

  private void setBackupFolderName() {
    if (BackupUtil.canUserAccessBackupDirectory(requireContext())) {
      if (BackupUtil.isUserSelectionRequired(requireContext()) &&
              BackupUtil.canUserAccessBackupDirectory(requireContext()))
      {
        Uri backupUri = Objects.requireNonNull(TextSecurePreferences.getBackupDirectory(requireContext()));
        findPreference(TextSecurePreferences.BACKUP_DIRECTORY).setSummary(StorageUtil.getDisplayPath(requireContext(), backupUri));
      } else if (StorageUtil.canWriteInSignalStorageDir()) {
        try {
          findPreference(TextSecurePreferences.BACKUP_DIRECTORY).setSummary(StorageUtil.getOrCreateBackupDirectory().getPath());
        } catch (NoExternalStorageException e) {
          Log.w(TAG, "Could not display folder name.", e);
        }
      }
    }
  }

  private void setMediaDownloadSummaries() {
    findPreference(TextSecurePreferences.MEDIA_DOWNLOAD_MOBILE_PREF)
        .setSummary(getSummaryForMediaPreference(TextSecurePreferences.getMobileMediaDownloadAllowed(getActivity())));
    findPreference(TextSecurePreferences.MEDIA_DOWNLOAD_WIFI_PREF)
        .setSummary(getSummaryForMediaPreference(TextSecurePreferences.getWifiMediaDownloadAllowed(getActivity())));
    findPreference(TextSecurePreferences.MEDIA_DOWNLOAD_ROAMING_PREF)
        .setSummary(getSummaryForMediaPreference(TextSecurePreferences.getRoamingMediaDownloadAllowed(getActivity())));
  }

  private CharSequence getSummaryForMediaPreference(Set<String> allowedNetworks) {
    String[]     keys      = getResources().getStringArray(R.array.pref_media_download_entries);
    String[]     values    = getResources().getStringArray(R.array.pref_media_download_values);
    List<String> outValues = new ArrayList<>(allowedNetworks.size());

    for (int i=0; i < keys.length; i++) {
      if (allowedNetworks.contains(keys[i])) outValues.add(values[i]);
    }

    return outValues.isEmpty() ? getResources().getString(R.string.preferences__none)
                               : TextUtils.join(", ", outValues);
  }

  private class BackupClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      if (BackupUtil.isUserSelectionRequired(requireContext())) {
        onBackupClickedApi29();
      } else {
        onBackupClickedLegacy();
      }

      return true;
    }
  }

  @RequiresApi(29)
  private void onBackupClickedApi29() {
    if (!TextSecurePreferences.isBackupEnabled(requireContext())) {
      BackupDialog.showChooseBackupLocationDialog(this, CHOOSE_BACKUPS_LOCATION_REQUEST_CODE);
    } else {
      BackupDialog.showDisableBackupDialog(requireContext(), this::setBackupsDisabled);
    }
  }

  private void onBackupClickedLegacy() {
    Permissions.with(ChatsPreferenceFragment.this)
            .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            .ifNecessary()
            .onAllGranted(() -> {
              if (!TextSecurePreferences.isBackupEnabled(requireContext())) {
                BackupDialog.showEnableBackupDialog(getActivity(), null, this::setBackupsEnabled);
              } else {
                BackupDialog.showDisableBackupDialog(getActivity(), this::setBackupsDisabled);
              }
            })
            .withPermanentDenialDialog(getString(R.string.ChatsPreferenceFragment_signal_requires_external_storage_permission_in_order_to_create_backups))
            .execute();
  }

  private class BackupCreateListener implements Preference.OnPreferenceClickListener {
    @SuppressLint("StaticFieldLeak")
    @Override
    public boolean onPreferenceClick(Preference preference) {
      if (BackupUtil.isUserSelectionRequired(requireContext())) {
        onBackupCreateApi29();
      } else {
        onBackupCreateLegacy();
      }
      return true;
    }
  }

  @RequiresApi(29)
  private void onBackupCreateApi29() {
    Log.i(TAG, "Queing backup...");
    LocalBackupJob.enqueue();
  }

  private void onBackupCreateLegacy() {
    Permissions.with(ChatsPreferenceFragment.this)
               .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
               .ifNecessary()
               .onAllGranted(() -> {
                 Log.i(TAG, "Queing backup...");
                 LocalBackupJob.enqueue();
               })
              .withPermanentDenialDialog(getString(R.string.ChatsPreferenceFragment_signal_requires_external_storage_permission_in_order_to_create_backups))
              .execute();
  }

  private class MediaDownloadChangeListener implements Preference.OnPreferenceChangeListener {
    @SuppressWarnings("unchecked")
    @Override public boolean onPreferenceChange(Preference preference, Object newValue) {
      Log.i(TAG, "onPreferenceChange");
      preference.setSummary(getSummaryForMediaPreference((Set<String>)newValue));
      return true;
    }
  }

  public static CharSequence getSummary(Context context) {
    return null;
  }

  private void setBackupsEnabled() {
    ((SwitchPreferenceCompat) findPreference(TextSecurePreferences.BACKUP_ENABLED)).setChecked(true);
    setBackupFolderName();
  }

  private void setBackupsDisabled() {
    ((SwitchPreferenceCompat) findPreference(TextSecurePreferences.BACKUP_ENABLED)).setChecked(false);
    findPreference(TextSecurePreferences.BACKUP_DIRECTORY).setSummary("");
  }
}
