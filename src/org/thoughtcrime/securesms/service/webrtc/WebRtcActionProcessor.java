package org.thoughtcrime.securesms.service.webrtc;

import android.content.Context;
import android.os.ResultReceiver;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.signal.ringrtc.CallException;
import org.signal.ringrtc.CallId;
import org.signal.ringrtc.CallManager;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.ringrtc.CallState;
import org.thoughtcrime.securesms.ringrtc.CameraState;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.WebRtcData.CallMetadata;
import org.thoughtcrime.securesms.service.webrtc.WebRtcData.OfferMetadata;
import org.thoughtcrime.securesms.service.webrtc.WebRtcData.ReceivedOfferMetadata;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceStateBuilder;
import org.thoughtcrime.securesms.util.TelephonyUtil;
import org.thoughtcrime.securesms.webrtc.locks.LockManager;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.calls.BusyMessage;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;

import java.util.List;
import java.util.Objects;

import static org.thoughtcrime.securesms.service.webrtc.WebRtcData.AnswerMetadata;

/**
 * Base WebRTC action processor and core of the calling state machine. As actions (as intents)
 * are sent to the service, they are passed to an instance of the current state's action processor.
 * Based on the state of the system, the action processor will either handle the event or do nothing.
 * <p>
 * For example, the {@link OutgoingCallActionProcessor} responds to the the
 * {@link #handleReceivedBusy(WebRtcServiceState, CallMetadata)} event but no others do.
 * <p>
 * Processing of the actions occur in by calls from {@link SignalCallManager} and
 * result in atomic state updates that are returned to the caller. Part of the state change can be
 * the replacement of the current action processor.
 */
public abstract class WebRtcActionProcessor {

  protected final Context          context;
  protected final WebRtcInteractor webRtcInteractor;
  protected final String           tag;

  public WebRtcActionProcessor(@NonNull WebRtcInteractor webRtcInteractor, @NonNull String tag) {
    this.context          = webRtcInteractor.getContext();
    this.webRtcInteractor = webRtcInteractor;
    this.tag              = tag;
  }

  public @NonNull String getTag() {
    return tag;
  }

  protected @NonNull WebRtcServiceState handleIsInCallQuery(@NonNull WebRtcServiceState currentState, @Nullable ResultReceiver resultReceiver) {
    if (resultReceiver != null) {
      resultReceiver.send(0, null);
    }
    return currentState;
  }

  //region Outgoing Call

