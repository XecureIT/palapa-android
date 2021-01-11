package org.thoughtcrime.securesms.util;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaDataSource;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Pair;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.gif.GifDrawable;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.AudioSlide;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.mms.DocumentSlide;
import org.thoughtcrime.securesms.mms.GifSlide;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.MmsSlide;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.StickerSlide;
import org.thoughtcrime.securesms.mms.TextSlide;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.providers.BlobProvider;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.concurrent.ExecutionException;

public class MediaUtil {

  private static final String TAG = MediaUtil.class.getSimpleName();

  public static final String IMAGE_PNG         = "image/png";
  public static final String IMAGE_JPEG        = "image/jpeg";
  public static final String IMAGE_WEBP        = "image/webp";
  public static final String IMAGE_GIF         = "image/gif";
  public static final String AUDIO_AAC         = "audio/aac";
  public static final String AUDIO_UNSPECIFIED = "audio/*";
  public static final String VIDEO_MP4         = "video/mp4";
  public static final String VIDEO_UNSPECIFIED = "video/*";
  public static final String VCARD             = "text/x-vcard";
  public static final String LONG_TEXT         = "text/x-signal-plain";

  public static SlideType getSlideTypeFromContentType(@NonNull String contentType) {
    if (isGif(contentType)) {
      return SlideType.GIF;
    } else if (isImageType(contentType)) {
      return SlideType.IMAGE;
    } else if (isVideoType(contentType)) {
      return SlideType.VIDEO;
    } else if (isAudioType(contentType)) {
      return SlideType.AUDIO;
    } else if (isMms(contentType)) {
      return SlideType.MMS;
    } else if (isLongTextType(contentType)) {
      return SlideType.LONG_TEXT;
    } else {
      return SlideType.DOCUMENT;
    }
  }

  public static @NonNull Slide getSlideForAttachment(Context context, Attachment attachment) {
    if (attachment.isSticker()) {
      return new StickerSlide(context, attachment);
    }

    switch (getSlideTypeFromContentType(attachment.getContentType())) {
      case GIF       : return new GifSlide(context, attachment);
      case IMAGE     : return new ImageSlide(context, attachment);
      case VIDEO     : return new VideoSlide(context, attachment);
      case AUDIO     : return new AudioSlide(context, attachment);
      case MMS       : return new MmsSlide(context, attachment);
      case LONG_TEXT : return new TextSlide(context, attachment);
      case DOCUMENT  : return new DocumentSlide(context, attachment);
      default        : throw new AssertionError();
    }
  }

  public static @Nullable String getMimeType(@NonNull Context context, @Nullable Uri uri) {
    if (uri == null) return null;

    if (PartAuthority.isLocalUri(uri)) {
      return PartAuthority.getAttachmentContentType(context, uri);
    }

    String type = context.getContentResolver().getType(uri);
    if (type == null) {
      final String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
      type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
    }

    return getCorrectedMimeType(type);
  }

  public static @Nullable String getExtension(@NonNull Context context, @Nullable Uri uri) {
    return MimeTypeMap.getSingleton()
                      .getExtensionFromMimeType(getMimeType(context, uri));
  }

  public static @Nullable String getCorrectedMimeType(@Nullable String mimeType) {
    if (mimeType == null) return null;

    switch(mimeType) {
    case "image/jpg":
      return MimeTypeMap.getSingleton().hasMimeType(IMAGE_JPEG)
             ? IMAGE_JPEG
             : mimeType;
    default:
      return mimeType;
    }
  }

  public static long getMediaSize(Context context, Uri uri) throws IOException {
    InputStream in = PartAuthority.getAttachmentStream(context, uri);
    if (in == null) throw new IOException("Couldn't obtain input stream.");

    long   size   = 0;
    byte[] buffer = new byte[4096];
    int    read;

    while ((read = in.read(buffer)) != -1) {
      size += read;
    }
    in.close();

    return size;
  }

