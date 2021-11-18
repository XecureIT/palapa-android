package org.whispersystems.signalservice.api.profiles;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AccountTestToken {
    @JsonProperty
    private boolean result;
    @JsonProperty
    private String username;
    @JsonProperty
    private String authToken;
    @JsonProperty
    private String userTime;
    @JsonProperty
    private String accessToken;
    @JsonProperty
    private String errorMessage;

    public AccountTestToken() {}

    public AccountTestToken(boolean result, String errorMessage, String username, String authToken, String userTime) {
        this.result = result;
        this.username = username;
        this.authToken = authToken;
        this.userTime = userTime;
        this.errorMessage = errorMessage;
        this.accessToken = username + "," + userTime + "," + authToken;
    }

    public boolean isResult() {
        return result;
    }

    public void setResult(boolean result) {
        this.result = result;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getUserTime() {
        return userTime;
    }

    public void setUserTime(String userTime) {
        this.userTime = userTime;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
