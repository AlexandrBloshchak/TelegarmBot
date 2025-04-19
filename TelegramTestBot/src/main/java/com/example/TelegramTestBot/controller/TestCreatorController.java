package com.example.TelegramTestBot.controller;

import com.example.TelegramTestBot.model.User;
import com.example.TelegramTestBot.model.Test;
import com.example.TelegramTestBot.service.TestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Slf4j
@Component
public class TestCreatorController {
    private final TestService testService;

    public TestCreatorController(TestService testService) {
        this.testService = testService;
    }

    public BotApiMethod<?> handleUpdate(Update update, User user) {
        String text = update.getMessage().getText();
        Long chatId = update.getMessage().getChatId();

        if (text.equals("/creator")) {
            return createMainCreatorMenu(chatId);
        } else if (text.equals("/creator_new_test")) {
            return createNewTest(chatId, user);
        }
        // Другие команды создателя

        return null;
    }
    public BotApiMethod<?> handleDocument(Update update, User user) {
        Message message = update.getMessage();
        if (message.hasDocument()) {
            String fileName = message.getDocument().getFileName();
            if (fileName.endsWith(".txt") || fileName.endsWith(".docx")) {
                // Логика обработки документа
                return SendMessage.builder()
                        .chatId(message.getChatId().toString())
                        .text("Документ получен. Обработка...")
                        .build();
            }
        }
        return null;
    }

    private BotApiMethod<?> createMainCreatorMenu(Long chatId) {
        // Реализация меню создателя
        return null;
    }

    private BotApiMethod<?> createNewTest(Long chatId, User user) {
        Test test = testService.createNewTest(user);
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("Создан новый тест. Введите название:")
                .build();
    }
}