package org.thoughtcrime.securesms.scribbles;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.scribbles.widget.ColorPaletteAdapter;
import org.thoughtcrime.securesms.scribbles.widget.VerticalSlideColorPicker;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The HUD (heads-up display) that contains all of the tools for interacting with
 * {@link org.thoughtcrime.securesms.imageeditor.ImageEditorView}
 */
public final class ImageEditorHud extends LinearLayout {

  private View                     cropButton;
  private View                     cropFlipButton;
  private View                     cropRotateButton;
  private ImageView                cropAspectLock;
  private View                     drawButton;
  private View                     highlightButton;
  private View                     textButton;
  private View                     oldStickerButton;
  private View                     newStickerButton;
  private View                     undoButton;
  private View                     saveButton;
  private View                     deleteButton;
  private View                     confirmButton;
  private VerticalSlideColorPicker colorPicker;
  private RecyclerView             colorPalette;

  @NonNull
  private EventListener              eventListener = NULL_EVENT_LISTENER;
  @Nullable
  private ColorPaletteAdapter        colorPaletteAdapter;

  private final Map<Mode, Set<View>> visibilityModeMap = new HashMap<>();
  private final Set<View>            allViews          = new HashSet<>();

  private Mode    currentMode;
  private boolean undoAvailable;

  public ImageEditorHud(@NonNull Context context) {
    super(context);
    initialize();
  }

