package org.thoughtcrime.securesms.vicon;

import android.widget.ImageView;

import java.util.HashMap;
import java.util.Map;

public class RoomListAdapterModel {
    public String name;
    public String userId;
    public String roomId;
    public String uri;
    public String uriCopy;
    public String uriJoinModerator;
    public String accessCodeModerator;
    public String accessCodeUser;
    public Map<String, String> map = new HashMap<>();

    public RoomListAdapterModel(String name,
                                String userId,
                                String roomId,
                                String uri,
                                String uriCopy,
                                String uriJoinModerator,
                                String accessCodeModerator,
                                String accessCodeUser,
                                Map<String, String> map) {
        this.name = name;
        this.userId = userId;
        this.roomId = roomId;
        this.uri = uri;
        this.uriCopy = uriCopy;
        this.uriJoinModerator = uriJoinModerator;
        this.accessCodeModerator = accessCodeModerator;
        this.accessCodeUser = accessCodeUser;
        this.map = map;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getUriCopy() {        return uriCopy;    }

    public void setUriCopy(String uriCopy) {        this.uriCopy = uriCopy;    }

    public String getUriJoinModerator() {        return uriJoinModerator;    }

    public void setUriJoinModerator(String uriJoinModerator) {        this.uriJoinModerator = uriJoinModerator;    }

    public String getAccessCodeModerator() {
        return accessCodeModerator;
    }

    public void setAccessCodeModerator(String accessCodeModerator) {        this.accessCodeModerator = accessCodeModerator;    }

    public String getAccessCodeUser() {
        return accessCodeUser;
    }

    public void setAccessCodeUser(String accessCodeUser) {
        this.accessCodeUser = accessCodeUser;
    }

    public Map<String, String> getMap() {
        return map;
    }

    public void setMap(Map<String, String> map) {
        this.map = map;
    }
}