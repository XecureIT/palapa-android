package org.thoughtcrime.securesms.revealable;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProviders;

import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.video.VideoPlayer;

import java.util.concurrent.TimeUnit;

public class ViewOnceMessageActivity extends PassphraseRequiredActionBarActivity implements VideoPlayer.PlayerStateCallback {

  private static final String TAG = Log.tag(ViewOnceMessageActivity.class);

  private static final String KEY_MESSAGE_ID = "message_id";
  private static final String KEY_URI        = "uri";

  private static final int  OVERLAY_TIMEOUT_S   = 2;
  private static final int  FADE_OUT_DURATION_MS = 200;

  private ImageView                image;
  private VideoPlayer              video;
  private View                     closeButton;
  private TextView                 duration;
  private ViewOnceMessageViewModel viewModel;
  private Uri                      uri;

  private int updateCounter;

  private final Handler  handler                = new Handler(Looper.getMainLooper());
  private final Runnable durationUpdateRunnable = () -> {
    long timeLeft = TimeUnit.MILLISECONDS.toSeconds(video.getDuration()) - updateCounter;
    long minutes  = timeLeft / 60;
    long seconds  = timeLeft % 60;
    duration.setText(getString(R.string.ViewOnceMessageActivity_video_duration, minutes, seconds));
    updateCounter++;
    if (updateCounter > OVERLAY_TIMEOUT_S) {
      animateOutOverlay();
    } else {
      scheduleDurationUpdate();
    }
  };

  public static Intent getIntent(@NonNull Context context, long messageId, @NonNull Uri uri) {
    Intent intent = new Intent(context, ViewOnceMessageActivity.class);
    intent.putExtra(KEY_MESSAGE_ID, messageId);
    intent.putExtra(KEY_URI, uri);
    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    super.onCreate(savedInstanceState, ready);
    setContentView(R.layout.view_once_message_activity);

    this.image       = findViewById(R.id.view_once_image);
    this.video       = findViewById(R.id.view_once_video);
    this.duration    = findViewById(R.id.view_once_duration);
    this.closeButton = findViewById(R.id.view_once_close_button);
    this.uri         = getIntent().getParcelableExtra(KEY_URI);

    ViewOnceGestureListener imageListener = new ViewOnceGestureListener(image);
    GestureDetector         imageDetector = new GestureDetector(this, imageListener);

    ViewOnceGestureListener videoListener = new ViewOnceGestureListener(video);
    GestureDetector         videoDetector = new GestureDetector(this, videoListener);

    image.setOnTouchListener((view, event) -> imageDetector.onTouchEvent(event));
    image.setOnClickListener(v -> finish());

    video.setOnTouchListener((view, event) -> videoDetector.onTouchEvent(event));
    video.setOnClickListener(v -> finish());

    closeButton.setOnClickListener(v -> finish());

    initViewModel(getIntent().getLongExtra(KEY_MESSAGE_ID, -1), uri);
  }

  @Override
  protected void onStop() {
    super.onStop();
    cancelDurationUpdate();
    video.cleanup();
    BlobProvider.getInstance().delete(this, uri);
    finish();
  }

  @Override
  public void onPlayerReady() {
    updateCounter = 0;
    handler.post(durationUpdateRunnable);
  }

  private void initViewModel(long messageId, @NonNull Uri uri) {
    ViewOnceMessageRepository repository = new ViewOnceMessageRepository(this);

    viewModel = ViewModelProviders.of(this, new ViewOnceMessageViewModel.Factory(getApplication(), messageId, repository))
                                  .get(ViewOnceMessageViewModel.class);

    viewModel.getMessage().observe(this, (message) -> {
      if (message == null) return;

      if (message.isPresent()) {
        displayMedia(uri);
      } else {
        image.setImageDrawable(null);
        finish();
      }
    });
  }

  private void displayMedia(@NonNull Uri uri) {
    if (MediaUtil.isVideoType(PartAuthority.getAttachmentContentType(this, uri))) {
      displayVideo(uri);
    } else {
      displayImage(uri);
    }
  }

  private void displayVideo(@NonNull Uri uri) {
    video.setVisibility(View.VISIBLE);
    image.setVisibility(View.GONE);
    duration.setVisibility(View.VISIBLE);

    VideoSlide videoSlide = new VideoSlide(this, uri, 0);

    video.setWindow(getWindow());
    video.setPlayerStateCallbacks(this);
    video.setVideoSource(videoSlide, true);

    video.hideControls();
    video.loopForever();
  }

  private void displayImage(@NonNull Uri uri) {
    video.setVisibility(View.GONE);
    image.setVisibility(View.VISIBLE);
    duration.setVisibility(View.GONE);

    GlideApp.with(this)
            .load(new DecryptableUri(uri))
            .into(image);
  }

  private void animateOutOverlay() {
    duration.animate().alpha(0f).setDuration(200).start();
    closeButton.animate().alpha(0f).setDuration(200).start();
  }

  private void scheduleDurationUpdate() {
    handler.postDelayed(durationUpdateRunnable, 1000L);
  }

  private void cancelDurationUpdate() {
    handler.removeCallbacks(durationUpdateRunnable);
  }

  private class ViewOnceGestureListener extends GestureDetector.SimpleOnGestureListener {

    private final View view;

    private ViewOnceGestureListener(View view) {
      this.view = view;
    }

    @Override
    public boolean onDown(MotionEvent e) {
      return true;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
      view.performClick();
      return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
      finish();
      return true;
    }
  }
}
