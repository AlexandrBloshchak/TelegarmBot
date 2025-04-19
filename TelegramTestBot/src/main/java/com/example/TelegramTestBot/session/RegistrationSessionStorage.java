package com.example.TelegramTestBot.session;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RegistrationSessionStorage {
    private final Map<Long, RegistrationSession> sessions = new HashMap<>();

    public RegistrationSession getOrCreateSession(Long chatId) {
        return sessions.computeIfAbsent(chatId, id -> new RegistrationSession());
    }

    public void clearSession(Long chatId) {
        sessions.remove(chatId);
    }
}