  public ImageEditorHud(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public ImageEditorHud(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  private void initialize() {
    inflate(getContext(), R.layout.image_editor_hud, this);
    setOrientation(VERTICAL);

    cropButton       = findViewById(R.id.scribble_crop_button);
    cropFlipButton   = findViewById(R.id.scribble_crop_flip);
    cropRotateButton = findViewById(R.id.scribble_crop_rotate);
    cropAspectLock   = findViewById(R.id.scribble_crop_aspect_lock);
    colorPalette     = findViewById(R.id.scribble_color_palette);
    drawButton       = findViewById(R.id.scribble_draw_button);
    highlightButton  = findViewById(R.id.scribble_highlight_button);
    textButton       = findViewById(R.id.scribble_text_button);
    oldStickerButton = findViewById(R.id.old_scribble_sticker_button);
    newStickerButton = findViewById(R.id.scribble_sticker_button);
    undoButton       = findViewById(R.id.scribble_undo_button);
    saveButton       = findViewById(R.id.scribble_save_button);
    deleteButton     = findViewById(R.id.scribble_delete_button);
    confirmButton    = findViewById(R.id.scribble_confirm_button);
    colorPicker      = findViewById(R.id.scribble_color_picker);

    cropAspectLock.setOnClickListener(v -> {
      eventListener.onCropAspectLock(!eventListener.isCropAspectLocked());
      updateCropAspectLockImage(eventListener.isCropAspectLocked());
    });

    initializeViews();
    initializeVisibilityMap();
    setMode(Mode.NONE);
  }

  private void updateCropAspectLockImage(boolean cropAspectLocked) {
    cropAspectLock.setImageDrawable(getResources().getDrawable(cropAspectLocked ? R.drawable.ic_crop_lock_32 : R.drawable.ic_crop_unlock_32));
  }

  private void initializeVisibilityMap() {
    setStickersAvailable(false);

    setVisibleViewsWhenInMode(Mode.DRAW, confirmButton, undoButton, colorPicker, colorPalette);

    setVisibleViewsWhenInMode(Mode.HIGHLIGHT, confirmButton, undoButton, colorPicker, colorPalette);

    setVisibleViewsWhenInMode(Mode.TEXT, confirmButton, deleteButton, colorPicker, colorPalette);

    setVisibleViewsWhenInMode(Mode.MOVE_DELETE, confirmButton, deleteButton);

    setVisibleViewsWhenInMode(Mode.INSERT_STICKER, confirmButton);

    setVisibleViewsWhenInMode(Mode.INSERT_ASSET_STICKER, confirmButton);

    setVisibleViewsWhenInMode(Mode.CROP, confirmButton, cropFlipButton, cropRotateButton, cropAspectLock, undoButton);

    for (Set<View> views : visibilityModeMap.values()) {
      allViews.addAll(views);
    }

    allViews.add(newStickerButton);
    allViews.add(oldStickerButton);
  }

  private void setVisibleViewsWhenInMode(Mode mode, View... views) {
    visibilityModeMap.put(mode, new HashSet<>(Arrays.asList(views)));
  }

  @MainThread
  public void setStickersAvailable(boolean stickersAvailable) {
    if (stickersAvailable) {
      setVisibleViewsWhenInMode(Mode.NONE, drawButton, highlightButton, textButton, newStickerButton, cropButton, undoButton, saveButton);
    } else {
      setVisibleViewsWhenInMode(Mode.NONE, drawButton, highlightButton, textButton, oldStickerButton, cropButton, undoButton, saveButton);
    }
    updateButtonVisibility(currentMode);
  }

  private void initializeViews() {
    undoButton.setOnClickListener(v -> eventListener.onUndo());

    deleteButton.setOnClickListener(v -> {
      eventListener.onDelete();
      setMode(Mode.NONE);
    });

    cropButton.setOnClickListener(v -> setMode(Mode.CROP));
    cropFlipButton.setOnClickListener(v -> eventListener.onFlipHorizontal());
    cropRotateButton.setOnClickListener(v -> eventListener.onRotate90AntiClockwise());

    confirmButton.setOnClickListener(v -> setMode(Mode.NONE));

    colorPaletteAdapter = new ColorPaletteAdapter();
    colorPaletteAdapter.setEventListener(colorPicker::setActiveColor);

    colorPalette.setLayoutManager(new LinearLayoutManager(getContext()));
    colorPalette.setAdapter(colorPaletteAdapter);

    drawButton.setOnClickListener(v -> setMode(Mode.DRAW));
    highlightButton.setOnClickListener(v -> setMode(Mode.HIGHLIGHT));
    textButton.setOnClickListener(v -> setMode(Mode.TEXT));
    oldStickerButton.setOnClickListener(v -> setMode(Mode.INSERT_ASSET_STICKER));
    newStickerButton.setOnClickListener(v -> setMode(Mode.INSERT_STICKER));
    saveButton.setOnClickListener(v -> eventListener.onSave());
  }

  public void setColorPalette(@NonNull Set<Integer> colors) {
    if (colorPaletteAdapter != null) {
      colorPaletteAdapter.setColors(colors);
    }
  }

  public int getActiveColor() {
    return colorPicker.getActiveColor();
  }

  public void setActiveColor(int color) {
    colorPicker.setActiveColor(color);
  }

  public void setEventListener(@Nullable EventListener eventListener) {
    this.eventListener = eventListener != null ? eventListener : NULL_EVENT_LISTENER;
  }

  public void enterMode(@NonNull Mode mode) {
    setMode(mode, false);
  }

  public void setMode(@NonNull Mode mode) {
    setMode(mode, true);
  }

  private void setMode(@NonNull Mode mode, boolean notify) {
    this.currentMode = mode;
    updateButtonVisibility(mode);

    switch (mode) {
      case CROP:      presentModeCrop();      break;
      case DRAW:      presentModeDraw();      break;
      case HIGHLIGHT: presentModeHighlight(); break;
      case TEXT:      presentModeText();      break;
    }

    if (notify) {
      eventListener.onModeStarted(mode);
    }
    eventListener.onRequestFullScreen(mode != Mode.NONE, mode != Mode.TEXT);
  }

  private void updateButtonVisibility(@NonNull Mode mode) {
    Set<View> visibleButtons = visibilityModeMap.get(mode);
    for (View button : allViews) {
      button.setVisibility(buttonIsVisible(visibleButtons, button) ? VISIBLE : GONE);
    }
  }

  private boolean buttonIsVisible(@Nullable Set<View> visibleButtons, @NonNull View button) {
    return visibleButtons != null &&
           visibleButtons.contains(button) &&
           (button != undoButton || undoAvailable);
  }

  private void presentModeCrop() {
    updateCropAspectLockImage(eventListener.isCropAspectLocked());
  }

  private void presentModeDraw() {
    colorPicker.setOnColorChangeListener(standardOnColorChangeListener);
    colorPicker.setActiveColor(Color.RED);
  }

  private void presentModeHighlight() {
    colorPicker.setOnColorChangeListener(highlightOnColorChangeListener);
    colorPicker.setActiveColor(Color.YELLOW);
  }

  private void presentModeText() {
    colorPicker.setOnColorChangeListener(standardOnColorChangeListener);
    colorPicker.setActiveColor(Color.WHITE);
  }

  private final VerticalSlideColorPicker.OnColorChangeListener standardOnColorChangeListener = selectedColor -> eventListener.onColorChange(selectedColor);

  private final VerticalSlideColorPicker.OnColorChangeListener highlightOnColorChangeListener = selectedColor -> eventListener.onColorChange(replaceAlphaWith128(selectedColor));

  private static int replaceAlphaWith128(int color) {
    return color & ~0xff000000 | 0x80000000;
  }

  public void setUndoAvailability(boolean undoAvailable) {
    this.undoAvailable = undoAvailable;

    undoButton.setVisibility(buttonIsVisible(visibilityModeMap.get(currentMode), undoButton) ? VISIBLE : GONE);
  }

  public enum Mode {
    NONE,
    CROP,
    TEXT,
    DRAW,
    HIGHLIGHT,
    MOVE_DELETE,
    INSERT_STICKER,
    INSERT_ASSET_STICKER
  }

  public interface EventListener {
    void onModeStarted(@NonNull Mode mode);
    void onColorChange(int color);
    void onUndo();
    void onDelete();
    void onSave();
    void onFlipHorizontal();
    void onRotate90AntiClockwise();
    void onCropAspectLock(boolean locked);
    boolean isCropAspectLocked();
    void onRequestFullScreen(boolean fullScreen, boolean hideKeyboard);
  }

  private static final EventListener NULL_EVENT_LISTENER = new EventListener() {

    @Override
    public void onModeStarted(@NonNull Mode mode) {
    }

    @Override
    public void onColorChange(int color) {
    }

    @Override
    public void onUndo() {
    }

    @Override
    public void onDelete() {
    }

    @Override
    public void onSave() {
    }

    @Override
    public void onFlipHorizontal() {
    }

    @Override
    public void onRotate90AntiClockwise() {
    }

    @Override
    public void onCropAspectLock(boolean locked) {
    }

    @Override
    public boolean isCropAspectLocked() {
      return false;
    }

    @Override
    public void onRequestFullScreen(boolean fullScreen, boolean hideKeyboard) {
    }
  };
}
