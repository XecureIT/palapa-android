package org.thoughtcrime.securesms.jobs;


import androidx.annotation.NonNull;
import android.text.TextUtils;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class RetrieveProfileAvatarJob extends BaseJob {

  public static final String KEY = "RetrieveProfileAvatarJob";

  private static final String TAG = RetrieveProfileAvatarJob.class.getSimpleName();

  private static final int MAX_PROFILE_SIZE_BYTES = 20 * 1024 * 1024;

  private static final String KEY_PROFILE_AVATAR = "profile_avatar";
  private static final String KEY_RECIPIENT      = "recipient";

  private String    profileAvatar;
  private Recipient recipient;

  public RetrieveProfileAvatarJob(Recipient recipient, String profileAvatar) {
    this(new Job.Parameters.Builder()
                           .setQueue("RetrieveProfileAvatarJob::" + recipient.getId().toQueueKey())
                           .addConstraint(NetworkConstraint.KEY)
                           .setLifespan(TimeUnit.HOURS.toMillis(1))
                           .setMaxInstances(1)
                           .build(),
        recipient,
        profileAvatar);
  }

  private RetrieveProfileAvatarJob(@NonNull Job.Parameters parameters, @NonNull Recipient recipient, String profileAvatar) {
    super(parameters);

    this.recipient     = recipient;
    this.profileAvatar = profileAvatar;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putString(KEY_PROFILE_AVATAR, profileAvatar)
                             .putString(KEY_RECIPIENT, recipient.getId().serialize())
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException {
    RecipientDatabase database   = DatabaseFactory.getRecipientDatabase(context);
    byte[]            profileKey = recipient.resolve().getProfileKey();

    if (profileKey == null) {
      Log.w(TAG, "Recipient profile key is gone!");
      return;
    }

    if (Util.equals(profileAvatar, recipient.resolve().getProfileAvatar())) {
      Log.w(TAG, "Already retrieved profile avatar: " + profileAvatar);
      return;
    }

    if (TextUtils.isEmpty(profileAvatar)) {
      Log.w(TAG, "Removing profile avatar (no url) for: " + recipient.getId().serialize());
      AvatarHelper.delete(context, recipient.getId());
      database.setProfileAvatar(recipient.getId(), profileAvatar);
      return;
    }

    File downloadDestination = File.createTempFile("avatar", "jpg", context.getCacheDir());

    try {
      SignalServiceMessageReceiver receiver           = ApplicationDependencies.getSignalServiceMessageReceiver();
      InputStream                  avatarStream       = receiver.retrieveProfileAvatar(profileAvatar, downloadDestination, profileKey, MAX_PROFILE_SIZE_BYTES);
      File                         decryptDestination = File.createTempFile("avatar", "jpg", context.getCacheDir());

      try {
        Util.copy(avatarStream, new FileOutputStream(decryptDestination));
      } catch (AssertionError e) {
        throw new IOException("Failed to copy stream. Likely a Conscrypt issue.", e);
      }

      decryptDestination.renameTo(AvatarHelper.getAvatarFile(context, recipient.getId()));
    } catch (PushNetworkException e) {
      if (e.getCause() instanceof NonSuccessfulResponseCodeException) {
        Log.w(TAG, "Removing profile avatar (no image available) for: " + recipient.getId().serialize());
        AvatarHelper.delete(context, recipient.getId());
      } else {
        throw e;
      }
    } finally {
      if (downloadDestination != null) downloadDestination.delete();
    }

    database.setProfileAvatar(recipient.getId(), profileAvatar);

    if (recipient.isLocalNumber()) {
      TextSecurePreferences.setProfileAvatarId(context, Util.getSecureRandom().nextInt());
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    if (e instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onCanceled() {
  }

  public static final class Factory implements Job.Factory<RetrieveProfileAvatarJob> {

    @Override
    public @NonNull RetrieveProfileAvatarJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new RetrieveProfileAvatarJob(parameters,
                                          Recipient.resolved(RecipientId.from(data.getString(KEY_RECIPIENT))),
                                          data.getString(KEY_PROFILE_AVATAR));
    }
  }
}
