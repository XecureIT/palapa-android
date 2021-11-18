package org.thoughtcrime.securesms.conversation;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.AdapterView;
import android.widget.RelativeLayout;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.core.util.Preconditions;
import androidx.vectordrawable.graphics.drawable.AnimatorInflaterCompat;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.animation.AnimationCompleteListener;
import org.thoughtcrime.securesms.components.MaskView;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.MessageRecordUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.List;

public final class ConversationReactionOverlayListRoom extends RelativeLayout {

  private static final Interpolator INTERPOLATOR = new DecelerateInterpolator();

  private final Rect  emojiViewGlobalRect = new Rect();
  private final Rect  emojiStripViewBounds = new Rect();
  private       float segmentSize;

  private final ConversationReactionOverlayListRoom.Boundary horizontalEmojiBoundary = new ConversationReactionOverlayListRoom.Boundary();
  private final ConversationReactionOverlayListRoom.Boundary verticalScrubBoundary   = new ConversationReactionOverlayListRoom.Boundary();
  private final PointF   deadzoneTouchPoint      = new PointF();
  private final PointF   lastSeenDownPoint       = new PointF();

  private Activity      activity;
  private MessageRecord messageRecord;
  private ConversationReactionOverlayListRoom.OverlayState overlayState = ConversationReactionOverlayListRoom.OverlayState.HIDDEN;

  private boolean downIsOurs;
  private boolean isToolbarTouch;
  private int     selected = -1;
  private int     originalStatusBarColor;

  private View             backgroundView;
  private ConstraintLayout foregroundView;
  private View             selectedView;
  private MaskView         maskView;
  private ConversationReactionOverlayListRoom reactionOverlay;
  private Toolbar          toolbar;

  private float touchDownDeadZoneSize;
  private float distanceFromTouchDownPointToTopOfScrubberDeadZone;
  private float distanceFromTouchDownPointToBottomOfScrubberDeadZone;
  private int   scrubberDistanceFromTouchDown;
  private int   scrubberHeight;
  private int   scrubberWidth;
  private int   halfActionBarHeight;
  private int   selectedVerticalTranslation;
  private int   scrubberHorizontalMargin;
  private int   animationEmojiStartDelayFactor;
  private int   statusBarHeight;
  private View viewList;
  private RelativeLayout relativelayoutHeader;
  private Toolbar toolbarlist;
  private AdapterView<?> argView;
  private ConversationReactionOverlayListRoom.OnReactionSelectedListener onReactionSelectedListener;
  private Toolbar.OnMenuItemClickListener  onToolbarItemClickedListener;
  private ConversationReactionOverlayListRoom.OnHideListener onHideListener;
  private Window window;

  private AnimatorSet hideAnimatorSet   = new AnimatorSet();

  public ConversationReactionOverlayListRoom(@NonNull Context context) {
    super(context);
  }

