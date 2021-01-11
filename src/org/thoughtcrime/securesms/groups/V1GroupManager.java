package org.thoughtcrime.securesms.groups;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.protobuf.ByteString;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.UriAttachment;
import org.thoughtcrime.securesms.blurhash.BlurHash;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.groups.GroupManager.GroupActionResult;
import org.thoughtcrime.securesms.mms.OutgoingGroupMediaMessage;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

final class V1GroupManager {

  static @NonNull GroupActionResult createGroup(@NonNull Context          context,
                                                @NonNull Set<RecipientId> memberIds,
                                                @Nullable Bitmap          avatar,
                                                @Nullable String          name,
                                                          boolean         mms)
  {
    final byte[]        avatarBytes      = BitmapUtil.toByteArray(avatar);
    final GroupDatabase groupDatabase    = DatabaseFactory.getGroupDatabase(context);
    final String        groupId          = GroupUtil.getEncodedId(groupDatabase.allocateGroupId(), mms);
    final RecipientId   groupRecipientId = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);
    final Recipient     groupRecipient   = Recipient.resolved(groupRecipientId);

    String owner = Recipient.self().getE164().get();
    memberIds.add(Recipient.self().getId());
    groupDatabase.create(groupId, name, new LinkedList<>(memberIds), owner, new LinkedList<>(),null, null);

    if (!mms) {
      groupDatabase.updateAvatar(groupId, avatarBytes);
      DatabaseFactory.getRecipientDatabase(context).setProfileSharing(groupRecipient.getId(), true);
      return sendGroupUpdate(context, groupId, owner, memberIds, new HashSet<>(), name, avatarBytes, null);
    } else {
      long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipient, ThreadDatabase.DistributionTypes.CONVERSATION);
      return new GroupActionResult(groupRecipient, threadId);
    }
  }

  static GroupActionResult updateGroup(@NonNull  Context          context,
                                       @NonNull  String           groupId,
                                       @NonNull  Set<RecipientId> memberAddresses,
                                       @NonNull  Set<RecipientId> adminAddresses,
                                       @Nullable Bitmap           avatar,
                                       @Nullable String           name)
      throws InvalidNumberException
  {
    final GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);
    final GroupDatabase.GroupRecord groupRecord = groupDatabase.getGroup(groupId).get();
    final byte[]        avatarBytes   = BitmapUtil.toByteArray(avatar);

    List<RecipientId> xMembers = groupDatabase.getCurrentMembers(groupId);
    xMembers.removeAll(memberAddresses);
    if (xMembers.size() > 0) {
      xMembers.addAll(memberAddresses);
    }

    memberAddresses.add(Recipient.self().getId());
    groupDatabase.updateMembers(groupId, new LinkedList<>(memberAddresses));
    groupDatabase.updateAdmins(groupId, new LinkedList<>(adminAddresses));
    groupDatabase.updateTitle(groupId, name);
    groupDatabase.updateAvatar(groupId, avatarBytes);

    if (!GroupUtil.isMmsGroup(groupId)) {
      return sendGroupUpdate(context, groupId, groupRecord.getOwner(), memberAddresses, adminAddresses, name, avatarBytes, xMembers);
    } else {
      RecipientId groupRecipientId = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);
      Recipient   groupRecipient   = Recipient.resolved(groupRecipientId);
      long        threadId         = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipient);
      return new GroupActionResult(groupRecipient, threadId);
    }
  }

  private static GroupActionResult sendGroupUpdate(@NonNull  Context          context,
                                                   @NonNull  String           groupId,
                                                   @Nullable String           owner,
                                                   @NonNull  Set<RecipientId> members,
                                                   @NonNull  Set<RecipientId> admins,
                                                   @Nullable String           groupName,
                                                   @Nullable byte[]           avatar,
                                                   @Nullable List<RecipientId> xMembers)
  {
    try {
      Attachment  avatarAttachment = null;
      RecipientId groupRecipientId = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);
      Recipient   groupRecipient   = Recipient.resolved(groupRecipientId);

      List<GroupContext.Member> uuidMembers = new LinkedList<>();
      List<String>              e164Members = new LinkedList<>();
      List<GroupContext.Member> uuidAdmins  = new LinkedList<>();
      List<String>              e164Admins  = new LinkedList<>();

      for (RecipientId member : members) {
        Recipient recipient = Recipient.resolved(member);
        uuidMembers.add(GroupMessageProcessor.createMember(RecipientUtil.toSignalServiceAddress(context, recipient)));
      }
      for (RecipientId member : admins) {
        Recipient recipient = Recipient.resolved(member);
        uuidAdmins.add(GroupMessageProcessor.createMember(RecipientUtil.toSignalServiceAddress(context, recipient)));
      }

      GroupContext.Builder groupContextBuilder = GroupContext.newBuilder()
                                                             .setId(ByteString.copyFrom(GroupUtil.getDecodedId(groupId)))
                                                             .setType(GroupContext.Type.UPDATE)
                                                             .addAllMembersE164(e164Members)
                                                             .addAllMembers(uuidMembers)
                                                             .addAllAdminsE164(e164Admins)
                                                             .addAllAdmins(uuidAdmins);
      if (owner != null) groupContextBuilder.setOwner(owner);
      if (groupName != null) groupContextBuilder.setName(groupName);
      GroupContext groupContext = groupContextBuilder.build();

      if (avatar != null) {
        Uri avatarUri = BlobProvider.getInstance().forData(avatar).createForSingleUseInMemory();
        avatarAttachment = new UriAttachment(avatarUri, MediaUtil.IMAGE_PNG, AttachmentDatabase.TRANSFER_PROGRESS_DONE, avatar.length, null, false, false, null, null, null, null);
      }

      OutgoingGroupMediaMessage outgoingMessage = new OutgoingGroupMediaMessage(groupRecipient, groupContext, avatarAttachment, System.currentTimeMillis(), 0, false, null, Collections.emptyList(), Collections.emptyList());
      long                      threadId        = MessageSender.send(context, outgoingMessage, -1, false, null, xMembers);

      return new GroupActionResult(groupRecipient, threadId);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }
}
