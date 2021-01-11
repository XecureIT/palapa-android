package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientExporter;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.Util;

import java.util.LinkedList;
import java.util.List;

public class GroupMembersDialog extends AsyncTask<Void, Void, List<Recipient>> {

  private static final String TAG = GroupMembersDialog.class.getSimpleName();

  private final Recipient  recipient;
  private final Context    context;

  public GroupMembersDialog(Context context, Recipient recipient) {
    this.recipient = recipient;
    this.context   = context;
  }

  @Override
  public void onPreExecute() {}

  @Override
  protected List<Recipient> doInBackground(Void... params) {
    return DatabaseFactory.getGroupDatabase(context).getGroupMembers(recipient.requireGroupId(), true);
  }

  @Override
  public void onPostExecute(List<Recipient> members) {
    GroupMembers groupMembers = new GroupMembers(members);
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setTitle(R.string.ConversationActivity_group_members);
    builder.setIconAttribute(R.attr.group_members_dialog_icon);
    builder.setCancelable(true);
    builder.setItems(groupMembers.getRecipientStrings(), new GroupMembersOnClickListener(context, groupMembers));
    builder.setPositiveButton(android.R.string.ok, null);
    builder.show();
  }

  public void display() {
    executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private static class GroupMembersOnClickListener implements DialogInterface.OnClickListener {
    private final GroupMembers groupMembers;
    private final Context      context;

    public GroupMembersOnClickListener(Context context, GroupMembers members) {
      this.context      = context;
      this.groupMembers = members;
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int item) {
      Recipient recipient = groupMembers.get(item);

      if (recipient.getContactUri() != null) {
        Intent intent = new Intent(context, RecipientPreferenceActivity.class);
        intent.putExtra(RecipientPreferenceActivity.RECIPIENT_ID, recipient.getId());

        context.startActivity(intent);
      } else {
        context.startActivity(RecipientExporter.export(recipient).asAddContactIntent());
      }
    }
  }

  /**
   * Wraps a List of Recipient (just like @class Recipients),
   * but with focus on the order of the Recipients.
   * So that the order of the RecipientStrings[] matches
   * the internal order.
   *
   * @author Christoph Haefner
   */
  private class GroupMembers {
    private final String TAG = GroupMembers.class.getSimpleName();

    private final LinkedList<Recipient> members = new LinkedList<>();

    public GroupMembers(List<Recipient> recipients) {
      for (Recipient recipient : recipients) {
        if (recipient.isLocalNumber()) {
          members.push(recipient);
        } else {
          members.add(recipient);
        }
      }
    }

    public String[] getRecipientStrings() {
      List<String> recipientStrings = new LinkedList<>();

      for (Recipient recipient : members) {
        if (recipient.isLocalNumber()) {
          recipientStrings.add(context.getString(R.string.GroupMembersDialog_me));
        } else {
          String name = getRecipientName(recipient);
          recipientStrings.add(name);
        }
      }

      return recipientStrings.toArray(new String[members.size()]);
    }

    private String getRecipientName(Recipient recipient) {
      if (FeatureFlags.PROFILE_DISPLAY) return recipient.getDisplayName(context);

      String name = recipient.toShortString(context);

      if (recipient.getName(context) == null && !TextUtils.isEmpty(recipient.getProfileName())) {
        name += " ~" + recipient.getProfileName();
      }

      return name;
    }

    public Recipient get(int index) {
      return members.get(index);
    }
  }
}
