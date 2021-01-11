package org.thoughtcrime.securesms.registration.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.navigation.Navigation;

import com.dd.CircularProgressButton;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.registration.service.CodeVerificationRequest;
import org.thoughtcrime.securesms.registration.service.RegistrationService;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public final class RegistrationLockFragment extends BaseRegistrationFragment {

  private static final String TAG = Log.tag(RegistrationLockFragment.class);

  private EditText               pinEntry;
  private CircularProgressButton pinButton;
  private long                   timeRemaining;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_registration_lock, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    setDebugLogSubmitMultiTapView(view.findViewById(R.id.verify_header));

    pinEntry  = view.findViewById(R.id.pin);
    pinButton = view.findViewById(R.id.pinButton);

    View clarificationLabel = view.findViewById(R.id.clarification_label);
    View subHeader          = view.findViewById(R.id.verify_subheader);
    View pinForgotButton    = view.findViewById(R.id.forgot_button);

    String code = getModel().getTextCodeEntered();

    timeRemaining = RegistrationLockFragmentArgs.fromBundle(requireArguments()).getTimeRemaining();

    pinForgotButton.setOnClickListener(v -> handleForgottenPin(timeRemaining));

    pinEntry.addTextChangedListener(new TextWatcher() {

      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
      }

      @Override
      public void afterTextChanged(Editable s) {
        boolean matchesTextCode = s != null && s.toString().equals(code);
        clarificationLabel.setVisibility(matchesTextCode ? View.VISIBLE : View.INVISIBLE);
        subHeader.setVisibility(matchesTextCode ? View.INVISIBLE : View.VISIBLE);
      }
    });

    pinEntry.setImeOptions(EditorInfo.IME_ACTION_DONE);
    pinEntry.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        hideKeyboard(requireContext(), v);
        handlePinEntry();
        return true;
      }
      return false;
    });

    pinButton.setOnClickListener((v) -> {
      hideKeyboard(requireContext(), pinEntry);
      handlePinEntry();
    });

    RegistrationViewModel model = getModel();
    model.getTokenResponseCredentialsPair()
         .observe(this, pair -> {
           TokenResponse token       = pair.first();
           String        credentials = pair.second();
           updateContinueText(token, credentials);
         });

    model.onRegistrationLockFragmentCreate();
  }

  private void updateContinueText(@Nullable TokenResponse tokenResponse, @Nullable String storageCredentials) {
    if (tokenResponse == null) {
      if (storageCredentials == null) {
        pinButton.setIdleText(getString(R.string.RegistrationActivity_continue));
      } else {
        // TODO: This is the case where we can determine they are locked out
        //  no token, but do have storage credentials. Might want to change text.
        pinButton.setIdleText(getString(R.string.RegistrationActivity_continue));
      }
    } else {
      int triesRemaining = tokenResponse.getTries();
      if (triesRemaining == 1) {
        pinButton.setIdleText(getString(R.string.RegistrationActivity_continue_last_attempt));
      } else {
        pinButton.setIdleText(getString(R.string.RegistrationActivity_continue_d_attempts_left, triesRemaining));
      }
    }
    pinButton.setText(pinButton.getIdleText());
  }

  private void handlePinEntry() {
    final String pin = pinEntry.getText().toString();

    if (TextUtils.isEmpty(pin) || TextUtils.isEmpty(pin.replace(" ", ""))) {
      Toast.makeText(requireContext(), R.string.RegistrationActivity_you_must_enter_your_registration_lock_PIN, Toast.LENGTH_LONG).show();
      return;
    }

    RegistrationViewModel model               = getModel();
    RegistrationService   registrationService = RegistrationService.getInstance(model.getNumber().getE164Number(), model.getRegistrationSecret());
    String                storageCredentials  = model.getBasicStorageCredentials();
    TokenResponse         tokenResponse       = model.getKeyBackupCurrentToken();

    setSpinning(pinButton);

    registrationService.verifyAccount(requireActivity(),
                                      model.getFcmToken(),
                                      model.getTextCodeEntered(),
                                      pin, storageCredentials, tokenResponse,

      new CodeVerificationRequest.VerifyCallback() {

        @Override
        public void onSuccessfulRegistration() {
          cancelSpinning(pinButton);

          Navigation.findNavController(requireView()).navigate(RegistrationLockFragmentDirections.actionSuccessfulRegistration());
        }

        @Override
        public void onIncorrectRegistrationLockPin(long timeRemaining, String storageCredentials) {
          model.setStorageCredentials(storageCredentials);
          cancelSpinning(pinButton);

          pinEntry.setText("");
          Toast.makeText(requireContext(), R.string.RegistrationActivity_incorrect_registration_lock_pin, Toast.LENGTH_LONG).show();
        }

        @Override
        public void onIncorrectKbsRegistrationLockPin(@NonNull TokenResponse tokenResponse) {
          cancelSpinning(pinButton);

          model.setKeyBackupCurrentToken(tokenResponse);

          int triesRemaining = tokenResponse.getTries();

          if (triesRemaining == 0) {
            handleForgottenPin(timeRemaining);
            return;
          }

          new AlertDialog.Builder(requireContext())
                         .setTitle(R.string.RegistrationActivity_pin_incorrect)
                         .setMessage(getString(R.string.RegistrationActivity_you_have_d_tries_remaining, triesRemaining))
                         .setPositiveButton(android.R.string.ok, null)
                         .show();
        }

        @Override
        public void onTooManyAttempts() {
          cancelSpinning(pinButton);

          new AlertDialog.Builder(requireContext())
                         .setTitle(R.string.RegistrationActivity_too_many_attempts)
                         .setMessage(R.string.RegistrationActivity_you_have_made_too_many_incorrect_registration_lock_pin_attempts_please_try_again_in_a_day)
                         .setPositiveButton(android.R.string.ok, null)
                         .show();
        }

        @Override
        public void onError() {
          cancelSpinning(pinButton);

          Toast.makeText(requireContext(), R.string.RegistrationActivity_error_connecting_to_service, Toast.LENGTH_LONG).show();
        }
      });
  }

  private void handleForgottenPin(long timeRemainingMs) {
    new AlertDialog.Builder(requireContext())
                   .setTitle(R.string.RegistrationActivity_oh_no)
                   .setMessage(getString(R.string.RegistrationActivity_registration_of_this_phone_number_will_be_possible_without_your_registration_lock_pin_after_seven_days_have_passed, (TimeUnit.MILLISECONDS.toDays(timeRemainingMs) + 1)))
                   .setPositiveButton(android.R.string.ok, null)
                   .show();
  }
}
