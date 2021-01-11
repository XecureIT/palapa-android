/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.signalservice.api;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.SessionBuilder;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.AttachmentCipherOutputStream;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.signalservice.api.messages.calls.AnswerMessage;
import org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;
import org.whispersystems.signalservice.api.messages.multidevice.BlockedListMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ConfigurationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.StickerPackOperationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ViewOnceOpenMessage;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream;
import org.whispersystems.signalservice.internal.push.AttachmentUploadAttributes;
import org.whispersystems.signalservice.internal.push.MismatchedDevices;
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage;
import org.whispersystems.signalservice.internal.push.OutgoingPushMessageList;
import org.whispersystems.signalservice.internal.push.ProvisioningProtos;
import org.whispersystems.signalservice.internal.push.PushAttachmentData;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.push.SendMessageResponse;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.AttachmentPointer;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.CallMessage;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Content;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.DataMessage;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.NullMessage;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.ReceiptMessage;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.TypingMessage;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Verified;
import org.whispersystems.signalservice.internal.push.StaleDevices;
import org.whispersystems.signalservice.internal.push.exceptions.MismatchedDevicesException;
import org.whispersystems.signalservice.internal.push.exceptions.StaleDevicesException;
import org.whispersystems.signalservice.internal.push.http.AttachmentCipherOutputStreamFactory;
import org.whispersystems.signalservice.internal.util.StaticCredentialsProvider;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The main interface for sending Signal Service messages.
 *
 * @author Moxie Marlinspike
 */
public class SignalServiceMessageSender {

  private static final String TAG = SignalServiceMessageSender.class.getSimpleName();

  private final PushServiceSocket                                   socket;
  private final SignalProtocolStore                                 store;
  private final SignalServiceAddress                                localAddress;
  private final Optional<EventListener>                             eventListener;

  private final AtomicReference<Optional<SignalServiceMessagePipe>> pipe;
  private final AtomicReference<Optional<SignalServiceMessagePipe>> unidentifiedPipe;
  private final AtomicBoolean                                       isMultiDevice;

  /**
   * Construct a SignalServiceMessageSender.
   *
   * @param urls The URL of the Signal Service.
   * @param uuid The Signal Service UUID.
   * @param e164 The Signal Service phone number.
   * @param password The Signal Service user password.
   * @param store The SignalProtocolStore.
   * @param eventListener An optional event listener, which fires whenever sessions are
   *                      setup or torn down for a recipient.
   */
  public SignalServiceMessageSender(SignalServiceConfiguration urls,
                                    UUID uuid, String e164, String password,
                                    SignalProtocolStore store,
                                    String userAgent,
                                    boolean isMultiDevice,
                                    Optional<SignalServiceMessagePipe> pipe,
                                    Optional<SignalServiceMessagePipe> unidentifiedPipe,
                                    Optional<EventListener> eventListener)
  {
    this(urls, new StaticCredentialsProvider(uuid, e164, password, null), store, userAgent, isMultiDevice, pipe, unidentifiedPipe, eventListener);
  }

  public SignalServiceMessageSender(SignalServiceConfiguration urls,
                                    CredentialsProvider credentialsProvider,
                                    SignalProtocolStore store,
                                    String userAgent,
                                    boolean isMultiDevice,
                                    Optional<SignalServiceMessagePipe> pipe,
                                    Optional<SignalServiceMessagePipe> unidentifiedPipe,
                                    Optional<EventListener> eventListener)
  {
    this.socket           = new PushServiceSocket(urls, credentialsProvider, userAgent);
    this.store            = store;
    this.localAddress     = new SignalServiceAddress(credentialsProvider.getUuid(), credentialsProvider.getE164());
    this.pipe             = new AtomicReference<>(pipe);
    this.unidentifiedPipe = new AtomicReference<>(unidentifiedPipe);
    this.isMultiDevice    = new AtomicBoolean(isMultiDevice);
    this.eventListener    = eventListener;
  }

  /**
   * Send a read receipt for a received message.
   *
   * @param recipient The sender of the received message you're acknowledging.
   * @param message The read receipt to deliver.
   * @throws IOException
   * @throws UntrustedIdentityException
   */
  public void sendReceipt(SignalServiceAddress recipient,
                          Optional<UnidentifiedAccessPair> unidentifiedAccess,
                          SignalServiceReceiptMessage message)
      throws IOException, UntrustedIdentityException
  {
    byte[] content = createReceiptContent(message);

    sendMessage(recipient, getTargetUnidentifiedAccess(unidentifiedAccess), message.getWhen(), content, false);
  }

  /**
   * Send a typing indicator.
   *
   * @param recipient The destination
   * @param message The typing indicator to deliver
   * @throws IOException
   * @throws UntrustedIdentityException
   */
  public void sendTyping(SignalServiceAddress recipient,
                         Optional<UnidentifiedAccessPair> unidentifiedAccess,
                         SignalServiceTypingMessage message)
      throws IOException, UntrustedIdentityException
  {
    byte[] content = createTypingContent(message);

    sendMessage(recipient, getTargetUnidentifiedAccess(unidentifiedAccess), message.getTimestamp(), content, true);
  }

  public void sendTyping(List<SignalServiceAddress>             recipients,
                         List<Optional<UnidentifiedAccessPair>> unidentifiedAccess,
                         SignalServiceTypingMessage             message)
      throws IOException
  {
    byte[] content = createTypingContent(message);
    sendMessage(recipients, getTargetUnidentifiedAccess(unidentifiedAccess), message.getTimestamp(), content, true);
  }


  /**
   * Send a call setup message to a single recipient.
   *
   * @param recipient The message's destination.
   * @param message The call message.
   * @throws IOException
   */
  public void sendCallMessage(SignalServiceAddress recipient,
                              Optional<UnidentifiedAccessPair> unidentifiedAccess,
                              SignalServiceCallMessage message)
      throws IOException, UntrustedIdentityException
  {
    byte[] content = createCallContent(message);
    sendMessage(recipient, getTargetUnidentifiedAccess(unidentifiedAccess), System.currentTimeMillis(), content, false);
  }

  /**
   * Send a message to a single recipient.
   *
   * @param recipient The message's destination.
   * @param message The message.
   * @throws UntrustedIdentityException
   * @throws IOException
   */
  public SendMessageResult sendMessage(SignalServiceAddress             recipient,
                                       Optional<UnidentifiedAccessPair> unidentifiedAccess,
                                       SignalServiceDataMessage         message)
      throws UntrustedIdentityException, IOException
  {
    byte[]            content   = createMessageContent(message);
    long              timestamp = message.getTimestamp();
    SendMessageResult result    = sendMessage(recipient, getTargetUnidentifiedAccess(unidentifiedAccess), timestamp, content, false);

    if (result.getSuccess() != null && result.getSuccess().isNeedsSync()) {
      byte[] syncMessage = createMultiDeviceSentTranscriptContent(content, Optional.of(recipient), timestamp, Collections.singletonList(result), false);
      sendMessage(localAddress, Optional.<UnidentifiedAccess>absent(), timestamp, syncMessage, false);
    }

    if (message.isEndSession()) {
      if (recipient.getUuid().isPresent()) {
        store.deleteAllSessions(recipient.getUuid().get().toString());
      }
      if (recipient.getNumber().isPresent()) {
        store.deleteAllSessions(recipient.getNumber().get());
      }

      if (eventListener.isPresent()) {
        eventListener.get().onSecurityEvent(recipient);
      }
    }

    return result;
  }