  @WorkerThread
  public static Pair<Integer, Integer> getDimensions(@NonNull Context context, @Nullable String contentType, @Nullable Uri uri) {
    if (uri == null || !MediaUtil.isImageType(contentType)) {
      return new Pair<>(0, 0);
    }

    Pair<Integer, Integer> dimens = null;

    if (MediaUtil.isGif(contentType)) {
      try {
        GifDrawable drawable = GlideApp.with(context)
                .asGif()
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .load(new DecryptableUri(uri))
                .submit()
                .get();
        dimens = new Pair<>(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
      } catch (InterruptedException e) {
        Log.w(TAG, "Was unable to complete work for GIF dimensions.", e);
      } catch (ExecutionException e) {
        Log.w(TAG, "Glide experienced an exception while trying to get GIF dimensions.", e);
      }
    } else {
      InputStream attachmentStream = null;
      try {
        if (MediaUtil.isJpegType(contentType)) {
          attachmentStream = PartAuthority.getAttachmentStream(context, uri);
          dimens = BitmapUtil.getExifDimensions(attachmentStream);
          attachmentStream.close();
          attachmentStream = null;
        }
        if (dimens == null) {
          attachmentStream = PartAuthority.getAttachmentStream(context, uri);
          dimens = BitmapUtil.getDimensions(attachmentStream);
        }
      } catch (FileNotFoundException e) {
        Log.w(TAG, "Failed to find file when retrieving media dimensions.", e);
      } catch (IOException e) {
        Log.w(TAG, "Experienced a read error when retrieving media dimensions.", e);
      } catch (BitmapDecodingException e) {
        Log.w(TAG, "Bitmap decoding error when retrieving dimensions.", e);
      } finally {
        if (attachmentStream != null) {
          try {
            attachmentStream.close();
          } catch (IOException e) {
            Log.w(TAG, "Failed to close stream after retrieving dimensions.", e);
          }
        }
      }
    }
    if (dimens == null) {
      dimens = new Pair<>(0, 0);
    }
    Log.d(TAG, "Dimensions for [" + uri + "] are " + dimens.first + " x " + dimens.second);
    return dimens;
  }

  public static boolean isMms(String contentType) {
    return !TextUtils.isEmpty(contentType) && contentType.trim().equals("application/mms");
  }

  public static boolean isGif(Attachment attachment) {
    return isGif(attachment.getContentType());
  }

  public static boolean isJpeg(Attachment attachment) {
    return isJpegType(attachment.getContentType());
  }

  public static boolean isImage(Attachment attachment) {
    return isImageType(attachment.getContentType());
  }

  public static boolean isAudio(Attachment attachment) {
    return isAudioType(attachment.getContentType());
  }

  public static boolean isVideo(Attachment attachment) {
    return isVideoType(attachment.getContentType());
  }

  public static boolean isVideo(String contentType) {
    return !TextUtils.isEmpty(contentType) && contentType.trim().startsWith("video/");
  }

  public static boolean isVcard(String contentType) {
    return !TextUtils.isEmpty(contentType) && contentType.trim().equals(VCARD);
  }

  public static boolean isGif(String contentType) {
    return !TextUtils.isEmpty(contentType) && contentType.trim().equals("image/gif");
  }

  public static boolean isJpegType(String contentType) {
    return !TextUtils.isEmpty(contentType) && contentType.trim().equals(IMAGE_JPEG);
  }

  public static boolean isFile(Attachment attachment) {
    return !isGif(attachment) && !isImage(attachment) && !isAudio(attachment) && !isVideo(attachment);
  }

  public static boolean isTextType(String contentType) {
    return (null != contentType) && contentType.startsWith("text/");
  }

  public static boolean isImageType(String contentType) {
    return (null != contentType) && contentType.startsWith("image/");
  }

  public static boolean isAudioType(String contentType) {
    return (null != contentType) && contentType.startsWith("audio/");
  }

  public static boolean isVideoType(String contentType) {
    return (null != contentType) && contentType.startsWith("video/");
  }

  public static boolean isImageOrVideoType(String contentType) {
    return isImageType(contentType) || isVideoType(contentType);
  }

  public static boolean isLongTextType(String contentType) {
    return (null != contentType) && contentType.equals(LONG_TEXT);
  }

  public static boolean hasVideoThumbnail(Uri uri) {
    if (BlobProvider.isAuthority(uri) && MediaUtil.isVideo(BlobProvider.getMimeType(uri)) && Build.VERSION.SDK_INT >= 23) {
      return true;
    }

    if (uri == null || !isSupportedVideoUriScheme(uri.getScheme())) {
      return false;
    }

    if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
      return uri.getLastPathSegment().contains("video");
    } else if (uri.toString().startsWith(MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString())) {
      return true;
    } else if (uri.toString().startsWith("file://") &&
               MediaUtil.isVideo(URLConnection.guessContentTypeFromName(uri.toString()))) {
      return true;
    } else {
      return false;
    }
  }

