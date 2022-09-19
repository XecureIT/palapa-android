package org.thoughtcrime.securesms.service.webrtc.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.ringrtc.Camera;
import org.webrtc.EglBase;
import org.webrtc.SurfaceViewRenderer;

import java.util.Objects;

/**
 * Local device video state and infrastructure.
 */
public final class VideoState {
  EglBase             eglBase;
  Camera              camera;
  SurfaceViewRenderer localRenderer;
  SurfaceViewRenderer remoteRenderer;

  VideoState() {
    this(null, null, null, null);
  }

  VideoState(@NonNull VideoState toCopy) {
    this(toCopy.eglBase, toCopy.camera, toCopy.localRenderer, toCopy.remoteRenderer);
  }

  VideoState(@Nullable EglBase eglBase, @Nullable Camera camera, @Nullable SurfaceViewRenderer localRenderer, @Nullable SurfaceViewRenderer remoteRenderer) {
    this.eglBase        = eglBase;
    this.camera         = camera;
    this.localRenderer  = localRenderer;
    this.remoteRenderer = remoteRenderer;
  }

  public @Nullable EglBase getEglBase() {
    return eglBase;
  }

  public @NonNull EglBase requireEglBase() {
    return Objects.requireNonNull(eglBase);
  }

  public @Nullable Camera getCamera() {
    return camera;
  }

  public @NonNull Camera requireCamera() {
    return Objects.requireNonNull(camera);
  }

  public @Nullable SurfaceViewRenderer getLocalRenderer() {
    return localRenderer;
  }

  public @NonNull SurfaceViewRenderer requireLocalRenderer() {
    return Objects.requireNonNull(localRenderer);
  }

  public @Nullable SurfaceViewRenderer getRemoteRenderer() {
    return remoteRenderer;
  }

  public @NonNull SurfaceViewRenderer requireRemoteRenderer() {
    return Objects.requireNonNull(remoteRenderer);
  }
}
