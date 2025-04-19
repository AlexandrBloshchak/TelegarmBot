package com.example.TelegramTestBot.controller;

import com.example.TelegramTestBot.dto.QuestionDto;
import com.example.TelegramTestBot.model.User;
import com.example.TelegramTestBot.service.UserService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.*;

@Component
public class AuthController {
    private final UserService userService;
    private final Map<Long, AuthSession> sessions = new HashMap<>();

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    public SendMessage handleAuth(Update update) {
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();
        AuthSession session = sessions.computeIfAbsent(chatId, k -> new AuthSession());

        if (text.equals("/start")) {
            session.reset();
            return createWelcomeMessage(chatId);
        }

        // Если пользователь в процессе регистрации или входа
        if (session.state != AuthState.START) {
            return handleStatefulMessage(chatId, text, session, update); // ✅ добавили update
        }

        // Обработка начального выбора
        return switch (text) {
            case "Войти" -> {
                session.state = AuthState.LOGIN_USERNAME;
                yield new SendMessage(chatId.toString(), "Введите ваш логин:");
            }
            case "Зарегистрироваться" -> {
                session.state = AuthState.REGISTER_FULLNAME;
                yield new SendMessage(chatId.toString(), "Введите ваше ФИО (через пробел):");
            }
            default -> createDefaultResponse(chatId);
        };
    }

    private SendMessage handleStatefulMessage(Long chatId, String text, AuthSession session, Update update) {
        switch (session.state) {
            case LOGIN_USERNAME -> {
                session.username = text;
                session.state = AuthState.LOGIN_PASSWORD;
                return new SendMessage(chatId.toString(), "Введите пароль:");
            }
            case LOGIN_PASSWORD -> {
                if (userService.authenticate(session.username, text, chatId)) {
                    sessions.remove(chatId);
                    return new SendMessage(chatId.toString(), "Успешный вход!");
                } else {
                    session.reset();
                    return new SendMessage(chatId.toString(), "Неверный логин или пароль. Попробуйте снова.");
                }
            }
            case REGISTER_FULLNAME -> {
                String[] parts = text.trim().split(" ");
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
                user.setLogin(session.username); // логин, который ввёл пользователь
                user.setPassword(session.password); // желательно зашифровать в register()
                user.setFullName(session.firstName + " " + session.lastName);
                user.setRole("USER");
                user.setChatId(chatId);

                // Сохраняем Telegram username, если есть
                String telegramUsername = update.getMessage().getFrom().getUserName();
                if (telegramUsername == null || telegramUsername.isBlank()) {
                    telegramUsername = "user_" + chatId; // запасной вариант
                }
                user.setUsername(telegramUsername); // ⚠️ это то, что нужно для поля username (в БД NOT NULL)

                userService.register(user);
                sessions.remove(chatId);
                return new SendMessage(chatId.toString(), "Регистрация успешна!");
            }
            default -> {
                session.reset();
                return createDefaultResponse(chatId);
            }
        }
    }


    private SendMessage createWelcomeMessage(Long chatId) {
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("Добро пожаловать! Выберите действие:")
                .replyMarkup(createAuthKeyboard())
                .build();
    }

    private SendMessage createDefaultResponse(Long chatId) {
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("Пожалуйста, используйте кнопки меню")
                .replyMarkup(createAuthKeyboard())
                .build();
    }

    private ReplyKeyboardMarkup createAuthKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add("Войти");
        row.add("Зарегистрироваться");
        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        return keyboardMarkup;
    }

    // Хранилище состояния авторизации/регистрации
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

    private enum AuthState {
        START,
        LOGIN_USERNAME,
        LOGIN_PASSWORD,
        REGISTER_FULLNAME,
        REGISTER_USERNAME,
        REGISTER_PASSWORD
    }
    enum TestCreationState {
        NONE,
        AWAITING_CHOICE,
        AWAITING_QUESTION_COUNT,
        ENTER_QUESTION_TEXT,
        ENTER_ANSWER,
        COMPLETE
    }
    private static class TestSession {
        TestCreationState state = TestCreationState.NONE;
        int totalQuestions;
        int currentQuestion = 0;
        List<QuestionDto> questions = new ArrayList<>();
    }
    Map<Long, TestSession> testSessions = new HashMap<>();
}
