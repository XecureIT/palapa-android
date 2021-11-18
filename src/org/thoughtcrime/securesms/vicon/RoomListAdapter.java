package org.thoughtcrime.securesms.vicon;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.registration.PulsingFloatingActionButton;
import org.thoughtcrime.securesms.conversation.ConversationReactionOverlayListRoom;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.ArrayList;
import java.util.Map;
import java.util.Random;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RoomListAdapter extends ArrayAdapter<RoomListAdapterModel>{
    private AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getContext());
    private ServiceApiRoomPalapa service;
    private Map<String, String> map;
    private ArrayList<RoomListAdapterModel> usersList;
    private RoomModel paramModel = new RoomModel();
    private ProgressDialog progressDoalog = new ProgressDialog(getContext());
    private String dataCopy;
    private String dataShare;
    private ConversationReactionOverlayListRoom reactionOverlay;
    private RelativeLayout relativelayoutHeader;
    private Toolbar toolbarlist;
    private Toolbar.OnMenuItemClickListener toolbarListener;
    private Window window;
    private ConversationReactionOverlayListRoom.OnHideListener onHideListener;

    public void setReactionOverlay(ConversationReactionOverlayListRoom reactionOverlay) {        this.reactionOverlay = reactionOverlay;    }
    public void setRelativelayoutHeader(RelativeLayout relativelayoutHeader) {        this.relativelayoutHeader = relativelayoutHeader;    }
    public void setToolbarlist(Toolbar toolbarlist) {        this.toolbarlist = toolbarlist;    }
    public void setToolbarListener(Toolbar.OnMenuItemClickListener toolbarListener) {        this.toolbarListener = toolbarListener;    }
    public void setWindow(Window window) {        this.window = window;    }
    public void setOnHideListener(ConversationReactionOverlayListRoom.OnHideListener onHideListener) {        this.onHideListener = onHideListener;    }

    public void setDataCopy(String dataCopy){
        this.dataCopy = dataCopy;
    }
    public void setDataShare(String dataShare){
        this.dataShare = dataShare;
    }

