package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.media.MediaDataSource;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.greenrobot.eventbus.EventBus;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.events.PartProgressEvent;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.mms.MediaStream;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.service.GenericForegroundService;
import org.thoughtcrime.securesms.service.NotificationController;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.MemoryFileDescriptor;
import org.thoughtcrime.securesms.util.MemoryFileDescriptor.MemoryFileException;
import org.thoughtcrime.securesms.video.InMemoryTranscoder;
import org.thoughtcrime.securesms.video.VideoSizeException;
import org.thoughtcrime.securesms.video.VideoSourceException;
import org.thoughtcrime.securesms.video.videoconverter.EncodingException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public final class AttachmentCompressionJob extends BaseJob {

  public static final String KEY = "AttachmentCompressionJob";

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(AttachmentCompressionJob.class);

  private static final String KEY_ROW_ID              = "row_id";
  private static final String KEY_UNIQUE_ID           = "unique_id";
  private static final String KEY_MMS                 = "mms";
  private static final String KEY_MMS_SUBSCRIPTION_ID = "mms_subscription_id";

  private final AttachmentId attachmentId;
  private final boolean      mms;
  private final int          mmsSubscriptionId;

  public static AttachmentCompressionJob fromAttachment(@NonNull DatabaseAttachment databaseAttachment,
                                                        boolean mms,
                                                        int mmsSubscriptionId)
  {
    return new AttachmentCompressionJob(databaseAttachment.getAttachmentId(),
                                        MediaUtil.isVideo(databaseAttachment) && MediaConstraints.isVideoTranscodeAvailable(),
                                        mms,
                                        mmsSubscriptionId);
  }

  private AttachmentCompressionJob(@NonNull AttachmentId attachmentId,
                                   boolean isVideoTranscode,
                                   boolean mms,
                                   int mmsSubscriptionId)
  {
    this(new Parameters.Builder()
                       .addConstraint(NetworkConstraint.KEY)
                       .setLifespan(TimeUnit.DAYS.toMillis(1))
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .setQueue(isVideoTranscode ? "VIDEO_TRANSCODE" : "GENERIC_TRANSCODE")
                       .build(),
         attachmentId,
         mms,
         mmsSubscriptionId);
  }

  private AttachmentCompressionJob(@NonNull Parameters parameters,
                                   @NonNull AttachmentId attachmentId,
                                   boolean mms,
                                   int mmsSubscriptionId)
  {
    super(parameters);
    this.attachmentId      = attachmentId;
    this.mms               = mms;
    this.mmsSubscriptionId = mmsSubscriptionId;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putLong(KEY_ROW_ID, attachmentId.getRowId())
                             .putLong(KEY_UNIQUE_ID, attachmentId.getUniqueId())
                             .putBoolean(KEY_MMS, mms)
                             .putInt(KEY_MMS_SUBSCRIPTION_ID, mmsSubscriptionId)
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws Exception {
    Log.d(TAG, "Running for: " + attachmentId);

    AttachmentDatabase         database           = DatabaseFactory.getAttachmentDatabase(context);
    DatabaseAttachment         databaseAttachment = database.getAttachment(attachmentId);

    if (databaseAttachment == null) {
      throw new UndeliverableMessageException("Cannot find the specified attachment.");
    }

    if (databaseAttachment.getTransformProperties().shouldSkipTransform()) {
      Log.i(TAG, "Skipping at the direction of the TransformProperties.");
      return;
    }

    MediaConstraints mediaConstraints = mms ? MediaConstraints.getMmsMediaConstraints(mmsSubscriptionId)
                                            : MediaConstraints.getPushMediaConstraints();

    scaleAndStripExif(database, mediaConstraints, databaseAttachment);
  }

  @Override
  public void onCanceled() { }

  @Override
  protected boolean onShouldRetry(@NonNull Exception exception) {
    return exception instanceof IOException;
  }

  private void scaleAndStripExif(@NonNull AttachmentDatabase attachmentDatabase,
                                 @NonNull MediaConstraints constraints,
                                 @NonNull DatabaseAttachment attachment)
      throws UndeliverableMessageException
  {
    try {
      if (MediaUtil.isVideo(attachment) && MediaConstraints.isVideoTranscodeAvailable()) {
        transcodeVideoIfNeededToDatabase(context, attachmentDatabase, attachment, constraints, EventBus.getDefault());
      } else if (constraints.isSatisfied(context, attachment)) {
        if (MediaUtil.isJpeg(attachment)) {
          MediaStream stripped = getResizedMedia(context, attachment, constraints);
          attachmentDatabase.updateAttachmentData(attachment, stripped);
          attachmentDatabase.markAttachmentAsTransformed(attachmentId);
        }
      } else if (constraints.canResize(attachment)) {
        MediaStream resized = getResizedMedia(context, attachment, constraints);
        attachmentDatabase.updateAttachmentData(attachment, resized);
        attachmentDatabase.markAttachmentAsTransformed(attachmentId);
      } else {
        throw new UndeliverableMessageException("Size constraints could not be met!");
      }
    } catch (IOException | MmsException e) {
      throw new UndeliverableMessageException(e);
    }
  }

  @RequiresApi(26)
  private static void transcodeVideoIfNeededToDatabase(@NonNull Context context,
                                                       @NonNull AttachmentDatabase attachmentDatabase,
                                                       @NonNull DatabaseAttachment attachment,
                                                       @NonNull MediaConstraints constraints,
                                                       @NonNull EventBus eventBus)
      throws UndeliverableMessageException
  {
    try (NotificationController notification = GenericForegroundService.startForegroundTask(context, context.getString(R.string.AttachmentUploadJob_compressing_video_start))) {

      notification.setIndeterminateProgress();

      try (MediaDataSource dataSource = attachmentDatabase.mediaDataSourceFor(attachment.getAttachmentId())) {

        if (dataSource == null) {
          throw new UndeliverableMessageException("Cannot get media data source for attachment.");
        }

        try (InMemoryTranscoder transcoder = new InMemoryTranscoder(context, dataSource, constraints.getCompressedVideoMaxSize(context))) {

          if (transcoder.isTranscodeRequired()) {

            MediaStream mediaStream = transcoder.transcode(percent -> {
              notification.setProgress(100, percent);
              eventBus.postSticky(new PartProgressEvent(attachment,
                                                        PartProgressEvent.Type.COMPRESSION,
                                                        100,
                                                        percent));
            });

            attachmentDatabase.updateAttachmentData(attachment, mediaStream);
            attachmentDatabase.markAttachmentAsTransformed(attachment.getAttachmentId());
          }
        }
      }
    } catch (VideoSourceException | EncodingException | MemoryFileException e) {
      if (attachment.getSize() > constraints.getVideoMaxSize(context)) {
        throw new UndeliverableMessageException("Duration not found, attachment too large to skip transcode", e);
      } else {
        Log.w(TAG, "Problem with video source, but video small enough to skip transcode", e);
      }
    } catch (IOException | MmsException | VideoSizeException e) {
      throw new UndeliverableMessageException("Failed to transcode", e);
    }
  }

  private static MediaStream getResizedMedia(@NonNull Context context,
                                             @NonNull Attachment attachment,
                                             @NonNull MediaConstraints constraints)
      throws IOException
  {
    if (!constraints.canResize(attachment)) {
      throw new UnsupportedOperationException("Cannot resize this content type");
    }

    try {
      BitmapUtil.ScaleResult scaleResult = BitmapUtil.createScaledBytes(context,
                                                                        new DecryptableStreamUriLoader.DecryptableUri(attachment.getDataUri()),
                                                                        constraints);

      return new MediaStream(new ByteArrayInputStream(scaleResult.getBitmap()),
                             MediaUtil.IMAGE_JPEG,
                             scaleResult.getWidth(),
                             scaleResult.getHeight());
    } catch (BitmapDecodingException e) {
      throw new IOException(e);
    }
  }

  public static final class Factory implements Job.Factory<AttachmentCompressionJob> {
    @Override
    public @NonNull AttachmentCompressionJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new AttachmentCompressionJob(parameters,
                                          new AttachmentId(data.getLong(KEY_ROW_ID), data.getLong(KEY_UNIQUE_ID)),
                                          data.getBoolean(KEY_MMS),
                                          data.getInt(KEY_MMS_SUBSCRIPTION_ID));
    }
  }
}