  /**
   * Send a message to a group.
   *
   * @param recipients The group members.
   * @param message The group message.
   * @throws IOException
   */
  public List<SendMessageResult> sendMessage(List<SignalServiceAddress>             recipients,
                                             List<Optional<UnidentifiedAccessPair>> unidentifiedAccess,
                                             boolean                                isRecipientUpdate,
                                             SignalServiceDataMessage               message)
      throws IOException, UntrustedIdentityException
  {
    byte[]                  content            = createMessageContent(message);
    long                    timestamp          = message.getTimestamp();
    List<SendMessageResult> results            = sendMessage(recipients, getTargetUnidentifiedAccess(unidentifiedAccess), timestamp, content, false);
    boolean                 needsSyncInResults = false;

    for (SendMessageResult result : results) {
      if (result.getSuccess() != null && result.getSuccess().isNeedsSync()) {
        needsSyncInResults = true;
        break;
      }
    }

    if (needsSyncInResults || isMultiDevice.get()) {
      byte[] syncMessage = createMultiDeviceSentTranscriptContent(content, Optional.<SignalServiceAddress>absent(), timestamp, results, isRecipientUpdate);
      sendMessage(localAddress, Optional.<UnidentifiedAccess>absent(), timestamp, syncMessage, false);
    }

    return results;
  }

  public void sendMessage(SignalServiceSyncMessage message, Optional<UnidentifiedAccessPair> unidentifiedAccess)
      throws IOException, UntrustedIdentityException
  {
    byte[] content;

    if (message.getContacts().isPresent()) {
      content = createMultiDeviceContactsContent(message.getContacts().get().getContactsStream().asStream(),
                                                 message.getContacts().get().isComplete());
    } else if (message.getGroups().isPresent()) {
      content = createMultiDeviceGroupsContent(message.getGroups().get().asStream());
    } else if (message.getRead().isPresent()) {
      content = createMultiDeviceReadContent(message.getRead().get());
    } else if (message.getViewOnceOpen().isPresent()) {
      content = createMultiDeviceViewOnceOpenContent(message.getViewOnceOpen().get());
    } else if (message.getBlockedList().isPresent()) {
      content = createMultiDeviceBlockedContent(message.getBlockedList().get());
    } else if (message.getConfiguration().isPresent()) {
      content = createMultiDeviceConfigurationContent(message.getConfiguration().get());
    } else if (message.getSent().isPresent()) {
      content = createMultiDeviceSentTranscriptContent(message.getSent().get(), unidentifiedAccess);
    } else if (message.getStickerPackOperations().isPresent()) {
      content = createMultiDeviceStickerPackOperationContent(message.getStickerPackOperations().get());
    } else if (message.getFetchType().isPresent()) {
      content = createMultiDeviceFetchTypeContent(message.getFetchType().get());
    } else if (message.getVerified().isPresent()) {
      sendMessage(message.getVerified().get(), unidentifiedAccess);
      return;
    } else {
      throw new IOException("Unsupported sync message!");
    }

    long timestamp = message.getSent().isPresent() ? message.getSent().get().getTimestamp()
                                                   : System.currentTimeMillis();

    sendMessage(localAddress, Optional.<UnidentifiedAccess>absent(), timestamp, content, false);
  }

  public void setSoTimeoutMillis(long soTimeoutMillis) {
    socket.setSoTimeoutMillis(soTimeoutMillis);
  }

  public void cancelInFlightRequests() {
    socket.cancelInFlightRequests();
  }

  public void setMessagePipe(SignalServiceMessagePipe pipe, SignalServiceMessagePipe unidentifiedPipe) {
    this.pipe.set(Optional.fromNullable(pipe));
    this.unidentifiedPipe.set(Optional.fromNullable(unidentifiedPipe));
  }

  public void setIsMultiDevice(boolean isMultiDevice) {
    this.isMultiDevice.set(isMultiDevice);
  }

  public SignalServiceAttachmentPointer uploadAttachment(SignalServiceAttachmentStream attachment) throws IOException {
    byte[]             attachmentKey    = Util.getSecretBytes(64);
    long               paddedLength     = PaddingInputStream.getPaddedSize(attachment.getLength());
    InputStream        dataStream       = new PaddingInputStream(attachment.getInputStream(), attachment.getLength());
    long               ciphertextLength = AttachmentCipherOutputStream.getCiphertextLength(paddedLength);
    PushAttachmentData attachmentData   = new PushAttachmentData(attachment.getContentType(),
                                                                 dataStream,
                                                                 ciphertextLength,
                                                                 new AttachmentCipherOutputStreamFactory(attachmentKey),
                                                                 attachment.getListener());

//    AttachmentUploadAttributes uploadAttributes = null;
//
//    if (pipe.get().isPresent()) {
//      Log.d(TAG, "Using pipe to retrieve attachment upload attributes...");
//      try {
//        uploadAttributes = pipe.get().get().getAttachmentUploadAttributes();
//      } catch (IOException e) {
//        Log.w(TAG, "Failed to retrieve attachment upload attributes using pipe. Falling back...");
//      }
//    }
//
//    if (uploadAttributes == null) {
//      Log.d(TAG, "Not using pipe to retrieve attachment upload attributes...");
//      uploadAttributes = socket.getAttachmentUploadAttributes();
//    }
//
//    Pair<Long, byte[]> attachmentIdAndDigest = socket.uploadAttachment(attachmentData, uploadAttributes);

    Pair<Long, byte[]> attachmentIdAndDigest = socket.sendAttachment(attachmentData);

    return new SignalServiceAttachmentPointer(attachmentIdAndDigest.first(),
                                              attachment.getContentType(),
                                              attachmentKey,
                                              Optional.of(Util.toIntExact(attachment.getLength())),
                                              attachment.getPreview(),
                                              attachment.getWidth(), attachment.getHeight(),
                                              Optional.of(attachmentIdAndDigest.second()),
                                              attachment.getFileName(),
                                              attachment.getVoiceNote(),
                                              attachment.getCaption(),
                                              attachment.getBlurHash());
  }


