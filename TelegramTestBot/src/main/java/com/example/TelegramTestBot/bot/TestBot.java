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
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.Serializable;

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

    public TestBot(AuthController authController, UserService userService,
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
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();

        try {
            // Проверяем аутентификацию
            User user = userService.getAuthenticatedUser(chatId);

            if (user == null) {
                // Обработка аутентификации
                SendMessage authResponse = authController.handleAuth(update);
                execute(authResponse);
                return;
            }

            // Основная логика для аутентифицированных пользователей
            BotApiMethod<? extends Serializable> response;

            if (text.startsWith("/creator")) {
                response = creatorController.handleUpdate(update, user);
            } else {
                response = participantController.handleUpdate(update, user);
            }

            if (response != null) {
                execute(response);
            }
        } catch (TelegramApiException e) {
            log.error("Error processing update: {}", e.getMessage());
            sendErrorMessage(chatId);
        }
    }

    private void sendErrorMessage(Long chatId) {
        try {
            execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Произошла ошибка. Пожалуйста, попробуйте позже.")
                    .build());
        } catch (TelegramApiException e) {
            log.error("Error sending error message: {}", e.getMessage());
        }
    }
}