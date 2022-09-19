package org.thoughtcrime.securesms.service.webrtc;

import android.content.Context;
import android.media.AudioManager;
import androidx.annotation.NonNull;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.webrtc.locks.LockManager;

/**
 * Calling specific helpers.
 */
public final class WebRtcUtil {

  private WebRtcUtil() {}

  public static @NonNull LockManager.PhoneState getInCallPhoneState(@NonNull Context context) {
    AudioManager audioManager = ServiceUtil.getAudioManager(context);
    if (audioManager.isSpeakerphoneOn() || audioManager.isBluetoothScoOn() || audioManager.isWiredHeadsetOn()) {
      return LockManager.PhoneState.IN_HANDS_FREE_CALL;
    } else {
      return LockManager.PhoneState.IN_CALL;
    }
  }

  public static void enableSpeakerPhoneIfNeeded(@NonNull Context context, boolean enable) {
    if (!enable) {
      return;
    }

    AudioManager androidAudioManager = ServiceUtil.getAudioManager(context);
    //noinspection deprecation
    boolean shouldEnable = !(androidAudioManager.isSpeakerphoneOn() || androidAudioManager.isBluetoothScoOn() || androidAudioManager.isWiredHeadsetOn());

    if (shouldEnable) {
      androidAudioManager.setSpeakerphoneOn(true);
    }
  }
}