  private void sendMessage(VerifiedMessage message, Optional<UnidentifiedAccessPair> unidentifiedAccess)
      throws IOException, UntrustedIdentityException
  {
    byte[] nullMessageBody = DataMessage.newBuilder()
                                        .setBody(Base64.encodeBytes(Util.getRandomLengthBytes(140)))
                                        .build()
                                        .toByteArray();

    NullMessage nullMessage = NullMessage.newBuilder()
                                         .setPadding(ByteString.copyFrom(nullMessageBody))
                                         .build();

    byte[] content          = Content.newBuilder()
                                     .setNullMessage(nullMessage)
                                     .build()
                                     .toByteArray();

    SendMessageResult result = sendMessage(message.getDestination(), getTargetUnidentifiedAccess(unidentifiedAccess), message.getTimestamp(), content, false);

    if (result.getSuccess().isNeedsSync()) {
      byte[] syncMessage = createMultiDeviceVerifiedContent(message, nullMessage.toByteArray());
      sendMessage(localAddress, Optional.<UnidentifiedAccess>absent(), message.getTimestamp(), syncMessage, false);
    }
  }

  private byte[] createTypingContent(SignalServiceTypingMessage message) {
    Content.Builder       container = Content.newBuilder();
    TypingMessage.Builder builder   = TypingMessage.newBuilder();

    builder.setTimestamp(message.getTimestamp());

    if      (message.isTypingStarted()) builder.setAction(TypingMessage.Action.STARTED);
    else if (message.isTypingStopped()) builder.setAction(TypingMessage.Action.STOPPED);
    else                                throw new IllegalArgumentException("Unknown typing indicator");

    if (message.getGroupId().isPresent()) {
      builder.setGroupId(ByteString.copyFrom(message.getGroupId().get()));
    }

    return container.setTypingMessage(builder).build().toByteArray();
  }

  private byte[] createReceiptContent(SignalServiceReceiptMessage message) {
    Content.Builder        container = Content.newBuilder();
    ReceiptMessage.Builder builder   = ReceiptMessage.newBuilder();

    for (long timestamp : message.getTimestamps()) {
      builder.addTimestamp(timestamp);
    }

    if      (message.isDeliveryReceipt()) builder.setType(ReceiptMessage.Type.DELIVERY);
    else if (message.isReadReceipt())     builder.setType(ReceiptMessage.Type.READ);

    return container.setReceiptMessage(builder).build().toByteArray();
  }

  private byte[] createMessageContent(SignalServiceDataMessage message) throws IOException {
    Content.Builder         container = Content.newBuilder();
    DataMessage.Builder     builder   = DataMessage.newBuilder();
    List<AttachmentPointer> pointers  = createAttachmentPointers(message.getAttachments());

    if (!pointers.isEmpty()) {
      builder.addAllAttachments(pointers);
    }

    if (message.getBody().isPresent()) {
      builder.setBody(message.getBody().get());
    }

    if (message.getGroupInfo().isPresent()) {
      builder.setGroup(createGroupContent(message.getGroupInfo().get()));
    }

    if (message.isEndSession()) {
      builder.setFlags(DataMessage.Flags.END_SESSION_VALUE);
    }

    if (message.isExpirationUpdate()) {
      builder.setFlags(DataMessage.Flags.EXPIRATION_TIMER_UPDATE_VALUE);
    }

    if (message.isProfileKeyUpdate()) {
      builder.setFlags(DataMessage.Flags.PROFILE_KEY_UPDATE_VALUE);
    }

    if (message.getExpiresInSeconds() > 0) {
      builder.setExpireTimer(message.getExpiresInSeconds());
    }

    if (message.getProfileKey().isPresent()) {
      builder.setProfileKey(ByteString.copyFrom(message.getProfileKey().get()));
    }

    if (message.getQuote().isPresent()) {
      DataMessage.Quote.Builder quoteBuilder = DataMessage.Quote.newBuilder()
                                                                .setId(message.getQuote().get().getId())
                                                                .setText(message.getQuote().get().getText());

      if (message.getQuote().get().getAuthor().getUuid().isPresent()) {
        quoteBuilder = quoteBuilder.setAuthorUuid(message.getQuote().get().getAuthor().getUuid().get().toString());
      }

      if (message.getQuote().get().getAuthor().getNumber().isPresent()) {
        quoteBuilder = quoteBuilder.setAuthorE164(message.getQuote().get().getAuthor().getNumber().get());
      }

      for (SignalServiceDataMessage.Quote.QuotedAttachment attachment : message.getQuote().get().getAttachments()) {
        DataMessage.Quote.QuotedAttachment.Builder quotedAttachment = DataMessage.Quote.QuotedAttachment.newBuilder();

        quotedAttachment.setContentType(attachment.getContentType());

        if (attachment.getFileName() != null) {
          quotedAttachment.setFileName(attachment.getFileName());
        }

        if (attachment.getThumbnail() != null) {
          quotedAttachment.setThumbnail(createAttachmentPointer(attachment.getThumbnail().asStream()));
        }

        quoteBuilder.addAttachments(quotedAttachment);
      }

      builder.setQuote(quoteBuilder);
    }

    if (message.getSharedContacts().isPresent()) {
      builder.addAllContact(createSharedContactContent(message.getSharedContacts().get()));
    }

    if (message.getPreviews().isPresent()) {
      for (SignalServiceDataMessage.Preview preview : message.getPreviews().get()) {
        DataMessage.Preview.Builder previewBuilder = DataMessage.Preview.newBuilder();
        previewBuilder.setTitle(preview.getTitle());
        previewBuilder.setUrl(preview.getUrl());

        if (preview.getImage().isPresent()) {
          if (preview.getImage().get().isStream()) {
            previewBuilder.setImage(createAttachmentPointer(preview.getImage().get().asStream()));
          } else {
            previewBuilder.setImage(createAttachmentPointer(preview.getImage().get().asPointer()));
          }
        }

        builder.addPreview(previewBuilder.build());
      }
    }

    if (message.getSticker().isPresent()) {
      DataMessage.Sticker.Builder stickerBuilder = DataMessage.Sticker.newBuilder();

      stickerBuilder.setPackId(ByteString.copyFrom(message.getSticker().get().getPackId()));
      stickerBuilder.setPackKey(ByteString.copyFrom(message.getSticker().get().getPackKey()));
      stickerBuilder.setStickerId(message.getSticker().get().getStickerId());

      if (message.getSticker().get().getAttachment().isStream()) {
        stickerBuilder.setData(createAttachmentPointer(message.getSticker().get().getAttachment().asStream()));
      } else {
        stickerBuilder.setData(createAttachmentPointer(message.getSticker().get().getAttachment().asPointer()));
      }

      builder.setSticker(stickerBuilder.build());
    }

    if (message.isViewOnce()) {
      builder.setIsViewOnce(message.isViewOnce());
      builder.setRequiredProtocolVersion(Math.max(DataMessage.ProtocolVersion.VIEW_ONCE_VIDEO_VALUE, builder.getRequiredProtocolVersion()));
    }

    if (message.getReaction().isPresent()) {
      DataMessage.Reaction.Builder reactionBuilder = DataMessage.Reaction.newBuilder()
                                                                         .setEmoji(message.getReaction().get().getEmoji())
                                                                         .setRemove(message.getReaction().get().isRemove())
                                                                         .setTargetSentTimestamp(message.getReaction().get().getTargetSentTimestamp());

      if (message.getReaction().get().getTargetAuthor().getNumber().isPresent()) {
        reactionBuilder.setTargetAuthorE164(message.getReaction().get().getTargetAuthor().getNumber().get());
      }

      if (message.getReaction().get().getTargetAuthor().getUuid().isPresent()) {
        reactionBuilder.setTargetAuthorUuid(message.getReaction().get().getTargetAuthor().getUuid().get().toString());
      }

      builder.setReaction(reactionBuilder.build());
      builder.setRequiredProtocolVersion(Math.max(DataMessage.ProtocolVersion.REACTIONS_VALUE, builder.getRequiredProtocolVersion()));
    }

    builder.setTimestamp(message.getTimestamp());

    return container.setDataMessage(builder).build().toByteArray();
  }

