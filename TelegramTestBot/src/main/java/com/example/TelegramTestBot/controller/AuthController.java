package com.example.TelegramTestBot.controller;

import com.example.TelegramTestBot.model.User;
import com.example.TelegramTestBot.service.UserService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.HashMap;
import java.util.Map;

@Component
public class AuthController {

    private final UserService userService;
    // Хранилище сессий авторизации для каждого chatId
    private final Map<Long, AuthSession> sessions = new HashMap<>();

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Основной обработчик обновлений для авторизации.
     * Поддерживает команды: /login, /registr, а также их текстовые варианты.
     * Если пользователь уже находится в процессе авторизации, обрабатывает ввод как данные (логин, пароль и т.д.).
     */
    public SendMessage handleAuth(Update update) {
        Long chatId;
        String text = "";
        // Определяем, откуда пришли данные: текстовое сообщение или callback
        if (update.hasMessage()) {
            chatId = update.getMessage().getChatId();
            if (update.getMessage().getText() != null) {
                text = update.getMessage().getText().trim();
            }
        } else if (update.hasCallbackQuery()) {
            chatId = update.getCallbackQuery().getMessage().getChatId();
            text = update.getCallbackQuery().getData().trim();
        } else {
            return new SendMessage(); // Пустое сообщение, если обновление неизвестного типа
        }

        // Получаем или создаем сессию авторизации для данного chatId
        AuthSession session = sessions.computeIfAbsent(chatId, k -> new AuthSession());

        // Если пользователь уже находится в процессе авторизации – обрабатываем ввод как данные
        if (session.state != AuthState.START) {
            return handleStatefulMessage(chatId, text, session, update);
        }

        // Если сессия в начальном состоянии: ожидается команда начала авторизации
        String normText = text.toLowerCase();
        if (normText.equals("/login") || normText.equals("войти")) {
            session.reset();
            session.state = AuthState.LOGIN_USERNAME;
            return new SendMessage(chatId.toString(), "Введите ваш логин:");
        } else if (normText.equals("/registr") || normText.equals("зарегистрироваться")) {
            session.reset();
            session.state = AuthState.REGISTER_FULLNAME;
            return new SendMessage(chatId.toString(), "Введите ваше ФИО (через пробел):");
        }

        // Если введена неизвестная команда – выводим стартовое меню
        return createWelcomeMessage(chatId);
    }

    /**
     * Метод для проверки, находится ли чат в активной сессии авторизации.
     * Если сессия есть и её состояние не START – возвращается true.
     */
    public boolean isInAuthSession(Long chatId) {
        AuthSession session = sessions.get(chatId);
        return session != null && session.state != AuthState.START;
    }

    /**
     * Обработка ввода пользователя в процессе авторизации/регистрации.
     */
    private SendMessage handleStatefulMessage(Long chatId, String text, AuthSession session, Update update) {
        switch (session.state) {
            case LOGIN_USERNAME -> {
                // Сохраняем логин и запрашиваем пароль
                session.username = text;
                session.state = AuthState.LOGIN_PASSWORD;
                return new SendMessage(chatId.toString(), "Введите пароль:");
            }
            case LOGIN_PASSWORD -> {
                String password = text;
                if (userService.authenticate(session.username, password, chatId)) {
                    sessions.remove(chatId); // Удаляем сессию после успешной авторизации
                    return new SendMessage(chatId.toString(), "Вы успешно авторизовались!");
                } else {
                    session.reset();
                    return new SendMessage(chatId.toString(),
                            "Неверный логин или пароль. Попробуйте снова. Для начала введите /login или 'Войти'");
                }
            }
            case REGISTER_FULLNAME -> {
                // Ожидаем ввод ФИО
                String[] parts = text.split("\\s+");
                if (parts.length < 2) {
                    return new SendMessage(chatId.toString(), "Введите имя и фамилию через пробел:");
                }
                session.firstName = parts[0];
                session.lastName = parts[1];
                session.state = AuthState.REGISTER_USERNAME;
                return new SendMessage(chatId.toString(), "Придумайте логин:");
            }
            case REGISTER_USERNAME -> {
                session.username = text;
                session.state = AuthState.REGISTER_PASSWORD;
                return new SendMessage(chatId.toString(), "Придумайте пароль:");
            }
            case REGISTER_PASSWORD -> {
                session.password = text;
                User user = new User();
                user.setLogin(session.username);
                // Рекомендуется шифрование пароля
                user.setPassword(session.password);
                user.setFullName(session.firstName + " " + session.lastName);
                user.setRole("USER");
                user.setChatId(chatId);
                // Получаем Telegram username
                String telegramUsername;
                if (update.hasMessage() && update.getMessage().getFrom().getUserName() != null
                        && !update.getMessage().getFrom().getUserName().isBlank()) {
                    telegramUsername = update.getMessage().getFrom().getUserName().trim();
                } else {
                    telegramUsername = "user_" + chatId;
                }
                user.setUsername(telegramUsername);
                userService.register(user);
                sessions.remove(chatId);
                return new SendMessage(chatId.toString(), "Регистрация прошла успешно!");
            }
            default -> {
                session.reset();
                return createWelcomeMessage(chatId);
            }
        }
    }

    /**
     * Формирует стартовое сообщение для выбора команды авторизации/регистрации.
     */
    private SendMessage createWelcomeMessage(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Добро пожаловать! Введите /login или 'Войти' для входа, либо /registr или 'Зарегистрироваться' для регистрации.");
        return message;
    }

    /**
     * Вспомогательная сессия для хранения данных авторизации/регистрации.
     */
    private static class AuthSession {
        AuthState state = AuthState.START;
        String username;
        String password;
        String firstName;
        String lastName;

        void reset() {
            state = AuthState.START;
            username = null;
            password = null;
            firstName = null;
            lastName = null;
        }
    }

    /**
     * Возможные состояния сессии авторизации.
     */
    private enum AuthState {
        START,
        LOGIN_USERNAME,
        LOGIN_PASSWORD,
        REGISTER_FULLNAME,
        REGISTER_USERNAME,
        REGISTER_PASSWORD
    }
}
