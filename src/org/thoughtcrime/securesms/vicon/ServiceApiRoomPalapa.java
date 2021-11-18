package org.thoughtcrime.securesms.vicon;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.HeaderMap;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface ServiceApiRoomPalapa {
    @GET("room/{userId}")
    Call<RoomModel> getRoomListByUserId(@Path("userId") String userId,@HeaderMap Map<String, String> headers);

    @HTTP(method = "POST", path = "room", hasBody = true)
    Call<RoomModel> createRoom(@Body JsonObject param, @HeaderMap Map<String, String> headers);

    @HTTP(method = "DELETE", path = "room", hasBody = true)
    Call<RoomModel> deleteRoom(@Body JsonObject param,@HeaderMap Map<String, String> headers);
}
