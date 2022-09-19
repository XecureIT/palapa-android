package org.thoughtcrime.securesms.service.webrtc.state;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.ringrtc.CameraState;

/**
 * Local device specific state.
 */
public final class LocalDeviceState {
  CameraState cameraState;
  boolean     microphoneEnabled;
  boolean     bluetoothAvailable;
  boolean     wantsBluetooth;

  LocalDeviceState() {
    this(CameraState.UNKNOWN, true, false, false);
  }

  LocalDeviceState(@NonNull LocalDeviceState toCopy) {
    this(toCopy.cameraState, toCopy.microphoneEnabled, toCopy.bluetoothAvailable, toCopy.wantsBluetooth);
  }

  LocalDeviceState(@NonNull CameraState cameraState,
                   boolean microphoneEnabled,
                   boolean bluetoothAvailable,
                   boolean wantsBluetooth)
  {
    this.cameraState        = cameraState;
    this.microphoneEnabled  = microphoneEnabled;
    this.bluetoothAvailable = bluetoothAvailable;
    this.wantsBluetooth     = wantsBluetooth;
  }

  public @NonNull CameraState getCameraState() {
    return cameraState;
  }

  public boolean isMicrophoneEnabled() {
    return microphoneEnabled;
  }

  public boolean isBluetoothAvailable() {
    return bluetoothAvailable;
  }

  public boolean wantsBluetooth() {
    return wantsBluetooth;
  }
}
