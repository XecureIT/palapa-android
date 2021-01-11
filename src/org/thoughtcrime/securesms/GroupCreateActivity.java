/*
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import android.text.TextUtils;

import org.thoughtcrime.securesms.avatar.AvatarSelection;
import org.thoughtcrime.securesms.conversation.ConversationActivity;
import org.thoughtcrime.securesms.logging.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import org.thoughtcrime.securesms.components.PushRecipientsPanel;
import org.thoughtcrime.securesms.components.PushRecipientsPanel.RecipientsPanelChangedListener;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader.DisplayMode;
import org.thoughtcrime.securesms.contacts.RecipientsEditor;
import org.thoughtcrime.securesms.contacts.avatars.ContactColors;
import org.thoughtcrime.securesms.contacts.avatars.ResourceContactPhoto;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase.GroupRecord;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.groups.GroupManager.GroupActionResult;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.SelectedRecipientsAdapter;
import org.thoughtcrime.securesms.util.SelectedRecipientsAdapter.OnRecipientDeletedListener;
import org.thoughtcrime.securesms.util.SelectedRecipientsAdapter.RecipientWrapper;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Activity to create and update groups
 *
 * @author Jake McGinty
 */
public class GroupCreateActivity extends PassphraseRequiredActionBarActivity
                                 implements OnRecipientDeletedListener,
                                            RecipientsPanelChangedListener
{

  private final static String TAG = GroupCreateActivity.class.getSimpleName();

  public static final String GROUP_ID_EXTRA      = "group_id";
  public static final String GROUP_THREAD_EXTRA  = "group_thread";

  private final DynamicTheme    dynamicTheme    = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private static final int PICK_CONTACT = 1;
  public static final  int AVATAR_SIZE  = 210;

  private EditText     groupName;
  private ListView     lv;
  private ImageView    avatar;
  private TextView     creatingText;
  private Bitmap       avatarBmp;
  RecipientsEditor     recipientsEditor;
  private ImageButton  contactsButton;
  public int updateGroup = 1;

  @NonNull private Optional<GroupData> groupToUpdate = Optional.absent();

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle state, boolean ready) {
    setContentView(R.layout.group_create_activity);
    //noinspection ConstantConditions
    initializeAppBar();
    initializeResources();
    initializeExistingGroup();
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
    updateViewState();
  }

  private boolean isSignalGroup() {
    return TextSecurePreferences.isPushRegistered(this) && !getAdapter().hasNonPushMembers();
  }

  private void disableSignalGroupViews(int reasonResId) {
    View pushDisabled = findViewById(R.id.push_disabled);
    pushDisabled.setVisibility(View.VISIBLE);
    ((TextView) findViewById(R.id.push_disabled_reason)).setText(reasonResId);
    avatar.setEnabled(false);
    groupName.setEnabled(false);
  }

  private void enableSignalGroupViews() {
    findViewById(R.id.push_disabled).setVisibility(View.GONE);
    //avatar.setEnabled(true);
    //groupName.setEnabled(true);
  }

  @SuppressWarnings("ConstantConditions")
  private void updateViewState() {
    if (!TextSecurePreferences.isPushRegistered(this)) {
      disableSignalGroupViews(R.string.GroupCreateActivity_youre_not_registered_for_signal);
      getSupportActionBar().setTitle(R.string.GroupCreateActivity_actionbar_mms_title);
    } else if (getAdapter().hasNonPushMembers()) {
      disableSignalGroupViews(R.string.GroupCreateActivity_contacts_dont_support_push);
      getSupportActionBar().setTitle(R.string.GroupCreateActivity_actionbar_mms_title);
    } else {
      enableSignalGroupViews();
      getSupportActionBar().setTitle(groupToUpdate.isPresent()
                                     ? R.string.GroupCreateActivity_actionbar_edit_title
                                     : R.string.GroupCreateActivity_actionbar_title);
    }
  }

  private static boolean isActiveInDirectory(Recipient recipient) {
    return recipient.resolve().getRegistered() == RecipientDatabase.RegisteredState.REGISTERED;
  }

  private void addSelectedContacts(@NonNull Recipient... recipients) {
    new AddMembersTask(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, recipients);
  }

  private void addSelectedContacts(@NonNull Collection<Recipient> recipients) {
    addSelectedContacts(recipients.toArray(new Recipient[recipients.size()]));
  }

  private void initializeAppBar() {
    Drawable upIcon = ContextCompat.getDrawable(this, R.drawable.ic_arrow_left_24);
    getSupportActionBar().setHomeAsUpIndicator(upIcon);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
  }

  private void initializeResources() {
                        recipientsEditor = ViewUtil.findById(this, R.id.recipients_text);
    PushRecipientsPanel recipientsPanel  = ViewUtil.findById(this, R.id.recipients);
    lv           = ViewUtil.findById(this, R.id.selected_contacts_list);
    avatar       = ViewUtil.findById(this, R.id.avatar);
    groupName    = ViewUtil.findById(this, R.id.group_name);
    groupName.setEnabled(false);
    creatingText = ViewUtil.findById(this, R.id.creating_group_text);
    SelectedRecipientsAdapter adapter = new SelectedRecipientsAdapter(this);
    adapter.setOnRecipientDeletedListener(this);
    lv.setAdapter(adapter);
    recipientsEditor.setVisibility(View.GONE);
    recipientsEditor.setHint(R.string.recipients_panel__add_members);
    recipientsPanel.setPanelChangeListener(this);
    contactsButton = ViewUtil.findById(this, R.id.contacts_button);
    contactsButton.setVisibility(View.GONE);
    contactsButton.setOnClickListener(new AddRecipientButtonListener());
    avatar.setImageDrawable(new ResourceContactPhoto(R.drawable.ic_group_outline_40, R.drawable.ic_group_outline_20).asDrawable(this, ContactColors.UNKNOWN_COLOR.toConversationColor(this)));
    avatar.setOnClickListener(view -> AvatarSelection.startAvatarSelection(this, false, false));
    avatar.setEnabled(false);
  }

  private void initializeExistingGroup() {
    final String groupId = getIntent().getStringExtra(GROUP_ID_EXTRA);

    if (groupId != null) {
      new FillExistingGroupInfoAsyncTask(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, groupId);
    } else {
      groupName.setEnabled(true);
      avatar.setEnabled(true);
      recipientsEditor.setVisibility(View.VISIBLE);
      contactsButton.setVisibility(View.VISIBLE);
    }
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    inflater.inflate(R.menu.group_create, menu);
    if (updateGroup == 0) {
      menu.findItem(R.id.menu_create_group).setVisible(false);
    }
    super.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
        return true;
      case R.id.menu_create_group:
        if (groupToUpdate.isPresent()) handleGroupUpdate();
        else                           handleGroupCreate();
        return true;
    }

    return false;
  }

  @Override
  public void onRecipientDeleted(Recipient recipient) {
    getAdapter().remove(recipient);
    updateViewState();
  }

  @Override
  public void onRecipientsPanelUpdate(List<Recipient> recipients) {
    if (recipients != null && !recipients.isEmpty()) addSelectedContacts(recipients);
  }

  private void handleGroupCreate() {
    if (getGroupName() == null || getGroupName().length() == 0) {
      Toast.makeText(getApplicationContext(), R.string.GroupCreateActivity_group_name_empty, Toast.LENGTH_SHORT).show();
      return ;
    }

    if (getAdapter().getCount() < 1) {
      Log.i(TAG, getString(R.string.GroupCreateActivity_contacts_no_members));
      Toast.makeText(getApplicationContext(), R.string.GroupCreateActivity_contacts_no_members, Toast.LENGTH_SHORT).show();
      return;
    }
    if (isSignalGroup()) {
      new CreateSignalGroupTask(this, avatarBmp, getGroupName(), getAdapter().getRecipients()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    } else {
      new CreateMmsGroupTask(this, getAdapter().getRecipients()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  private void handleGroupUpdate() {
    if (getGroupName() == null || getGroupName().length() == 0) {
      Toast.makeText(getApplicationContext(), R.string.GroupCreateActivity_group_name_empty, Toast.LENGTH_SHORT).show();
      return ;
    }

    new UpdateSignalGroupTask(this, groupToUpdate.get().id, avatarBmp,
                              getGroupName(), getAdapter().getRecipients(), groupToUpdate.get().admins).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private void handleOpenConversation(long threadId, Recipient recipient) {
    Intent intent = new Intent(this, ConversationActivity.class);
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);
    intent.putExtra(ConversationActivity.RECIPIENT_EXTRA, recipient.getId());
    startActivity(intent);
    finish();
  }

  private void handleDisplayContextMenu(int position) {
    RecipientWrapper rw = (RecipientWrapper) getAdapter().getItem(position);
    new RecipientContextDialog(this, rw).display();
  }

  private void handleRemoveRecipient(Recipient recipient) {
    onRecipientDeleted(recipient);
  }

  private void handleGrantAdmin(Recipient recipient) {
    getAdapter().addAdmin(recipient);
  }

  private void handleRevokeAdmin(Recipient recipient) {
    getAdapter().removeAdmin(recipient);
  }

  private SelectedRecipientsAdapter getAdapter() {
    return (SelectedRecipientsAdapter)lv.getAdapter();
  }

  private @Nullable String getGroupName() {
    return groupName.getText() != null ? groupName.getText().toString() : null;
  }

  @Override
  public void onActivityResult(int reqCode, int resultCode, final Intent data) {
    super.onActivityResult(reqCode, resultCode, data);
    Uri outputFile = Uri.fromFile(new File(getCacheDir(), "cropped"));

    if (data == null || resultCode != Activity.RESULT_OK)
      return;

    switch (reqCode) {
      case PICK_CONTACT:
        List<RecipientId> selected = data.getParcelableArrayListExtra(PushContactSelectionActivity.KEY_SELECTED_RECIPIENTS);

        for (RecipientId contact : selected) {
          Recipient recipient = Recipient.resolved(contact);
          addSelectedContacts(recipient);
        }

        break;

      case AvatarSelection.REQUEST_CODE_AVATAR:
        AvatarSelection.circularCropImage(this, data.getData(), outputFile, R.string.CropImageActivity_group_avatar);
        break;
      case AvatarSelection.REQUEST_CODE_CROP_IMAGE:
        final Uri resultUri = AvatarSelection.getResultUri(data);
        GlideApp.with(this)
                .asBitmap()
                .load(resultUri)
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .centerCrop()
                .override(AVATAR_SIZE, AVATAR_SIZE)
                .into(new SimpleTarget<Bitmap>() {
                  @Override
                  public void onResourceReady(@NonNull Bitmap resource, Transition<? super Bitmap> transition) {
                    setAvatar(resultUri, resource);
                  }
                });
    }
  }

  private class RecipientContextDialog {

    private GroupCreateActivity        activity;
    private final RecipientWrapper     wrapper;

    public RecipientContextDialog(GroupCreateActivity activity, RecipientWrapper wrapper) {
      this.activity    = activity;
      this.wrapper     = wrapper;
    }

    public void display() {
      String localNumber   = Recipient.self().getE164().get();
      //String localNumber   = TextSecurePreferences.getLocalNumber(activity);
      boolean isLocalOwner = activity.getAdapter().isOwnerNumber(localNumber);
      boolean isLocalAdmin = activity.getAdapter().isAdminNumber(localNumber);
      if (!wrapper.isOwner() && (isLocalOwner || isLocalAdmin) && !Recipient.self().equals(wrapper.getRecipient())) {
        List<String> actions = new LinkedList<>();
        actions.add(activity.getString(R.string.GroupCreateActivity_menu_remove_member, wrapper.getRecipient().getDisplayName(getApplicationContext())));
        if (isLocalOwner) {
          if (wrapper.isAdmin()) {
            actions.add(activity.getString(R.string.GroupCreateActivity_menu_revoke_group_admin));
          } else {
            actions.add(activity.getString(R.string.GroupCreateActivity_menu_make_group_admin));
          }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setCancelable(true);
        builder.setItems(actions.toArray(new String[actions.size()]), new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            switch (which) {
              case 0:
                handleRemoveRecipient(wrapper.getRecipient());
                break;
              case 1:
                if (wrapper.isAdmin()) {
                  handleRevokeAdmin(wrapper.getRecipient());
                } else {
                  handleGrantAdmin(wrapper.getRecipient());
                }
                break;
            }
          }
        });
        builder.show();
      }
    }
  }

  private class AddRecipientButtonListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      Intent intent = new Intent(GroupCreateActivity.this, PushContactSelectionActivity.class);
      if (groupToUpdate.isPresent()) {
        intent.putExtra(ContactSelectionListFragment.DISPLAY_MODE, DisplayMode.FLAG_PUSH);
      } else {
        intent.putExtra(ContactSelectionListFragment.DISPLAY_MODE, DisplayMode.FLAG_PUSH | DisplayMode.FLAG_SMS);
      }
      startActivityForResult(intent, PICK_CONTACT);
    }
  }

  private static class CreateMmsGroupTask extends AsyncTask<Void,Void,GroupActionResult> {
    private final GroupCreateActivity activity;
    private final Set<Recipient>      members;

    public CreateMmsGroupTask(GroupCreateActivity activity, Set<Recipient> members) {
      this.activity     = activity;
      this.members      = members;
    }

    @Override
    protected GroupActionResult doInBackground(Void... avoid) {
      List<RecipientId> memberAddresses = new LinkedList<>();

      for (Recipient recipient : members) {
        memberAddresses.add(recipient.getId());
      }
      memberAddresses.add(Recipient.self().getId());

      String      groupId          = DatabaseFactory.getGroupDatabase(activity).getOrCreateGroupForMembers(memberAddresses, true);
      RecipientId groupRecipientId = DatabaseFactory.getRecipientDatabase(activity).getOrInsertFromGroupId(groupId);
      Recipient   groupRecipient   = Recipient.resolved(groupRecipientId);
      long        threadId         = DatabaseFactory.getThreadDatabase(activity).getThreadIdFor(groupRecipient, ThreadDatabase.DistributionTypes.DEFAULT);

      return new GroupActionResult(groupRecipient, threadId);
    }

    @Override
    protected void onPostExecute(GroupActionResult result) {
      activity.handleOpenConversation(result.getThreadId(), result.getGroupRecipient());
    }

    @Override
    protected void onProgressUpdate(Void... values) {
      super.onProgressUpdate(values);
    }
  }

  private abstract static class SignalGroupTask extends AsyncTask<Void,Void,Optional<GroupActionResult>> {

    protected GroupCreateActivity activity;
    protected Bitmap              avatar;
    protected Set<Recipient>      members;
    protected String              owner;
    protected Set<Recipient>      admins;
    protected String              name;

    public SignalGroupTask(GroupCreateActivity activity,
                           Bitmap              avatar,
                           String              name,
                           Set<Recipient>      members,
                           String              owner,
                           Set<Recipient>      admins)
    {
      this.activity     = activity;
      this.avatar       = avatar;
      this.name         = name;
      this.members      = members;
      this.owner        = owner;
      this.admins       = admins;
    }

    @Override
    protected void onPreExecute() {
      activity.findViewById(R.id.group_details_layout).setVisibility(View.GONE);
      activity.findViewById(R.id.creating_group_layout).setVisibility(View.VISIBLE);
      activity.findViewById(R.id.menu_create_group).setVisibility(View.GONE);
        final int titleResId = activity.groupToUpdate.isPresent()
                             ? R.string.GroupCreateActivity_updating_group
                             : R.string.GroupCreateActivity_creating_group;
        activity.creatingText.setText(activity.getString(titleResId, activity.getGroupName()));
      }

    @Override
    protected void onPostExecute(Optional<GroupActionResult> groupActionResultOptional) {
      if (activity.isFinishing()) return;
      activity.findViewById(R.id.group_details_layout).setVisibility(View.VISIBLE);
      activity.findViewById(R.id.creating_group_layout).setVisibility(View.GONE);
      activity.findViewById(R.id.menu_create_group).setVisibility(View.VISIBLE);
    }
  }

  private static class CreateSignalGroupTask extends SignalGroupTask {
    public CreateSignalGroupTask(GroupCreateActivity activity, Bitmap avatar, String name, Set<Recipient> members) {
      super(activity, avatar, name, members, null, null);
    }

    @Override
    protected Optional<GroupActionResult> doInBackground(Void... aVoid) {
      return Optional.of(GroupManager.createGroup(activity, members, avatar, name, false));
    }

    @Override
    protected void onPostExecute(Optional<GroupActionResult> result) {
      if (result.isPresent() && result.get().getThreadId() > -1) {
        if (!activity.isFinishing()) {
          activity.handleOpenConversation(result.get().getThreadId(), result.get().getGroupRecipient());
        }
      } else {
        super.onPostExecute(result);
        Toast.makeText(activity.getApplicationContext(),
                       R.string.GroupCreateActivity_contacts_invalid_number, Toast.LENGTH_LONG).show();
      }
    }
  }

  private static class UpdateSignalGroupTask extends SignalGroupTask {
    private String groupId;

    public UpdateSignalGroupTask(GroupCreateActivity activity, String groupId,
                                 Bitmap avatar, String name, Set<Recipient> members, Set<Recipient> admins)
    {
      super(activity, avatar, name, members, null, admins);
      this.groupId = groupId;
    }

    @Override
    protected Optional<GroupActionResult> doInBackground(Void... aVoid) {
      try {
        return Optional.of(GroupManager.updateGroup(activity, groupId, members, admins, avatar, name));
      } catch (InvalidNumberException e) {
        return Optional.absent();
      }
    }

    @Override
    protected void onPostExecute(Optional<GroupActionResult> result) {
      if (result.isPresent() && result.get().getThreadId() > -1) {
        if (!activity.isFinishing()) {
          Intent intent = activity.getIntent();
          intent.putExtra(GROUP_THREAD_EXTRA, result.get().getThreadId());
          intent.putExtra(GROUP_ID_EXTRA, result.get().getGroupRecipient().requireGroupId());
          activity.setResult(RESULT_OK, intent);
          activity.finish();
        }
      } else {
        super.onPostExecute(result);
        Toast.makeText(activity.getApplicationContext(),
                       R.string.GroupCreateActivity_contacts_invalid_number, Toast.LENGTH_LONG).show();
      }
    }
  }

  private static class AddMembersTask extends AsyncTask<Recipient,Void,List<AddMembersTask.Result>> {
    static class Result {
      Optional<Recipient> recipient;
      boolean             isPush;
      String              reason;

      public Result(@Nullable Recipient recipient, boolean isPush, @Nullable String reason) {
        this.recipient = Optional.fromNullable(recipient);
        this.isPush    = isPush;
        this.reason    = reason;
      }
    }

    private GroupCreateActivity activity;
    private boolean             failIfNotPush;

    public AddMembersTask(@NonNull GroupCreateActivity activity) {
      this.activity      = activity;
      this.failIfNotPush = activity.groupToUpdate.isPresent();
    }

    @Override
    protected List<Result> doInBackground(Recipient... recipients) {
      final List<Result> results = new LinkedList<>();

      for (Recipient recipient : recipients) {
        boolean isPush = isActiveInDirectory(recipient);

        if (failIfNotPush && !isPush) {
          results.add(new Result(null, false, activity.getString(R.string.GroupCreateActivity_cannot_add_non_push_to_existing_group,
                                                                 recipient.toShortString(activity))));
        } else if (TextUtils.equals(TextSecurePreferences.getLocalNumber(activity), recipient.getE164().or(""))) {
          results.add(new Result(null, false, activity.getString(R.string.GroupCreateActivity_youre_already_in_the_group)));
        } else {
          results.add(new Result(recipient, isPush, null));
        }
      }
      return results;
    }

    @Override
    protected void onPostExecute(List<Result> results) {
      if (activity.isFinishing()) return;

      for (Result result : results) {
        if (result.recipient.isPresent()) {
          activity.getAdapter().add(result.recipient.get(), result.isPush);
        } else {
          Toast.makeText(activity, result.reason, Toast.LENGTH_SHORT).show();
        }
      }
      activity.updateViewState();
    }
  }

  private static class FillExistingGroupInfoAsyncTask extends ProgressDialogAsyncTask<String,Void,Optional<GroupData>> {
    private GroupCreateActivity activity;

    public FillExistingGroupInfoAsyncTask(GroupCreateActivity activity) {
      super(activity,
            R.string.GroupCreateActivity_loading_group_details,
            R.string.please_wait);
      this.activity = activity;
    }

    @Override
    protected Optional<GroupData> doInBackground(String... groupIds) {
      final GroupDatabase         db               = DatabaseFactory.getGroupDatabase(activity);
      final List<Recipient>       recipients       = db.getGroupMembers(groupIds[0], true);
      final List<Recipient>       admins           = db.getGroupAdmins(groupIds[0]);
      final Optional<GroupRecord> group            = db.getGroup(groupIds[0]);
      final Set<Recipient>        existingContacts = new HashSet<>(recipients.size());
      final Set<Recipient>        existingAdmins   = new HashSet<>(admins.size());
      existingContacts.addAll(recipients);
      existingAdmins.addAll(admins);

      if (group.isPresent()) {
        return Optional.of(new GroupData(groupIds[0],
                                         existingContacts,
                                         group.get().getOwner(),
                                         existingAdmins,
                                         BitmapUtil.fromByteArray(group.get().getAvatar()),
                                         group.get().getAvatar(),
                                         group.get().getTitle()));
      } else {
        return Optional.absent();
      }
    }

    @Override
    protected void onPostExecute(Optional<GroupData> group) {
      super.onPostExecute(group);

      if (group.isPresent() && !activity.isFinishing()) {
        activity.groupToUpdate = group;

        activity.groupName.setText(group.get().name);
        if (group.get().avatarBmp != null) {
          activity.setAvatar(group.get().avatarBytes, group.get().avatarBmp);
        }
        SelectedRecipientsAdapter adapter = new SelectedRecipientsAdapter(activity, group.get().recipients, group.get().owner, group.get().admins);
        adapter.setOnRecipientDeletedListener(activity);
        activity.lv.setAdapter(adapter);
        activity.lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
          @Override
          public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            activity.handleDisplayContextMenu(position);
          }
        });
        activity.updateViewState();
        String localNumber = TextSecurePreferences.getLocalNumber(activity);
        if (adapter.isOwnerNumber(localNumber) || adapter.isAdminNumber(localNumber)) {
          activity.groupName.setEnabled(true);
          activity.avatar.setEnabled(true);
          activity.recipientsEditor.setVisibility(View.VISIBLE);
          activity.contactsButton.setVisibility(View.VISIBLE);
        } else {
          activity.updateGroup = 0;
          activity.supportInvalidateOptionsMenu();
        }
      } else if (!group.isPresent()) {
        activity.groupName.setEnabled(true);
        activity.avatar.setEnabled(true);
        activity.recipientsEditor.setVisibility(View.VISIBLE);
        activity.contactsButton.setVisibility(View.VISIBLE);
      }
    }
  }

  private <T> void setAvatar(T model, Bitmap bitmap) {
    avatarBmp = bitmap;
    GlideApp.with(this)
            .load(model)
            .circleCrop()
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .into(avatar);
  }

  private static class GroupData {
    String         id;
    Set<Recipient> recipients;
    String         owner;
    Set<Recipient> admins;
    Bitmap         avatarBmp;
    byte[]         avatarBytes;
    String         name;

    public GroupData(String id, Set<Recipient> recipients, String owner, Set<Recipient> admins, Bitmap avatarBmp, byte[] avatarBytes, String name) {
      this.id          = id;
      this.recipients  = recipients;
      this.owner       = owner;
      this.admins      = admins;
      this.avatarBmp   = avatarBmp;
      this.avatarBytes = avatarBytes;
      this.name        = name;
    }
  }
}
