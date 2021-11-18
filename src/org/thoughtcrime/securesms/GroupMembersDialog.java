package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.loaders.DeviceListLoader;
import org.thoughtcrime.securesms.devicelist.Device;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientExporter;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

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
    String tittle = context.getString(R.string.ConversationActivity_group_members) + "   ("+members.size()+")";
    GroupMembers groupMembers = new GroupMembers(members);
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    //builder.setTitle(R.string.ConversationActivity_group_members);
    builder.setTitle(tittle);
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
      Collections.sort(members, new Comparator<Recipient>() {
          @Override
          public int compare(Recipient r1, Recipient r2) {
            String nameshortR1 = null;   String nameshortR2 = null;
            if(r1.toShortString(context) == null && r2.toShortString(context)  == null){
              return -1;
            }else if(r1.toShortString(context) == null){
              return 1;
            }else{
              if (r1.isLocalNumber()) {
                nameshortR1 = context.getString(R.string.GroupMembersDialog_me);
              }else{
                nameshortR1 = getRecipientName(r1);
              }
              if (r2.isLocalNumber()) {
                nameshortR2 = context.getString(R.string.GroupMembersDialog_me);
              }else{
                nameshortR2 = getRecipientName(r2);
              }
              return nameshortR1.compareTo(nameshortR2);
            }
          }
      });
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
//      Collections.sort(recipientStrings,new MyComparatorMamberDialog());
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

  private static class MyComparatorMamberDialog implements Comparator<String>{

    @Override
    public int compare(String str1, String str2) {
      if(str1 == null && str2 == null){
        return -1;
      }else if(str1 == null){
        return 1;
      }else{
        return str1.compareTo(str2);
      }
    }
  }

}
