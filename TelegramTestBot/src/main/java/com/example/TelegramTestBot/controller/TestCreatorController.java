package com.example.TelegramTestBot.controller;

import com.example.TelegramTestBot.dto.QuestionDto;
import com.example.TelegramTestBot.model.User;
import com.example.TelegramTestBot.service.TestCreationService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Component
public class TestCreatorController {

    private final TestCreationService testCreationService;
    private Map<Long, TestSession> userSessions = new HashMap<>();

    public TestCreatorController(TestCreationService testCreationService) {
        this.testCreationService = testCreationService;
    }

    public SendMessage handleUpdate(Update update, User user) {
        try {
            Long chatId = update.getMessage().getChatId();
            String text = update.getMessage().getText();

            // Проверка на документ
            if (update.getMessage().hasDocument()) {
                return handleDocument(update, chatId, user);
            }

            // Остальная логика обработки...
            return new SendMessage(chatId.toString(), "Неизвестная команда. Введите /createtest чтобы начать.");

        } catch (IOException e) {
            log.error("Ошибка при обработке файла: {}", e.getMessage());
            return new SendMessage(update.getMessage().getChatId().toString(),
                    "Произошла ошибка при обработке файла. Пожалуйста, попробуйте еще раз.");
        }
    }

    private SendMessage handleDocument(Update update, Long chatId, User user) throws IOException {
        TestSession session = userSessions.get(chatId);
        if (session == null || !session.hasTestName()) {
            return new SendMessage(chatId.toString(),
                    "Сначала введите название теста командой /createtest");
        }

        Document doc = update.getMessage().getDocument();
        String fileName = doc.getFileName();

        // Проверка формата файла
        if (!fileName.endsWith(".docx")) {
            return new SendMessage(chatId.toString(),
                    "Пожалуйста, отправьте файл в формате DOCX");
        }

        // Скачивание файла
        File tempFile = downloadFile(update.getMessage());
        List<QuestionDto> questions = parseDocxFile(tempFile);
        tempFile.delete(); // Удаляем временный файл

        // Сохранение теста
        testCreationService.createTest(user, session.getTestName(), questions);
        userSessions.remove(chatId); // Очищаем сессию

        return new SendMessage(chatId.toString(),
                "✅ Тест '" + session.getTestName() + "' успешно создан! Количество вопросов: " + questions.size());
    }

    private File downloadFile(Message message) throws IOException {
        // Здесь должна быть реализация скачивания файла через Telegram API
        // Это упрощенный пример - в реальности используйте TelegramBot.execute()
        File tempFile = Files.createTempFile("test", ".docx").toFile();
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            // Реальная реализация будет использовать TelegramBot.downloadFile()
            fos.write("Пример содержимого".getBytes());
        }
        return tempFile;
    }

    private List<QuestionDto> parseDocxFile(File file) throws IOException {
        List<QuestionDto> questions = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {

            String currentQuestion = null;

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText().trim();

                if (text.isEmpty()) continue;

                if (text.endsWith("?")) {
                    currentQuestion = text;
                } else if (currentQuestion != null) {
                    questions.add(new QuestionDto(currentQuestion, text));
                    currentQuestion = null;
                }
            }
        }

        return questions;
    }
}