package org.thoughtcrime.securesms.registration.service;

import android.content.Context;
import android.os.AsyncTask;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import org.jsoup.helper.StringUtil;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.PreKeyUtil;
import org.thoughtcrime.securesms.crypto.SessionUtil;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob;
import org.thoughtcrime.securesms.jobs.RotateCertificateJob;
import org.thoughtcrime.securesms.lock.RegistrationLockReminders;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.migrations.RegistrationPinV2MigrationJob;
import org.thoughtcrime.securesms.push.AccountManagerFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.service.DirectoryRefreshListener;
import org.thoughtcrime.securesms.service.RotateSignedPreKeyListener;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.KeyBackupService;
import org.whispersystems.signalservice.api.KeyBackupServicePinException;
import org.whispersystems.signalservice.api.RegistrationLockData;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;
import org.whispersystems.signalservice.internal.push.LockedException;
import org.whispersystems.signalservice.internal.registrationpin.InvalidPinException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public final class CodeVerificationRequest {

  private static final String TAG = Log.tag(CodeVerificationRequest.class);

  static TokenResponse getToken(@Nullable String basicStorageCredentials) throws IOException {
    if (basicStorageCredentials == null) return null;
    if (!FeatureFlags.KBS) return null;
    return ApplicationDependencies.getKeyBackupService().getToken(basicStorageCredentials);
  }

  private enum Result {
    SUCCESS,
    PIN_LOCKED,
    KBS_WRONG_PIN,
    RATE_LIMITED,
    ERROR
  }

  /**
   * Asynchronously verify the account via the code.
   *
   * @param fcmToken         The FCM token for the device.
   * @param code             The code that was delivered to the user.
   * @param pin              The users registration pin.
   * @param callback         Exactly one method on this callback will be called.
   * @param kbsTokenResponse By keeping the token, on failure, a newly returned token will be reused in subsequent pin
   *                         attempts, preventing certain attacks, we can also track the attempts making missing replies easier to spot.
   */
  static void verifyAccount(@NonNull Context context,
                            @NonNull Credentials credentials,
                            @Nullable String fcmToken,
                            @NonNull String code,
                            @Nullable String pin,
                            @Nullable String basicStorageCredentials,
                            @Nullable TokenResponse kbsTokenResponse,
                            @NonNull VerifyCallback callback)
  {
    new AsyncTask<Void, Void, Result>() {

      private volatile LockedException                  lockedException;
      private volatile KeyBackupSystemWrongPinException keyBackupSystemWrongPinException;

      @Override
      protected Result doInBackground(Void... voids) {
        try {
          verifyAccount(context, credentials, code, pin, basicStorageCredentials, kbsTokenResponse, fcmToken);
          return Result.SUCCESS;
        } catch (LockedException e) {
          Log.w(TAG, e);
          lockedException = e;
          return Result.PIN_LOCKED;
        } catch (RateLimitException e) {
          Log.w(TAG, e);
          return Result.RATE_LIMITED;
        } catch (IOException e) {
          Log.w(TAG, e);
          return Result.ERROR;
        } catch (KeyBackupSystemWrongPinException e) {
          keyBackupSystemWrongPinException = e;
          return Result.KBS_WRONG_PIN;
        }
      }

      @Override
      protected void onPostExecute(Result result) {
        switch (result) {
          case SUCCESS:
            handleSuccessfulRegistration(context);
            if (!FeatureFlags.KBS && StringUtil.isBlank(TextSecurePreferences.getDeprecatedRegistrationLockPin(context))) {
              setNewPinWithPromptV1(context, callback);
            } else {
              callback.onSuccessfulRegistration();
            }
            break;
          case PIN_LOCKED:
            callback.onIncorrectRegistrationLockPin(lockedException.getTimeRemaining(), lockedException.getBasicStorageCredentials());
            break;
          case RATE_LIMITED:
            callback.onTooManyAttempts();
            break;
          case ERROR:
            callback.onError();
            break;
          case KBS_WRONG_PIN:
            callback.onIncorrectKbsRegistrationLockPin(keyBackupSystemWrongPinException.getTokenResponse());
            break;
        }
      }
    }.execute();
  }

  private static void handleSuccessfulRegistration(@NonNull Context context) {
    JobManager jobManager = ApplicationDependencies.getJobManager();
    jobManager.add(new DirectoryRefreshJob(false));
    jobManager.add(new RotateCertificateJob(context));

    DirectoryRefreshListener.schedule(context);
    RotateSignedPreKeyListener.schedule(context);
  }

  private static void verifyAccount(@NonNull Context context,
                                    @NonNull Credentials credentials,
                                    @NonNull String code,
                                    @Nullable String pin,
                                    @Nullable String basicStorageCredentials,
                                    @Nullable TokenResponse kbsTokenResponse,
                                    @Nullable String fcmToken)
    throws IOException, KeyBackupSystemWrongPinException
  {
    int     registrationId              = KeyHelper.generateRegistrationId(false);
    byte[]  unidentifiedAccessKey       = UnidentifiedAccessUtil.getSelfUnidentifiedAccessKey(context);
    boolean universalUnidentifiedAccess = TextSecurePreferences.isUniversalUnidentifiedAccess(context);

    TextSecurePreferences.setLocalRegistrationId(context, registrationId);
    SessionUtil.archiveAllSessions(context);

    SignalServiceAccountManager accountManager   = AccountManagerFactory.createUnauthenticated(context, credentials.getE164number(), credentials.getPassword());
    RegistrationLockData        kbsData          = restoreMasterKey(pin, basicStorageCredentials, kbsTokenResponse);
    String                      registrationLock = kbsData != null ? kbsData.getMasterKey().getRegistrationLock() : null;
    boolean                     present          = fcmToken != null;

    UUID uuid = accountManager.verifyAccountWithCode(code, null, registrationId, !present,
                                                     pin, registrationLock,
                                                     unidentifiedAccessKey, universalUnidentifiedAccess);

    IdentityKeyPair    identityKey  = IdentityKeyUtil.getIdentityKeyPair(context);
    List<PreKeyRecord> records      = PreKeyUtil.generatePreKeys(context);
    SignedPreKeyRecord signedPreKey = PreKeyUtil.generateSignedPreKey(context, identityKey, true);

    accountManager = AccountManagerFactory.createAuthenticated(context, uuid, credentials.getE164number(), credentials.getPassword());
    accountManager.setPreKeys(identityKey.getPublicKey(), signedPreKey, records);

    if (present) {
      accountManager.setGcmId(Optional.fromNullable(fcmToken));
    }

    RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    RecipientId       selfId            = recipientDatabase.getOrInsertFromE164(credentials.getE164number());

    recipientDatabase.setProfileSharing(selfId, true);
    recipientDatabase.markRegistered(selfId, uuid);

    TextSecurePreferences.setLocalNumber(context, credentials.getE164number());
    TextSecurePreferences.setLocalUuid(context, uuid);
    TextSecurePreferences.setFcmToken(context, fcmToken);
    TextSecurePreferences.setFcmDisabled(context, !present);
    TextSecurePreferences.setWebsocketRegistered(context, true);

    DatabaseFactory.getIdentityDatabase(context)
                   .saveIdentity(Recipient.self().getId(),
                                 identityKey.getPublicKey(), IdentityDatabase.VerifiedStatus.VERIFIED,
                                 true, System.currentTimeMillis(), true);

    TextSecurePreferences.setVerifying(context, false);
    TextSecurePreferences.setPushRegistered(context, true);
    TextSecurePreferences.setPushServerPassword(context, credentials.getPassword());
    TextSecurePreferences.setSignedPreKeyRegistered(context, true);
    TextSecurePreferences.setPromptedPushRegistration(context, true);
    TextSecurePreferences.setUnauthorizedReceived(context, false);
    TextSecurePreferences.setRegistrationLockMasterKey(context, kbsData, System.currentTimeMillis());
    if (kbsData == null) {
      //noinspection deprecation Only acceptable place to write the old pin.
      TextSecurePreferences.setDeprecatedRegistrationLockPin(context, pin);
      if (pin != null) {
        if (FeatureFlags.KBS) {
          Log.i(TAG, "Pin V1 successfully entered during registration, scheduling a migration to Pin V2");
          ApplicationDependencies.getJobManager().add(new RegistrationPinV2MigrationJob());
        }
      }
    } else {
      repostPinToResetTries(context, pin, kbsData);
    }
    TextSecurePreferences.setRegistrationLockEnabled(context, pin != null);
    if (pin != null) {
      TextSecurePreferences.setRegistrationLockLastReminderTime(context, System.currentTimeMillis());
      TextSecurePreferences.setRegistrationLockNextReminderInterval(context, RegistrationLockReminders.INITIAL_INTERVAL);
    }
  }

  private static void setNewPinWithPromptV1(@NonNull Context context, @NonNull VerifyCallback callback) {
    if (FeatureFlags.KBS) return;

    AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle(R.string.RegistrationLockDialog_registration_lock)
            .setView(R.layout.registration_lock_dialog_view)
            .setPositiveButton(R.string.RegistrationLockDialog_enable, null)
            .setCancelable(false)
            .create();

    dialog.setOnShowListener(created -> {
      Button button = ((AlertDialog) created).getButton(AlertDialog.BUTTON_POSITIVE);
      button.setOnClickListener(v -> {
        EditText    pin         = dialog.findViewById(R.id.pin);
        EditText    repeat      = dialog.findViewById(R.id.repeat);
        ProgressBar progressBar = dialog.findViewById(R.id.progress);

        if (pin         == null) throw new AssertionError();
        if (repeat      == null) throw new AssertionError();
        if (progressBar == null) throw new AssertionError();

        String pinValue    = pin.getText().toString().replace(" ", "");
        String repeatValue = repeat.getText().toString().replace(" ", "");

        if (pinValue.length() < 4) {
          Toast.makeText(context, R.string.RegistrationLockDialog_the_registration_lock_pin_must_be_at_least_four_digits, Toast.LENGTH_LONG).show();
          return;
        }

        if (!pinValue.equals(repeatValue)) {
          Toast.makeText(context, R.string.RegistrationLockDialog_the_two_pins_you_entered_do_not_match, Toast.LENGTH_LONG).show();
          return;
        }

        new AsyncTask<Void, Void, Boolean>() {
          @Override
          protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setIndeterminate(true);
            button.setEnabled(false);
          }

          @Override
          protected Boolean doInBackground(Void... voids) {
            try {
              Log.i(TAG, "Setting V1 pin");
              SignalServiceAccountManager accountManager = ApplicationDependencies.getSignalServiceAccountManager();
              accountManager.setPin(pinValue);
              TextSecurePreferences.setDeprecatedRegistrationLockPin(context, pinValue);
              // Set last reminder to current time so it won't be prompted too quickly
              TextSecurePreferences.setRegistrationLockLastReminderTime(context, System.currentTimeMillis());
              TextSecurePreferences.setRegistrationLockNextReminderInterval(context, RegistrationLockReminders.INITIAL_INTERVAL);
              return true;
            } catch (IOException e) {
              Log.w(TAG, e);
              return false;
            }
          }

          @Override
          protected void onPostExecute(@NonNull Boolean result) {
            button.setEnabled(true);
            progressBar.setVisibility(View.GONE);

            TextSecurePreferences.setRegistrationLockEnabled(context, result);

            if (result) {
              callback.onSuccessfulRegistration();
              created.dismiss();
            } else {
              Toast.makeText(context, R.string.RegistrationLockDialog_error_connecting_to_the_service, Toast.LENGTH_LONG).show();
            }
          }
        }.execute();
      });
    });

    dialog.show();
  }

  private static void repostPinToResetTries(@NonNull Context context, @Nullable String pin, @NonNull RegistrationLockData kbsData) {
    if (!FeatureFlags.KBS) return;

    KeyBackupService keyBackupService = ApplicationDependencies.getKeyBackupService();

    try {
      RegistrationLockData newData = keyBackupService.newPinChangeSession(kbsData.getTokenResponse())
                                                     .setPin(pin);
      TextSecurePreferences.setRegistrationLockMasterKey(context, newData, System.currentTimeMillis());
    } catch (IOException e) {
      Log.w(TAG, "May have failed to reset pin attempts!", e);
    } catch (UnauthenticatedResponseException e) {
      Log.w(TAG, "Failed to reset pin attempts", e);
    } catch (InvalidPinException e) {
      throw new AssertionError(e);
    }
  }

  private static @Nullable RegistrationLockData restoreMasterKey(@Nullable String pin,
                                                                 @Nullable String basicStorageCredentials,
                                                                 @Nullable TokenResponse tokenResponse)
    throws IOException, KeyBackupSystemWrongPinException
  {
    if (pin == null) return null;

    if (basicStorageCredentials == null) {
      Log.i(TAG, "No storage credentials supplied, pin is not on KBS");
      return null;
    }

    if (!FeatureFlags.KBS) {
      Log.w(TAG, "User appears to have a KBS pin, but this build has KBS off.");
      return null;
    }

    KeyBackupService keyBackupService = ApplicationDependencies.getKeyBackupService();

    Log.i(TAG, "Opening key backup service session");
    KeyBackupService.RestoreSession session = keyBackupService.newRegistrationSession(basicStorageCredentials, tokenResponse);

    try {
      Log.i(TAG, "Restoring pin from KBS");
      RegistrationLockData kbsData = session.restorePin(pin);
      if (kbsData != null) {
        Log.i(TAG, "Found registration lock token on KBS.");
      } else {
        Log.i(TAG, "No KBS data found.");
      }
      return kbsData;
    } catch (UnauthenticatedResponseException e) {
      Log.w(TAG, "Failed to restore key", e);
      throw new IOException(e);
    } catch (KeyBackupServicePinException e) {
      Log.w(TAG, "Incorrect pin", e);
      throw new KeyBackupSystemWrongPinException(e.getToken());
    } catch (InvalidPinException e) {
      Log.w(TAG, "Invalid pin", e);
      return null;
    }
  }

  public interface VerifyCallback {

    void onSuccessfulRegistration();

    /**
     * @param timeRemaining Time until pin expires and number can be reused.
     */
    void onIncorrectRegistrationLockPin(long timeRemaining, String storageCredentials);

    void onIncorrectKbsRegistrationLockPin(@NonNull TokenResponse kbsTokenResponse);

    void onTooManyAttempts();

    void onError();
  }
}