  private byte[] createCallContent(SignalServiceCallMessage callMessage) {
    Content.Builder     container = Content.newBuilder();
    CallMessage.Builder builder   = CallMessage.newBuilder();

    if (callMessage.getOfferMessage().isPresent()) {
      OfferMessage offer = callMessage.getOfferMessage().get();
      builder.setOffer(CallMessage.Offer.newBuilder()
                                        .setId(offer.getId())
                                        .setDescription(offer.getDescription()));
    } else if (callMessage.getAnswerMessage().isPresent()) {
      AnswerMessage answer = callMessage.getAnswerMessage().get();
      builder.setAnswer(CallMessage.Answer.newBuilder()
                                          .setId(answer.getId())
                                          .setDescription(answer.getDescription()));
    } else if (callMessage.getIceUpdateMessages().isPresent()) {
      List<IceUpdateMessage> updates = callMessage.getIceUpdateMessages().get();

      for (IceUpdateMessage update : updates) {
        builder.addIceUpdate(CallMessage.IceUpdate.newBuilder()
                                                  .setId(update.getId())
                                                  .setSdp(update.getSdp())
                                                  .setSdpMid(update.getSdpMid())
                                                  .setSdpMLineIndex(update.getSdpMLineIndex()));
      }
    } else if (callMessage.getHangupMessage().isPresent()) {
      builder.setHangup(CallMessage.Hangup.newBuilder().setId(callMessage.getHangupMessage().get().getId()));
    } else if (callMessage.getBusyMessage().isPresent()) {
      builder.setBusy(CallMessage.Busy.newBuilder().setId(callMessage.getBusyMessage().get().getId()));
    }

    container.setCallMessage(builder);
    return container.build().toByteArray();
  }

  private byte[] createMultiDeviceContactsContent(SignalServiceAttachmentStream contacts, boolean complete) throws IOException {
    Content.Builder     container = Content.newBuilder();
    SyncMessage.Builder builder   = createSyncMessageBuilder();
    builder.setContacts(SyncMessage.Contacts.newBuilder()
                                            .setBlob(createAttachmentPointer(contacts))
                                            .setComplete(complete));

    return container.setSyncMessage(builder).build().toByteArray();
  }

  private byte[] createMultiDeviceGroupsContent(SignalServiceAttachmentStream groups) throws IOException {
    Content.Builder     container = Content.newBuilder();
    SyncMessage.Builder builder   = createSyncMessageBuilder();
    builder.setGroups(SyncMessage.Groups.newBuilder()
                                        .setBlob(createAttachmentPointer(groups)));

    return container.setSyncMessage(builder).build().toByteArray();
  }

  private byte[] createMultiDeviceSentTranscriptContent(SentTranscriptMessage transcript, Optional<UnidentifiedAccessPair> unidentifiedAccess) throws IOException {
    SignalServiceAddress address = transcript.getDestination().get();
    SendMessageResult    result  = SendMessageResult.success(address, unidentifiedAccess.isPresent(), true);

    return createMultiDeviceSentTranscriptContent(createMessageContent(transcript.getMessage()),
                                                  Optional.of(address),
                                                  transcript.getTimestamp(),
                                                  Collections.singletonList(result),
                                                  false);
  }

  private byte[] createMultiDeviceSentTranscriptContent(byte[] content, Optional<SignalServiceAddress> recipient,
                                                        long timestamp, List<SendMessageResult> sendMessageResults,
                                                        boolean isRecipientUpdate)
  {
    try {
      Content.Builder          container   = Content.newBuilder();
      SyncMessage.Builder      syncMessage = createSyncMessageBuilder();
      SyncMessage.Sent.Builder sentMessage = SyncMessage.Sent.newBuilder();
      DataMessage              dataMessage = Content.parseFrom(content).getDataMessage();

      sentMessage.setTimestamp(timestamp);
      sentMessage.setMessage(dataMessage);

      for (SendMessageResult result : sendMessageResults) {
        if (result.getSuccess() != null) {
          SyncMessage.Sent.UnidentifiedDeliveryStatus.Builder builder = SyncMessage.Sent.UnidentifiedDeliveryStatus.newBuilder();

          if (result.getAddress().getUuid().isPresent()) {
            builder = builder.setDestinationUuid(result.getAddress().getUuid().get().toString());
          }

          if (result.getAddress().getNumber().isPresent()) {
            builder = builder.setDestinationE164(result.getAddress().getNumber().get());
          }

          builder.setUnidentified(result.getSuccess().isUnidentified());

          sentMessage.addUnidentifiedStatus(builder.build());
        }
      }

      if (recipient.isPresent()) {
        if (recipient.get().getUuid().isPresent())   sentMessage.setDestinationUuid(recipient.get().getUuid().get().toString());
        if (recipient.get().getNumber().isPresent()) sentMessage.setDestinationE164(recipient.get().getNumber().get());
      }

      if (dataMessage.getExpireTimer() > 0) {
        sentMessage.setExpirationStartTimestamp(System.currentTimeMillis());
      }

      if (dataMessage.getIsViewOnce()) {
        dataMessage = dataMessage.toBuilder().clearAttachments().build();
        sentMessage.setMessage(dataMessage);
      }

      sentMessage.setIsRecipientUpdate(isRecipientUpdate);

      return container.setSyncMessage(syncMessage.setSent(sentMessage)).build().toByteArray();
    } catch (InvalidProtocolBufferException e) {
      throw new AssertionError(e);
    }
  }

