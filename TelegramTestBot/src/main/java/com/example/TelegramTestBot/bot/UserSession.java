package com.example.TelegramTestBot.bot;

import com.example.TelegramTestBot.controller.TestSession;
import com.example.TelegramTestBot.model.User;

import java.util.HashMap;
import java.util.Map;

public class UserSession {
    private final User user;
    private String currentState;
    private TestSession currentTestSession;
    private final Map<String, Object> sessionData;

    public UserSession(User user) {
        this.user = user;
        this.sessionData = new HashMap<>();
    }

    public User getUser() {
        return user;
    }
    public void put(String key, Object value) {
        sessionData.put(key, value);
    }
    public Object get(String key) {
        return sessionData.get(key);
    }
    public void remove(String key) {
        sessionData.remove(key);
    }
}
