package org.thoughtcrime.securesms.vicon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;

import org.json.JSONException;
import org.json.JSONObject;

public class RoomModel<T> {
    @SerializedName("result")
    private Boolean result;
    @SerializedName("errorMessage")
    private String errorMessage;
    @SerializedName("roomList")
    private T roomList;
    @SerializedName("roomName")
    private String roomName;
    @SerializedName("username")
    private String username;
    @SerializedName("roomId")
    private String roomId;
    @SerializedName("roomAccsessCodeMod")
    private String roomAccsessCodeMod;
    @SerializedName("roomAccessCodeUser")
    private String roomAccessCodeUser;
    @SerializedName("roomURI")
    private String roomURI;
    @SerializedName("eror")
    private String eror = "";

    public String getEror() {
        return eror;
    }

    public void setEror(String eror) {
        this.eror = eror;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getRoomAccsessCodeMod() {
        return roomAccsessCodeMod;
    }

    public void setRoomAccsessCodeMod(String roomAccsessCodeMod) {
        this.roomAccsessCodeMod = roomAccsessCodeMod;
    }

    public String getRoomAccessCodeUser() {
        return roomAccessCodeUser;
    }

    public void setRoomAccessCodeUser(String roomAccessCodeUser) {
        this.roomAccessCodeUser = roomAccessCodeUser;
    }

    public String getRoomURI() {
        return roomURI;
    }

    public void setRoomURI(String roomURI) {
        this.roomURI = roomURI;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public T getRoomList() {
        return roomList;
    }

    public void setRoomList(T roomList) {
        this.roomList = roomList;
    }

    public Boolean getResult() {
        return result;
    }

    public void setResult(Boolean result) {
        this.result = result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }


    public RoomModel(){}

    public JsonObject ApiJsonMapCreate(String paramRoomName,String paramUserName) {
        JsonObject gsonObject = new JsonObject();
        try {
            JSONObject jsonObj_ = new JSONObject();
            jsonObj_.put("roomName", paramRoomName);
            jsonObj_.put("username", paramUserName);
            JsonParser jsonParser = new JsonParser();
            gsonObject = (JsonObject) jsonParser.parse(jsonObj_.toString());

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return gsonObject;
    }
    public JsonObject ApiJsonMapDelete(String paramRoomId,String paramUserName) {
        JsonObject gsonObject = new JsonObject();
        try {
            JSONObject jsonObj_ = new JSONObject();
            jsonObj_.put("roomId", paramRoomId);
            jsonObj_.put("username", paramUserName);
            JsonParser jsonParser = new JsonParser();
            gsonObject = (JsonObject) jsonParser.parse(jsonObj_.toString());

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return gsonObject;
    }
}
