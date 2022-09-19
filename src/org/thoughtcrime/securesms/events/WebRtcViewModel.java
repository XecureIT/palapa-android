package org.thoughtcrime.securesms.events;

import androidx.annotation.NonNull;
import com.annimon.stream.Stream;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;

import java.util.List;

public class WebRtcViewModel {

  public enum State {
    IDLE,

    // Normal states
    CALL_INCOMING,
    CALL_OUTGOING,
    CALL_CONNECTED,
    CALL_RINGING,
    CALL_BUSY,
    CALL_DISCONNECTED,

    // Error states
    NETWORK_FAILURE,
    RECIPIENT_UNAVAILABLE,
    NO_SUCH_USER,
    UNTRUSTED_IDENTITY;

    public boolean isErrorState() {
      return this == NETWORK_FAILURE       ||
             this == RECIPIENT_UNAVAILABLE ||
             this == NO_SUCH_USER          ||
             this == UNTRUSTED_IDENTITY;
    }
  }

  private final @NonNull State        state;
  private final @NonNull Recipient    recipient;

  private final boolean               isBluetoothAvailable;
  private final CallParticipant       localParticipant;
  private final List<CallParticipant> remoteParticipants;

  public WebRtcViewModel(@NonNull WebRtcServiceState state) {
    this.state                     = state.getCallInfoState().getCallState();
    this.recipient                 = state.getCallInfoState().getCallRecipient();
    this.isBluetoothAvailable      = state.getLocalDeviceState().isBluetoothAvailable();
    this.remoteParticipants        = state.getCallInfoState().getRemoteCallParticipants();
    this.localParticipant          = CallParticipant.createLocal(state.getLocalDeviceState().getCameraState(),
                                                                 state.getVideoState().requireLocalRenderer(),
                                                                 state.getLocalDeviceState().isMicrophoneEnabled());
  }

  public @NonNull State getState() {
    return state;
  }

  public @NonNull Recipient getRecipient() {
    return recipient;
  }

  public boolean isRemoteVideoEnabled() {
    return Stream.of(remoteParticipants).anyMatch(CallParticipant::isVideoEnabled);
  }

  public boolean isBluetoothAvailable() {
    return isBluetoothAvailable;
  }

  public @NonNull CallParticipant getLocalParticipant() {
    return localParticipant;
  }

  public @NonNull List<CallParticipant> getRemoteParticipants() {
    return remoteParticipants;
  }

  @Override
  public @NonNull String toString() {
    return "WebRtcViewModel{" +
           "state=" + state +
           ", recipient=" + recipient.getId() +
           ", isBluetoothAvailable=" + isBluetoothAvailable +
           ", localParticipant=" + localParticipant +
           ", remoteParticipants=" + remoteParticipants +
           '}';
  }
}
