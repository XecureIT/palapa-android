package org.thoughtcrime.securesms.service.webrtc.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.CallParticipantId;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;

import java.util.*;

/**
 * General state of ongoing calls.
 */
public class CallInfoState {

  WebRtcViewModel.State                   callState;
  Recipient                               callRecipient;
  Map<CallParticipantId, CallParticipant> remoteParticipants;
  Map<Integer, RemotePeer>                peerMap;
  RemotePeer                              activePeer;

  public CallInfoState() {
    this(WebRtcViewModel.State.IDLE,
         Recipient.UNKNOWN,
         Collections.emptyMap(),
         Collections.emptyMap(),
         null);
  }

  public CallInfoState(@NonNull CallInfoState toCopy) {
    this(toCopy.callState,
         toCopy.callRecipient,
         toCopy.remoteParticipants,
         toCopy.peerMap,
         toCopy.activePeer);
  }

  public CallInfoState(@NonNull WebRtcViewModel.State callState,
                       @NonNull Recipient callRecipient,
                       @NonNull Map<CallParticipantId, CallParticipant> remoteParticipants,
                       @NonNull Map<Integer, RemotePeer> peerMap,
                       @Nullable RemotePeer activePeer)
  {
    this.callState                 = callState;
    this.callRecipient             = callRecipient;
    this.remoteParticipants        = new LinkedHashMap<>(remoteParticipants);
    this.peerMap                   = new HashMap<>(peerMap);
    this.activePeer                = activePeer;
  }

  public @NonNull Recipient getCallRecipient() {
    return callRecipient;
  }

  public @Nullable CallParticipant getRemoteCallParticipant(@NonNull Recipient recipient) {
    return getRemoteCallParticipant(new CallParticipantId(recipient));
  }

  public @Nullable CallParticipant getRemoteCallParticipant(@NonNull CallParticipantId callParticipantId) {
    return remoteParticipants.get(callParticipantId);
  }

  public @NonNull List<CallParticipant> getRemoteCallParticipants() {
    return new ArrayList<>(remoteParticipants.values());
  }

  public @NonNull WebRtcViewModel.State getCallState() {
    return callState;
  }

  public @Nullable RemotePeer getPeer(int hashCode) {
    return peerMap.get(hashCode);
  }

  public @Nullable RemotePeer getActivePeer() {
    return activePeer;
  }

  public @NonNull RemotePeer requireActivePeer() {
    return Objects.requireNonNull(activePeer);
  }
}