  public ConversationReactionOverlayListRoom(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public void setWindow(Window window) {    this.window = window;  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    maskView       = findViewById(R.id.conversation_reaction_mask_listroom);
    toolbar        = findViewById(R.id.conversation_reaction_toolbar_listroom);
    reactionOverlay = ViewUtil.findById(this, R.id.conversation_reaction_scrubber_listroom);

    toolbar.setOnMenuItemClickListener(this::handleToolbarItemClicked);
    toolbar.setNavigationOnClickListener(view -> hide());


    distanceFromTouchDownPointToTopOfScrubberDeadZone    = getResources().getDimensionPixelSize(R.dimen.conversation_reaction_scrub_deadzone_distance_from_touch_top);
    distanceFromTouchDownPointToBottomOfScrubberDeadZone = getResources().getDimensionPixelSize(R.dimen.conversation_reaction_scrub_deadzone_distance_from_touch_bottom);

    touchDownDeadZoneSize         = getResources().getDimensionPixelSize(R.dimen.conversation_reaction_touch_deadzone_size);
    scrubberDistanceFromTouchDown = getResources().getDimensionPixelOffset(R.dimen.conversation_reaction_scrubber_distance);
    scrubberHeight                = getResources().getDimensionPixelOffset(R.dimen.conversation_reaction_scrubber_height);
    scrubberWidth                 = getResources().getDimensionPixelOffset(R.dimen.reaction_scrubber_width);
    halfActionBarHeight           = (int) ThemeUtil.getThemedDimen(getContext(), R.attr.actionBarSize) / 2;
    selectedVerticalTranslation   = getResources().getDimensionPixelOffset(R.dimen.conversation_reaction_scrub_vertical_translation);
    scrubberHorizontalMargin      = getResources().getDimensionPixelOffset(R.dimen.conversation_reaction_scrub_horizontal_margin);

    animationEmojiStartDelayFactor = getResources().getInteger(R.integer.reaction_scrubber_emoji_reveal_duration_start_delay_factor);

    initAnimators();
    setupToolbarMenuItems();
  }

  public void show(@NonNull Activity activity, @NonNull View maskTarget, @NonNull MessageRecord messageRecord) {
    if (overlayState != ConversationReactionOverlayListRoom.OverlayState.HIDDEN) {
      return;
    }

    this.messageRecord = messageRecord;
    overlayState       = ConversationReactionOverlayListRoom.OverlayState.UNINITAILIZED;
    selected           = -1;

    setupToolbarMenuItems();

    if (Build.VERSION.SDK_INT >= 21) {
      View statusBarBackground = activity.findViewById(android.R.id.statusBarBackground);
      statusBarHeight = statusBarBackground == null ? 0 : statusBarBackground.getHeight();
    } else {
      statusBarHeight = ViewUtil.getStatusBarHeight(this);
    }

    final float scrubberTranslationY = Math.max(-scrubberDistanceFromTouchDown + halfActionBarHeight,
            lastSeenDownPoint.y - scrubberHeight - scrubberDistanceFromTouchDown - statusBarHeight);

    final float halfWidth            = scrubberWidth / 2f + scrubberHorizontalMargin;
    final float screenWidth          = getResources().getDisplayMetrics().widthPixels;
    final float scrubberTranslationX = Util.clamp(lastSeenDownPoint.x - halfWidth,
            scrubberHorizontalMargin,
            screenWidth + scrubberHorizontalMargin - halfWidth * 2);



    verticalScrubBoundary.update(lastSeenDownPoint.y - distanceFromTouchDownPointToTopOfScrubberDeadZone,
            lastSeenDownPoint.y + distanceFromTouchDownPointToBottomOfScrubberDeadZone);

    maskView.setTarget(maskTarget);

    setVisibility(View.VISIBLE);


  }

  public void hide() {
    maskView.setTarget(null);
    overlayState = OverlayState.HIDDEN;
    if (onHideListener != null) {
            String themeApp = TextSecurePreferences.getTheme(getContext());
            reactionOverlay.setVisibility(View.GONE);
            viewList.setBackgroundColor(Color.WHITE);
            if((themeApp).equals(DynamicTheme.DARK))
              viewList.setBackgroundColor(Color.parseColor("#121212"));

            relativelayoutHeader.setVisibility(View.GONE);
            toolbarlist.setVisibility(View.VISIBLE);
            argView.setEnabled(true);
            onHideListener.onHide();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
              window.setStatusBarColor(getResources().getColor(R.color.textsecure_primary));
    }

  }

  public boolean isShowing() {
    return overlayState != ConversationReactionOverlayListRoom.OverlayState.HIDDEN;
  }

  public @NonNull MessageRecord getMessageRecord() {
    return messageRecord;
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);


    emojiStripViewBounds.left = emojiViewGlobalRect.left;
    emojiStripViewBounds.right = emojiViewGlobalRect.right;

  }

  public boolean applyTouchEvent(@NonNull MotionEvent motionEvent) {
      return false;
  }


  private int getSelectedIndexViaDownEvent(@NonNull MotionEvent motionEvent) {
    return getSelectedIndexViaMotionEvent(motionEvent, new ConversationReactionOverlayListRoom.Boundary(emojiStripViewBounds.top, emojiStripViewBounds.bottom));
  }

