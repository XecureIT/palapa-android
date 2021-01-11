package org.thoughtcrime.securesms.attachments;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.blurhash.BlurHash;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.AttachmentDatabase.TransformProperties;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.stickers.StickerLocator;

public class DatabaseAttachment extends Attachment {

  private final AttachmentId attachmentId;
  private final long         mmsId;
  private final boolean      hasData;
  private final boolean      hasThumbnail;

  public DatabaseAttachment(AttachmentId attachmentId, long mmsId,
                            boolean hasData, boolean hasThumbnail,
                            String contentType, int transferProgress, long size,
                            String fileName, String location, String key, String relay,
                            byte[] digest, String fastPreflightId, boolean voiceNote,
                            int width, int height, boolean quote, @Nullable String caption,
                            @Nullable StickerLocator stickerLocator, @Nullable BlurHash blurHash,
                            @Nullable TransformProperties transformProperties)
  {
    super(contentType, transferProgress, size, fileName, location, key, relay, digest, fastPreflightId, voiceNote, width, height, quote, caption, stickerLocator, blurHash, transformProperties);
    this.attachmentId = attachmentId;
    this.hasData      = hasData;
    this.hasThumbnail = hasThumbnail;
    this.mmsId        = mmsId;
  }

  @Override
  @Nullable
  public Uri getDataUri() {
    if (hasData) {
      return PartAuthority.getAttachmentDataUri(attachmentId);
    } else {
      return null;
    }
  }

  @Override
  @Nullable
  public Uri getThumbnailUri() {
    if (hasThumbnail) {
      return PartAuthority.getAttachmentThumbnailUri(attachmentId);
    } else {
      return null;
    }
  }

  public AttachmentId getAttachmentId() {
    return attachmentId;
  }

  @Override
  public boolean equals(Object other) {
    return other != null &&
           other instanceof DatabaseAttachment &&
           ((DatabaseAttachment) other).attachmentId.equals(this.attachmentId);
  }

  @Override
  public int hashCode() {
    return attachmentId.hashCode();
  }

  public long getMmsId() {
    return mmsId;
  }

  public boolean hasData() {
    return hasData;
  }

  public boolean hasThumbnail() {
    return hasThumbnail;
  }
}