//    public RoomListAdapter(Context context, ArrayList<RoomListAdapterModel> usersList, ServiceApiRoomPalapa service,ConversationReactionOverlayListRoom reactionOverlay,
//                           RelativeLayout relativelayoutHeader,
//                           Toolbar toolbarlist,
//                           Toolbar.OnMenuItemClickListener toolbarListener,
//                           Window window,
//                           ConversationReactionOverlayListRoom.OnHideListener onHideListener) {
//        super(context, 0, usersList);
//        this.service = service;
//        this.usersList = usersList;
//        this.reactionOverlay = reactionOverlay;
//        this.relativelayoutHeader = relativelayoutHeader;
//        this.toolbarlist = toolbarlist;
//        this.toolbarListener = toolbarListener;
//        this.window = window;
//        this.onHideListener = onHideListener;
//    }


    public RoomListAdapter(Context context, ArrayList<RoomListAdapterModel> usersList,ServiceApiRoomPalapa service) {
        super(context, 0, usersList);
        this.service = service;
        this.usersList = usersList;
    }



    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.list_room_view_activity, parent, false);
            TextView      listTextRoom         = (TextView)      convertView.findViewById(R.id.txt_room_name);
            ImageView imageButton = (ImageView) convertView.findViewById(R.id.imageButtonShowMenu);
            String themeApp = TextSecurePreferences.getTheme(getContext());
            if((themeApp).equals(DynamicTheme.DARK)){
                listTextRoom.setTextColor(Color.WHITE);
                imageButton.setImageResource(R.drawable.ic_arrow_right_white);
            }
        }
        handelListRoom(position, convertView, parent);
        return convertView;
    }

    private void handelListRoom(int position, View convertView, ViewGroup parent){
        RoomListAdapterModel user = getItem(position);
        TextView roomName = (TextView) convertView.findViewById(R.id.txt_room_name);

        String dataUriUser = "Join room : "+user.name+" \n" +
                ""+user.uriCopy+"  \n" +
                "Room ID "+user.roomId+"" ;

        roomName.setText(user.name);

        String dataShareInfo = "Join room : "+user.name+" \n" +
                ""+user.uri+"  \n" +
                "Access code Moderator : "+user.accessCodeModerator+"\n"+
                "Access code User : "+user.accessCodeUser;

        TableLayout tableP = new TableLayout(getContext());
        TableRow.LayoutParams paramP = new TableRow.LayoutParams();
        paramP.span = 12;
        TableRow trP = new TableRow(getContext());
        trP.setGravity(Gravity.CENTER);

        PulsingFloatingActionButton buttonCopyUrl = new PulsingFloatingActionButton(getContext());
        PulsingFloatingActionButton buttonCopyAccesCode = new PulsingFloatingActionButton(getContext());
        PulsingFloatingActionButton buttonShare = new PulsingFloatingActionButton(getContext());

        buttonShare.setImageDrawable(getContext().getResources().getDrawable(R.drawable.ic_share_white_24dp));
        buttonCopyUrl.setImageDrawable(getContext().getResources().getDrawable(R.drawable.ic_copy_solid_24));
        buttonCopyAccesCode.setImageDrawable(getContext().getResources().getDrawable(R.drawable.ic_copy_outline_24));

        buttonShare.setPadding(20,0,0,0);
        buttonShare.setTextAlignment(R.string.common_signin_button_text);
        buttonCopyUrl.setPadding(20,0,0,0);
        buttonCopyAccesCode.setPadding(20,0,0,0);

        trP.setBackgroundColor(Color.parseColor("#fee7e7"));
        trP.addView(buttonShare);
        trP.addView(buttonCopyUrl);
        trP.addView(buttonCopyAccesCode);
        tableP.addView(trP,paramP);


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
        Random rnd = new Random();
        int color = Color.argb(255, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256));
        tvT.setBackgroundColor(Color.parseColor("#f65552"));


        AlertDialog.Builder alertDialogBuilderInfo = new AlertDialog.Builder(getContext());
        alertDialogBuilderInfo
                .setCustomTitle(tvT)
                .setMessage("\n"+dataShareInfo+" \nShare:\n ")
                .setView(tableP)
                .setCancelable(false)
                .setPositiveButton(R.string.MessageRequestViconInformationRoom_footer,new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        dialog.cancel();
                    }
                });
        final  AlertDialog alertDialogInfo = alertDialogBuilderInfo.create();

        buttonShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleShareRoom(dataUriUser);
                alertDialogInfo.dismiss();
            }
        });

        buttonCopyUrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText(user.uri, user.uri);
                clipboard.setPrimaryClip(clip);
            }
        });

        buttonCopyAccesCode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager clipboardAcess = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clipAcess = ClipData.newPlainText(user.accessCodeModerator, user.accessCodeModerator);
                clipboardAcess.setPrimaryClip(clipAcess);
            }
        });

        alertDialogBuilder = new AlertDialog.Builder(getContext());
        alertDialogBuilder
                .setTitle(R.string.MessageRequestViconInformationRoom_header)
                .setMessage(getContext().getResources().getString(R.string.MessageRequestViconInformationRoom_delete)+" "+user.name+"?")
                .setCancelable(false)
                .setPositiveButton(R.string.yes,new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        handelDeleteRoom(position, convertView, parent);
                    }
                })
                .setNegativeButton(R.string.no,new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        dialog.cancel();
                    }
                });


        AlertDialog alertDialog = alertDialogBuilder.create();
    }



    public void handleShareRoom(String dataparam){
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        String shareBody = dataparam;
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "Subject Here");
        sharingIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
        getContext().startActivity(Intent.createChooser(sharingIntent, "Share via"));
    }

    public void handelDeleteRoom(int position, View convertView, ViewGroup parent){
        progressDoalog = new ProgressDialog(getContext());
        progressDoalog.setMessage("Loading....");
        progressDoalog.show();
        RoomListAdapterModel user = getItem(position);
        try {
            Call<RoomModel> call = service.deleteRoom(paramModel.ApiJsonMapDelete(user.roomId, user.userId), user.map);
            call.enqueue(new Callback<RoomModel>() {
                @Override
                public void onResponse(Call<RoomModel> call, Response<RoomModel> response) {
                    progressDoalog.dismiss();
                    RoomModel paramModel = (RoomModel) response.body();
                    if (response.isSuccessful()) {
                        if (paramModel.getResult())
                            remove(user);
                    } else {
                    }
                    progressDoalog.dismiss();
                }

                @Override
                public void onFailure(Call<RoomModel> call, Throwable t) {
                    t.printStackTrace();
                    progressDoalog.dismiss();
                    Toast.makeText(getContext(), R.string.ConversationActivity_connection_server_scp_room_delete_failed, Toast.LENGTH_SHORT).show();
                }
            });
        }catch (Exception e){
            e.printStackTrace();
            Toast.makeText(getContext(), R.string.ConversationActivity_connection_server_scp_failed, Toast.LENGTH_SHORT).show();
        }
    }

    public void handelDeleteRoomShare(int position){
        progressDoalog = new ProgressDialog(getContext());
        progressDoalog.setMessage("Loading....");
        progressDoalog.show();
        RoomListAdapterModel user = getItem(position);
        try {
            Call<RoomModel> call = service.deleteRoom(paramModel.ApiJsonMapDelete(user.roomId, user.userId), user.map);
            call.enqueue(new Callback<RoomModel>() {
                @Override
                public void onResponse(Call<RoomModel> call, Response<RoomModel> response) {
                    progressDoalog.dismiss();
                    RoomModel paramModel = (RoomModel) response.body();
                    if (response.isSuccessful()) {
                        if (paramModel.getResult())
                            remove(user);
                    } else {
                    }
                    progressDoalog.dismiss();
                }

                @Override
                public void onFailure(Call<RoomModel> call, Throwable t) {
                    t.printStackTrace();
                    progressDoalog.dismiss();
                    Toast.makeText(getContext(), R.string.ConversationActivity_connection_server_scp_room_delete_failed, Toast.LENGTH_SHORT).show();
                }
            });
        }catch (Exception e){
            e.printStackTrace();
            Toast.makeText(getContext(), R.string.ConversationActivity_connection_server_scp_failed, Toast.LENGTH_SHORT).show();
        }
    }

}