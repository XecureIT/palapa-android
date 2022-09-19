package org.thoughtcrime.securesms.service.webrtc;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.ResultReceiver;
import android.util.Pair;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.greenrobot.eventbus.EventBus;
import org.signal.core.util.ThreadUtil;
import org.signal.ringrtc.CallException;
import org.signal.ringrtc.CallId;
import org.signal.ringrtc.CallManager;
import org.signal.ringrtc.Remote;
import org.thoughtcrime.securesms.WebRtcCallActivity;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.ringrtc.CameraEventListener;
import org.thoughtcrime.securesms.ringrtc.CameraState;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.util.AppForegroundObserver;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager;
import org.thoughtcrime.securesms.webrtc.locks.LockManager;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;
import org.whispersystems.signalservice.api.messages.calls.TurnServerInfo;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.thoughtcrime.securesms.events.WebRtcViewModel.State.*;

/**
 * Entry point for all things calling. Lives for the life of the app instance and will spin up a foreground service when needed to
 * handle "active" calls.
 */
public final class SignalCallManager implements CallManager.Observer, CameraEventListener, AppForegroundObserver.Listener {

  private static final String TAG = Log.tag(SignalCallManager.class);

  public static final int BUSY_TONE_LENGTH = 2000;

  @Nullable private final CallManager callManager;

  private final Context                     context;
  private final SignalServiceMessageSender  messageSender;
  private final SignalServiceAccountManager accountManager;
  private final ExecutorService             serviceExecutor;
  private final Executor                    networkExecutor;
  private       LockManager                 lockManager;

  private WebRtcServiceState serviceState;

  public SignalCallManager(@NonNull Application application) {
    this.context         = application.getApplicationContext();
    this.messageSender   = ApplicationDependencies.getSignalServiceMessageSender();
    this.accountManager  = ApplicationDependencies.getSignalServiceAccountManager();
    ThreadUtil.runOnMainSync(() -> {
      this.lockManager   = new LockManager(this.context);
    });
    this.serviceExecutor = Executors.newSingleThreadExecutor();
    this.networkExecutor = Executors.newSingleThreadExecutor();

    CallManager callManager = null;
    try {
      callManager = CallManager.createCallManager(this);
    } catch (CallException e) {
      Log.w(TAG, "Unable to create CallManager", e);
    }
    this.callManager = callManager;

    this.serviceState = new WebRtcServiceState(new IdleActionProcessor(new WebRtcInteractor(this.context,
                                                                                            this,
                                                                                            lockManager,
                                                                                            new SignalAudioManager(context),
                                                                                            this,
                                                                                            this)));
  }

  @NonNull CallManager getRingRtcCallManager() {
    //noinspection ConstantConditions
    return callManager;
  }

  @NonNull LockManager getLockManager() {
    return lockManager;
  }

  private void process(@NonNull ProcessAction action) {
    if (callManager == null) {
      Log.w(TAG, "Unable to process action, call manager is not initialized");
      return;
    }

    serviceExecutor.execute(() -> {
      Log.v(TAG, "Processing action, handler: " + serviceState.getActionProcessor().getTag());
      WebRtcServiceState previous = serviceState;
      serviceState = action.process(previous, previous.getActionProcessor());

      if (previous != serviceState) {
        if (serviceState.getCallInfoState().getCallState() != WebRtcViewModel.State.IDLE) {
          postStateUpdate(serviceState);
        }
      }
    });
  }

  public void startOutgoingAudioCall(@NonNull Recipient recipient) {
    process((s, p) -> p.handleOutgoingCall(s, new RemotePeer(recipient.getId())));
  }

  public void startOutgoingVideoCall(@NonNull Recipient recipient) {
    process((s, p) -> p.handleOutgoingCall(s, new RemotePeer(recipient.getId())));
  }

  public void setAudioSpeaker(boolean isSpeaker) {
    process((s, p) -> p.handleSetSpeakerAudio(s, isSpeaker));
  }

  public void setAudioBluetooth(boolean isBluetooth) {
    process((s, p) -> p.handleSetBluetoothAudio(s, isBluetooth));
  }

  public void setMuteAudio(boolean enabled) {
    process((s, p) -> p.handleSetMuteAudio(s, enabled));
  }

  public void setMuteVideo(boolean enabled) {
    process((s, p) -> p.handleSetEnableVideo(s, enabled));
  }

  public void flipCamera() {
    process((s, p) -> p.handleSetCameraFlip(s));
  }

  public void acceptCall(boolean answerWithVideo) {
    process((s, p) -> p.handleAcceptCall(s));
  }

  public void denyCall() {
    process((s, p) -> p.handleDenyCall(s));
  }

  public void localHangup() {
    process((s, p) -> p.handleLocalHangup(s));
  }

