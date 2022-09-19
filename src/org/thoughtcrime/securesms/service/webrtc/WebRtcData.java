package org.thoughtcrime.securesms.service.webrtc;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.signal.ringrtc.CallId;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;

/**
 * Collection of classes to ease passing calling data around.
 */
public class WebRtcData {

  /**
   * Low-level metadata Information about the call.
   */
  public static class CallMetadata {
    private final @NonNull RemotePeer remotePeer;
    private final @NonNull CallId     callId;
    private final          int        remoteDevice;

    public CallMetadata(@NonNull RemotePeer remotePeer, @NonNull CallId callId, int remoteDevice) {
      this.remotePeer   = remotePeer;
      this.callId       = callId;
      this.remoteDevice = remoteDevice;
    }

    @NonNull RemotePeer getRemotePeer() {
      return remotePeer;
    }

    @NonNull CallId getCallId() {
      return callId;
    }

    int getRemoteDevice() {
      return remoteDevice;
    }
  }

  /**
   * Metadata for a call offer to be sent or received.
   */
  public static class OfferMetadata {
    private final @Nullable String sdp;

    public OfferMetadata(@Nullable String sdp) {
      this.sdp = sdp;
    }

    @Nullable String getSdp() {
      return sdp;
    }
  }

  /**
   * Additional metadata for a received call.
   */
  public static class ReceivedOfferMetadata {
    private final          long    timestamp;

    public ReceivedOfferMetadata(long timestamp) {
      this.timestamp         = timestamp;
    }

    long getTimestamp() {
      return timestamp;
    }
  }

  /**
   * Metadata for an answer to be sent or received.
   */
  public static class AnswerMetadata {
    private final @Nullable String sdp;

    public AnswerMetadata(@Nullable String sdp) {
      this.sdp = sdp;
    }

    @Nullable String getSdp() {
      return sdp;
    }
  }
}
