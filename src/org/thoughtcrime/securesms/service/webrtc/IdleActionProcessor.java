package org.thoughtcrime.securesms.service.webrtc;

import androidx.annotation.NonNull;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;

/**
 * Action handler for when the system is at rest. Mainly responsible
 * for starting pre-call state, starting an outgoing call, or receiving an
 * incoming call.
 */
public class IdleActionProcessor extends WebRtcActionProcessor {

  private static final String TAG = Log.tag(IdleActionProcessor.class);

  private final BeginCallActionProcessorDelegate beginCallDelegate;

  public IdleActionProcessor(@NonNull WebRtcInteractor webRtcInteractor) {
    super(webRtcInteractor, TAG);
    beginCallDelegate = new BeginCallActionProcessorDelegate(webRtcInteractor, TAG);
  }

  protected @NonNull WebRtcServiceState handleStartIncomingCall(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(TAG, "handleStartIncomingCall():");

    currentState = WebRtcVideoUtil.initializeVideo(context, webRtcInteractor.getCameraEventListener(), currentState);
    return beginCallDelegate.handleStartIncomingCall(currentState, remotePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleOutgoingCall(@NonNull WebRtcServiceState currentState,
                                                           @NonNull RemotePeer remotePeer)
  {
    Log.i(TAG, "handleOutgoingCall():");

    currentState = WebRtcVideoUtil.initializeVideo(context, webRtcInteractor.getCameraEventListener(), currentState);
    return beginCallDelegate.handleOutgoingCall(currentState, remotePeer);
  }
}
