package com.example.TelegramTestBot.bot;

import com.example.TelegramTestBot.model.User;
import com.example.TelegramTestBot.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class SessionService {
    private final Map<Long, UserSession> sessions = new ConcurrentHashMap<>();
    private final UserService userService;
    public void createSession(Long chatId, User user) {
        sessions.put(chatId, new UserSession(user));
    }
    public void invalidateSession(Long chatId) {
        sessions.remove(chatId);
        userService.logout(chatId);
    }
    public Optional<UserSession> getSession(Long chatId) {
        return Optional.ofNullable(sessions.get(chatId));
    }
}