  public void isCallActive(@Nullable ResultReceiver resultReceiver) {
    process((s, p) -> p.handleIsInCallQuery(s, resultReceiver));
  }

  public void wiredHeadsetChange(boolean available) {
    process((s, p) -> p.handleWiredHeadsetChange(s, available));
  }

  public void screenOff() {
    process((s, p) -> p.handleScreenOffChange(s));
  }

  public void bluetoothChange(boolean available) {
    process((s, p) -> p.handleBluetoothChange(s, available));
  }

  public void postStateUpdate(@NonNull WebRtcServiceState state) {
    EventBus.getDefault().postSticky(new WebRtcViewModel(state));
  }

  public void receivedOffer(@NonNull WebRtcData.CallMetadata callMetadata,
                            @NonNull WebRtcData.OfferMetadata offerMetadata,
                            @NonNull WebRtcData.ReceivedOfferMetadata receivedOfferMetadata)
  {
    process((s, p) -> p.handleReceivedOffer(s, callMetadata, offerMetadata, receivedOfferMetadata));
  }

  public void receivedAnswer(@NonNull WebRtcData.CallMetadata callMetadata,
                             @NonNull WebRtcData.AnswerMetadata answerMetadata)
  {
    process((s, p) -> p.handleReceivedAnswer(s, callMetadata, answerMetadata));
  }

  public void receivedIceCandidates(@NonNull WebRtcData.CallMetadata callMetadata, @NonNull List<IceCandidate> iceCandidates) {
    process((s, p) -> p.handleReceivedIceCandidates(s, callMetadata, iceCandidates));
  }

  public void receivedCallHangup(@NonNull WebRtcData.CallMetadata callMetadata) {
    process((s, p) -> p.handleReceivedHangup(s, callMetadata));
  }

  public void receivedCallBusy(@NonNull WebRtcData.CallMetadata callMetadata) {
    process((s, p) -> p.handleReceivedBusy(s, callMetadata));
  }

