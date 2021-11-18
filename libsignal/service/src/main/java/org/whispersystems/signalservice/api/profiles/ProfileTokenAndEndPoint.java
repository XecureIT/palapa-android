package org.whispersystems.signalservice.api.profiles;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProfileTokenAndEndPoint {
    @JsonProperty
    private boolean result;
    @JsonProperty
    private String errorMessage;
    @JsonProperty
    private String endpointUrl;

    public boolean isResult() {
        return result;
    }

    public void setResult(boolean result) {
        this.result = result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }
}
