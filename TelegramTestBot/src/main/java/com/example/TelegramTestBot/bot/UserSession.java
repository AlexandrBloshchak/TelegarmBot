package com.example.TelegramTestBot.bot;

import com.example.TelegramTestBot.controller.TestSession;
import com.example.TelegramTestBot.model.User;

import java.util.HashMap;
import java.util.Map;

public class UserSession {
    private final User user;
    private String currentState;
    private TestSession currentTestSession;      // теперь храним сессию теста прямо в UserSession
    private final Map<String, Object> sessionData;

    public UserSession(User user) {
        this.user = user;
        this.sessionData = new HashMap<>();
    }

    public User getUser() {
        return user;
    }

    public String getState() {
        return currentState;
    }

    public void setState(String state) {
        this.currentState = state;
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

    public void clear() {
        sessionData.clear();
        currentState = null;
        currentTestSession = null;
    }

    // --- Работа с TestSession ---

    public void setTestSession(TestSession session) {
        this.currentTestSession = session;
    }

    public TestSession getTestSession() {
        return currentTestSession;
    }

    public void resetProgress() {
        if (currentTestSession != null) {
            currentTestSession.resetProgress();
        }
    }

    public boolean isTestCompleted() {
        return currentTestSession != null && currentTestSession.isCompleted();
    }

    public int getScore() {
        return currentTestSession != null ? currentTestSession.getScore() : 0;
    }

    public int getTotalQuestions() {
        return currentTestSession != null ? currentTestSession.getTotalQuestions() : 0;
    }
}
