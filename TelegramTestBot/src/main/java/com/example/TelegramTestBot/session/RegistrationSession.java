package com.example.TelegramTestBot.session;

import java.util.HashMap;
import java.util.Map;

public class RegistrationSession {
    public enum State {
        FULL_NAME,
        LOGIN,
        PASSWORD,
        ROLE,
        DONE
    }

    private State state = State.FULL_NAME;
    private final Map<String, String> data = new HashMap<>();

    public State getState() {
        return state;
    }

    public void nextState() {
        switch (state) {
            case FULL_NAME -> state = State.LOGIN;
            case LOGIN -> state = State.PASSWORD;
            case PASSWORD -> state = State.ROLE;
            case ROLE -> state = State.DONE;
        }
    }

    public void setValue(String key, String value) {
        data.put(key, value);
    }

    public String getValue(String key) {
        return data.get(key);
    }

    public Map<String, String> getAllData() {
        return data;
    }
}
