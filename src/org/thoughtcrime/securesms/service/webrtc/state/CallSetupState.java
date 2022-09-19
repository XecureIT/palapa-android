package org.thoughtcrime.securesms.service.webrtc.state;

import androidx.annotation.NonNull;

/**
 * Information specific to setting up a call.
 */
public final class CallSetupState {
  boolean enableVideoOnCreate;

  public CallSetupState() {
    this(false);
  }

  public CallSetupState(@NonNull CallSetupState toCopy) {
    this(toCopy.enableVideoOnCreate);
  }

  public CallSetupState(boolean enableVideoOnCreate) {
    this.enableVideoOnCreate = enableVideoOnCreate;
  }

  public boolean isEnableVideoOnCreate() {
    return enableVideoOnCreate;
  }
}