  private byte[] createMultiDeviceReadContent(List<ReadMessage> readMessages) {
    Content.Builder     container = Content.newBuilder();
    SyncMessage.Builder builder   = createSyncMessageBuilder();

    for (ReadMessage readMessage : readMessages) {
      SyncMessage.Read.Builder readBuilder = SyncMessage.Read.newBuilder().setTimestamp(readMessage.getTimestamp());

      if (readMessage.getSender().getUuid().isPresent()) {
        readBuilder.setSenderUuid(readMessage.getSender().getUuid().get().toString());
      }

      if (readMessage.getSender().getNumber().isPresent()) {
        readBuilder.setSenderE164(readMessage.getSender().getNumber().get());
      }

      builder.addRead(readBuilder.build());
    }

    return container.setSyncMessage(builder).build().toByteArray();
  }

  private byte[] createMultiDeviceViewOnceOpenContent(ViewOnceOpenMessage readMessage) {
    Content.Builder                  container       = Content.newBuilder();
    SyncMessage.Builder              builder         = createSyncMessageBuilder();
    SyncMessage.ViewOnceOpen.Builder viewOnceBuilder = SyncMessage.ViewOnceOpen.newBuilder().setTimestamp(readMessage.getTimestamp());

    if (readMessage.getSender().getUuid().isPresent()) {
      viewOnceBuilder.setSenderUuid(readMessage.getSender().getUuid().get().toString());
    }

    if (readMessage.getSender().getNumber().isPresent()) {
      viewOnceBuilder.setSenderE164(readMessage.getSender().getNumber().get());
    }

    builder.setViewOnceOpen(viewOnceBuilder.build());

    return container.setSyncMessage(builder).build().toByteArray();
  }

  private byte[] createMultiDeviceBlockedContent(BlockedListMessage blocked) {
    Content.Builder             container      = Content.newBuilder();
    SyncMessage.Builder         syncMessage    = createSyncMessageBuilder();
    SyncMessage.Blocked.Builder blockedMessage = SyncMessage.Blocked.newBuilder();

    for (SignalServiceAddress address : blocked.getAddresses()) {
      if (address.getUuid().isPresent()) {
        blockedMessage.addUuids(address.getUuid().get().toString());
      }
      if (address.getNumber().isPresent()) {
        blockedMessage.addNumbers(address.getNumber().get());
      }
    }

    for (byte[] groupId : blocked.getGroupIds()) {
      blockedMessage.addGroupIds(ByteString.copyFrom(groupId));
    }

    return container.setSyncMessage(syncMessage.setBlocked(blockedMessage)).build().toByteArray();
  }

  private byte[] createMultiDeviceConfigurationContent(ConfigurationMessage configuration) {
    Content.Builder                   container            = Content.newBuilder();
    SyncMessage.Builder               syncMessage          = createSyncMessageBuilder();
    SyncMessage.Configuration.Builder configurationMessage = SyncMessage.Configuration.newBuilder();

    if (configuration.getReadReceipts().isPresent()) {
      configurationMessage.setReadReceipts(configuration.getReadReceipts().get());
    }

    if (configuration.getUnidentifiedDeliveryIndicators().isPresent()) {
      configurationMessage.setUnidentifiedDeliveryIndicators(configuration.getUnidentifiedDeliveryIndicators().get());
    }

    if (configuration.getTypingIndicators().isPresent()) {
      configurationMessage.setTypingIndicators(configuration.getTypingIndicators().get());
    }

    if (configuration.getLinkPreviews().isPresent()) {
      configurationMessage.setLinkPreviews(configuration.getLinkPreviews().get());
    }

    configurationMessage.setProvisioningVersion(ProvisioningProtos.ProvisioningVersion.CURRENT_VALUE);

    return container.setSyncMessage(syncMessage.setConfiguration(configurationMessage)).build().toByteArray();
  }

  private byte[] createMultiDeviceStickerPackOperationContent(List<StickerPackOperationMessage> stickerPackOperations) {
    Content.Builder     container   = Content.newBuilder();
    SyncMessage.Builder syncMessage = createSyncMessageBuilder();

    for (StickerPackOperationMessage stickerPackOperation : stickerPackOperations) {
      SyncMessage.StickerPackOperation.Builder builder = SyncMessage.StickerPackOperation.newBuilder();

      if (stickerPackOperation.getPackId().isPresent()) {
        builder.setPackId(ByteString.copyFrom(stickerPackOperation.getPackId().get()));
      }

      if (stickerPackOperation.getPackKey().isPresent()) {
        builder.setPackKey(ByteString.copyFrom(stickerPackOperation.getPackKey().get()));
      }

      if (stickerPackOperation.getType().isPresent()) {
        switch (stickerPackOperation.getType().get()) {
          case INSTALL: builder.setType(SyncMessage.StickerPackOperation.Type.INSTALL); break;
          case REMOVE:  builder.setType(SyncMessage.StickerPackOperation.Type.REMOVE); break;
        }
      }

      syncMessage.addStickerPackOperation(builder);
    }

    return container.setSyncMessage(syncMessage).build().toByteArray();
  }

  private byte[] createMultiDeviceFetchTypeContent(SignalServiceSyncMessage.FetchType fetchType) {
    Content.Builder                 container    = Content.newBuilder();
    SyncMessage.Builder             syncMessage  = createSyncMessageBuilder();
    SyncMessage.FetchLatest.Builder fetchMessage = SyncMessage.FetchLatest.newBuilder();

    switch (fetchType) {
      case LOCAL_PROFILE:
        fetchMessage.setType(SyncMessage.FetchLatest.Type.LOCAL_PROFILE);
        break;
      case STORAGE_MANIFEST:
        fetchMessage.setType(SyncMessage.FetchLatest.Type.STORAGE_MANIFEST);
        break;
      default:
        Log.w(TAG, "Unknown fetch type!");
        break;
    }

    return container.setSyncMessage(syncMessage.setFetchLatest(fetchMessage)).build().toByteArray();
  }

  private byte[] createMultiDeviceVerifiedContent(VerifiedMessage verifiedMessage, byte[] nullMessage) {
    Content.Builder     container              = Content.newBuilder();
    SyncMessage.Builder syncMessage            = createSyncMessageBuilder();
    Verified.Builder    verifiedMessageBuilder = Verified.newBuilder();

    verifiedMessageBuilder.setNullMessage(ByteString.copyFrom(nullMessage));
    verifiedMessageBuilder.setIdentityKey(ByteString.copyFrom(verifiedMessage.getIdentityKey().serialize()));

    if (verifiedMessage.getDestination().getUuid().isPresent()) {
      verifiedMessageBuilder.setDestinationUuid(verifiedMessage.getDestination().getUuid().get().toString());
    }

    if (verifiedMessage.getDestination().getNumber().isPresent()) {
      verifiedMessageBuilder.setDestinationE164(verifiedMessage.getDestination().getNumber().get());
    }

    switch(verifiedMessage.getVerified()) {
      case DEFAULT:    verifiedMessageBuilder.setState(Verified.State.DEFAULT);    break;
      case VERIFIED:   verifiedMessageBuilder.setState(Verified.State.VERIFIED);   break;
      case UNVERIFIED: verifiedMessageBuilder.setState(Verified.State.UNVERIFIED); break;
      default:         throw new AssertionError("Unknown: " + verifiedMessage.getVerified());
    }

    syncMessage.setVerified(verifiedMessageBuilder);
    return container.setSyncMessage(syncMessage).build().toByteArray();
  }

