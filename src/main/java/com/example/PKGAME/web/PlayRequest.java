package com.example.PKGAME.web;

public class PlayRequest {
    private String action; // "SHOOT" or "KEEP"
    private String key;    // single character

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