  private int getSelectedIndexViaMoveEvent(@NonNull MotionEvent motionEvent) {
    return getSelectedIndexViaMotionEvent(motionEvent, verticalScrubBoundary);
  }

  private int getSelectedIndexViaMotionEvent(@NonNull MotionEvent motionEvent, @NonNull ConversationReactionOverlayListRoom.Boundary boundary) {
    int selected = -1;




    return selected;
  }

  private void growView(@NonNull View view) {
    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    view.animate()
            .scaleY(1.5f)
            .scaleX(1.5f)
            .translationY(-selectedVerticalTranslation)
            .setDuration(400)
            .setInterpolator(INTERPOLATOR)
            .start();
  }

  private void shrinkView(@NonNull View view) {
    view.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .translationY(0)
            .setDuration(400)
            .setInterpolator(INTERPOLATOR)
            .start();
  }

  private void handleUpEvent() {
    hide();

  }

  public void setOnReactionSelectedListener(@Nullable ConversationReactionOverlayListRoom.OnReactionSelectedListener onReactionSelectedListener) {
    this.onReactionSelectedListener = onReactionSelectedListener;
  }

  public void setOnToolbarItemClickedListener(@Nullable Toolbar.OnMenuItemClickListener onToolbarItemClickedListener) {
    this.onToolbarItemClickedListener = onToolbarItemClickedListener;
  }

  public void setOnHideListener(@Nullable ConversationReactionOverlayListRoom.OnHideListener onHideListener) {
    this.onHideListener = onHideListener;
  }

  public void setView(@Nullable View viewList) {
    this.viewList = viewList;
  }

  public void setRelativelayoutHeader(@Nullable RelativeLayout relativelayoutHeader){
    this.relativelayoutHeader = relativelayoutHeader;
  }

  public void setToolbarlist(Toolbar toolbarlist) {
    this.toolbarlist = toolbarlist;
  }

  public void setArgView(@Nullable AdapterView<?> argView){
    this.argView = argView;
  }

  private static @Nullable String getOldEmoji(@NonNull MessageRecord messageRecord) {
    return Stream.of(messageRecord.getReactions())
            .filter(record -> record.getAuthor()
                    .serialize()
                    .equals(Recipient.self()
                            .getId()
                            .serialize()))
            .findFirst()
            .map(ReactionRecord::getEmoji)
            .orElse(null);
  }

  private void setupToolbarMenuItems() {
    toolbar.getMenu().findItem(R.id.action_copy).setVisible(shouldShowCopy());
    toolbar.getMenu().findItem(R.id.action_share).setVisible(shouldShowShare());
    toolbar.getMenu().findItem(R.id.action_delete).setVisible(shouldShowDeleted());
  }

  private boolean shouldShowCopy() { return true;  }

  private boolean shouldShowShare() {    return true;  }


  private boolean shouldShowDeleted() {
    return true;      }

  private boolean handleToolbarItemClicked(@NonNull MenuItem menuItem) {
    hide();
    if (onToolbarItemClickedListener == null) {
      return false;
    }

    return onToolbarItemClickedListener.onMenuItemClick(menuItem);
  }

  private void initAnimators() {

    int duration = getContext().getResources().getInteger(R.integer.reaction_scrubber_reveal_duration);

  }

  public interface OnHideListener {
    void onHide();
  }

  public interface OnReactionSelectedListener {
    void onReactionSelected(@NonNull MessageRecord messageRecord, String emoji);
  }

  private static class Boundary {
    private float min;
    private float max;

    Boundary() {}

    Boundary(float min, float max) {
      update(min, max);
    }

    private void update(float min, float max) {
      Preconditions.checkArgument(min < max, "Min must be less than max");
      this.min = min;
      this.max = max;
    }

    public boolean contains(float value) {
      return this.min < value && this.max > value;
    }
  }



  private enum OverlayState {
    HIDDEN,
    UNINITAILIZED,
    DEADZONE,
    SCRUB,
    TAP
  }
}