  public boolean startCallCardActivityIfPossible() {
    if (Build.VERSION.SDK_INT >= 29 && !ApplicationDependencies.getAppForegroundObserver().isForegrounded()) {
      return false;
    }

    context.startActivity(new Intent(context, WebRtcCallActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    return true;
  }

  @Override
  public void onStartCall(@Nullable Remote remote,
                          @NonNull CallId callId,
                          @NonNull Boolean isOutgoing)
  {
    Log.i(TAG, "onStartCall(): callId: " + callId + ", outgoing: " + isOutgoing);

    if (callManager == null) {
      Log.w(TAG, "Unable to start call, call manager is not initialized");
      return;
    }

    if (remote == null) {
      return;
    }

    process((s, p) -> {
      RemotePeer remotePeer = (RemotePeer) remote;
      if (s.getCallInfoState().getPeer(remotePeer.hashCode()) == null) {
        Log.w(TAG, "remotePeer not found in map with key: " + remotePeer.hashCode() + "! Dropping.");
        try {
          callManager.drop(callId);
        } catch (CallException e) {
          s = p.callFailure(s, "callManager.drop() failed: ", e);
        }
      }

      remotePeer.setCallId(callId);

      if (isOutgoing) {
        return p.handleStartOutgoingCall(s, remotePeer);
      } else {
        return p.handleStartIncomingCall(s, remotePeer);
      }
    });
  }

  @Override
  public void onCallEvent(@Nullable Remote remote, @NonNull CallManager.CallEvent event) {
    if (callManager == null) {
      Log.w(TAG, "Unable to process call event, call manager is not initialized");
      return;
    }

    if (!(remote instanceof RemotePeer)) {
      return;
    }

    process((s, p) -> {
      RemotePeer remotePeer = (RemotePeer) remote;
      if (s.getCallInfoState().getPeer(remotePeer.hashCode()) == null) {
        Log.w(TAG, "remotePeer not found in map with key: " + remotePeer.hashCode() + "! Dropping.");
        try {
          callManager.drop(remotePeer.getCallId());
        } catch (CallException e) {
          return p.callFailure(s, "callManager.drop() failed: ", e);
        }
        return s;
      }

      Log.i(TAG, "onCallEvent(): call_id: " + remotePeer.getCallId() + ", state: " + remotePeer.getState() + ", event: " + event);

      switch (event) {
        case LOCAL_RINGING:
          return p.handleLocalRinging(s, remotePeer);
        case REMOTE_RINGING:
          return p.handleRemoteRinging(s, remotePeer);
        case RECONNECTING:
          Log.i(TAG, "Reconnecting: NOT IMPLEMENTED");
          break;
        case RECONNECTED:
          Log.i(TAG, "Reconnected: NOT IMPLEMENTED");
          break;
        case LOCAL_CONNECTED:
        case REMOTE_CONNECTED:
          return p.handleCallConnected(s, remotePeer);
        case REMOTE_VIDEO_ENABLE:
          return p.handleRemoteVideoEnable(s, true);
        case REMOTE_VIDEO_DISABLE:
          return p.handleRemoteVideoEnable(s, false);
        case ENDED_REMOTE_HANGUP:
        case ENDED_REMOTE_BUSY:
        case ENDED_REMOTE_GLARE:
          return p.handleEndedRemote(s, event, remotePeer);
        case ENDED_TIMEOUT:
        case ENDED_INTERNAL_FAILURE:
        case ENDED_SIGNALING_FAILURE:
        case ENDED_CONNECTION_FAILURE:
          return p.handleEnded(s, event, remotePeer);
        case ENDED_RECEIVED_OFFER_EXPIRED:
          return p.handleReceivedOfferExpired(s, remotePeer);
        case ENDED_RECEIVED_OFFER_WHILE_ACTIVE:
          return p.handleReceivedOfferWhileActive(s, remotePeer);
        case ENDED_LOCAL_HANGUP:
        case ENDED_APP_DROPPED_CALL:
          Log.i(TAG, "Ignoring event: " + event);
          break;
        default:
          throw new AssertionError("Unexpected event: " + event.toString());
      }

      return s;
    });
  }

  @Override
  public void onCallConcluded(@Nullable Remote remote) {
    if (!(remote instanceof RemotePeer)) {
      return;
    }

    RemotePeer remotePeer = (RemotePeer) remote;
    Log.i(TAG, "onCallConcluded: call_id: " + remotePeer.getCallId());
    process((s, p) -> p.handleCallConcluded(s, remotePeer));
  }

  @Override
  public void onSendOffer(@NonNull CallId callId,
                          @Nullable Remote remote,
                          @NonNull Integer remoteDevice,
                          @NonNull Boolean broadcast,
                          @NonNull String offer,
                          @NonNull CallManager.CallMediaType callMediaType)
  {
    Log.i(TAG, "onSendOffer: id: " + callId.format(remoteDevice));

    if (!(remote instanceof RemotePeer)) {
      return;
    }

    RemotePeer        remotePeer = (RemotePeer) remote;

    WebRtcData.CallMetadata  callMetadata  = new WebRtcData.CallMetadata(remotePeer, callId, remoteDevice);
    WebRtcData.OfferMetadata offerMetadata = new WebRtcData.OfferMetadata(offer);

    process((s, p) -> p.handleSendOffer(s, callMetadata, offerMetadata, broadcast));
  }

  @Override
  public void onSendAnswer(@NonNull CallId callId,
                           @Nullable Remote remote,
                           @NonNull Integer remoteDevice,
                           @NonNull Boolean broadcast,
                           @NonNull String answer)
  {
    Log.i(TAG, "onSendAnswer: id: " + callId.format(remoteDevice));

    if (!(remote instanceof RemotePeer)) {
      return;
    }

    RemotePeer                remotePeer     = (RemotePeer) remote;
    WebRtcData.CallMetadata   callMetadata   = new WebRtcData.CallMetadata(remotePeer, callId, remoteDevice);
    WebRtcData.AnswerMetadata answerMetadata = new WebRtcData.AnswerMetadata(answer);

    process((s, p) -> p.handleSendAnswer(s, callMetadata, answerMetadata, broadcast));
  }

  @Override
  public void onSendIceCandidates(@NonNull CallId callId,
                                  @Nullable Remote remote,
                                  @NonNull Integer remoteDevice,
                                  @NonNull Boolean broadcast,
                                  @NonNull List<IceCandidate> iceCandidates)
  {
    Log.i(TAG, "onSendIceCandidates: id: " + callId.format(remoteDevice));

    if (!(remote instanceof RemotePeer)) {
      return;
    }

    RemotePeer              remotePeer   = (RemotePeer) remote;
    WebRtcData.CallMetadata callMetadata = new WebRtcData.CallMetadata(remotePeer, callId, remoteDevice);

    process((s, p) -> p.handleSendIceCandidates(s, callMetadata, broadcast, iceCandidates));
  }

  @Override
  public void onSendHangup(@NonNull CallId callId, @Nullable Remote remote, @NonNull Integer remoteDevice, @NonNull Boolean broadcast, @NonNull CallManager.HangupType hangupType, @NonNull Integer deviceId, @NonNull Boolean useLegacyHangupMessage) {
    Log.i(TAG, "onSendHangup: id: " + callId.format(remoteDevice));

    if (!(remote instanceof RemotePeer)) {
      return;
    }

    RemotePeer                remotePeer     = (RemotePeer) remote;
    WebRtcData.CallMetadata   callMetadata   = new WebRtcData.CallMetadata(remotePeer, callId, remoteDevice);

    process((s, p) -> hangupType == CallManager.HangupType.NORMAL ? p.handleSendHangup(s, callMetadata, broadcast) : p.handleMessageSentSuccess(s, callId));
  }

  @Override
  public void onSendBusy(@NonNull CallId callId, @Nullable Remote remote, @NonNull Integer remoteDevice, @NonNull Boolean broadcast) {
    Log.i(TAG, "onSendBusy: id: " + callId.format(remoteDevice));

    if (!(remote instanceof RemotePeer)) {
      return;
    }

    RemotePeer              remotePeer   = (RemotePeer) remote;
    WebRtcData.CallMetadata callMetadata = new WebRtcData.CallMetadata(remotePeer, callId, remoteDevice);

    process((s, p) -> p.handleSendBusy(s, callMetadata, broadcast));
  }

  @Override
  public void onCameraSwitchCompleted(@NonNull final CameraState newCameraState) {
    process((s, p) -> p.handleCameraSwitchCompleted(s, newCameraState));
  }

  @Override
  public void onForeground() {
    process((s, p) -> {
      WebRtcViewModel.State callState = s.getCallInfoState().getCallState();
      if (callState == CALL_INCOMING) {
        startCallCardActivityIfPossible();
      }
      ApplicationDependencies.getAppForegroundObserver().removeListener(this);
      return s;
    });
  }

  public void insertMissedCall(@NonNull RemotePeer remotePeer, boolean signal) {
    Pair<Long, Long> messageAndThreadId = DatabaseFactory.getSmsDatabase(context)
                                                         .insertMissedCall(remotePeer.getId());

    MessageNotifier.updateNotification(context, messageAndThreadId.second, signal);
  }

  public void retrieveTurnServers(@NonNull RemotePeer remotePeer) {
    networkExecutor.execute(() -> {
      try {
        TurnServerInfo turnServerInfo = accountManager.getTurnServerInfo();

        List<PeerConnection.IceServer> iceServers = new LinkedList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        for (String url : turnServerInfo.getUrls()) {
          Log.i(TAG, "ice_server: " + url);
          if (url.startsWith("turn")) {
            iceServers.add(PeerConnection.IceServer.builder(url)
                                                   .setUsername(turnServerInfo.getUsername())
                                                   .setPassword(turnServerInfo.getPassword())
                                                   .createIceServer());
          } else {
            iceServers.add(PeerConnection.IceServer.builder(url).createIceServer());
          }
        }

        process((s, p) -> p.handleTurnServerUpdate(s, iceServers, TextSecurePreferences.isTurnOnly(context)));
      } catch (IOException e) {
        Log.w(TAG, "Unable to retrieve turn servers: ", e);
        process((s, p) -> p.handleSetupFailure(s, remotePeer.getCallId()));
      }
    });
  }

  public void sendCallMessage(@NonNull final RemotePeer remotePeer,
                              @NonNull final SignalServiceCallMessage callMessage)
  {
    networkExecutor.execute(() -> {
      Recipient recipient = Recipient.resolved(remotePeer.getId());
      if (recipient.isBlocked()) {
        return;
      }

      try {
        messageSender.sendCallMessage(RecipientUtil.toSignalServiceAddress(context, recipient),
                                      UnidentifiedAccessUtil.getAccessFor(context, recipient),
                                      callMessage);
        process((s, p) -> p.handleMessageSentSuccess(s, remotePeer.getCallId()));
      } catch (UntrustedIdentityException e) {
        processSendMessageFailureWithChangeDetection(remotePeer,
                                                     (s, p) -> p.handleMessageSentError(s,
                                                                                        remotePeer.getCallId(),
                                                                                        UNTRUSTED_IDENTITY,
                                                                                        Optional.fromNullable(e.getIdentityKey())));
      } catch (IOException e) {
        processSendMessageFailureWithChangeDetection(remotePeer,
                                                     (s, p) -> p.handleMessageSentError(s,
                                                                                        remotePeer.getCallId(),
                                                                                        e instanceof UnregisteredUserException ? NO_SUCH_USER : NETWORK_FAILURE,
                                                                                        Optional.absent()));
      }
    });
  }

  private void processSendMessageFailureWithChangeDetection(@NonNull RemotePeer remotePeer,
                                                            @NonNull ProcessAction failureProcessAction)
  {
    process((s, p) -> {
      RemotePeer activePeer = s.getCallInfoState().getActivePeer();

      boolean stateChanged = activePeer == null ||
                             remotePeer.getState() != activePeer.getState() ||
                             !remotePeer.getCallId().equals(activePeer.getCallId());

      if (stateChanged) {
        return p.handleMessageSentSuccess(s, remotePeer.getCallId());
      } else {
        return failureProcessAction.process(s, p);
      }
    });
  }

  interface ProcessAction {
    @NonNull WebRtcServiceState process(@NonNull WebRtcServiceState currentState, @NonNull WebRtcActionProcessor processor);
  }
}
