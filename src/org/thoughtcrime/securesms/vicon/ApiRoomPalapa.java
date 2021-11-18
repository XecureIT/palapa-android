package org.thoughtcrime.securesms.vicon;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiRoomPalapa {
    public static Retrofit retrofit;
   // public static String BASE_URL = "http://192.168.0.16:2929/";

    public static Retrofit getRetrofitInstance(String BASE_URL){
        if (retrofit == null){
            retrofit = new retrofit2.Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}