  @WorkerThread
  public static @Nullable Bitmap getVideoThumbnail(Context context, Uri uri) {
    if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
      long videoId = Long.parseLong(uri.getLastPathSegment().split(":")[1]);

      return MediaStore.Video.Thumbnails.getThumbnail(context.getContentResolver(),
                                                      videoId,
                                                      MediaStore.Images.Thumbnails.MINI_KIND,
                                                      null);
    } else if (uri.toString().startsWith(MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString())) {
      long videoId = Long.parseLong(uri.getLastPathSegment());

      return MediaStore.Video.Thumbnails.getThumbnail(context.getContentResolver(),
                                                      videoId,
                                                      MediaStore.Images.Thumbnails.MINI_KIND,
                                                      null);
    } else if (uri.toString().startsWith("file://") &&
               MediaUtil.isVideo(URLConnection.guessContentTypeFromName(uri.toString()))) {
      return ThumbnailUtils.createVideoThumbnail(uri.toString().replace("file://", ""),
                                                 MediaStore.Video.Thumbnails.MINI_KIND);
    } else if (BlobProvider.isAuthority(uri) &&
               MediaUtil.isVideo(BlobProvider.getMimeType(uri)) &&
               Build.VERSION.SDK_INT >= 23) {
      try {
        MediaDataSource        mediaDataSource        = BlobProvider.getInstance().getMediaDataSource(context, uri);
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();

        MediaMetadataRetrieverUtil.setDataSource(mediaMetadataRetriever, mediaDataSource);
        return mediaMetadataRetriever.getFrameAtTime(1000);
      } catch (IOException e) {
        Log.w(TAG, "failed to get thumbnail for video blob uri: " + uri, e);
        return null;
      }
    }

    return null;
  }

  public static @Nullable String getDiscreteMimeType(@NonNull String mimeType) {
    final String[] sections = mimeType.split("/", 2);
    return sections.length > 1 ? sections[0] : null;
  }

  public static class ThumbnailData implements AutoCloseable {

    @NonNull private final Bitmap bitmap;
             private final float  aspectRatio;

    public ThumbnailData(@NonNull Bitmap bitmap) {
      this.bitmap      = bitmap;
      this.aspectRatio = (float) bitmap.getWidth() / (float) bitmap.getHeight();
    }

    public @NonNull Bitmap getBitmap() {
      return bitmap;
    }

    public float getAspectRatio() {
      return aspectRatio;
    }

    public InputStream toDataStream() {
      return BitmapUtil.toCompressedJpeg(bitmap);
    }

    @Override
    public void close() {
     bitmap.recycle();
    }
  }

  private static boolean isSupportedVideoUriScheme(@Nullable String scheme) {
    return ContentResolver.SCHEME_CONTENT.equals(scheme) ||
           ContentResolver.SCHEME_FILE.equals(scheme);
  }

  public enum SlideType {
    GIF,
    IMAGE,
    VIDEO,
    AUDIO,
    MMS,
    LONG_TEXT,
    DOCUMENT
  }
}
