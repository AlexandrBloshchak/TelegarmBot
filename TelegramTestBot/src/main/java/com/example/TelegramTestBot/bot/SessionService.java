package com.example.TelegramTestBot.bot;

import com.example.TelegramTestBot.model.User;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {
    private final Map<Long, UserSession> userSessions = new ConcurrentHashMap<>();

    public void createSession(Long chatId, User user) {
        userSessions.put(chatId, new UserSession(user));
    }

    public void removeSession(Long chatId) {
        userSessions.remove(chatId);
    }

    public Optional<UserSession> getSession(Long chatId) {
        return Optional.ofNullable(userSessions.get(chatId));
    }

    public boolean hasSession(Long chatId) {
        return userSessions.containsKey(chatId);
    }
}