  private SyncMessage.Builder createSyncMessageBuilder() {
    SecureRandom random  = new SecureRandom();
    byte[]       padding = Util.getRandomLengthBytes(512);
    random.nextBytes(padding);

    SyncMessage.Builder builder = SyncMessage.newBuilder();
    builder.setPadding(ByteString.copyFrom(padding));

    return builder;
  }

  private GroupContext createGroupContent(SignalServiceGroup group) throws IOException {
    GroupContext.Builder builder = GroupContext.newBuilder();
    builder.setId(ByteString.copyFrom(group.getGroupId()));

    if (group.getType() != SignalServiceGroup.Type.DELIVER) {
      if      (group.getType() == SignalServiceGroup.Type.UPDATE)       builder.setType(GroupContext.Type.UPDATE);
      else if (group.getType() == SignalServiceGroup.Type.QUIT)         builder.setType(GroupContext.Type.QUIT);
      else if (group.getType() == SignalServiceGroup.Type.REQUEST_INFO) builder.setType(GroupContext.Type.REQUEST_INFO);
      else                                                              throw new AssertionError("Unknown type: " + group.getType());

      if (group.getName().isPresent()) {
        builder.setName(group.getName().get());
      }

      if (group.getMembers().isPresent()) {
        for (SignalServiceAddress address : group.getMembers().get()) {
          if (address.getNumber().isPresent()) {
            builder.addMembersE164(address.getNumber().get());
          }

          GroupContext.Member.Builder memberBuilder = GroupContext.Member.newBuilder();

          if (address.getUuid().isPresent()) {
            memberBuilder.setUuid(address.getUuid().get().toString());
          }

          if (address.getNumber().isPresent()) {
            memberBuilder.setE164(address.getNumber().get());
          }

          builder.addMembers(memberBuilder.build());
        }
      }

      if (group.getOwner().isPresent()) builder.setOwner(group.getOwner().get());

      if (group.getAdmins().isPresent()) {
        for (SignalServiceAddress address : group.getAdmins().get()) {
          if (address.getNumber().isPresent()) {
            builder.addAdminsE164(address.getNumber().get());
          }

          GroupContext.Member.Builder memberBuilder = GroupContext.Member.newBuilder();

          if (address.getUuid().isPresent()) {
            memberBuilder.setUuid(address.getUuid().get().toString());
          }

          if (address.getNumber().isPresent()) {
            memberBuilder.setE164(address.getNumber().get());
          }

          builder.addAdmins(memberBuilder.build());
        }
      }

      if (group.getAvatar().isPresent()) {
        if (group.getAvatar().get().isStream()) {
          builder.setAvatar(createAttachmentPointer(group.getAvatar().get().asStream()));
        } else {
          builder.setAvatar(createAttachmentPointer(group.getAvatar().get().asPointer()));
        }
      }
    } else {
      builder.setType(GroupContext.Type.DELIVER);
    }

    return builder.build();
  }

  private List<DataMessage.Contact> createSharedContactContent(List<SharedContact> contacts) throws IOException {
    List<DataMessage.Contact> results = new LinkedList<>();

    for (SharedContact contact : contacts) {
      DataMessage.Contact.Name.Builder nameBuilder    = DataMessage.Contact.Name.newBuilder();

      if (contact.getName().getFamily().isPresent())  nameBuilder.setFamilyName(contact.getName().getFamily().get());
      if (contact.getName().getGiven().isPresent())   nameBuilder.setGivenName(contact.getName().getGiven().get());
      if (contact.getName().getMiddle().isPresent())  nameBuilder.setMiddleName(contact.getName().getMiddle().get());
      if (contact.getName().getPrefix().isPresent())  nameBuilder.setPrefix(contact.getName().getPrefix().get());
      if (contact.getName().getSuffix().isPresent())  nameBuilder.setSuffix(contact.getName().getSuffix().get());
      if (contact.getName().getDisplay().isPresent()) nameBuilder.setDisplayName(contact.getName().getDisplay().get());

      DataMessage.Contact.Builder contactBuilder = DataMessage.Contact.newBuilder()
                                                                      .setName(nameBuilder);

      if (contact.getAddress().isPresent()) {
        for (SharedContact.PostalAddress address : contact.getAddress().get()) {
          DataMessage.Contact.PostalAddress.Builder addressBuilder = DataMessage.Contact.PostalAddress.newBuilder();

          switch (address.getType()) {
            case HOME:   addressBuilder.setType(DataMessage.Contact.PostalAddress.Type.HOME); break;
            case WORK:   addressBuilder.setType(DataMessage.Contact.PostalAddress.Type.WORK); break;
            case CUSTOM: addressBuilder.setType(DataMessage.Contact.PostalAddress.Type.CUSTOM); break;
            default:     throw new AssertionError("Unknown type: " + address.getType());
          }

          if (address.getCity().isPresent())         addressBuilder.setCity(address.getCity().get());
          if (address.getCountry().isPresent())      addressBuilder.setCountry(address.getCountry().get());
          if (address.getLabel().isPresent())        addressBuilder.setLabel(address.getLabel().get());
          if (address.getNeighborhood().isPresent()) addressBuilder.setNeighborhood(address.getNeighborhood().get());
          if (address.getPobox().isPresent())        addressBuilder.setPobox(address.getPobox().get());
          if (address.getPostcode().isPresent())     addressBuilder.setPostcode(address.getPostcode().get());
          if (address.getRegion().isPresent())       addressBuilder.setRegion(address.getRegion().get());
          if (address.getStreet().isPresent())       addressBuilder.setStreet(address.getStreet().get());

          contactBuilder.addAddress(addressBuilder);
        }
      }

      if (contact.getEmail().isPresent()) {
        for (SharedContact.Email email : contact.getEmail().get()) {
          DataMessage.Contact.Email.Builder emailBuilder = DataMessage.Contact.Email.newBuilder()
                                                                                    .setValue(email.getValue());

          switch (email.getType()) {
            case HOME:   emailBuilder.setType(DataMessage.Contact.Email.Type.HOME);   break;
            case WORK:   emailBuilder.setType(DataMessage.Contact.Email.Type.WORK);   break;
            case MOBILE: emailBuilder.setType(DataMessage.Contact.Email.Type.MOBILE); break;
            case CUSTOM: emailBuilder.setType(DataMessage.Contact.Email.Type.CUSTOM); break;
            default:     throw new AssertionError("Unknown type: " + email.getType());
          }

          if (email.getLabel().isPresent()) emailBuilder.setLabel(email.getLabel().get());

          contactBuilder.addEmail(emailBuilder);
        }
      }

      if (contact.getPhone().isPresent()) {
        for (SharedContact.Phone phone : contact.getPhone().get()) {
          DataMessage.Contact.Phone.Builder phoneBuilder = DataMessage.Contact.Phone.newBuilder()
                                                                                    .setValue(phone.getValue());

          switch (phone.getType()) {
            case HOME:   phoneBuilder.setType(DataMessage.Contact.Phone.Type.HOME);   break;
            case WORK:   phoneBuilder.setType(DataMessage.Contact.Phone.Type.WORK);   break;
            case MOBILE: phoneBuilder.setType(DataMessage.Contact.Phone.Type.MOBILE); break;
            case CUSTOM: phoneBuilder.setType(DataMessage.Contact.Phone.Type.CUSTOM); break;
            default:     throw new AssertionError("Unknown type: " + phone.getType());
          }

          if (phone.getLabel().isPresent()) phoneBuilder.setLabel(phone.getLabel().get());

          contactBuilder.addNumber(phoneBuilder);
        }
      }

      if (contact.getAvatar().isPresent()) {
        AttachmentPointer pointer = contact.getAvatar().get().getAttachment().isStream() ? createAttachmentPointer(contact.getAvatar().get().getAttachment().asStream())
                                                                                         : createAttachmentPointer(contact.getAvatar().get().getAttachment().asPointer());
        contactBuilder.setAvatar(DataMessage.Contact.Avatar.newBuilder()
                                                           .setAvatar(pointer)
                                                           .setIsProfile(contact.getAvatar().get().isProfile()));
      }

      if (contact.getOrganization().isPresent()) {
        contactBuilder.setOrganization(contact.getOrganization().get());
      }

      results.add(contactBuilder.build());
    }

    return results;
  }

