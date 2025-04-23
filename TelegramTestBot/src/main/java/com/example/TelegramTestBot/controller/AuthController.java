package com.example.TelegramTestBot.controller;

import com.example.TelegramTestBot.bot.SessionService;
import com.example.TelegramTestBot.bot.TestBot;
import com.example.TelegramTestBot.bot.UserSession;
import com.example.TelegramTestBot.model.User;
import com.example.TelegramTestBot.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class AuthController {
    private final UserService userService;
    private final SessionService sessionService;

    private enum AuthState {
        AWAITING_LOGIN,
        AWAITING_PASSWORD,
        REGISTER_AWAIT_LOGIN,
        REGISTER_AWAIT_PASSWORD,
        REGISTER_AWAIT_FULLNAME
    }

    private final Map<Long, AuthState> authStates = new HashMap<>();
    private final Map<Long, String> pendingLogins = new HashMap<>();
    private final Map<Long, String> pendingPasswords = new HashMap<>();

    public AuthController(UserService userService, SessionService sessionService) {
        this.userService = userService;
        this.sessionService = sessionService;
    }

    /** Возвращает сообщение для отправки клиенту. */
    public SendMessage startLoginProcess(Long chatId) {
        authStates.put(chatId, AuthState.AWAITING_LOGIN);
        SendMessage message = new SendMessage(chatId.toString(), "Введите ваш логин:");
        return message;
    }

    /** Возвращает сообщение для отправки клиенту. */
    public SendMessage startRegistrationProcess(Long chatId) {
        authStates.put(chatId, AuthState.REGISTER_AWAIT_LOGIN);
        return new SendMessage(chatId.toString(), "🆕 Регистрация: введите желаемый логин:");
    }
    public SendMessage handleAuth(Update update) {
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();
        AuthState state = authStates.get(chatId);

        if (state == null) return null;

        switch (state) {
            case AWAITING_LOGIN:
                pendingLogins.put(chatId, text);
                authStates.put(chatId, AuthState.AWAITING_PASSWORD);
                return new SendMessage(chatId.toString(), "🔒 Введите ваш пароль:");

            case AWAITING_PASSWORD:
                String login = pendingLogins.remove(chatId);
                String password = text;
                boolean ok = userService.authenticate(login, password, chatId);
                authStates.remove(chatId);

                if (ok) {
                    // Создаем сессию для пользователя
                    User user = userService.getAuthenticatedUser(chatId)
                            .orElseThrow(() -> new IllegalStateException("User not found after authentication"));
                    sessionService.createSession(chatId, user);
                }

                return ok ?
                        new SendMessage(chatId.toString(), "✅ Вы успешно вошли!") :
                        new SendMessage(chatId.toString(), "❌ Неверный логин или пароль.");
            case REGISTER_AWAIT_LOGIN:
                pendingLogins.put(chatId, text);
                authStates.put(chatId, AuthState.REGISTER_AWAIT_PASSWORD);
                return new SendMessage(chatId.toString(), "🔒 Введите пароль для нового аккаунта:");

            case REGISTER_AWAIT_PASSWORD:
                pendingPasswords.put(chatId, text);
                authStates.put(chatId, AuthState.REGISTER_AWAIT_FULLNAME);
                return new SendMessage(chatId.toString(), "✍️ Введите ваше ФИО:");

            case REGISTER_AWAIT_FULLNAME:
                String username = pendingLogins.remove(chatId);
                String pass = pendingPasswords.remove(chatId);
                String fullName = text;
                authStates.remove(chatId);

                try {
                    userService.register(username, pass, fullName, chatId);
                    return new SendMessage(chatId.toString(),
                            "🎉 Регистрация завершена! Добро пожаловать, " + fullName + "!");
                } catch (IllegalArgumentException e) {
                    return new SendMessage(chatId.toString(), "❌ " + e.getMessage());
                }

            default:
                authStates.remove(chatId);
                return null;
        }
    }
}
