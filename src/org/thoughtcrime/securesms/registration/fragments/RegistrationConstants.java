package org.thoughtcrime.securesms.registration.fragments;

import org.thoughtcrime.securesms.BuildConfig;

final class RegistrationConstants {

  private RegistrationConstants() {
  }

  static final int    FIRST_CALL_AVAILABLE_AFTER      =  64;
  static final int    SUBSEQUENT_CALL_AVAILABLE_AFTER = 300;
  static final String TERMS_AND_CONDITIONS_URL        = "https://signal.org/legal";

  static final String SIGNAL_CAPTCHA_URL    = BuildConfig.SIGNAL_CAPTCHA_URL;
  static final String SIGNAL_CAPTCHA_SCHEME = BuildConfig.SIGNAL_CAPTCHA_SCHEME;

}
