package com.example.TelegramTestBot.controller;

import com.example.TelegramTestBot.dto.QuestionDto;
import com.example.TelegramTestBot.model.Question;
import com.example.TelegramTestBot.model.Test;
import com.example.TelegramTestBot.model.User;
import com.example.TelegramTestBot.service.TestService;
import com.example.TelegramTestBot.bot.TestBot;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TestCreatorController {

    private final TestBot testBot;
    private final TestService testService;
    private Map<Long, TestSession> testSessions;

    public TestCreatorController(TestBot testBot, TestService testService) {
        this.testBot = testBot;
        this.testService = testService;
    }

    public SendMessage handleUpdate(Update update, User user) {
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();
        TestSession session = testSessions.computeIfAbsent(chatId, k -> new TestSession());

        if (session.state == TestCreationState.AWAITING_TITLE) {
            session.testTitle = text;  // Сохраняем название теста
            session.state = TestCreationState.AWAITING_FILE;
            return new SendMessage(chatId.toString(), "Пожалуйста, отправьте файл с вопросами.");
        }

        // Обработка загрузки файла с вопросами
        if (session.state == TestCreationState.AWAITING_FILE && update.getMessage().hasDocument()) {
            Document fileDocument = update.getMessage().getDocument();
            String fileId = fileDocument.getFileId();

            try {
                // Получаем путь к файлу
                File file = getFilePath(fileId);
                String filePath = file.getFilePath();

                // Парсим файл
                List<QuestionDto> questions = parseFile(filePath);

                // Заполняем список вопросов
                session.questions.addAll(questions);

                // Создаём тест и сохраняем его в БД
                Test test = new Test();
                test.setTitle(session.testTitle);
                test.setCreator(user);
                test = testService.saveTest(test);

                // Сохраняем вопросы в БД, привязываем их к тесту
                for (QuestionDto questionDto : session.questions) {
                    Question question = new Question();
                    question.setText(questionDto.getQuestionText());
                    question.setCorrectAnswer(questionDto.getCorrectAnswer());
                    question.setTest(test);
                    testService.saveQuestion(question);
                }

                session.state = TestCreationState.COMPLETE;
                return new SendMessage(chatId.toString(), "✅ Вопросы успешно загружены!\nТест сохранён.");
            } catch (TelegramApiException | IOException e) {
                e.printStackTrace();
                return new SendMessage(chatId.toString(), "Ошибка при обработке файла.");
            }
        }

        return new SendMessage(chatId.toString(), "Что-то пошло не так.");
    }

    // Получаем файл по ID
    private File getFilePath(String fileId) throws TelegramApiException {
        GetFile getFileMethod = new GetFile();
        getFileMethod.setFileId(fileId);
        return testBot.execute(getFileMethod);
    }

    // Парсинг файла (используем Apache POI для DOCX)
    private List<QuestionDto> parseFile(String filePath) throws IOException {
        List<QuestionDto> questions = new ArrayList<>();

        // Открываем файл
        try (FileInputStream fis = new FileInputStream(filePath)) {
            XWPFDocument document = new XWPFDocument(fis);

            // Извлекаем текст из каждого абзаца
            for (org.apache.poi.xwpf.usermodel.XWPFParagraph paragraph : document.getParagraphs()) {
                String paragraphText = paragraph.getText();
                // Определяем вопрос и ответ по структуре текста
                if (paragraphText.startsWith("Q: ")) {
                    String questionText = paragraphText.substring(3).trim();
                    String correctAnswer = document.getParagraphs().get(document.getParagraphs().indexOf(paragraph) + 1).getText(); // Следующий абзац - ответ
                    questions.add(new QuestionDto(questionText, correctAnswer));
                }
            }
        }
        return questions;
    }

    private static class TestSession {
        TestCreationState state = TestCreationState.NONE;
        String testTitle;
        List<QuestionDto> questions = new ArrayList<>();
    }

    enum TestCreationState {
        NONE,
        AWAITING_TITLE,     // Запрос названия теста
        AWAITING_FILE,      // Ожидание файла
        COMPLETE            // Завершено
    }
}
