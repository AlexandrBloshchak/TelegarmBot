package com.example.TelegramTestBot.controller;

import com.example.TelegramTestBot.model.User;
import com.example.TelegramTestBot.service.UserService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

@Component
public class AuthController {
    private final UserService userService;

    // Для хранения состояния пользователей
    private enum AuthState {
        START, LOGIN_USERNAME, LOGIN_PASSWORD,
        REGISTER_FIRSTNAME, REGISTER_LASTNAME,
        REGISTER_MIDDLENAME, REGISTER_USERNAME,
        REGISTER_PASSWORD
    }

    public AuthController(UserService userService) {
        this.userService = userService;
    }
    public SendMessage handleAuth(Update update) {
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();

        if (text.equals("/start")) {
            return createWelcomeMessage(chatId);
        } else if (text.equals("Войти")) {
            return requestLogin(chatId);
        } else if (text.equals("Зарегистрироваться")) {
            return requestRegistration(chatId);
        }

        return createDefaultResponse(chatId);
    }

    private SendMessage createWelcomeMessage(Long chatId) {
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("Добро пожаловать! Выберите действие:")
                .replyMarkup(createAuthKeyboard())
                .build();
    }

    private SendMessage requestLogin(Long chatId) {
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("Введите ваш логин:")
                .build();
    }

    private SendMessage requestRegistration(Long chatId) {
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("Введите ваше имя:")
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
}