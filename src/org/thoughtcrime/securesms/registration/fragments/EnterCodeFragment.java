package org.thoughtcrime.securesms.registration.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.registration.CallMeCountDownView;
import org.thoughtcrime.securesms.components.registration.VerificationCodeView;
import org.thoughtcrime.securesms.components.registration.VerificationPinKeyboard;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.registration.ReceivedSmsEvent;
import org.thoughtcrime.securesms.registration.service.CodeVerificationRequest;
import org.thoughtcrime.securesms.registration.service.RegistrationCodeRequest;
import org.thoughtcrime.securesms.registration.service.RegistrationService;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;
import org.thoughtcrime.securesms.util.concurrent.AssertedSuccessListener;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class EnterCodeFragment extends BaseRegistrationFragment {

  private static final String TAG = Log.tag(EnterCodeFragment.class);

  private ScrollView              scrollView;
  private TextView                header;
  private VerificationCodeView    verificationCodeView;
  private VerificationPinKeyboard keyboard;
  private CallMeCountDownView     callMeCountDown;
  private View                    wrongNumber;
  private View                    noCodeReceivedHelp;
  private boolean                 autoCompleting;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_registration_enter_code, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    setDebugLogSubmitMultiTapView(view.findViewById(R.id.verify_header));

    scrollView           = view.findViewById(R.id.scroll_view);
    header               = view.findViewById(R.id.verify_header);
    verificationCodeView = view.findViewById(R.id.code);
    keyboard             = view.findViewById(R.id.keyboard);
    callMeCountDown      = view.findViewById(R.id.call_me_count_down);
    wrongNumber          = view.findViewById(R.id.wrong_number);
    noCodeReceivedHelp   = view.findViewById(R.id.no_code);

    connectKeyboard(verificationCodeView, keyboard);

    setOnCodeFullyEnteredListener(verificationCodeView);

    wrongNumber.setOnClickListener(v -> Navigation.findNavController(view).navigate(EnterCodeFragmentDirections.actionWrongNumber()));

    callMeCountDown.setOnClickListener(v -> handlePhoneCallRequest());

    callMeCountDown.setListener((v, remaining) -> {
      if (remaining <= 30) {
        scrollView.smoothScrollTo(0, v.getBottom());
        callMeCountDown.setListener(null);
      }
    });

    noCodeReceivedHelp.setOnClickListener(v -> sendEmailToSupport());

    getModel().getSuccessfulCodeRequestAttempts().observe(this, (attempts) -> {
      if (attempts >= 3) {
//        noCodeReceivedHelp.setVisibility(View.VISIBLE);
        scrollView.postDelayed(() -> scrollView.smoothScrollTo(0, noCodeReceivedHelp.getBottom()), 15000);
      }
    });
  }

  private void setOnCodeFullyEnteredListener(VerificationCodeView verificationCodeView) {
    verificationCodeView.setOnCompleteListener(code -> {
      RegistrationViewModel model = getModel();

      model.onVerificationCodeEntered(code);
      callMeCountDown.setVisibility(View.INVISIBLE);
      wrongNumber.setVisibility(View.INVISIBLE);
      keyboard.displayProgress();

      RegistrationService registrationService = RegistrationService.getInstance(model.getNumber().getE164Number(), model.getRegistrationSecret());

      registrationService.verifyAccount(requireActivity(), model.getFcmToken(), code, null, null, null,
        new CodeVerificationRequest.VerifyCallback() {

          @Override
          public void onSuccessfulRegistration() {
            keyboard.displaySuccess().addListener(new AssertedSuccessListener<Boolean>() {
              @Override
              public void onSuccess(Boolean result) {
                handleSuccessfulRegistration();
              }
            });
          }

          @Override
          public void onIncorrectRegistrationLockPin(long timeRemaining, String storageCredentials) {
            model.setStorageCredentials(storageCredentials);
            keyboard.displayLocked().addListener(new AssertedSuccessListener<Boolean>() {
              @Override
              public void onSuccess(Boolean r) {
                Navigation.findNavController(requireView())
                          .navigate(EnterCodeFragmentDirections.actionRequireRegistrationLockPin(timeRemaining));
              }
            });
          }

          @Override
          public void onIncorrectKbsRegistrationLockPin(@NonNull TokenResponse triesRemaining) {
            // Unexpected, because at this point, no pin has been provided by the user.
            throw new AssertionError();
          }

          @Override
          public void onTooManyAttempts() {
            keyboard.displayFailure().addListener(new AssertedSuccessListener<Boolean>() {
              @Override
              public void onSuccess(Boolean r) {
                new AlertDialog.Builder(requireContext())
                               .setTitle(R.string.RegistrationActivity_too_many_attempts)
                               .setMessage(R.string.RegistrationActivity_you_have_made_too_many_incorrect_registration_lock_pin_attempts_please_try_again_in_a_day)
                               .setPositiveButton(android.R.string.ok, (dialog, which) -> {
//                                 callMeCountDown.setVisibility(View.VISIBLE);
                                 wrongNumber.setVisibility(View.VISIBLE);
                                 verificationCodeView.clear();
                                 keyboard.displayKeyboard();
                               })
                               .show();
              }
            });
          }

          @Override
          public void onError() {
            Toast.makeText(requireContext(), R.string.RegistrationActivity_error_connecting_to_service, Toast.LENGTH_LONG).show();
            keyboard.displayFailure().addListener(new AssertedSuccessListener<Boolean>() {
              @Override
              public void onSuccess(Boolean result) {
//                callMeCountDown.setVisibility(View.VISIBLE);
                wrongNumber.setVisibility(View.VISIBLE);
                verificationCodeView.clear();
                keyboard.displayKeyboard();
              }
            });
          }
        });
    });
  }

  private void handleSuccessfulRegistration() {
    Navigation.findNavController(requireView()).navigate(EnterCodeFragmentDirections.actionSuccessfulRegistration());
  }

  @Override
  public void onStart() {
    super.onStart();
    EventBus.getDefault().register(this);
  }

  @Override
  public void onStop() {
    super.onStop();
    EventBus.getDefault().unregister(this);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onVerificationCodeReceived(@NonNull ReceivedSmsEvent event) {
    verificationCodeView.clear();

    List<Integer> parsedCode = convertVerificationCodeToDigits(event.getCode());

    autoCompleting = true;

    final int size = parsedCode.size();

    for (int i = 0; i < size; i++) {
      final int index = i;
      verificationCodeView.postDelayed(() -> {
        verificationCodeView.append(parsedCode.get(index));
        if (index == size - 1) {
          autoCompleting = false;
        }
      }, i * 200);
    }
  }

  private static List<Integer> convertVerificationCodeToDigits(@Nullable String code) {
    if (code == null || code.length() != 6) {
      return Collections.emptyList();
    }

    List<Integer> result = new ArrayList<>(code.length());

    try {
      for (int i = 0; i < code.length(); i++) {
        result.add(Integer.parseInt(Character.toString(code.charAt(i))));
      }
    } catch (NumberFormatException e) {
      Log.w(TAG, "Failed to convert code into digits.", e);
      return Collections.emptyList();
    }

    return result;
  }

  private void handlePhoneCallRequest() {
    callMeCountDown.startCountDown(RegistrationConstants.SUBSEQUENT_CALL_AVAILABLE_AFTER);

    RegistrationViewModel model   = getModel();
    String                captcha = model.getCaptchaToken();
    model.clearCaptchaResponse();

    NavController navController = Navigation.findNavController(callMeCountDown);

    RegistrationService registrationService = RegistrationService.getInstance(model.getNumber().getE164Number(), model.getRegistrationSecret());

    registrationService.requestVerificationCode(requireActivity(), RegistrationCodeRequest.Mode.PHONE_CALL, captcha,
      new RegistrationCodeRequest.SmsVerificationCodeCallback() {

        @Override
        public void onNeedCaptcha() {
          navController.navigate(EnterCodeFragmentDirections.actionRequestCaptcha());
        }

        @Override
        public void requestSent(@Nullable String fcmToken) {
          model.setFcmToken(fcmToken);
          model.markASuccessfulAttempt();
        }

        @Override
        public void onError() {
          Toast.makeText(requireContext(), R.string.RegistrationActivity_unable_to_connect_to_service, Toast.LENGTH_LONG).show();
        }
      });
  }

  private void connectKeyboard(VerificationCodeView verificationCodeView, VerificationPinKeyboard keyboard) {
    keyboard.setOnKeyPressListener(key -> {
      if (!autoCompleting) {
        if (key >= 0) {
          verificationCodeView.append(key);
        } else {
          verificationCodeView.delete();
        }
      }
    });
  }

  @Override
  public void onResume() {
    super.onResume();

    getModel().getLiveNumber().observe(this, (s) -> header.setText(requireContext().getString(R.string.RegistrationActivity_enter_the_code_we_sent_to_s, s.getFullFormattedNumber())));

//    callMeCountDown.startCountDown(RegistrationConstants.FIRST_CALL_AVAILABLE_AFTER);
  }

  private void sendEmailToSupport() {
    Intent intent = new Intent(Intent.ACTION_SENDTO);
    intent.setData(Uri.parse("mailto:"));
    intent.putExtra(Intent.EXTRA_EMAIL, new String[]{ getString(R.string.RegistrationActivity_support_email) });
    intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.RegistrationActivity_code_support_subject));
    intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.RegistrationActivity_code_support_body,
                                                 getDevice(),
                                                 getAndroidVersion(),
                                                 BuildConfig.VERSION_NAME,
                                                 Locale.getDefault()));
    startActivity(intent);
  }

  private static String getDevice() {
    return String.format("%s %s (%s)", Build.MANUFACTURER, Build.MODEL, Build.PRODUCT);
  }

  private static String getAndroidVersion() {
    return String.format("%s (%s, %s)", Build.VERSION.RELEASE, Build.VERSION.INCREMENTAL, Build.DISPLAY);
  }
}
