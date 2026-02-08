package com.example.PKGAME.web;

import com.example.PKGAME.viewmodel.StateViewModel;

public class PlayResponse {
    private final boolean ok;
    private final String message;
    private final String shotKey;
    private final String keepKey;
    private final String result;
    private final StateViewModel state;

    public PlayResponse(boolean ok, String message, String shotKey, String keepKey, String result, StateViewModel state) {
        this.ok = ok;
        this.message = message;
        this.shotKey = shotKey;
        this.keepKey = keepKey;
        this.result = result;
        this.state = state;
    }

    public boolean isOk() {
        return ok;
    }

    public String getMessage() {
        return message;
    }

    public String getShotKey() {
        return shotKey;
    }

    public String getKeepKey() {
        return keepKey;
    }

    public String getResult() {
        return result;
    }

    public StateViewModel getState() {
        return state;
    }
}
