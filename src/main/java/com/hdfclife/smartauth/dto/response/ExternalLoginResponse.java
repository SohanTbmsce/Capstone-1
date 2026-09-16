package com.hdfclife.smartauth.dto.response;

import java.time.Instant;

public class ExternalLoginResponse {

    private boolean success;
    private String username;
    private String message;
    private Instant timestamp;

    public ExternalLoginResponse() {
    }

    public ExternalLoginResponse(boolean success, String username, String message) {
        this.success = success;
        this.username = username;
        this.message = message;
        this.timestamp = Instant.now();
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
