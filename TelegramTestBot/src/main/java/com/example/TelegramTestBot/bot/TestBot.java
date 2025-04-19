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

            BotApiMethod<? extends Serializable> response = null;

            if (update.getMessage().hasText() && update.getMessage().getText().equals("/start")) {
                execute(mainMenu(chatId));
                return;
            }

            try {
                // Обработка через контроллеры
                response = creatorController.handleUpdate(update, user);

                if (response == null && update.getMessage().hasText()) {
                    String text = update.getMessage().getText();
                    switch (text) {
                        case "Создать тест" -> response = creatorController.handleUpdate(update, user);
                        case "Пройти тест" -> response = participantController.handleUpdate(update, user);
                        default -> response = SendMessage.builder()
                                .chatId(chatId.toString())
                                .text("Выберите действие с клавиатуры:")
                                .replyMarkup(mainKeyboard())
                                .build();
                    }
                }

                if (response != null) {
                    execute(response);
                }

            } catch (Exception e) {
                log.error("Ошибка при обработке команды: {}", e.getMessage());
                sendErrorMessage(chatId, "Произошла ошибка при обработке команды");
            }

        } catch (TelegramApiException e) {
            log.error("Ошибка Telegram API: {}", e.getMessage());
            sendErrorMessage(chatId, "Ошибка связи с Telegram");
        }
    }

    private void sendErrorMessage(Long chatId, String message) {
        try {
            execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(message)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения об ошибке: {}", e.getMessage());
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
        KeyboardRow row = new KeyboardRow();
        row.add("Пройти тест");
        row.add("Создать тест");
        keyboard.setKeyboard(List.of(row));
        keyboard.setResizeKeyboard(true);
        return keyboard;
    }

    private void sendErrorMessage(Long chatId) {
        try {
            execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Произошла ошибка. Пожалуйста, попробуйте позже.")
                    .build());
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения об ошибке: {}", e.getMessage());
        }
    }
}
