package org.thoughtcrime.securesms.vicon;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.material.appbar.AppBarLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.MainNavigator;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.InputAwareLayout;
import org.thoughtcrime.securesms.components.emoji.EmojiTextView;
import org.thoughtcrime.securesms.components.registration.PulsingFloatingActionButton;
import org.thoughtcrime.securesms.conversation.ConversationActivity;
import org.thoughtcrime.securesms.conversation.ConversationFragment;
import org.thoughtcrime.securesms.conversation.ConversationReactionOverlayListRoom;
import org.thoughtcrime.securesms.conversation.ConversationTitleView;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ConversationListRoomFragment extends Fragment{

    public static final String ROOM_LIST                 = "room_list";
    public static final String RECIPIENT_EXTRA           = "recipient_id";
    public static final String END_POINT                 = "end_point";
    public static final String RESULT_END_POINT          = "resultEndPoint";
    public static final String ERROR_MESSAGE_END_POINT   = "errorMessageEndPoint";
    public static final String AUTH_TOKEN                = "authToken";
    public static final String USER_TIME                 = "userTime";
    public static final String RESULT_TOKEN              = "resultToken";
    public static final String ERROR_MESSAGE_TOKEN       ="errorMessageToken";
    public static final String MODULE_ID                 = "id.tnisiber.palapa.intgepi";
    private Drawable dr;
    private  AlertDialog.Builder alertDialogBuilder;
    private ProgressDialog progressDoalog;
    private RoomModel paramModel = new RoomModel();
    private TableLayout table;
    private Map<String, String> map = new HashMap<>();
    private String userId;
    private int padding_5dp;
    private PushServiceSocket pushServiceSocket;
    private LiveRecipient recipient;
    private RecipientId recipientId;
    ListView myList;
    public RelativeLayout relativelayout;
    public RelativeLayout relativelayoutHeader;
    private TextView text_list_header;
    public ConversationReactionOverlayListRoom reactionOverlay;
    private Toolbar toolbar;
    public Toolbar toolbarlist;
    private ConversationFragment.ConversationFragmentListener listener;
    public @NonNull ConversationReactionOverlayListRoom.OnHideListener onHideListener;
    public @NonNull Toolbar.OnMenuItemClickListener toolbarListener;
    private View viewList;
    private AdapterView<?> argView;
    private RoomListAdapter adapter;
    private RoomListAdapterModel items;
    private int position;
    private LinearLayout layoutLinierListRoom;
    private AppBarLayout toolbarlisthider;
    private ConversationTitleView titleView;
    private InputAwareLayout container;
    private Recipient recipientSnapshot;
    private GlideRequests glideRequests;
    private Boolean conversationActivity = false;
    private int statusBarColor;
    private View viewListComponent;
    private ImageButton imageButtonViewListComponent;

    public void setPosition(int position)                   {   this.position = position;                     }
    public void setView(@Nullable View viewList)            {   this.viewList = viewList;                     }
    public void setArgView(@Nullable AdapterView<?> argView){   this.argView = argView;                       }
    public static ConversationListRoomFragment newInstance(){   return new ConversationListRoomFragment();    }
    public void setStatusBarColor(int statusBarColor) {        this.statusBarColor = statusBarColor;    }

    public void setToolbarlisthider(AppBarLayout toolbarlisthider)    {        this.toolbarlisthider = toolbarlisthider;            }
    public void setTitleView(ConversationTitleView titleView)         {        this.titleView = titleView;                          }
    public void setContainer(InputAwareLayout container)              {        this.container = container;                          }
    public void setConversationActivity(Boolean conversationActivity) {        this.conversationActivity = conversationActivity;    }
    public void setRecipientSnapshot(Recipient recipientSnapshot)     {        this.recipientSnapshot = recipientSnapshot;          }
    public void setGlideRequests(GlideRequests glideRequests)         {        this.glideRequests = glideRequests;                  }

    public void setLayoutLinierListRoom(LinearLayout layoutLinierListRoom) {        this.layoutLinierListRoom = layoutLinierListRoom;    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.list_room_activity, parent, false);
    }


    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        relativelayoutHeader    = view.findViewById(R.id.layout_listroom_header);
        relativelayout          = view.findViewById(R.id.conversation_reaction_toolbar_list);
        text_list_header        = view.findViewById(R.id.text_listroom_header);
        reactionOverlay         = view.findViewById(R.id.conversation_reaction_scrubber_listroom);
        toolbar                 = view.findViewById(R.id.conversation_reaction_toolbar_listroom);
        layoutLinierListRoom    = view.findViewById(R.id.layout_linier_list_room);

       //getActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS); full screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            getActivity().getWindow().setStatusBarColor(getResources().getColor(R.color.textsecure_primary));


        toolbarlist = view.findViewById(R.id.toolbar);
        ((AppCompatActivity) getActivity()).setSupportActionBar(toolbarlist);

        ActionBar supportActionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (supportActionBar == null) throw new AssertionError();

        supportActionBar.setDisplayHomeAsUpEnabled(true);
        supportActionBar.setDisplayShowTitleEnabled(false);


        toolbarlist.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().getWindow().setStatusBarColor(statusBarColor);
                if(conversationActivity == true){
                    container.setVisibility(View.VISIBLE);
                    titleView.setTitle(glideRequests, recipientSnapshot);
                    toolbarlisthider.setVisibility(View.VISIBLE);
                    layoutLinierListRoom.setVisibility(View.GONE);
                }else{
                    getFragmentManager().popBackStack();
                }
            }
        });

        EmojiTextView emojiTextView = (EmojiTextView)  view.findViewById(R.id.title);
        emojiTextView.setVisibility(View.VISIBLE);
        emojiTextView.setText(R.string.MessageRequestViconInformationRoom_daftar_ruangan);

        LinearLayout linierConversationActivity = (LinearLayout)  view.findViewById(R.id.subtitle_container);
        linierConversationActivity.setVisibility(View.GONE);

        AvatarImageView avatarImageView = (AvatarImageView) view.findViewById(R.id.contact_photo_image);
       // avatarImageView.setBackgroundDrawable(getResources().getDrawable(R.drawable.ic_list_room));
        avatarImageView.setBackgroundDrawable(getResources().getDrawable(R.drawable.ic_list_room_bar));

       Recipient recipients = Recipient.self();

        userId = recipients.requireE164();
        progressDoalog = new ProgressDialog(getContext());
        progressDoalog.setMessage("Loading....");
        progressDoalog.show();

        ServiceApiRoomPalapa service = ApiRoomPalapa.getRetrofitInstance(getArguments().getString(END_POINT).toString()).create(ServiceApiRoomPalapa.class);
        map.put("x-authToken", getArguments().getString(AUTH_TOKEN).toString());
        map.put("x-userTime", getArguments().getString(USER_TIME).toString());

        myList = (ListView) view.findViewById(R.id.simpleListView);
        ArrayList<RoomListAdapterModel> arrayOfRoom = new ArrayList<RoomListAdapterModel>();

        adapter = new RoomListAdapter(getContext(), arrayOfRoom,service);
        try {
            Call<RoomModel> call = service.getRoomListByUserId(recipients.requireE164(), map);
            call.enqueue(new Callback<RoomModel>() {
                @Override
                public void onResponse(Call<RoomModel> call, Response<RoomModel> response) {
                    RoomModel paramModel = (RoomModel) response.body();
                    try {
                        String json = null;
                        ObjectMapper mapper = new ObjectMapper();
                        try {
                            json = mapper.writeValueAsString(paramModel);
                        } catch (JsonProcessingException ee) {
                            ee.printStackTrace();
                        }
                        JSONObject reader = new JSONObject(json);
                        JSONArray entriesArr = reader.getJSONArray("roomList");
                        for (int i = 0; i < entriesArr.length(); i++) {
                            JSONObject jObj = entriesArr.getJSONObject(i);
                            String strDataUri = "" + jObj.getString("roomURI").replaceAll(" ", "");

                            String strDataUriCopy = "" + jObj.getString("roomURI").replaceAll(" ", "");
                            strDataUriCopy = (strDataUriCopy + "/?roomAccessCode=" + String.valueOf(jObj.getString("roomAccessCodeUser")));

                            String strDataUriJoint = "" + jObj.getString("roomURI").replaceAll(" ", "");
                            String profileName = recipients.getProfileName();
                            if (profileName == null || profileName.equals(""))
                                strDataUriJoint = (strDataUriJoint + "/?roomAccessCode=" + String.valueOf(jObj.getString("roomAccessCodeMod")));
                            else
                                strDataUriJoint = (strDataUriJoint + "/?roomAccessCode=" + String.valueOf(jObj.getString("roomAccessCodeMod")) + "&userName=" + profileName);

                            String tokenData[] = strDataUri.split("/r/");
                            String paramRoomId = tokenData[1];
                            RoomListAdapterModel itemAdapter = new RoomListAdapterModel(jObj.getString("roomName"),
                                    recipients.requireE164(),
                                    paramRoomId,
                                    strDataUri,
                                    strDataUriCopy,
                                    strDataUriJoint,
                                    String.valueOf(jObj.getString("roomAccessCodeMod")),
                                    String.valueOf(jObj.getString("roomAccessCodeUser")),
                                    map);
                            adapter.add(itemAdapter);
                        }
                        myList.setAdapter(adapter);

                     myList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> arg0, View arg1,int pos, long id){
                            items = adapter.getItem(pos);
                            setPosition(pos);
                            relativelayoutHeader.setVisibility(View.GONE);

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                               getActivity().getWindow().setStatusBarColor(getResources().getColor(R.color.action_mode_status_bar));

                            reactionOverlay.setVisibility(View.VISIBLE);
                            reactionOverlay.setWindow(getActivity().getWindow());
                            reactionOverlay.setOnToolbarItemClickedListener(toolbarListener);
                            reactionOverlay.setOnHideListener(onHideListener);
                            reactionOverlay.setView(arg1);
                            reactionOverlay.setArgView(arg0);
                            reactionOverlay.setRelativelayoutHeader(relativelayoutHeader);
                            reactionOverlay.setToolbarlist(toolbarlist);
                            toolbarlist.setVisibility(View.GONE);

                            setView(arg1);
                            setArgView(arg0);
                            arg0.setEnabled(false);

                            String themeApp = TextSecurePreferences.getTheme(getActivity());
                            arg1.setBackgroundColor(Color.parseColor("#fee7e7"));
                            if((themeApp).equals(DynamicTheme.DARK))
                                arg1.setBackgroundColor(Color.parseColor("#914747"));
                            }
                        });


                        myList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                            @Override
                            public boolean onItemLongClick(AdapterView<?> arg0, View arg1,int pos, long id) {
                                items = adapter.getItem(pos);
                                setPosition(pos);
                                relativelayoutHeader.setVisibility(View.GONE);

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                                    getActivity().getWindow().setStatusBarColor(getResources().getColor(R.color.action_mode_status_bar));

                                reactionOverlay.setVisibility(View.VISIBLE);
                                reactionOverlay.setWindow(getActivity().getWindow());
                                reactionOverlay.setOnToolbarItemClickedListener(toolbarListener);
                                reactionOverlay.setOnHideListener(onHideListener);
                                reactionOverlay.setView(arg1);
                                reactionOverlay.setArgView(arg0);
                                reactionOverlay.setRelativelayoutHeader(relativelayoutHeader);
                                reactionOverlay.setToolbarlist(toolbarlist);

                                toolbarlist.setVisibility(View.GONE);

                                setView(arg1);
                                setArgView(arg0);
                                arg0.setEnabled(false);

                                String themeApp = TextSecurePreferences.getTheme(getActivity());
                                arg1.setBackgroundColor(Color.parseColor("#fee7e7"));
                                if((themeApp).equals(DynamicTheme.DARK))
                                    arg1.setBackgroundColor(Color.parseColor("#914747"));

                                return true;
                            }
                        });

                        progressDoalog.dismiss();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(Call<RoomModel> call, Throwable t) {
                    t.printStackTrace();
                    progressDoalog.dismiss();
                    Toast.makeText(getContext(), R.string.ConversationActivity_connection_server_scp_room_list_failed, Toast.LENGTH_SHORT).show();
                }
            });
        }catch (Exception e){
            e.printStackTrace();
            Toast.makeText(getContext(), R.string.ConversationActivity_connection_server_scp_failed, Toast.LENGTH_SHORT).show();
        }


        toolbarListener = new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if((item.toString()).equals(getResources().getString(R.string.conversation_context__menu_message_action_copy))){
                    String dataUriUser = getResources().getString(R.string.ConversationListRoomFragment_desc_join_room)+"\n"+
                            "\n"+
                            "Join room : "+items.name+" \n" +
                            ""+items.uriCopy+"  \n" +
                            "Room ID "+items.roomId+" \n"+
                            "\n"+
                            getResources().getString(R.string.ConversationListRoomFragment_desc_join_room_info)+"\n"+
                            "\n"+
                            getResources().getString(R.string.ConversationListRoomFragment_desc_join_room_catatan);

                    ClipboardManager clipboardCopy = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clipCopy = ClipData.newPlainText(items.uriCopy, dataUriUser);
                    clipboardCopy.setPrimaryClip(clipCopy);
                    Toast.makeText(getContext(), R.string.ConversationActivity_copy_url_join, Toast.LENGTH_SHORT).show();
                }else if((item.toString()).equals(getResources().getString(R.string.conversation_context__menu_message_action_share))){
                    String dataUriUser = getResources().getString(R.string.ConversationListRoomFragment_desc_join_room)+"\n"+
                            "\n"+
                            "Join room : "+items.name+" \n" +
                            ""+items.uriCopy+"  \n" +
                            "Room ID "+items.roomId+" \n"+
                            "\n"+
                            getResources().getString(R.string.ConversationListRoomFragment_desc_join_room_info)+"\n"+
                            "\n"+
                            getResources().getString(R.string.ConversationListRoomFragment_desc_join_room_catatan);

                    adapter.handleShareRoom(dataUriUser);
                }else if((item.toString()).equals(getResources().getString(R.string.conversation_context__menu_message_action_delete))){
                    adapter.handelDeleteRoomShare(position);
                }else if((item.toString()).equals(getResources().getString(R.string.conversation_context__menu_message_action_vicon))){
                  RoomListAdapterModel itemsClick = adapter.getItem(position);
                  Uri uri = Uri.parse(itemsClick.getUriJoinModerator());
                  Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                  startActivity(intent);
                }
                return false;
            }
        };

        onHideListener = new ConversationReactionOverlayListRoom.OnHideListener() {
            @Override
            public void onHide() {

            }
        };




    }

}
