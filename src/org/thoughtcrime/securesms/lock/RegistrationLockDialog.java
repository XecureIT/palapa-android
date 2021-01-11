package org.thoughtcrime.securesms.lock;


import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.text.Editable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.SwitchPreferenceCompat;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.migrations.RegistrationPinV2MigrationJob;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.whispersystems.signalservice.api.KeyBackupService;
import org.whispersystems.signalservice.api.KeyBackupServicePinException;
import org.whispersystems.signalservice.api.RegistrationLockData;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;
import org.whispersystems.signalservice.internal.registrationpin.InvalidPinException;
import org.whispersystems.signalservice.internal.registrationpin.PinStretcher;

import java.io.IOException;

public final class RegistrationLockDialog {

  private static final String TAG = Log.tag(RegistrationLockDialog.class);

  public static void showReminderIfNecessary(@NonNull Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
    if (!RegistrationLockReminders.needsReminder(context))    return;

    if (!TextSecurePreferences.hasOldRegistrationLockPin(context) &&
         TextUtils.isEmpty(TextSecurePreferences.getRegistrationLockToken(context))) {
      // Neither v1 or v2 to check against
      Log.w(TAG, "Reg lock enabled, but no pin stored to verify against");
      return;
    }

    AlertDialog dialog = new AlertDialog.Builder(context, ThemeUtil.isDarkTheme(context) ? R.style.RationaleDialogDark : R.style.RationaleDialogLight)
                                        .setView(R.layout.registration_lock_reminder_view)
                                        .setCancelable(true)
                                        .setOnCancelListener(d -> RegistrationLockReminders.scheduleReminder(context, false))
                                        .create();

    WindowManager  windowManager = ServiceUtil.getWindowManager(context);
    Display        display       = windowManager.getDefaultDisplay();
    DisplayMetrics metrics       = new DisplayMetrics();
    display.getMetrics(metrics);

    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    dialog.show();
    dialog.getWindow().setLayout((int)(metrics.widthPixels * .80), ViewGroup.LayoutParams.WRAP_CONTENT);

    EditText pinEditText = dialog.findViewById(R.id.pin);
    TextView reminder    = dialog.findViewById(R.id.reminder);

    if (pinEditText == null) throw new AssertionError();
    if (reminder    == null) throw new AssertionError();

    SpannableString reminderIntro = new SpannableString(context.getString(R.string.RegistrationLockDialog_reminder));
    SpannableString reminderText  = new SpannableString(context.getString(R.string.RegistrationLockDialog_registration_lock_is_enabled_for_your_phone_number));
    SpannableString forgotText    = new SpannableString(context.getString(R.string.RegistrationLockDialog_i_forgot_my_pin));

    ClickableSpan clickableSpan = new ClickableSpan() {
      @Override
      public void onClick(@NonNull View widget) {
        dialog.dismiss();
        new AlertDialog.Builder(context).setTitle(R.string.RegistrationLockDialog_forgotten_pin)
                                        .setMessage(R.string.RegistrationLockDialog_registration_lock_helps_protect_your_phone_number_from_unauthorized_registration_attempts)
                                        .setPositiveButton(android.R.string.ok, null)
                                        .create()
                                        .show();
      }
    };

    reminderIntro.setSpan(new StyleSpan(Typeface.BOLD), 0, reminderIntro.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    forgotText.setSpan(clickableSpan, 0, forgotText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    reminder.setText(new SpannableStringBuilder(reminderIntro).append(" ").append(reminderText).append(" ").append(forgotText));
    reminder.setMovementMethod(LinkMovementMethod.getInstance());

    pinEditText.addTextChangedListener(TextSecurePreferences.hasOldRegistrationLockPin(context)
                                       ? getV1PinWatcher(context, dialog)
                                       : getV2PinWatcher(context, dialog));
  }

  private static TextWatcher getV1PinWatcher(@NonNull Context context, AlertDialog dialog) {
    //noinspection deprecation Acceptable to check the old pin in a reminder on a non-migrated system.
    String pin = TextSecurePreferences.getDeprecatedRegistrationLockPin(context);

    return new TextWatcher() {

      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {}

      @Override
      public void afterTextChanged(Editable s) {
        if (s != null && s.toString().replace(" ", "").equals(pin)) {
          dialog.dismiss();
          RegistrationLockReminders.scheduleReminder(context, true);

          if (FeatureFlags.KBS) {
            Log.i(TAG, "Pin V1 successfully remembered, scheduling a migration to V2");
            ApplicationDependencies.getJobManager().add(new RegistrationPinV2MigrationJob());
          }
        }
      }
    };
  }

  private static TextWatcher getV2PinWatcher(@NonNull Context context, AlertDialog dialog) {
    String        registrationLockToken         = TextSecurePreferences.getRegistrationLockToken(context);
    byte[]        pinKey2                       = TextSecurePreferences.getRegistrationLockPinKey2(context);
    TokenResponse registrationLockTokenResponse = TextSecurePreferences.getRegistrationLockTokenResponse(context);

    if (registrationLockToken == null) throw new AssertionError("No V2 reg lock token set at time of reminder");
    if (pinKey2 == null) throw new AssertionError("No pin key2 set at time of reminder");
    if (registrationLockTokenResponse == null) throw new AssertionError("No registrationLockTokenResponse set at time of reminder");

    return new TextWatcher() {

      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {}

      @Override
      public void afterTextChanged(Editable s) {
        if (s == null) return;
        String pin = s.toString();
        if (TextUtils.isEmpty(pin)) return;
        if (pin.length() < 4) return;

        try {
          if (registrationLockToken.equals(PinStretcher.stretchPin(pin).withPinKey2(pinKey2).getRegistrationLock())) {
            dialog.dismiss();
            RegistrationLockReminders.scheduleReminder(context, true);
          }
        } catch (InvalidPinException e) {
          Log.w(TAG, e);
        }
      }
    };
  }

  @SuppressLint("StaticFieldLeak")
  public static void showRegistrationLockPrompt(@NonNull Context context, @NonNull SwitchPreferenceCompat preference) {
    AlertDialog dialog = new AlertDialog.Builder(context)
                                        .setTitle(R.string.RegistrationLockDialog_registration_lock)
                                        .setView(R.layout.registration_lock_dialog_view)
                                        .setPositiveButton(R.string.RegistrationLockDialog_enable, null)
                                        .setNegativeButton(android.R.string.cancel, null)
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
              if (!FeatureFlags.KBS) {
                Log.i(TAG, "Setting V1 pin");
                SignalServiceAccountManager accountManager = ApplicationDependencies.getSignalServiceAccountManager();
                accountManager.setPin(pinValue);
                TextSecurePreferences.setDeprecatedRegistrationLockPin(context, pinValue);
              } else {
                Log.i(TAG, "Setting pin on KBS");
                KeyBackupService     keyBackupService = ApplicationDependencies.getKeyBackupService();
                RegistrationLockData kbsData          = keyBackupService.newPinChangeSession()
                                                                        .setPin(pinValue);
                RegistrationLockData restoredData     = keyBackupService.newRestoreSession(kbsData.getTokenResponse())
                                                                        .restorePin(pinValue);
                String               restoredLock     = restoredData.getMasterKey()
                                                                    .getRegistrationLock();

                if (!restoredLock.equals(kbsData.getMasterKey().getRegistrationLock())) {
                  throw new AssertionError("Failed to set the pin correctly");
                } else {
                  Log.i(TAG, "Set and retrieved pin on KBS successfully");
                }

                TextSecurePreferences.setRegistrationLockMasterKey(context, restoredData, System.currentTimeMillis());
                TextSecurePreferences.setRegistrationLockLastReminderTime(context, System.currentTimeMillis());
                TextSecurePreferences.setRegistrationLockNextReminderInterval(context, RegistrationLockReminders.INITIAL_INTERVAL);
              }
              return true;
            } catch (IOException | UnauthenticatedResponseException | KeyBackupServicePinException | InvalidPinException e) {
              Log.w(TAG, e);
              return false;
            }
          }

          @Override
          protected void onPostExecute(@NonNull Boolean result) {
            button.setEnabled(true);
            progressBar.setVisibility(View.GONE);

            if (result) {
              preference.setChecked(true);
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

  @SuppressLint("StaticFieldLeak")
  public static void showRegistrationUnlockPrompt(@NonNull Context context, @NonNull SwitchPreferenceCompat preference) {

    AlertDialog dialog = new AlertDialog.Builder(context)
                                        .setTitle(R.string.RegistrationLockDialog_disable_registration_lock_pin)
                                        .setView(R.layout.registration_unlock_dialog_view)
                                        .setPositiveButton(R.string.RegistrationLockDialog_disable, null)
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .create();

    dialog.setOnShowListener(created -> {
      Button button = ((AlertDialog) created).getButton(AlertDialog.BUTTON_POSITIVE);
      button.setOnClickListener(v -> {
        ProgressBar progressBar = dialog.findViewById(R.id.progress);
        assert progressBar != null;

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
              if (!FeatureFlags.KBS) {
                Log.i(TAG, "Removing v1 registration lock pin from server");
                ApplicationDependencies.getSignalServiceAccountManager().removeV1Pin();
              } else {
                Log.i(TAG, "Removing v2 registration lock pin from server");
                TokenResponse currentToken = TextSecurePreferences.getRegistrationLockTokenResponse(context);

                KeyBackupService keyBackupService = ApplicationDependencies.getKeyBackupService();
                keyBackupService.newPinChangeSession(currentToken).removePin();
                TextSecurePreferences.setRegistrationLockMasterKey(context, null, System.currentTimeMillis());

                // It is possible a migration has not occurred, in this case, we need to remove the old V1 Pin
                if (TextSecurePreferences.hasOldRegistrationLockPin(context)) {
                  Log.i(TAG, "Removing v1 registration lock pin from server");
                  ApplicationDependencies.getSignalServiceAccountManager().removeV1Pin();
                  TextSecurePreferences.clearOldRegistrationLockPin(context);
                }
              }
              return true;
            } catch (IOException | UnauthenticatedResponseException e) {
              Log.w(TAG, e);
              return false;
            }
          }

          @Override
          protected void onPostExecute(Boolean result) {
            progressBar.setVisibility(View.GONE);
            button.setEnabled(true);

            if (result) {
              preference.setChecked(false);
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

}
