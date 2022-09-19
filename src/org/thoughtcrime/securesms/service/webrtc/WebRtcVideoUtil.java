package org.thoughtcrime.securesms.service.webrtc;

import android.content.Context;
import androidx.annotation.NonNull;
import org.signal.core.util.ThreadUtil;
import org.thoughtcrime.securesms.ringrtc.Camera;
import org.thoughtcrime.securesms.ringrtc.CameraEventListener;
import org.thoughtcrime.securesms.ringrtc.CameraState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceStateBuilder;
import org.webrtc.EglBase;
import org.webrtc.SurfaceViewRenderer;

/**
 * Helper for initializing, reinitializing, and deinitializing the camera and it's related
 * infrastructure.
 */
public final class WebRtcVideoUtil {

  private WebRtcVideoUtil() {}

  public static @NonNull WebRtcServiceState initializeVideo(@NonNull Context context,
                                                            @NonNull CameraEventListener cameraEventListener,
                                                            @NonNull WebRtcServiceState currentState)
  {
    final WebRtcServiceStateBuilder builder = currentState.builder();

    ThreadUtil.runOnMainSync(() -> {
      EglBase             eglBase        = EglBase.create();
      Camera              camera         = new Camera(context, cameraEventListener, eglBase);
      SurfaceViewRenderer localRenderer  = new SurfaceViewRenderer(context);
      SurfaceViewRenderer remoteRenderer = new SurfaceViewRenderer(context);

      localRenderer.init(eglBase.getEglBaseContext(), null);
      remoteRenderer.init(eglBase.getEglBaseContext(), null);

      builder.changeVideoState()
             .eglBase(eglBase)
             .camera(camera)
             .localRenderer(localRenderer)
             .remoteRenderer(remoteRenderer)
             .commit()
             .changeLocalDeviceState()
             .cameraState(camera.getCameraState())
             .commit();
    });

    return builder.build();
  }

  public static @NonNull WebRtcServiceState deinitializeVideo(@NonNull WebRtcServiceState currentState) {
    Camera camera = currentState.getVideoState().getCamera();
    if (camera != null) {
      camera.dispose();
    }

    EglBase eglBase = currentState.getVideoState().getEglBase();
    if (eglBase != null) {
      eglBase.release();
    }

    return currentState.builder()
                       .changeVideoState()
                       .eglBase(null)
                       .camera(null)
                       .commit()
                       .changeLocalDeviceState()
                       .cameraState(CameraState.UNKNOWN)
                       .build();
  }
}
