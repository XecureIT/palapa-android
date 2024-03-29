/*
 * Copyright (C) 2015 Open Whisper Systems
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
package org.thoughtcrime.securesms.conversationlist;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.PluralsRes;
import androidx.annotation.WorkerThread;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.widget.TooltipCompat;
import androidx.lifecycle.ViewModelProviders;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.MainFragment;
import org.thoughtcrime.securesms.MainNavigator;
import org.thoughtcrime.securesms.NewConversationActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversationlist.ConversationListAdapter.ItemClickListener;
import org.thoughtcrime.securesms.components.RatingManager;
import org.thoughtcrime.securesms.components.SearchToolbar;
import org.thoughtcrime.securesms.components.recyclerview.DeleteItemAnimator;
import org.thoughtcrime.securesms.components.registration.PulsingFloatingActionButton;
import org.thoughtcrime.securesms.components.reminder.DefaultSmsReminder;
import org.thoughtcrime.securesms.components.reminder.DozeReminder;
import org.thoughtcrime.securesms.components.reminder.ExpiredBuildReminder;
import org.thoughtcrime.securesms.components.reminder.OutdatedBuildReminder;
import org.thoughtcrime.securesms.components.reminder.PushRegistrationReminder;
import org.thoughtcrime.securesms.components.reminder.Reminder;
import org.thoughtcrime.securesms.components.reminder.ReminderView;
import org.thoughtcrime.securesms.components.reminder.ServiceOutageReminder;
import org.thoughtcrime.securesms.components.reminder.ShareReminder;
import org.thoughtcrime.securesms.components.reminder.SystemSmsImportReminder;
import org.thoughtcrime.securesms.components.reminder.UnauthorizedReminder;
import org.thoughtcrime.securesms.conversationlist.model.MessageResult;
import org.thoughtcrime.securesms.conversationlist.model.SearchResult;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessagingDatabase.MarkedMessageInfo;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.loaders.ConversationListLoader;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.events.ReminderUpdateEvent;
import org.thoughtcrime.securesms.insights.InsightsLauncher;
import org.thoughtcrime.securesms.jobs.ServiceOutageDetectionJob;
import org.thoughtcrime.securesms.lock.RegistrationLockDialog;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mediaoverview.MediaOverviewActivity;
import org.thoughtcrime.securesms.mediasend.MediaSendActivity;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.notifications.MarkReadReceiver;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.AvatarUtil;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.task.SnackbarAsyncTask;
import org.thoughtcrime.securesms.vicon.ApiRoomPalapa;
import org.thoughtcrime.securesms.vicon.RoomActivity;
import org.thoughtcrime.securesms.vicon.RoomModel;
import org.thoughtcrime.securesms.vicon.ServiceApiRoomPalapa;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.profiles.AccountTestToken;
import org.whispersystems.signalservice.api.profiles.ProfileTokenAndEndPoint;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class ConversationListFragment extends MainFragment implements LoaderManager.LoaderCallbacks<Cursor>,
                                                                      ActionMode.Callback,
                                                                      ItemClickListener,
                                                                      ConversationListSearchAdapter.EventListener,
                                                                      MainNavigator.BackHandler
{
  private static final String TAG = Log.tag(ConversationListFragment.class);

  private static final int[] EMPTY_IMAGES = new int[] { R.drawable.empty_inbox_1,
                                                        R.drawable.empty_inbox_2,
                                                        R.drawable.empty_inbox_3,
                                                        R.drawable.empty_inbox_4,
                                                        R.drawable.empty_inbox_5 };

  private ActionMode                    actionMode;
  private RecyclerView                  list;
  private ReminderView                  reminderView;
  private View                          emptyState;
  private ImageView                     emptyImage;
  private TextView                      searchEmptyState;
  private PulsingFloatingActionButton   fab;
  private PulsingFloatingActionButton   cameraFab;
  private PulsingFloatingActionButton   viconFab;
  private SearchToolbar                 searchToolbar;
  private ImageView                     searchAction;
  private View                          toolbarShadow;
  private ConversationListViewModel     viewModel;
  private RecyclerView.Adapter          activeAdapter;
  private ConversationListAdapter       defaultAdapter;
  private ConversationListSearchAdapter searchAdapter;
  private StickyHeaderDecoration        searchAdapterDecoration;
  private String username;
  private Map<String, String> map;
  private ProfileTokenAndEndPoint profileTokenAndEndPoint;
  private AccountTestToken accountTestToken;
  private SignalServiceMessageSender messageSender;
  private android.app.AlertDialog dialogCreateAndList;
  private AlertDialog.Builder dialogBuilder;
  private AlertDialog.Builder dialogBuilderCreate;
  private ProgressDialog progressDialog;

  public static ConversationListFragment newInstance() {
    return new ConversationListFragment();
  }

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setHasOptionsMenu(true);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    initialEndPoint();
    initialAuthToken();
    return inflater.inflate(R.layout.conversation_list_fragment, container, false);
  }


  public TextView titleViewAlert(){
    LinearLayout.LayoutParams layoutP = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
                              layoutP.topMargin = 35;
                              layoutP.bottomMargin = 35;

    TextView tvT = new TextView(getContext());
    tvT.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
    tvT.setGravity(Gravity.LEFT);
    tvT.setTypeface(Typeface.SERIF, Typeface.NORMAL);
    tvT.setPadding(20,35,10,35);
    tvT.setLayoutParams(layoutP);
    tvT.setText(R.string.MessageRequestViconInformationRoom_header);
    tvT.setTextColor(Color.parseColor("#ffffff"));
    String themeApp = TextSecurePreferences.getTheme(getActivity());
    if((themeApp).equals(DynamicTheme.DARK)){
      tvT.setBackgroundResource(R.color.signal_primary_dark);
    } else {
      tvT.setBackgroundResource(R.color.signal_primary);
    }

    return tvT;
  }

  private void handleActionRoomList(){
    ProgressDialog progressDoalogList = new ProgressDialog(getContext());
    progressDoalogList.setMessage("Loading....");
    progressDoalogList.show();
    try {
      //RecipientId recipientId = getActivity().getIntent().getParcelableExtra(ConversationActivity.RECIPIENT_EXTRA);
      Intent intent = new Intent(getActivity(), RoomActivity.class);
      //intent.putExtra(RoomActivity.RECIPIENT_EXTRA, recipientId);
      intent.putExtra(RoomActivity.END_POINT, profileTokenAndEndPoint.getEndpointUrl());
      intent.putExtra(RoomActivity.RESULT_END_POINT, String.valueOf(profileTokenAndEndPoint.isResult()));
      intent.putExtra(RoomActivity.ERROR_MESSAGE_END_POINT, profileTokenAndEndPoint.getErrorMessage());
      intent.putExtra(RoomActivity.AUTH_TOKEN, accountTestToken.getAuthToken());
      intent.putExtra(RoomActivity.USER_TIME, accountTestToken.getUserTime());
      intent.putExtra(RoomActivity.RESULT_TOKEN, String.valueOf(accountTestToken.isResult()));
      intent.putExtra(RoomActivity.ERROR_MESSAGE_TOKEN, accountTestToken.getErrorMessage());
      startActivity(intent);
    }catch (Exception e){
      e.printStackTrace();
      Toast.makeText(getContext(), R.string.ConversationActivity_connection_server_scp_failed, Toast.LENGTH_SHORT).show();
    }
    progressDoalogList.dismiss();
  }

  private void handleActionCreateRoomAndList(){
    LayoutInflater inflater = getLayoutInflater();
    View dialogView = inflater.inflate(R.layout.conversation_alert_room, null);
    dialogBuilder = new AlertDialog.Builder(getActivity());
    dialogBuilder.setView(dialogView);

    ImageButton   linierButtonCreate           = (ImageButton) dialogView.findViewById(R.id.linierButtonCreate);
    TextView      linierTextViewCreate         = (TextView)      dialogView.findViewById(R.id.linierTextViewCreate);

    ImageButton   linierButtonListRoom       = (ImageButton) dialogView.findViewById(R.id.linierButtonListRoom);
    TextView      linierTextViewList         = (TextView)      dialogView.findViewById(R.id.linierTextViewList);

    TextView      buttonCencel         = (TextView)      dialogView.findViewById(R.id.buttonCencel);

   String themeApp = TextSecurePreferences.getTheme(getActivity());
   if((themeApp).equals(DynamicTheme.DARK)){
      linierButtonListRoom.setImageResource(R.drawable.ic_list_room_bar);
      linierTextViewCreate.setTextColor(Color.WHITE);
      linierButtonCreate.setImageResource(R.drawable.ic_create_room_bar);
      linierTextViewList.setTextColor(Color.WHITE);
    }


    AlertDialog alertDialog = dialogBuilder.create();
    alertDialog.show();

    linierButtonCreate.setOnClickListener(new View.OnClickListener(){
      public void onClick(View v)  {  alertDialog.dismiss();     handleActionCreateRoom();     }
    });
    linierButtonListRoom.setOnClickListener(new View.OnClickListener(){
      public void onClick(View v)  {
        alertDialog.dismiss();
        new AsyncTask<Void, Void, Void>() {
          @Override
          protected void onPreExecute() {
            super.onPreExecute();
            progressDialog.show();
          }

          @Override
          protected Void doInBackground(Void... params) {
            handleDisplayListRoom();
            return null;
          }

          @Override
          protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            progressDialog.dismiss();
          }
        }.execute();
      }
    });
    buttonCencel.setOnClickListener(new View.OnClickListener(){
      public void onClick(View v)  {  alertDialog.dismiss();     }
    });

  }

  private void handleActionCreateRoom() {
    LayoutInflater inflater = getLayoutInflater();
    View dialogView = inflater.inflate(R.layout.conversation_alert_room_create, null);
    EditText      createRoomTextView         = (EditText)      dialogView.findViewById(R.id.createRoom);
    String themeApp = TextSecurePreferences.getTheme(getActivity());
    if((themeApp).equals(DynamicTheme.DARK)){
      createRoomTextView.setHintTextColor(Color.WHITE);
    }
    createRoomTextView.requestFocus();
    showKeyboard();
    dialogBuilderCreate = new AlertDialog.Builder(getActivity());
    dialogBuilderCreate
            .setView(dialogView)
            .setPositiveButton(R.string.MessageRequestViconCreateRoom_footer_create, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int id) {
                new AsyncTask<Void, Void, Void>() {
                  @Override
                  protected void onPreExecute() {
                    super.onPreExecute();
                    progressDialog.show();
                  }
                  @Override
                  protected Void doInBackground(Void... params) {
                    try {
                      if (profileTokenAndEndPoint.isResult() && accountTestToken.isResult()) {
                        String BASE_URL = profileTokenAndEndPoint.getEndpointUrl();
                        ServiceApiRoomPalapa service = ApiRoomPalapa.getRetrofitInstance(BASE_URL).create(ServiceApiRoomPalapa.class);
                        Recipient self = Recipient.self();
                        username = self.requireE164();
                        map = new HashMap<>();
                        map.put("x-authToken", accountTestToken.getAuthToken());
                        map.put("x-userTime", accountTestToken.getUserTime());
                        try {
                              showUrlCreateRoom(dialogView, service,progressDialog);
                        } catch (Exception ex) {
                          onFailRoom();
                        }
                      }
                      hideKeyboard();
                    }catch (Exception e){
                      e.printStackTrace();
                      Toast.makeText(getContext(), R.string.ConversationActivity_connection_server_scp_failed, Toast.LENGTH_SHORT).show();
                    }
                    return null;
                  }
                  @Override
                  protected void onPostExecute(Void aVoid) {
                    super.onPostExecute(aVoid);
                    //progressDialog.dismiss();
                  }
                }.execute();
              }
            })
            .setNegativeButton(R.string.MessageRequestViconCreateRoom_footer_cancel, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int id) {       hideKeyboard();                dialog.dismiss();            }
            });
    TextView      buttonCencel         = (TextView)      dialogView.findViewById(R.id.buttonCencel);
    AlertDialog alertDialogCreate = dialogBuilderCreate.create();
    alertDialogCreate.show();
    buttonCencel.setOnClickListener(new View.OnClickListener(){
      public void onClick(View v)  {        hideKeyboard();        alertDialogCreate.dismiss();      }
    });
  }

  public void showKeyboard(){
    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
    imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);
  }
  public void hideKeyboard(){
    InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    reminderView     = view.findViewById(R.id.reminder);
    list             = view.findViewById(R.id.list);
    fab              = view.findViewById(R.id.fab);
    cameraFab        = view.findViewById(R.id.camera_fab);
    emptyState       = view.findViewById(R.id.empty_state);
    emptyImage       = view.findViewById(R.id.empty);
    searchEmptyState = view.findViewById(R.id.search_no_results);
    searchToolbar    = view.findViewById(R.id.search_toolbar);
    searchAction     = view.findViewById(R.id.search_action);
    toolbarShadow    = view.findViewById(R.id.conversation_list_toolbar_shadow);
    viconFab        = view.findViewById(R.id.vicon_fab);

    progressDialog = new ProgressDialog(getContext());
    progressDialog.setMessage("Loading....");

    Toolbar toolbar = view.findViewById(getToolbarRes());
    toolbar.setVisibility(View.VISIBLE);
    ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);


    fab.show();
    cameraFab.show();
    if(BuildConfig.VCON_ENABLED) {
      viconFab.show();
    } else {
      viconFab.hide();
    }

    reminderView.setOnDismissListener(this::updateReminders);

    list.setHasFixedSize(true);
    list.setLayoutManager(new LinearLayoutManager(getActivity()));
    list.setItemAnimator(new DeleteItemAnimator());
    list.addOnScrollListener(new ScrollListener());

    new ItemTouchHelper(new ArchiveListenerCallback()).attachToRecyclerView(list);

    fab.setOnClickListener(v -> startActivity(new Intent(getActivity(), NewConversationActivity.class)));
    cameraFab.setOnClickListener(v -> {
      Permissions.with(requireActivity())
                 .request(Manifest.permission.CAMERA)
                 .ifNecessary()
                 .withRationaleDialog(getString(R.string.ConversationActivity_to_capture_photos_and_video_allow_signal_access_to_the_camera), R.drawable.ic_camera_solid_24)
                 .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_needs_the_camera_permission_to_take_photos_or_video))
                 .onAllGranted(() -> startActivity(MediaSendActivity.buildCameraFirstIntent(requireActivity())))
                 .onAnyDenied(() -> Toast.makeText(requireContext(), R.string.ConversationActivity_signal_needs_camera_permissions_to_take_photos_or_video, Toast.LENGTH_LONG).show())
                 .execute();
    });

    viconFab.setOnClickListener(v -> {
      handleActionCreateRoomAndList();
    });



    initializeListAdapters();
    initializeViewModel();
    initializeTypingObserver();
    initializeSearchListener();

    RatingManager.showRatingDialogIfNecessary(requireContext());
    RegistrationLockDialog.showReminderIfNecessary(requireContext());

    TooltipCompat.setTooltipText(searchAction, getText(R.string.SearchToolbar_search_for_conversations_contacts_and_messages));
  }

  public void handleShareRoom(String dataparam){
    Intent sharingIntent = new Intent(Intent.ACTION_SEND);
    sharingIntent.setType("text/plain");
    String shareBody = dataparam;
    sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "Subject Here");
    sharingIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
    startActivity(Intent.createChooser(sharingIntent, "Share via"));
  }

  private void showUrlCreateRoom(View customLayout,ServiceApiRoomPalapa service,ProgressDialog progressDialogCreate) {
             try{
                      EditText editText = customLayout.findViewById(R.id.createRoom);
                      TableLayout tableP = new TableLayout(getContext());
                      TableRow trP = new TableRow(getContext());
                      TextView tvT = new TextView(getContext());
                      Call<RoomModel> call = service.createRoom(new RoomModel().ApiJsonMapCreate(editText.getText().toString(), username), map);
                      call.enqueue(new Callback<RoomModel>() {
                        @Override
                        public void onResponse(Call<RoomModel> call, Response<RoomModel> response) {
                          RoomModel paramModel = (RoomModel) response.body();
                          if (response.isSuccessful()) {
                            if ("".equals(paramModel.getEror())) {
                              if (paramModel.getResult()) {
                                new AsyncTask<Void, Void, Void>() {
                                  @Override
                                  protected void onPreExecute() {
                                    super.onPreExecute();
                                  }
                                  @Override
                                  protected Void doInBackground(Void... params) {
                                    handleDisplayListRoom();
                                    return null;
                                  }
                                  @Override
                                  protected void onPostExecute(Void aVoid) {
                                    super.onPostExecute(aVoid);
                                    progressDialogCreate.dismiss();
                                  }
                                }.execute();
                              } else {
                                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
                                alertDialogBuilder
                                        .setTitle(R.string.MessageRequestViconInformationRoom_header)
                                        .setMessage(paramModel.getErrorMessage())
                                        .setCancelable(false)
                                        .setPositiveButton(R.string.MessageRequestViconInformationRoom_footer, new DialogInterface.OnClickListener() {
                                          public void onClick(DialogInterface dialog, int id) {
                                            dialog.cancel();
                                          }
                                        });
                                AlertDialog alertDialog = alertDialogBuilder.create();
                                alertDialog.show();
                                progressDialogCreate.dismiss();
                              }
                            } else {
                              String strDataUri = "" + paramModel.getEror();
                              String tokenData[] = strDataUri.split(":");
                              String maxRoom = tokenData[1];
                              AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
                              alertDialogBuilder
                                      .setTitle(R.string.MessageRequestViconInformationRoom_header)
                                      .setMessage(getResources().getString(R.string.MessageRequestViconInformationRoom_max) + " : " + maxRoom + ". " + getResources().getString(R.string.MessageRequestViconInformationRoom_max_))
                                      .setCancelable(false)
                                      .setPositiveButton(R.string.MessageRequestViconInformationRoom_footer, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                          dialog.cancel();
                                        }
                                      });
                              AlertDialog alertDialog = alertDialogBuilder.create();
                              alertDialog.show();
                              progressDialogCreate.dismiss();
                            }
                          }
                        }

                        @Override
                        public void onFailure(Call<RoomModel> call, Throwable t) {
                          t.printStackTrace();
                          progressDialogCreate.dismiss();
                          Toast.makeText(getContext(), R.string.ConversationActivity_connection_server_scp_create_room_failed, Toast.LENGTH_SHORT).show();
                        }
                      });
          }catch (Exception e){
            e.printStackTrace();
            progressDialogCreate.dismiss();
            Toast.makeText(getContext(), R.string.ConversationActivity_connection_server_scp_failed, Toast.LENGTH_SHORT).show();
          }
  }

  private void onFailRoom(){
    AlertDialog.Builder alertFail = new AlertDialog.Builder(getActivity());
    alertFail
            .setTitle(R.string.MessageRequestViconInformationRoom_header)
            .setMessage(getResources().getString(R.string.MessageRequestViconInformationRoom_create_fail))
            .setCancelable(false)
            .setPositiveButton(R.string.MessageRequestViconInformationRoom_footer, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
              }
            });
    AlertDialog alertDialogFail = alertFail.create();
    alertDialogFail.show();
  }

  @Override
  public void onResume() {
    super.onResume();

    updateReminders();
    list.getAdapter().notifyDataSetChanged();
    EventBus.getDefault().register(this);

    if (TextSecurePreferences.isSmsEnabled(requireContext())) {
      InsightsLauncher.showInsightsModal(requireContext(), requireFragmentManager());
    }

    SimpleTask.run(getLifecycle(), Recipient::self, this::initializeProfileIcon);

    if (!searchToolbar.isVisible() && list.getAdapter() != defaultAdapter) {
      activeAdapter = defaultAdapter;
      list.removeItemDecoration(searchAdapterDecoration);
      list.setAdapter(defaultAdapter);
    }
  }

  @Override
  public void onPause() {
    super.onPause();

    fab.stopPulse();
    cameraFab.stopPulse();
    viconFab.stopPulse();
    EventBus.getDefault().unregister(this);
  }


  @Override
  public void onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = requireActivity().getMenuInflater();
    menu.clear();

    inflater.inflate(R.menu.text_secure_normal, menu);

    menu.findItem(R.id.menu_insights).setVisible(TextSecurePreferences.isSmsEnabled(requireContext()));
    menu.findItem(R.id.menu_clear_passphrase).setVisible(!TextSecurePreferences.isPasswordDisabled(requireContext()));
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
      case R.id.menu_new_group:         handleCreateGroup();     return true;
      case R.id.menu_view_media:        handleViewMedia();       return true;
      case R.id.menu_settings:          handleDisplaySettings(); return true;
      case R.id.menu_clear_passphrase:  handleClearPassphrase(); return true;
      case R.id.menu_mark_all_read:     handleMarkAllRead();     return true;
      case R.id.menu_invite:            handleInvite();          return true;
      case R.id.menu_insights:          handleInsights();        return true;
      case R.id.menu_help:              handleHelp();            return true;
      case R.id.menu_note_to_self:      handleNoteToSelf();      return true;
    }

    return false;
  }

  @Override
  public boolean onBackPressed() {
    if (searchToolbar.isVisible() || activeAdapter == searchAdapter) {
      activeAdapter = defaultAdapter;
      list.removeItemDecoration(searchAdapterDecoration);
      list.setAdapter(defaultAdapter);
      searchToolbar.collapse();
      return true;
    }

    return false;
  }

  @Override
  public void onConversationClicked(@NonNull ThreadRecord threadRecord) {
    getNavigator().goToConversation(threadRecord.getRecipient().getId(),
                                    threadRecord.getThreadId(),
                                    threadRecord.getDistributionType(),
                                    threadRecord.getLastSeen(),
                                    -1);
  }

  @Override
  public void onContactClicked(@NonNull Recipient contact) {
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
      return DatabaseFactory.getThreadDatabase(getContext()).getThreadIdIfExistsFor(contact);
    }, threadId -> {
      getNavigator().goToConversation(contact.getId(),
                                      threadId,
                                      ThreadDatabase.DistributionTypes.DEFAULT,
                                      -1,
                                      -1);
    });
  }

  @Override
  public void onMessageClicked(@NonNull MessageResult message) {
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
      int startingPosition = DatabaseFactory.getMmsSmsDatabase(getContext()).getMessagePositionInConversation(message.threadId, message.receivedTimestampMs);
      return Math.max(0, startingPosition);
    }, startingPosition -> {
      getNavigator().goToConversation(message.conversationRecipient.getId(),
                                      message.threadId,
                                      ThreadDatabase.DistributionTypes.DEFAULT,
                                      -1,
                                      startingPosition);
    });
  }

  private void initializeProfileIcon(@NonNull Recipient recipient) {
    ImageView icon = requireView().findViewById(R.id.toolbar_icon);

    AvatarUtil.loadIconIntoImageView(recipient, icon);
    icon.setOnClickListener(v -> getNavigator().goToAppSettings());
  }
  private void initialEndPoint(){
       new AsyncTask<Void, Void, Void>() {
         ProfileTokenAndEndPoint profileTokenAndEndPointParam;
         protected Void doInBackground(Void... arg0) {
           try {
             messageSender = ApplicationDependencies.getSignalServiceMessageSender();
             profileTokenAndEndPointParam = messageSender.getEndPoint(RoomActivity.MODULE_ID);
           } catch (IOException e){
             e.printStackTrace();
           }
           return null;
         }
         protected void onPostExecute(Void result) {
           if(profileTokenAndEndPointParam != null && profileTokenAndEndPointParam.isResult())
            profileTokenAndEndPoint = profileTokenAndEndPointParam;
       }
      }.execute();
  }

  private void initialAuthToken(){
       new AsyncTask<Void, Void, Void>() {
         AccountTestToken accountTestTokenParam;
        protected Void doInBackground(Void... arg0) {
           try {
             messageSender = ApplicationDependencies.getSignalServiceMessageSender();
             accountTestTokenParam = messageSender.getToken(RoomActivity.MODULE_ID);
           } catch (IOException e){
             e.printStackTrace();
           }
           return null;
         }
         protected void onPostExecute(Void result) {
           if(accountTestTokenParam != null && accountTestTokenParam.isResult())
             accountTestToken = accountTestTokenParam;
         }
       }.execute();
  }

  private void initializeSearchListener() {
    searchAction.setOnClickListener(v -> {
      searchToolbar.display(searchAction.getX() + (searchAction.getWidth() / 2.0f),
                            searchAction.getY() + (searchAction.getHeight() / 2.0f));
    });

    searchToolbar.setListener(new SearchToolbar.SearchListener() {
      @Override
      public void onSearchTextChange(String text) {
        String trimmed = text.trim();

        viewModel.updateQuery(trimmed);

        if (trimmed.length() > 0) {
          if (activeAdapter != searchAdapter) {
            activeAdapter = searchAdapter;
            list.setAdapter(searchAdapter);
            list.removeItemDecoration(searchAdapterDecoration);
            list.addItemDecoration(searchAdapterDecoration);
          }
        } else {
          if (activeAdapter != defaultAdapter) {
            activeAdapter = defaultAdapter;
            list.removeItemDecoration(searchAdapterDecoration);
            list.setAdapter(defaultAdapter);
          }
        }
      }

      @Override
      public void onSearchClosed() {
        list.removeItemDecoration(searchAdapterDecoration);
        list.setAdapter(defaultAdapter);
      }
    });
  }

  private void initializeListAdapters() {
    defaultAdapter          = new ConversationListAdapter      (requireContext(), GlideApp.with(this), Locale.getDefault(), null, this);
    searchAdapter           = new ConversationListSearchAdapter(GlideApp.with(this), this, Locale.getDefault            ()            );
    searchAdapterDecoration = new StickyHeaderDecoration(searchAdapter, false, false);
    activeAdapter           = defaultAdapter;

    list.setAdapter(defaultAdapter);
    LoaderManager.getInstance(this).restartLoader(0, null, this);
  }

  private void initializeTypingObserver() {
    ApplicationContext.getInstance(requireContext()).getTypingStatusRepository().getTypingThreads().observe(this, threadIds -> {
      if (threadIds == null) {
        threadIds = Collections.emptySet();
      }

      defaultAdapter.setTypingThreads(threadIds);
    });
  }

  private void initializeViewModel() {
    viewModel = ViewModelProviders.of(this, new ConversationListViewModel.Factory()).get(ConversationListViewModel.class);

    viewModel.getSearchResult().observe(this, result -> {
      result = result != null ? result : SearchResult.EMPTY;
      searchAdapter.updateResults(result);

      if (result.isEmpty() && activeAdapter == searchAdapter) {
        searchEmptyState.setText(getString(R.string.SearchFragment_no_results, result.getQuery()));
        searchEmptyState.setVisibility(View.VISIBLE);
      } else {
        searchEmptyState.setVisibility(View.GONE);
      }
    });
  }

  private void updateReminders() {
    Context context = requireContext();

    SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
      if (UnauthorizedReminder.isEligible(context)) {
        return Optional.of(new UnauthorizedReminder(context));
      } else if (ExpiredBuildReminder.isEligible()) {
        return Optional.of(new ExpiredBuildReminder(context));
      } else if (ServiceOutageReminder.isEligible(context)) {
        ApplicationDependencies.getJobManager().add(new ServiceOutageDetectionJob());
        return Optional.of(new ServiceOutageReminder(context));
      } else if (OutdatedBuildReminder.isEligible()) {
        return Optional.of(new OutdatedBuildReminder(context));
      } else if (DefaultSmsReminder.isEligible(context)) {
        return Optional.of(new DefaultSmsReminder(context));
      } else if (Util.isDefaultSmsProvider(context) && SystemSmsImportReminder.isEligible(context)) {
        return Optional.of((new SystemSmsImportReminder(context)));
      } else if (PushRegistrationReminder.isEligible(context)) {
        return Optional.of((new PushRegistrationReminder(context)));
      } else if (ShareReminder.isEligible(context)) {
        return Optional.of(new ShareReminder(context));
      } else if (DozeReminder.isEligible(context)) {
        return Optional.of(new DozeReminder(context));
      } else {
        return Optional.<Reminder>absent();
      }
    }, reminder -> {
      if (reminder.isPresent() && getActivity() != null && !isRemoving()) {
        reminderView.showReminder(reminder.get());
      } else if (!reminder.isPresent()) {
        reminderView.hide();
      }
    });
  }

  private void handleCreateGroup() {
    getNavigator().goToGroupCreation();
  }

  private void handleViewMedia() {
    startActivity(MediaOverviewActivity.forAllFromMain(requireContext()));
  }


  private void handleDisplayListRoom() {
    try {
      Window window = getActivity().getWindow();
      int statusBarColor = window.getStatusBarColor();
      getNavigator().goToAppListRoom(profileTokenAndEndPoint, accountTestToken,statusBarColor);
    }catch (Exception e){
      e.printStackTrace();
      Toast.makeText(getContext(), R.string.ConversationActivity_connection_server_scp_failed, Toast.LENGTH_SHORT).show();
    }
  }

  private void handleDisplaySettings() {
    getNavigator().goToAppSettings();
  }

  private void handleClearPassphrase() {
    Intent intent = new Intent(requireActivity(), KeyCachingService.class);
    intent.setAction(KeyCachingService.CLEAR_KEY_ACTION);
    requireActivity().startService(intent);
  }

  private void handleMarkAllRead() {
    Context context = requireContext();

    SignalExecutors.BOUNDED.execute(() -> {
      List<MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context).setAllThreadsRead();

      MessageNotifier.updateNotification(context);
      MarkReadReceiver.process(context, messageIds);
    });
  }

  private void handleInvite() {
    getNavigator().goToInvite();
  }

  private void handleInsights() {
    getNavigator().goToInsights();
  }

  private void handleHelp() {
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://client.xecure.world")));
    } catch (ActivityNotFoundException e) {
      Toast.makeText(requireActivity(), R.string.ConversationListActivity_there_is_no_browser_installed_on_your_device, Toast.LENGTH_LONG).show();
    }
  }

  private void handleNoteToSelf() {
    Recipient self = Recipient.self();
    getNavigator().goToConversation(self.getId(),
                                    DatabaseFactory.getThreadDatabase(getActivity()).getThreadIdIfExistsFor(self),
                                    ThreadDatabase.DistributionTypes.DEFAULT,
                                    -1,
                                    -1);
  }

  @SuppressLint("StaticFieldLeak")
  private void handleArchiveAllSelected() {
    Set<Long> selectedConversations = new HashSet<>(defaultAdapter.getBatchSelections());
    int       count                 = selectedConversations.size();
    String    snackBarTitle         = getResources().getQuantityString(getArchivedSnackbarTitleRes(), count, count);

    new SnackbarAsyncTask<Void>(getView(),
                                snackBarTitle,
                                getString(R.string.ConversationListFragment_undo),
                                getResources().getColor(R.color.amber_500),
                                Snackbar.LENGTH_LONG, true)
    {

      @Override
      protected void onPostExecute(Void result) {
        super.onPostExecute(result);

        if (actionMode != null) {
          actionMode.finish();
          actionMode = null;
        }
      }

      @Override
      protected void executeAction(@Nullable Void parameter) {
        for (long threadId : selectedConversations) {
          archiveThread(threadId);
        }
      }

      @Override
      protected void reverseAction(@Nullable Void parameter) {
        for (long threadId : selectedConversations) {
          reverseArchiveThread(threadId);
        }
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  @SuppressLint("StaticFieldLeak")
  private void handleDeleteAllSelected() {
    int                 conversationsCount = defaultAdapter.getBatchSelections().size();
    AlertDialog.Builder alert              = new AlertDialog.Builder(getActivity());
    alert.setIconAttribute(R.attr.dialog_alert_icon);
    alert.setTitle(getActivity().getResources().getQuantityString(R.plurals.ConversationListFragment_delete_selected_conversations,
                                                                  conversationsCount, conversationsCount));
    alert.setMessage(getActivity().getResources().getQuantityString(R.plurals.ConversationListFragment_this_will_permanently_delete_all_n_selected_conversations,
                                                                    conversationsCount, conversationsCount));
    alert.setCancelable(true);

    alert.setPositiveButton(R.string.delete, (dialog, which) -> {
      final Set<Long> selectedConversations = defaultAdapter.getBatchSelections();

      if (!selectedConversations.isEmpty()) {
        new AsyncTask<Void, Void, Void>() {
          private ProgressDialog dialog;

          @Override
          protected void onPreExecute() {
            dialog = ProgressDialog.show(getActivity(),
                                         getActivity().getString(R.string.ConversationListFragment_deleting),
                                         getActivity().getString(R.string.ConversationListFragment_deleting_selected_conversations),
                                         true, false);
          }

          @Override
          protected Void doInBackground(Void... params) {
            DatabaseFactory.getThreadDatabase(getActivity()).deleteConversations(selectedConversations);
            MessageNotifier.updateNotification(getActivity());
            return null;
          }

          @Override
          protected void onPostExecute(Void result) {
            dialog.dismiss();
            if (actionMode != null) {
              actionMode.finish();
              actionMode = null;
            }
          }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
      }
    });

    alert.setNegativeButton(android.R.string.cancel, null);
    alert.show();
  }

  private void handleSelectAllThreads() {
    defaultAdapter.selectAllThreads();
    actionMode.setTitle(String.valueOf(defaultAdapter.getBatchSelections().size()));
  }

  private void handleCreateConversation(long threadId, Recipient recipient, int distributionType, long lastSeen) {
    getNavigator().goToConversation(recipient.getId(), threadId, distributionType, lastSeen, -1);
  }

  @Override
  public @NonNull Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
    return new ConversationListLoader(getActivity(), null, false);
  }

  @Override
  public void onLoadFinished(@NonNull Loader<Cursor> arg0, Cursor cursor) {
    if (cursor == null || cursor.getCount() <= 0) {
      list.setVisibility(View.INVISIBLE);
      emptyState.setVisibility(View.VISIBLE);
      emptyImage.setImageResource(EMPTY_IMAGES[(int) (Math.random() * EMPTY_IMAGES.length)]);
      fab.startPulse(3 * 1000);
      cameraFab.startPulse(3 * 1000);
      viconFab.startPulse(3 * 1000);
    } else {
      list.setVisibility(View.VISIBLE);
      emptyState.setVisibility(View.GONE);
      fab.stopPulse();
      cameraFab.stopPulse();
      viconFab.stopPulse();
    }

    defaultAdapter.changeCursor(cursor);
  }

  @Override
  public void onLoaderReset(@NonNull Loader<Cursor> arg0) {
    defaultAdapter.changeCursor(null);
  }

  @Override
  public void onItemClick(ConversationListItem item) {
    if (actionMode == null) {
      handleCreateConversation(item.getThreadId(), item.getRecipient(),
                               item.getDistributionType(), item.getLastSeen());
    } else {
      ConversationListAdapter adapter = (ConversationListAdapter)list.getAdapter();
      adapter.toggleThreadInBatchSet(item.getThreadId());

      if (adapter.getBatchSelections().size() == 0) {
        actionMode.finish();
      } else {
        actionMode.setTitle(String.valueOf(defaultAdapter.getBatchSelections().size()));
      }

      adapter.notifyDataSetChanged();
    }
  }

  @Override
  public void onItemLongClick(ConversationListItem item) {
    actionMode = ((AppCompatActivity)getActivity()).startSupportActionMode(ConversationListFragment.this);

    defaultAdapter.initializeBatchMode(true);
    defaultAdapter.toggleThreadInBatchSet(item.getThreadId());
    defaultAdapter.notifyDataSetChanged();
  }

  @Override
  public void onSwitchToArchive() {
    getNavigator().goToArchiveList();
  }

  @Override
  public boolean onCreateActionMode(ActionMode mode, Menu menu) {
    MenuInflater inflater = getActivity().getMenuInflater();

    inflater.inflate(getActionModeMenuRes(), menu);
    inflater.inflate(R.menu.conversation_list_batch, menu);

    mode.setTitle("1");

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      getActivity().getWindow().setStatusBarColor(getResources().getColor(R.color.action_mode_status_bar));
    }

    if (Build.VERSION.SDK_INT >= 23) {
      int current = getActivity().getWindow().getDecorView().getSystemUiVisibility();
      getActivity().getWindow().getDecorView().setSystemUiVisibility(current & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    return true;
  }

  @Override
  public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
    return false;
  }

  @Override
  public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
    switch (item.getItemId()) {
    case R.id.menu_select_all:       handleSelectAllThreads();   return true;
    case R.id.menu_delete_selected:  handleDeleteAllSelected();  return true;
    case R.id.menu_archive_selected: handleArchiveAllSelected(); return true;
    }

    return false;
  }

  @Override
  public void onDestroyActionMode(ActionMode mode) {
    defaultAdapter.initializeBatchMode(false);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      TypedArray color = getActivity().getTheme().obtainStyledAttributes(new int[] {android.R.attr.statusBarColor});
      getActivity().getWindow().setStatusBarColor(color.getColor(0, Color.BLACK));
      color.recycle();
    }

    if (Build.VERSION.SDK_INT >= 23) {
      TypedArray lightStatusBarAttr = getActivity().getTheme().obtainStyledAttributes(new int[] {android.R.attr.windowLightStatusBar});
      int        current            = getActivity().getWindow().getDecorView().getSystemUiVisibility();
      int        statusBarMode      = lightStatusBarAttr.getBoolean(0, false) ? current | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                                                                              : current & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;

      getActivity().getWindow().getDecorView().setSystemUiVisibility(statusBarMode);

      lightStatusBarAttr.recycle();
    }

    actionMode = null;
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onEvent(ReminderUpdateEvent event) {
    updateReminders();
  }

  protected @IdRes int getToolbarRes() {
    return R.id.toolbar;
  }

  protected @PluralsRes int getArchivedSnackbarTitleRes() {
    return R.plurals.ConversationListFragment_conversations_archived;
  }

  protected @MenuRes int getActionModeMenuRes() {
    return R.menu.conversation_list_batch_archive;
  }

  protected @DrawableRes int getArchiveIconRes() {
    return R.drawable.ic_archive_white_36dp;
  }

  @WorkerThread
  protected void archiveThread(long threadId) {
    DatabaseFactory.getThreadDatabase(getActivity()).archiveConversation(threadId);
  }

  @WorkerThread
  protected void reverseArchiveThread(long threadId) {
    DatabaseFactory.getThreadDatabase(getActivity()).unarchiveConversation(threadId);
  }

  @SuppressLint("StaticFieldLeak")
  protected void onItemSwiped(long threadId, int unreadCount) {
    new SnackbarAsyncTask<Long>(getView(),
        getResources().getQuantityString(R.plurals.ConversationListFragment_conversations_archived, 1, 1),
        getString(R.string.ConversationListFragment_undo),
        getResources().getColor(R.color.amber_500),
        Snackbar.LENGTH_LONG, false)
    {
      @Override
      protected void executeAction(@Nullable Long parameter) {
        DatabaseFactory.getThreadDatabase(getActivity()).archiveConversation(threadId);

        if (unreadCount > 0) {
          List<MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(getActivity()).setRead(threadId, false);
          MessageNotifier.updateNotification(getActivity());
          MarkReadReceiver.process(getActivity(), messageIds);
        }
      }

      @Override
      protected void reverseAction(@Nullable Long parameter) {
        DatabaseFactory.getThreadDatabase(getActivity()).unarchiveConversation(threadId);

        if (unreadCount > 0) {
          DatabaseFactory.getThreadDatabase(getActivity()).incrementUnread(threadId, unreadCount);
          MessageNotifier.updateNotification(getActivity());
        }
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, threadId);
  }

  private class ArchiveListenerCallback extends ItemTouchHelper.SimpleCallback {

    ArchiveListenerCallback() {
      super(0, ItemTouchHelper.RIGHT);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target)
    {
      return false;
    }

    @Override
    public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
      if (viewHolder.itemView instanceof ConversationListItemAction) {
        return 0;
      }

      if (actionMode != null) {
        return 0;
      }

      return super.getSwipeDirs(recyclerView, viewHolder);
    }

    @SuppressLint("StaticFieldLeak")
    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
      if (viewHolder.itemView instanceof ConversationListItemInboxZero) return;
      final long threadId    = ((ConversationListItem)viewHolder.itemView).getThreadId();
      final int  unreadCount = ((ConversationListItem)viewHolder.itemView).getUnreadCount();

      onItemSwiped(threadId, unreadCount);
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            float dX, float dY, int actionState,
                            boolean isCurrentlyActive)
    {
      if (viewHolder.itemView instanceof ConversationListItemInboxZero) return;
      if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
        View  itemView = viewHolder.itemView;
        Paint p        = new Paint();
        float alpha    = 1.0f - Math.abs(dX) / (float) viewHolder.itemView.getWidth();

        if (dX > 0) {
          Bitmap icon = BitmapFactory.decodeResource(getResources(), getArchiveIconRes());

          if (alpha > 0) p.setColor(getResources().getColor(R.color.green_500));
          else           p.setColor(Color.WHITE);

          c.drawRect((float) itemView.getLeft(), (float) itemView.getTop(), dX,
                     (float) itemView.getBottom(), p);

          c.drawBitmap(icon,
                       (float) itemView.getLeft() + getResources().getDimension(R.dimen.conversation_list_fragment_archive_padding),
                       (float) itemView.getTop() + ((float) itemView.getBottom() - (float) itemView.getTop() - icon.getHeight())/2,
                       p);
        }

        viewHolder.itemView.setAlpha(alpha);
        viewHolder.itemView.setTranslationX(dX);
      } else {
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
      }
    }
  }

  private class ScrollListener extends RecyclerView.OnScrollListener {
    @Override
    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
      if (recyclerView.canScrollVertically(-1)) {
        if (toolbarShadow.getVisibility() != View.VISIBLE) {
          ViewUtil.fadeIn(toolbarShadow, 250);
        }
      } else {
        if (toolbarShadow.getVisibility() != View.GONE) {
          ViewUtil.fadeOut(toolbarShadow, 250);
        }
      }
    }
  }
}


