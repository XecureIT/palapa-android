package org.thoughtcrime.securesms.service.webrtc;

import android.media.AudioManager;
import androidx.annotation.NonNull;
import org.signal.ringrtc.CallException;
import org.signal.ringrtc.CallManager;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.CallParticipantId;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.util.ServiceUtil;

import static org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.TYPE_INCOMING_CONNECTING;

/**
 * Encapsulates the logic to begin a 1:1 call from scratch. Other action processors
 * delegate the appropriate action to it but it is not intended to be the main processor for the system.
 */
public class BeginCallActionProcessorDelegate extends WebRtcActionProcessor {

  public BeginCallActionProcessorDelegate(@NonNull WebRtcInteractor webRtcInteractor, @NonNull String tag) {
    super(webRtcInteractor, tag);
  }

  @Override
  protected @NonNull WebRtcServiceState handleOutgoingCall(@NonNull WebRtcServiceState currentState,
                                                           @NonNull RemotePeer remotePeer)
  {
    remotePeer.setCallStartTimestamp(System.currentTimeMillis());
    currentState = currentState.builder()
                               .actionProcessor(new OutgoingCallActionProcessor(webRtcInteractor))
                               .changeCallInfoState()
                               .callRecipient(remotePeer.getRecipient())
                               .callState(WebRtcViewModel.State.CALL_OUTGOING)
                               .putRemotePeer(remotePeer)
                               .putParticipant(remotePeer.getRecipient(),
                                               CallParticipant.createRemote(
                                                       new CallParticipantId(remotePeer.getRecipient()),
                                                       remotePeer.getRecipient(),
                                                       null,
                                                       currentState.getVideoState().requireRemoteRenderer(),
                                                       true,
                                                       false,
                                                       0,
                                                       true,
                                                       0,
                                                       CallParticipant.DeviceOrdinal.PRIMARY
                                               ))
                               .build();

    try {
      webRtcInteractor.getCallManager().call(remotePeer, CallManager.CallMediaType.AUDIO_CALL);
    } catch (CallException e) {
      return callFailure(currentState, "Unable to create outgoing call: ", e);
    }

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleStartIncomingCall(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    remotePeer.answering();

    Log.i(tag, "assign activePeer callId: " + remotePeer.getCallId() + " key: " + remotePeer.hashCode());

    AudioManager androidAudioManager = ServiceUtil.getAudioManager(context);
    androidAudioManager.setSpeakerphoneOn(false);

    webRtcInteractor.setCallInProgressNotification(TYPE_INCOMING_CONNECTING, remotePeer);
    webRtcInteractor.retrieveTurnServers(remotePeer);

    return currentState.builder()
                       .actionProcessor(new IncomingCallActionProcessor(webRtcInteractor))
                       .changeCallInfoState()
                       .callRecipient(remotePeer.getRecipient())
                       .activePeer(remotePeer)
                       .callState(WebRtcViewModel.State.CALL_INCOMING)
                       .putParticipant(remotePeer.getRecipient(),
                                       CallParticipant.createRemote(
                                               new CallParticipantId(remotePeer.getRecipient()),
                                               remotePeer.getRecipient(),
                                               null,
                                               currentState.getVideoState().requireRemoteRenderer(),
                                               true,
                                               false,
                                               0,
                                               true,
                                               0,
                                               CallParticipant.DeviceOrdinal.PRIMARY
                                       ))
                       .build();
  }
}
