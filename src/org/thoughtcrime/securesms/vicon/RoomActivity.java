package org.thoughtcrime.securesms.vicon;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.conversation.ConversationFragment;
import org.thoughtcrime.securesms.conversation.ConversationReactionOverlayListRoom;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@SuppressLint("StaticFieldLeak")
public class RoomActivity extends PassphraseRequiredActionBarActivity{
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
    private static final String TAG = RoomActivity.class.getSimpleName();
    private Drawable dr;
    private  AlertDialog.Builder alertDialogBuilder;
    final Context context = this;
    private ProgressDialog progressDoalog;
    private RoomModel paramModel = new RoomModel();
    private TableLayout table;
    private Map<String, String> map = new HashMap<>();
    private String userId;
    private int padding_5dp;
    private PushServiceSocket pushServiceSocket;
    private LiveRecipient recipient;
    private RecipientId   recipientId;
    ListView myList;
    private RelativeLayout relativelayout;
    private RelativeLayout relativelayoutHeader;
    private TextView text_list_header;
    private ConversationReactionOverlayListRoom reactionOverlay;
    private Toolbar toolbar;
    private ConversationFragment.ConversationFragmentListener listener;
    private @NonNull ConversationReactionOverlayListRoom.OnHideListener onHideListener;
    private @NonNull Toolbar.OnMenuItemClickListener toolbarListener;
    private View viewList;
    private AdapterView<?> argView;
    private RoomListAdapter adapter;
    private RoomListAdapterModel items;
    private int position;
    private MaterialColor color;
    public void setPosition(int position){
        this.position = position;
    }


    public RoomActivity(){  }

    public void handleReaction(@NonNull View maskTarget1,
                               @NonNull MessageRecord messageRecord1,
                               @NonNull Toolbar.OnMenuItemClickListener toolbarListener1,
                               @NonNull ConversationReactionOverlayListRoom.OnHideListener onHideListener1)
    {
        reactionOverlay.setOnToolbarItemClickedListener(toolbarListener1);
        reactionOverlay.setOnHideListener(onHideListener1);
        reactionOverlay.show(this, maskTarget1, messageRecord1);
    }
    public void setView(@Nullable View viewList) {
        this.viewList = viewList;
    }

    public void setArgView(@Nullable AdapterView<?> argView){
        this.argView = argView;
    }


@Override
protected void onCreate(Bundle state, boolean ready) {
    setContentView(R.layout.list_room_activity);

   // messageRequestOverlay = findViewById(R.id.fragment_overlay_container_list);

   // Toolbar toolbar = findViewById(R.id.fragment_overlay_container_list);
    relativelayoutHeader    = ViewUtil.findById(this, R.id.layout_listroom_header);
    relativelayout          = ViewUtil.findById(this, R.id.conversation_reaction_toolbar_list);
    text_list_header         = ViewUtil.findById(this, R.id.text_listroom_header);
    reactionOverlay        = ViewUtil.findById(this, R.id.conversation_reaction_scrubber_listroom);
    toolbar             =  ViewUtil.findById(this,R.id.conversation_reaction_toolbar_listroom);

    relativelayoutHeader.setBackground(new ColorDrawable(color.toActionBarColor(this)));

   // recipientId   = getIntent().getParcelableExtra(RECIPIENT_EXTRA);
   // recipient = Recipient.live(recipientId);

   // Recipient recipients = recipient.get(); ini akan mengambil info heder dari coversation

    Recipient recipients = Recipient.self(); // ini akan mengambil info no kontac local number

    final Context contexts = getApplicationContext();
    userId = recipients.requireE164();
    progressDoalog = new ProgressDialog(RoomActivity.this);
    progressDoalog.setMessage("Loading....");
    progressDoalog.show();
    ServiceApiRoomPalapa service = ApiRoomPalapa.getRetrofitInstance(getIntent().getStringExtra(END_POINT).toString()).create(ServiceApiRoomPalapa.class);
    map.put("x-authToken", getIntent().getStringExtra(AUTH_TOKEN).toString());
    map.put("x-userTime", getIntent().getStringExtra(USER_TIME).toString());

    myList = (ListView) findViewById(R.id.simpleListView);
    ArrayList<RoomListAdapterModel> arrayOfUsers = new ArrayList<RoomListAdapterModel>();
    adapter = new RoomListAdapter(this, arrayOfUsers,service);
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
                    progressDoalog.dismiss();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(Call<RoomModel> call, Throwable t) {
                t.printStackTrace();
                progressDoalog.dismiss();
                Toast.makeText(RoomActivity.this, R.string.ConversationActivity_connection_server_scp_room_list_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }catch (Exception e){
        e.printStackTrace();
        Toast.makeText(RoomActivity.this, R.string.ConversationActivity_connection_server_scp_failed, Toast.LENGTH_SHORT).show();
    }
    myList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id){
            RoomListAdapterModel itemsClick = adapter.getItem(position);
            Uri uri = Uri.parse(itemsClick.getUriJoinModerator());
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        }
    });

    toolbarListener = new Toolbar.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            return false;
        }
    };

    onHideListener = new ConversationReactionOverlayListRoom.OnHideListener() {
        @Override
        public void onHide() {

        }
    };

    myList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
        @Override
        public boolean onItemLongClick(AdapterView<?> arg0, View arg1,int pos, long id) {
            items = adapter.getItem(pos);
            setPosition(pos);
            relativelayoutHeader.setVisibility(View.GONE);
            reactionOverlay.setVisibility(View.VISIBLE);

           reactionOverlay.setOnToolbarItemClickedListener(toolbarListener);
           reactionOverlay.setOnHideListener(onHideListener);
           reactionOverlay.setView(arg1);
           reactionOverlay.setArgView(arg0);
           reactionOverlay.setRelativelayoutHeader(relativelayoutHeader);
           setView(arg1);
           setArgView(arg0);
           arg0.setEnabled(false);
           arg1.setBackgroundColor(Color.parseColor("#fee7e7"));

           return true;
        }
    });



}




    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return reactionOverlay.applyTouchEvent(ev) || super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
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

                    ClipboardManager clipboardCopy = (ClipboardManager) getApplicationContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clipCopy = ClipData.newPlainText(items.uriCopy, dataUriUser);
                    clipboardCopy.setPrimaryClip(clipCopy);
                    Toast.makeText(getApplicationContext(), R.string.ConversationActivity_copy_url_join, Toast.LENGTH_SHORT).show();
                    clearLineToolbarList();
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
                    clearLineToolbarList();
                }else if((item.toString()).equals(getResources().getString(R.string.conversation_context__menu_message_action_delete))){
                    clearLineToolbarList();
                    adapter.handelDeleteRoomShare(position);
                }else{
                    clearLineToolbarList();
                }

                return false;
            }
        });

        super.onPrepareOptionsMenu(menu);
        return true;
    }
    public void clearLineToolbarList(){
        relativelayoutHeader.setVisibility(View.VISIBLE);
        reactionOverlay.setVisibility(View.GONE);
        viewList.setBackgroundColor(Color.WHITE);
        argView.setEnabled(true);
    }

   }
