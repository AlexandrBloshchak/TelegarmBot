package com.example.TelegramTestBot.controller;

import com.example.TelegramTestBot.model.Test;
import com.example.TelegramTestBot.model.User;
import com.example.TelegramTestBot.service.TestService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class TestParticipantController {
    private final TestService testService;

    public TestParticipantController(TestService testService) {
        this.testService = testService;
    }

    public BotApiMethod<?> handleUpdate(Update update, User user) {
        String text = update.getMessage().getText();
        Long chatId = update.getMessage().getChatId();

        if (text.equals("/start_test")) {
            return showAvailableTests(chatId, user);
        }
        // Другие команды участника

        return null;
    }
    private BotApiMethod<?> createTestSelectionKeyboard(Long chatId, List<Test> tests) {
        List<InlineKeyboardButton> buttons = tests.stream()
                .map(test -> InlineKeyboardButton.builder()
                        .text(test.getTitle())
                        .callbackData("select_test_" + test.getId())
                        .build())
                .collect(Collectors.toList());

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("Выберите тест:")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(Collections.singletonList(buttons))
                        .build())
                .build();
    }
    private BotApiMethod<?> showAvailableTests(Long chatId, User user) {
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("Доступные тесты:\n" + testService.getAvailableTests(user))
                .build();
    }
}