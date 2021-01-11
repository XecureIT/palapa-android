package org.thoughtcrime.securesms.jobs;

import android.graphics.Bitmap;
import android.media.MediaDataSource;
import android.media.MediaMetadataRetriever;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.attachments.PointerAttachment;
import org.thoughtcrime.securesms.blurhash.BlurHashEncoder;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.events.PartProgressEvent;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.service.GenericForegroundService;
import org.thoughtcrime.securesms.service.NotificationController;
import org.thoughtcrime.securesms.util.MediaMetadataRetrieverUtil;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Uploads an attachment without alteration.
 * <p>
 * Queue {@link AttachmentCompressionJob} before to compress.
 */
public final class AttachmentUploadJob extends BaseJob {

  public static final String KEY = "AttachmentUploadJobV2";

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(AttachmentUploadJob.class);

  private static final String KEY_ROW_ID    = "row_id";
  private static final String KEY_UNIQUE_ID = "unique_id";

  /**
   * Foreground notification shows while uploading attachments above this.
   */
  private static final int FOREGROUND_LIMIT = 10 * 1024 * 1024;

  private final AttachmentId attachmentId;

  public AttachmentUploadJob(AttachmentId attachmentId) {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .build(),
         attachmentId);
  }

  private AttachmentUploadJob(@NonNull Job.Parameters parameters, @NonNull AttachmentId attachmentId) {
    super(parameters);
    this.attachmentId = attachmentId;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putLong(KEY_ROW_ID, attachmentId.getRowId())
                             .putLong(KEY_UNIQUE_ID, attachmentId.getUniqueId())
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws Exception {
    SignalServiceMessageSender messageSender      = ApplicationDependencies.getSignalServiceMessageSender();
    AttachmentDatabase         database           = DatabaseFactory.getAttachmentDatabase(context);
    DatabaseAttachment         databaseAttachment = database.getAttachment(attachmentId);

    if (databaseAttachment == null) {
      throw new InvalidAttachmentException("Cannot find the specified attachment.");
    }

    Log.i(TAG, "Uploading attachment for message " + databaseAttachment.getMmsId() + " with ID " + databaseAttachment.getAttachmentId());

    try (NotificationController notification = getNotificationForAttachment(databaseAttachment)) {
      SignalServiceAttachment        localAttachment  = getAttachmentFor(databaseAttachment, notification);
      SignalServiceAttachmentPointer remoteAttachment = messageSender.uploadAttachment(localAttachment.asStream());
      Attachment                     attachment       = PointerAttachment.forPointer(Optional.of(remoteAttachment), null, databaseAttachment.getFastPreflightId()).get();

      database.updateAttachmentAfterUpload(databaseAttachment.getAttachmentId(), attachment);
    }
  }

  private @Nullable NotificationController getNotificationForAttachment(@NonNull Attachment attachment) {
    if (attachment.getSize() >= FOREGROUND_LIMIT) {
      return GenericForegroundService.startForegroundTask(context, context.getString(R.string.AttachmentUploadJob_uploading_media));
    } else {
      return null;
    }
  }

  @Override
  public void onCanceled() { }

  @Override
  protected boolean onShouldRetry(@NonNull Exception exception) {
    return exception instanceof IOException;
  }

  private @NonNull SignalServiceAttachment getAttachmentFor(Attachment attachment, @Nullable NotificationController notification) throws InvalidAttachmentException {
    try {
      if (attachment.getDataUri() == null || attachment.getSize() == 0) throw new IOException("Assertion failed, outgoing attachment has no data!");
      InputStream is = PartAuthority.getAttachmentStream(context, attachment.getDataUri());
      SignalServiceAttachment.Builder builder = SignalServiceAttachment.newStreamBuilder()
                                                                       .withStream(is)
                                                                       .withContentType(attachment.getContentType())
                                                                       .withLength(attachment.getSize())
                                                                       .withFileName(attachment.getFileName())
                                                                       .withVoiceNote(attachment.isVoiceNote())
                                                                       .withWidth(attachment.getWidth())
                                                                       .withHeight(attachment.getHeight())
                                                                       .withCaption(attachment.getCaption())
                                                                       .withListener((total, progress) -> {
                                                                         EventBus.getDefault().postSticky(new PartProgressEvent(attachment, PartProgressEvent.Type.NETWORK, total, progress));
                                                                         if (notification != null) {
                                                                           notification.setProgress(total, progress);
                                                                         }
                                                                       });
      if (MediaUtil.isImageType(attachment.getContentType())) {
        return builder.withBlurHash(getImageBlurHash(attachment)).build();
      } else if (MediaUtil.isVideoType(attachment.getContentType())) {
        return builder.withBlurHash(getVideoBlurHash(attachment)).build();
      } else {
        return builder.build();
      }

    } catch (IOException ioe) {
      throw new InvalidAttachmentException(ioe);
    }
  }

  private @Nullable String getImageBlurHash(@NonNull Attachment attachment) throws IOException {
    if (attachment.getBlurHash() != null) return attachment.getBlurHash().getHash();
    if (attachment.getDataUri() == null) return null;

    return BlurHashEncoder.encode(PartAuthority.getAttachmentStream(context, attachment.getDataUri()));
  }

  private @Nullable String getVideoBlurHash(@NonNull Attachment attachment) throws IOException {
    if (attachment.getThumbnailUri() != null) {
      return BlurHashEncoder.encode(PartAuthority.getAttachmentStream(context, attachment.getThumbnailUri()));
    }

    if (attachment.getBlurHash() != null) return attachment.getBlurHash().getHash();

    if (Build.VERSION.SDK_INT < 23) {
      Log.w(TAG, "Video thumbnails not supported...");
      return null;
    }

    try (MediaDataSource dataSource = DatabaseFactory.getAttachmentDatabase(context).mediaDataSourceFor(attachmentId)) {
      if (dataSource == null) return null;

      MediaMetadataRetriever retriever = new MediaMetadataRetriever();
      MediaMetadataRetrieverUtil.setDataSource(retriever, dataSource);

      Bitmap bitmap = retriever.getFrameAtTime(1000);

      if (bitmap != null) {
        Bitmap thumb = Bitmap.createScaledBitmap(bitmap, 100, 100, false);
        bitmap.recycle();

        Log.i(TAG, "Generated video thumbnail...");
        String hash = BlurHashEncoder.encode(thumb);
        thumb.recycle();

        return hash;
      } else {
        return null;
      }
    }
  }

  private class InvalidAttachmentException extends Exception {
    InvalidAttachmentException(String message) {
      super(message);
    }

    InvalidAttachmentException(Exception e) {
      super(e);
    }
  }

  public static final class Factory implements Job.Factory<AttachmentUploadJob> {
    @Override
    public @NonNull AttachmentUploadJob create(@NonNull Parameters parameters, @NonNull org.thoughtcrime.securesms.jobmanager.Data data) {
      return new AttachmentUploadJob(parameters, new AttachmentId(data.getLong(KEY_ROW_ID), data.getLong(KEY_UNIQUE_ID)));
    }
  }
}
