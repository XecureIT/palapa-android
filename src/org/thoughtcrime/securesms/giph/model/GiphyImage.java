package org.thoughtcrime.securesms.giph.model;


import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.thoughtcrime.securesms.util.Util;

public class GiphyImage {

  @JsonProperty
  private ImageTypes images;

  public String getGifUrl() {
    ImageData data = getGifData();
    return data != null ? data.url : null;
  }

  public long getGifSize() {
    ImageData data = getGifData();
    return data != null ? data.size : 0;
  }

  public String getGifMmsUrl() {
    ImageData data = getGifMmsData();
    return data != null ? data.url : null;
  }

  public long getMmsGifSize() {
    ImageData data = getGifMmsData();
    return data != null ? data.size : 0;
  }

  public float getGifAspectRatio() {
    return (float)images.downsized.width / (float)images.downsized.height;
  }

  public int getGifWidth() {
    ImageData data = getGifData();
    return data != null ? data.width : 0;
  }

  public int getGifHeight() {
    ImageData data = getGifData();
    return data != null ? data.height : 0;
  }

  public String getStillUrl() {
    ImageData data = getStillData();
    return data != null ? data.url : null;
  }

  public long getStillSize() {
    ImageData data = getStillData();
    return data != null ? data.size : 0;
  }

  private @Nullable ImageData getGifData() {
    return getFirstNonEmpty(images.downsized, images.downsized_medium, images.fixed_height, images.fixed_width);
  }

  private @Nullable ImageData getGifMmsData() {
    return getFirstNonEmpty(images.fixed_height_downsampled, images.fixed_width_downsampled);
  }

  private @Nullable ImageData getStillData() {
    return getFirstNonEmpty(images.downsized_still, images.fixed_height_still, images.fixed_width_still);
  }

  private static @Nullable ImageData getFirstNonEmpty(ImageData... data) {
    for (ImageData image : data) {
      if (!TextUtils.isEmpty(image.url)) {
        return image;
      }
    }

    return null;
  }

  public static class ImageTypes {
    @JsonProperty
    private ImageData fixed_height;
    @JsonProperty
    private ImageData fixed_height_still;
    @JsonProperty
    private ImageData fixed_height_downsampled;
    @JsonProperty
    private ImageData fixed_width;
    @JsonProperty
    private ImageData fixed_width_still;
    @JsonProperty
    private ImageData fixed_width_downsampled;
    @JsonProperty
    private ImageData fixed_width_small;
    @JsonProperty
    private ImageData downsized_medium;
    @JsonProperty
    private ImageData downsized;
    @JsonProperty
    private ImageData downsized_still;
  }

  public static class ImageData {
    @JsonProperty
    private String url;

    @JsonProperty
    private int width;

    @JsonProperty
    private int height;

    @JsonProperty
    private int size;

    @JsonProperty
    private String mp4;

    @JsonProperty
    private String webp;
  }

}