  private List<SendMessageResult> sendMessage(List<SignalServiceAddress>         recipients,
                                              List<Optional<UnidentifiedAccess>> unidentifiedAccess,
                                              long                               timestamp,
                                              byte[]                             content,
                                              boolean                            online)
      throws IOException
  {
    List<SendMessageResult>                results                    = new LinkedList<>();
    Iterator<SignalServiceAddress>         recipientIterator          = recipients.iterator();
    Iterator<Optional<UnidentifiedAccess>> unidentifiedAccessIterator = unidentifiedAccess.iterator();

    while (recipientIterator.hasNext()) {
      SignalServiceAddress recipient = recipientIterator.next();

      try {
        SendMessageResult result = sendMessage(recipient, unidentifiedAccessIterator.next(), timestamp, content, online);
        results.add(result);
      } catch (UntrustedIdentityException e) {
        Log.w(TAG, e);
        results.add(SendMessageResult.identityFailure(recipient, e.getIdentityKey()));
      } catch (UnregisteredUserException e) {
        Log.w(TAG, e);
        results.add(SendMessageResult.unregisteredFailure(recipient));
      } catch (PushNetworkException e) {
        Log.w(TAG, e);
        results.add(SendMessageResult.networkFailure(recipient));
      }
    }

    return results;
  }

  private SendMessageResult sendMessage(SignalServiceAddress         recipient,
                                        Optional<UnidentifiedAccess> unidentifiedAccess,
                                        long                         timestamp,
                                        byte[]                       content,
                                        boolean                      online)
      throws UntrustedIdentityException, IOException
  {
    for (int i=0;i<4;i++) {
      try {
        OutgoingPushMessageList            messages         = getEncryptedMessages(socket, recipient, unidentifiedAccess, timestamp, content, online);
        Optional<SignalServiceMessagePipe> pipe             = this.pipe.get();
        Optional<SignalServiceMessagePipe> unidentifiedPipe = this.unidentifiedPipe.get();

        if (pipe.isPresent() && !unidentifiedAccess.isPresent()) {
          try {
            Log.w(TAG, "Transmitting over pipe...");
            SendMessageResponse response = pipe.get().send(messages, Optional.<UnidentifiedAccess>absent());
            return SendMessageResult.success(recipient, false, response.getNeedsSync() || isMultiDevice.get());
          } catch (IOException e) {
            Log.w(TAG, e);
            Log.w(TAG, "Falling back to new connection...");
          }
        } else if (unidentifiedPipe.isPresent() && unidentifiedAccess.isPresent()) {
          try {
            Log.w(TAG, "Transmitting over unidentified pipe...");
            SendMessageResponse response = unidentifiedPipe.get().send(messages, unidentifiedAccess);
            return SendMessageResult.success(recipient, true, response.getNeedsSync() || isMultiDevice.get());
          } catch (IOException e) {
            Log.w(TAG, e);
            Log.w(TAG, "Falling back to new connection...");
          }
        }

        Log.w(TAG, "Not transmitting over pipe...");
        SendMessageResponse response = socket.sendMessage(messages, unidentifiedAccess);
        return SendMessageResult.success(recipient, unidentifiedAccess.isPresent(), response.getNeedsSync() || isMultiDevice.get());

      } catch (InvalidKeyException ike) {
        Log.w(TAG, ike);
        unidentifiedAccess = Optional.absent();
      } catch (AuthorizationFailedException afe) {
        Log.w(TAG, afe);
        if (unidentifiedAccess.isPresent()) {
          unidentifiedAccess = Optional.absent();
        } else {
          throw afe;
        }
      } catch (MismatchedDevicesException mde) {
        Log.w(TAG, mde);
        handleMismatchedDevices(socket, recipient, mde.getMismatchedDevices());
      } catch (StaleDevicesException ste) {
        Log.w(TAG, ste);
        handleStaleDevices(recipient, ste.getStaleDevices());
      }
    }

    throw new IOException("Failed to resolve conflicts after 3 attempts!");
  }

  private List<AttachmentPointer> createAttachmentPointers(Optional<List<SignalServiceAttachment>> attachments) throws IOException {
    List<AttachmentPointer> pointers = new LinkedList<>();

    if (!attachments.isPresent() || attachments.get().isEmpty()) {
      Log.w(TAG, "No attachments present...");
      return pointers;
    }

    for (SignalServiceAttachment attachment : attachments.get()) {
      if (attachment.isStream()) {
        Log.w(TAG, "Found attachment, creating pointer...");
        pointers.add(createAttachmentPointer(attachment.asStream()));
      } else if (attachment.isPointer()) {
        Log.w(TAG, "Including existing attachment pointer...");
        pointers.add(createAttachmentPointer(attachment.asPointer()));
      }
    }

    return pointers;
  }