  protected @NonNull WebRtcServiceState handleOutgoingCall(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(tag, "handleOutgoingCall not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleStartOutgoingCall(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(tag, "handleStartOutgoingCall not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleSendOffer(@NonNull WebRtcServiceState currentState, @NonNull CallMetadata callMetadata, @NonNull OfferMetadata offerMetadata, boolean broadcast) {
    Log.i(tag, "handleSendOffer not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleRemoteRinging(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(tag, "handleRemoteRinging not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleReceivedAnswer(@NonNull WebRtcServiceState currentState,
                                                             @NonNull CallMetadata callMetadata,
                                                             @NonNull AnswerMetadata answerMetadata)
  {
    Log.i(tag, "handleReceivedAnswer not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleReceivedBusy(@NonNull WebRtcServiceState currentState, @NonNull CallMetadata callMetadata) {
    Log.i(tag, "handleReceivedBusy not processed");
    return currentState;
  }

  //endregion Outgoing call

  //region Incoming call

  protected @NonNull WebRtcServiceState handleReceivedOffer(@NonNull WebRtcServiceState currentState,
                                                            @NonNull CallMetadata callMetadata,
                                                            @NonNull OfferMetadata offerMetadata,
                                                            @NonNull ReceivedOfferMetadata receivedOfferMetadata)
  {
    Log.i(tag, "handleReceivedOffer(): id: " + callMetadata.getCallId().format(callMetadata.getRemoteDevice()));

    if (TelephonyUtil.isAnyPstnLineBusy(context)) {
      Log.i(tag, "PSTN line is busy.");
      currentState = currentState.getActionProcessor().handleSendBusy(currentState, callMetadata, true);
      webRtcInteractor.insertMissedCall(callMetadata.getRemotePeer());
      return currentState;
    }

    Log.i(tag, "add remotePeer callId: " + callMetadata.getRemotePeer().getCallId() + " key: " + callMetadata.getRemotePeer().hashCode());

    callMetadata.getRemotePeer().setCallStartTimestamp(receivedOfferMetadata.getTimestamp());

    currentState = currentState.builder()
                               .changeCallSetupState()
                               .commit()
                               .changeCallInfoState()
                               .putRemotePeer(callMetadata.getRemotePeer())
                               .build();

    try {
      webRtcInteractor.getCallManager().receivedOffer(callMetadata.getCallId(),
                                                      callMetadata.getRemotePeer(),
                                                      callMetadata.getRemoteDevice(),
                                                      offerMetadata.getSdp(),
                                                      receivedOfferMetadata.getTimestamp(),
                                                      CallManager.CallMediaType.AUDIO_CALL,
                                                      false,
                                                      true);
    } catch (CallException e) {
      return callFailure(currentState, "Unable to process received offer: ", e);
    }

    return currentState;
  }

  protected @NonNull WebRtcServiceState handleReceivedOfferExpired(@NonNull WebRtcServiceState currentState,
                                                                   @NonNull RemotePeer remotePeer)
  {
    Log.i(tag, "handleReceivedOfferExpired(): call_id: " + remotePeer.getCallId());

    webRtcInteractor.insertMissedCall(remotePeer);

    return terminate(currentState, remotePeer);
  }

  protected @NonNull WebRtcServiceState handleStartIncomingCall(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(tag, "handleStartIncomingCall not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleAcceptCall(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleAcceptCall not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleLocalRinging(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(tag, "handleLocalRinging not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleDenyCall(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleDenyCall not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleSendAnswer(@NonNull WebRtcServiceState currentState,
                                                         @NonNull CallMetadata callMetadata,
                                                         @NonNull AnswerMetadata answerMetadata,
                                                         boolean broadcast)
  {
    Log.i(tag, "handleSendAnswer not processed");
    return currentState;
  }

  //endregion Incoming call

  //region Active call

  protected @NonNull WebRtcServiceState handleCallConnected(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(tag, "handleCallConnected not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleReceivedOfferWhileActive(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(tag, "handleReceivedOfferWhileActive not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleSendBusy(@NonNull WebRtcServiceState currentState, @NonNull CallMetadata callMetadata, boolean broadcast) {
    Log.i(tag, "handleSendBusy(): id: " + callMetadata.getCallId().format(callMetadata.getRemoteDevice()));

    BusyMessage              busyMessage         = new BusyMessage(callMetadata.getCallId().longValue());
    SignalServiceCallMessage callMessage         = SignalServiceCallMessage.forBusy(busyMessage);

    webRtcInteractor.sendCallMessage(callMetadata.getRemotePeer(), callMessage);

    return currentState;
  }

  protected @NonNull WebRtcServiceState handleCallConcluded(@NonNull WebRtcServiceState currentState, @Nullable RemotePeer remotePeer) {
    Log.i(tag, "handleCallConcluded not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleRemoteVideoEnable(@NonNull WebRtcServiceState currentState, boolean enable) {
    Log.i(tag, "handleRemoteVideoEnable not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleReceivedHangup(@NonNull WebRtcServiceState currentState,
                                                             @NonNull CallMetadata callMetadata)
  {
    Log.i(tag, "handleReceivedHangup(): id: " + callMetadata.getCallId().format(callMetadata.getRemoteDevice()));

    try {
      webRtcInteractor.getCallManager().receivedHangup(callMetadata.getCallId(), callMetadata.getRemoteDevice(), CallManager.HangupType.NORMAL, callMetadata.getRemoteDevice());
    } catch (CallException e) {
      return callFailure(currentState, "receivedHangup() failed: ", e);
    }

    return currentState;
  }

  protected @NonNull WebRtcServiceState handleLocalHangup(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleLocalHangup not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleSendHangup(@NonNull WebRtcServiceState currentState,
                                                         @NonNull CallMetadata callMetadata,
                                                         boolean broadcast)
  {
    Log.i(tag, "handleSendHangup(): id: " + callMetadata.getCallId().format(callMetadata.getRemoteDevice()));

    HangupMessage            hangupMessage       = new HangupMessage(callMetadata.getCallId().longValue());
    SignalServiceCallMessage callMessage         = SignalServiceCallMessage.forHangup(hangupMessage);

    webRtcInteractor.sendCallMessage(callMetadata.getRemotePeer(), callMessage);

    return currentState;
  }

  protected @NonNull WebRtcServiceState handleMessageSentSuccess(@NonNull WebRtcServiceState currentState, @NonNull CallId callId) {
    try {
      webRtcInteractor.getCallManager().messageSent(callId);
    } catch (CallException e) {
      return callFailure(currentState, "callManager.messageSent() failed: ", e);
    }
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleMessageSentError(@NonNull WebRtcServiceState currentState,
                                                               @NonNull CallId callId,
                                                               @NonNull WebRtcViewModel.State errorCallState,
                                                               @NonNull Optional<IdentityKey> identityKey)
  {
    Log.w(tag, "handleMessageSentError():");

    try {
      webRtcInteractor.getCallManager().messageSendFailure(callId);
    } catch (CallException e) {
      currentState = callFailure(currentState, "callManager.messageSendFailure() failed: ", e);
    }

    RemotePeer activePeer = currentState.getCallInfoState().getActivePeer();
    if (activePeer == null) {
      return currentState;
    }

    WebRtcServiceStateBuilder builder = currentState.builder();

    if (errorCallState == WebRtcViewModel.State.UNTRUSTED_IDENTITY) {
      CallParticipant participant = Objects.requireNonNull(currentState.getCallInfoState().getRemoteCallParticipant(activePeer.getRecipient()));
      CallParticipant untrusted   = participant.withIdentityKey(identityKey.orNull());

      builder.changeCallInfoState()
             .callState(WebRtcViewModel.State.UNTRUSTED_IDENTITY)
             .putParticipant(activePeer.getRecipient(), untrusted)
             .commit();
    } else {
      builder.changeCallInfoState()
             .callState(errorCallState)
             .commit();
    }

    return builder.build();
  }

  //endregion Active call

  //region Call setup

  protected @NonNull WebRtcServiceState handleSendIceCandidates(@NonNull WebRtcServiceState currentState, @NonNull CallMetadata callMetadata, boolean broadcast, @NonNull List<IceCandidate> iceCandidates) {
    Log.i(tag, "handleSendIceCandidates not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleReceivedIceCandidates(@NonNull WebRtcServiceState currentState, @NonNull CallMetadata callMetadata, @NonNull List<IceCandidate> iceCandidates) {
    Log.i(tag, "handleReceivedIceCandidates(): id: " + callMetadata.getCallId().format(callMetadata.getRemoteDevice()) + ", count: " + iceCandidates.size());

    try {
      webRtcInteractor.getCallManager().receivedIceCandidates(callMetadata.getCallId(), callMetadata.getRemoteDevice(), iceCandidates);
    } catch (CallException e) {
      return callFailure(currentState, "receivedIceCandidates() failed: ", e);
    }

    return currentState;
  }

  public @NonNull WebRtcServiceState handleTurnServerUpdate(@NonNull WebRtcServiceState currentState, @NonNull List<PeerConnection.IceServer> iceServers, boolean isAlwaysTurn) {
    Log.i(tag, "handleTurnServerUpdate not processed");
    return currentState;
  }

  //endregion Call setup

  //region Local device

  protected @NonNull WebRtcServiceState handleSetEnableVideo(@NonNull WebRtcServiceState currentState, boolean enable) {
    Log.i(tag, "handleSetEnableVideo not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleSetMuteAudio(@NonNull WebRtcServiceState currentState, boolean muted) {
    Log.i(tag, "handleSetMuteAudio not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleSetSpeakerAudio(@NonNull WebRtcServiceState currentState, boolean isSpeaker) {
    Log.i(tag, "handleSetSpeakerAudio not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleSetBluetoothAudio(@NonNull WebRtcServiceState currentState, boolean isBluetooth) {
    Log.i(tag, "handleSetBluetoothAudio not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleSetCameraFlip(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleSetCameraFlip not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleScreenOffChange(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleScreenOffChange not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleBluetoothChange(@NonNull WebRtcServiceState currentState, boolean available) {
    Log.i(tag, "handleBluetoothChange not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleWiredHeadsetChange(@NonNull WebRtcServiceState currentState, boolean present) {
    Log.i(tag, "handleWiredHeadsetChange not processed");
    return currentState;
  }

  public @NonNull WebRtcServiceState handleCameraSwitchCompleted(@NonNull WebRtcServiceState currentState, @NonNull CameraState newCameraState) {
    Log.i(tag, "handleCameraSwitchCompleted not processed");
    return currentState;
  }

  public @NonNull WebRtcServiceState handleNetworkChanged(@NonNull WebRtcServiceState currentState, boolean available) {
    Log.i(tag, "handleNetworkChanged not processed");
    return currentState;
  }

  //endregion Local device

  //region End call

  protected @NonNull WebRtcServiceState handleEndedRemote(@NonNull WebRtcServiceState currentState, @NonNull CallManager.CallEvent endedRemoteEvent, @NonNull RemotePeer remotePeer) {
    Log.i(tag, "handleEndedRemote not processed");
    return currentState;
  }

  //endregion End call

  //region End call failure

  protected @NonNull WebRtcServiceState handleEnded(@NonNull WebRtcServiceState currentState, @NonNull CallManager.CallEvent endedEvent, @NonNull RemotePeer remotePeer) {
    Log.i(tag, "handleEnded not processed");
    return currentState;
  }

  //endregion

  //region Local call failure

  protected @NonNull WebRtcServiceState handleSetupFailure(@NonNull WebRtcServiceState currentState, @NonNull CallId callId) {
    Log.i(tag, "handleSetupFailure not processed");
    return currentState;
  }

  //endregion

  //region Global call operations

  public @NonNull WebRtcServiceState callFailure(@NonNull WebRtcServiceState currentState,
                                                 @Nullable String message,
                                                 @Nullable Throwable error)
  {
    Log.w(tag, "callFailure(): " + message, error);

    WebRtcServiceStateBuilder builder = currentState.builder();

    if (currentState.getCallInfoState().getActivePeer() != null) {
      builder.changeCallInfoState()
             .callState(WebRtcViewModel.State.CALL_DISCONNECTED);
    }

    try {
      webRtcInteractor.getCallManager().reset();
    } catch (CallException e) {
      Log.w(tag, "Unable to reset call manager: ", e);
    }

    currentState = builder.changeCallInfoState().clearPeerMap().build();
    return terminate(currentState, currentState.getCallInfoState().getActivePeer());
  }

  public synchronized @NonNull WebRtcServiceState terminate(@NonNull WebRtcServiceState currentState, @Nullable RemotePeer remotePeer) {
    Log.i(tag, "terminate():");

    RemotePeer activePeer = currentState.getCallInfoState().getActivePeer();

    if (activePeer == null) {
      Log.i(tag, "skipping with no active peer");
      return currentState;
    }

    if (!activePeer.callIdEquals(remotePeer)) {
      Log.i(tag, "skipping remotePeer is not active peer");
      return currentState;
    }

    ApplicationDependencies.getAppForegroundObserver().removeListener(webRtcInteractor.getForegroundListener());

    webRtcInteractor.updatePhoneState(LockManager.PhoneState.PROCESSING);
    boolean playDisconnectSound = (activePeer.getState() == CallState.DIALING) ||
                                  (activePeer.getState() == CallState.REMOTE_RINGING) ||
                                  (activePeer.getState() == CallState.RECEIVED_BUSY) ||
                                  (activePeer.getState() == CallState.CONNECTED);
    webRtcInteractor.stopAudio(playDisconnectSound);

    webRtcInteractor.setWantsBluetoothConnection(false);
    webRtcInteractor.updatePhoneState(LockManager.PhoneState.IDLE);
    webRtcInteractor.stopForegroundService();

    return WebRtcVideoUtil.deinitializeVideo(currentState)
                          .builder()
                          .changeCallInfoState()
                          .activePeer(null)
                          .commit()
                          .changeLocalDeviceState()
                          .wantsBluetooth(false)
                          .commit()
                          .actionProcessor(currentState.getCallInfoState().getCallState() == WebRtcViewModel.State.CALL_DISCONNECTED ? new DisconnectingCallActionProcessor(webRtcInteractor) : new IdleActionProcessor(webRtcInteractor))
                          .terminate()
                          .build();
  }

  //endregion
}
