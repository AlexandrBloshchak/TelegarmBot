package com.example.TelegramTestBot.bot;

import com.example.TelegramTestBot.controller.AuthController;
import com.example.TelegramTestBot.controller.TestCreatorController;
import com.example.TelegramTestBot.controller.TestParticipantController;
import com.example.TelegramTestBot.model.User;
import com.example.TelegramTestBot.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class TestBot extends TelegramLongPollingBot {

    private final AuthController authController;
    private final UserService userService;
    private final TestCreatorController creatorController;
    private final TestParticipantController participantController;

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.bot.token}")
    private String botToken;

    public TestBot(AuthController authController,
                   UserService userService,
                   TestCreatorController creatorController,
                   TestParticipantController participantController) {
        this.authController = authController;
        this.userService = userService;
        this.creatorController = creatorController;
        this.participantController = participantController;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage()) return;

        Long chatId = update.getMessage().getChatId();

        try {
            User user = userService.getAuthenticatedUser(chatId).orElse(null);

            if (user == null) {
                SendMessage authResponse = authController.handleAuth(update);
                execute(authResponse);
                return;
            }

            // 📦 Передаём update в creatorController вне зависимости от текста/документа
            BotApiMethod<? extends Serializable> response = creatorController.handleUpdate(update, user);

            // 🔁 Если creatorController ничего не вернул, обрабатываем обычные команды
            if (response == null && update.getMessage().hasText()) {
                String text = update.getMessage().getText();
                switch (text) {
                    case "Создать тест" -> response = creatorController.handleUpdate(update, user);
                    case "Пройти тест" -> response = participantController.handleUpdate(update, user);
                    default -> response = SendMessage.builder()
                            .chatId(chatId.toString())
                            .text("Пожалуйста, выберите действие с клавиатуры:")
                            .replyMarkup(mainKeyboard())
                            .build();
                }
            }

            if (response != null) {
                execute(response);
            }

        } catch (TelegramApiException e) {
            log.error("Ошибка при обработке обновления: {}", e.getMessage());
            sendErrorMessage(chatId);
        }
    }


    private SendMessage mainMenu(Long chatId) {
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("Выберите действие:")
                .replyMarkup(mainKeyboard())
                .build();
    }

    private ReplyKeyboardMarkup mainKeyboard() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(false); // Клавиатура остаётся активной
        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row = new KeyboardRow();
        row.add("Пройти тест");
        row.add("Создать тест");
        rows.add(row);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    private ReplyKeyboardMarkup creatorKeyboard() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(false); // Клавиатура остаётся активной
        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row = new KeyboardRow();
        row.add("Ввести вручную");
        row.add("Загрузить файл с вопросами");
        rows.add(row);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    private void sendErrorMessage(Long chatId) {
        try {
            execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Произошла ошибка. Пожалуйста, попробуйте позже.")
                    .replyMarkup(mainKeyboard())
                    .build());
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения об ошибке: {}", e.getMessage());
        }
    }
}