  private AttachmentPointer createAttachmentPointer(SignalServiceAttachmentPointer attachment) {
    AttachmentPointer.Builder builder = AttachmentPointer.newBuilder()
                                                         .setContentType(attachment.getContentType())
                                                         .setId(attachment.getId())
                                                         .setKey(ByteString.copyFrom(attachment.getKey()))
                                                         .setDigest(ByteString.copyFrom(attachment.getDigest().get()))
                                                         .setSize(attachment.getSize().get());

    if (attachment.getFileName().isPresent()) {
      builder.setFileName(attachment.getFileName().get());
    }

    if (attachment.getPreview().isPresent()) {
      builder.setThumbnail(ByteString.copyFrom(attachment.getPreview().get()));
    }

    if (attachment.getWidth() > 0) {
      builder.setWidth(attachment.getWidth());
    }

    if (attachment.getHeight() > 0) {
      builder.setHeight(attachment.getHeight());
    }

    if (attachment.getVoiceNote()) {
      builder.setFlags(AttachmentPointer.Flags.VOICE_MESSAGE_VALUE);
    }

    if (attachment.getCaption().isPresent()) {
      builder.setCaption(attachment.getCaption().get());
    }

    if (attachment.getBlurHash().isPresent()) {
      builder.setBlurHash(attachment.getBlurHash().get());
    }

    return builder.build();
  }

  private AttachmentPointer createAttachmentPointer(SignalServiceAttachmentStream attachment)
      throws IOException
  {
    SignalServiceAttachmentPointer pointer = uploadAttachment(attachment);
    return createAttachmentPointer(pointer);
  }


  private OutgoingPushMessageList getEncryptedMessages(PushServiceSocket            socket,
                                                       SignalServiceAddress         recipient,
                                                       Optional<UnidentifiedAccess> unidentifiedAccess,
                                                       long                         timestamp,
                                                       byte[]                       plaintext,
                                                       boolean                      online)
      throws IOException, InvalidKeyException, UntrustedIdentityException
  {
    List<OutgoingPushMessage> messages = new LinkedList<>();

    if (!recipient.matches(localAddress) || unidentifiedAccess.isPresent()) {
      messages.add(getEncryptedMessage(socket, recipient, unidentifiedAccess, SignalServiceAddress.DEFAULT_DEVICE_ID, plaintext));
    }

    for (int deviceId : store.getSubDeviceSessions(recipient.getIdentifier())) {
      if (store.containsSession(new SignalProtocolAddress(recipient.getIdentifier(), deviceId))) {
        messages.add(getEncryptedMessage(socket, recipient, unidentifiedAccess, deviceId, plaintext));
      }
    }

    return new OutgoingPushMessageList(recipient.getIdentifier(), timestamp, messages, online);
  }

  private OutgoingPushMessage getEncryptedMessage(PushServiceSocket            socket,
                                                  SignalServiceAddress         recipient,
                                                  Optional<UnidentifiedAccess> unidentifiedAccess,
                                                  int                          deviceId,
                                                  byte[]                       plaintext)
      throws IOException, InvalidKeyException, UntrustedIdentityException
  {
    SignalProtocolAddress signalProtocolAddress = new SignalProtocolAddress(recipient.getIdentifier(), deviceId);
    SignalServiceCipher   cipher                = new SignalServiceCipher(localAddress, store, null);

    if (!store.containsSession(signalProtocolAddress)) {
      try {
        List<PreKeyBundle> preKeys = socket.getPreKeys(recipient, unidentifiedAccess, deviceId);

        for (PreKeyBundle preKey : preKeys) {
          try {
            SignalProtocolAddress preKeyAddress  = new SignalProtocolAddress(recipient.getIdentifier(), preKey.getDeviceId());
            SessionBuilder        sessionBuilder = new SessionBuilder(store, preKeyAddress);
            sessionBuilder.process(preKey);
          } catch (org.whispersystems.libsignal.UntrustedIdentityException e) {
            throw new UntrustedIdentityException("Untrusted identity key!", recipient.getIdentifier(), preKey.getIdentityKey());
          }
        }

        if (eventListener.isPresent()) {
          eventListener.get().onSecurityEvent(recipient);
        }
      } catch (InvalidKeyException e) {
        throw new IOException(e);
      }
    }

    try {
      return cipher.encrypt(signalProtocolAddress, unidentifiedAccess, plaintext);
    } catch (org.whispersystems.libsignal.UntrustedIdentityException e) {
      throw new UntrustedIdentityException("Untrusted on send", recipient.getIdentifier(), e.getUntrustedIdentity());
    }
  }

  private void handleMismatchedDevices(PushServiceSocket socket, SignalServiceAddress recipient,
                                       MismatchedDevices mismatchedDevices)
      throws IOException, UntrustedIdentityException
  {
    try {
      for (int extraDeviceId : mismatchedDevices.getExtraDevices()) {
        if (recipient.getUuid().isPresent()) {
          store.deleteSession(new SignalProtocolAddress(recipient.getUuid().get().toString(), extraDeviceId));
        }
        if (recipient.getNumber().isPresent()) {
          store.deleteSession(new SignalProtocolAddress(recipient.getNumber().get(), extraDeviceId));
        }
      }

      for (int missingDeviceId : mismatchedDevices.getMissingDevices()) {
        PreKeyBundle preKey = socket.getPreKey(recipient, missingDeviceId);

        try {
          SessionBuilder sessionBuilder = new SessionBuilder(store, new SignalProtocolAddress(recipient.getIdentifier(), missingDeviceId));
          sessionBuilder.process(preKey);
        } catch (org.whispersystems.libsignal.UntrustedIdentityException e) {
          throw new UntrustedIdentityException("Untrusted identity key!", recipient.getIdentifier(), preKey.getIdentityKey());
        }
      }
    } catch (InvalidKeyException e) {
      throw new IOException(e);
    }
  }

  private void handleStaleDevices(SignalServiceAddress recipient, StaleDevices staleDevices) {
    for (int staleDeviceId : staleDevices.getStaleDevices()) {
      if (recipient.getUuid().isPresent()) {
        store.deleteSession(new SignalProtocolAddress(recipient.getUuid().get().toString(), staleDeviceId));
      }
      if (recipient.getNumber().isPresent()) {
        store.deleteSession(new SignalProtocolAddress(recipient.getNumber().get(), staleDeviceId));
      }
    }
  }

  private Optional<UnidentifiedAccess> getTargetUnidentifiedAccess(Optional<UnidentifiedAccessPair> unidentifiedAccess) {
    if (unidentifiedAccess.isPresent()) {
      return unidentifiedAccess.get().getTargetUnidentifiedAccess();
    }

    return Optional.absent();
  }

  private List<Optional<UnidentifiedAccess>> getTargetUnidentifiedAccess(List<Optional<UnidentifiedAccessPair>> unidentifiedAccess) {
    List<Optional<UnidentifiedAccess>> results = new LinkedList<>();

    for (Optional<UnidentifiedAccessPair> item : unidentifiedAccess) {
      if (item.isPresent()) results.add(item.get().getTargetUnidentifiedAccess());
      else                  results.add(Optional.<UnidentifiedAccess>absent());
    }

    return results;
  }

  public static interface EventListener {
    public void onSecurityEvent(SignalServiceAddress address);
  }

}
