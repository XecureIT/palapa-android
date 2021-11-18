package org.thoughtcrime.securesms.migrations;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class PinToSignatureMigrationJob extends MigrationJob {

  public static final String KEY = "PinToSignatureMigrationJob";

  private static final String TAG = Log.tag(PinToSignatureMigrationJob.class);

  PinToSignatureMigrationJob() {
    this(new Parameters.Builder().build());
  }

  private PinToSignatureMigrationJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public boolean isUiBlocking() {
    return false;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void performMigration() {
    if (FeatureFlags.KBS) {
      Log.i(TAG, "Not migrating pin to signature, use KBS instead");
      return;
    }

    if (!TextSecurePreferences.isRegistrationLockEnabled(context)) {
      Log.i(TAG, "Registration lock disabled");
      return;
    }

    if (!TextSecurePreferences.hasOldRegistrationLockPin(context)) {
      Log.i(TAG, "No old pin to migrate");
      return;
    }

    //noinspection deprecation Only acceptable place to read the old pin.
    String registrationLockPin = TextSecurePreferences.getDeprecatedRegistrationLockPin(context);

    if (registrationLockPin == null | TextUtils.isEmpty(registrationLockPin)) {
      Log.i(TAG, "No old pin to migrate");
      return;
    }

    Log.i(TAG, "Migrating pin to signature");

    TextSecurePreferences.setDeprecatedRegistrationLockPin(context, registrationLockPin);
    ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());

    Log.i(TAG, "Pin migrated to signature");
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return false;
  }

  public static class Factory implements Job.Factory<PinToSignatureMigrationJob> {
    @Override
    public @NonNull PinToSignatureMigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new PinToSignatureMigrationJob(parameters);
    }
  }
}
