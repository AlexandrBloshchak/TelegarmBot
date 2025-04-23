package com.example.TelegramTestBot.controller;

import com.example.TelegramTestBot.bot.TestBot;
import com.example.TelegramTestBot.model.*;
import com.example.TelegramTestBot.service.TestCreationService;
import com.example.TelegramTestBot.service.TestResultService;
import jakarta.transaction.Transactional;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class TestCreatorController {
    private static final Logger log = LoggerFactory.getLogger(TestCreatorController.class);
    private final TestCreationService testCreationService;
    private final TestResultService testResultService;
    @Value("${telegram.bot.token}")
    private String botToken;

    // Ленивая инъекция TestBot чтобы не было цикла
    private TestBot testBot;

    // Поля для хранения состояний чата
    private final Map<Long, String> pendingTestNames = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> awaitingTestName = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> awaitingDocument = new ConcurrentHashMap<>();

    @Autowired
    public TestCreatorController(TestCreationService testCreationService, TestResultService testResultService) {
        this.testCreationService = testCreationService;
        this.testResultService = testResultService;
    }

    @Autowired
    public void setTestBot(@Lazy TestBot testBot) {
        this.testBot = testBot;
    }

    // Метод для сброса сессии для чата
    public void resetSession(Long chatId) {
        pendingTestNames.remove(chatId);
        awaitingTestName.remove(chatId);
        awaitingDocument.remove(chatId);
    }

    // Устанавливаем состояние ожидания имени теста
    public void setAwaitingTestName(Long chatId) {
        awaitingTestName.put(chatId, true);
    }

    // Устанавливаем состояние ожидания документа после получения имени теста
    public void setAwaitingDocument(Long chatId, String testName) {
        pendingTestNames.put(chatId, testName);
        awaitingTestName.remove(chatId);
        awaitingDocument.put(chatId, true);
    }

    // Проверяем, ждем ли мы имя теста
    public boolean isAwaitingTestName(Long chatId) {
        return awaitingTestName.getOrDefault(chatId, false);
    }

    // Проверяем, ждем ли мы документ
    public boolean isAwaitingDocument(Long chatId) {
        return awaitingDocument.getOrDefault(chatId, false);
    }

    // Метод возвращает сообщение с просьбой ввести имя теста
    public SendMessage promptTestName(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Введите имя теста:");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add("Отмена");
        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        message.setReplyMarkup(keyboardMarkup);

        return message;
    }

    // Метод возвращает сообщение с просьбой загрузить документ
    public SendMessage promptDocument(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Название теста установлено. Теперь загрузите файл DOCX с вопросами.");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add("Отмена");
        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        message.setReplyMarkup(keyboardMarkup);

        return message;
    }
    public SendMessage handleUpdate(Update update, User user) {
        if (!update.hasMessage()) {
            return null;
        }

        Message message = update.getMessage();
        Long chatId = message.getChatId();

        // Безопасное получение текста
        String text = message.hasText() ? message.getText().trim() : "";

        try {
            // 1. Обработка команды отмены
            if ("Отмена".equalsIgnoreCase(text)) {
                resetSession(chatId);
                return createMessage(chatId, "Создание теста отменено.", true);
            }

            // 2. Если ожидается имя теста
            if (isAwaitingTestName(chatId)) {
                if (text.isBlank()) {
                    return promptTestName(chatId);
                }

                // Валидация названия теста
                if (text.length() > 100) {
                    return createMessage(chatId,
                            "Название теста слишком длинное. Максимум 100 символов.", false);
                }

                setAwaitingDocument(chatId, text);
                log.info("Пользователь {} задал название теста: {}", user.getUsername(), text);
                return promptDocument(chatId);
            }

            // 3. Если ожидается документ
            if (isAwaitingDocument(chatId)) {
                if (!message.hasDocument()) {
                    return createMessage(chatId,
                            "Ожидается загрузка файла DOCX с вопросами.", false);
                }

                Document doc = message.getDocument();
                String fileName = doc.getFileName();

                // Проверка формата файла
                if (!fileName.toLowerCase().endsWith(".docx")) {
                    return createMessage(chatId,
                            "Пожалуйста, отправьте файл в формате DOCX.", false);
                }

                // Проверка размера файла (макс. 20MB)
                if (doc.getFileSize() > 20_000_000) {
                    return createMessage(chatId,
                            "Файл слишком большой. Максимальный размер - 20MB", false);
                }

                String testName = pendingTestNames.get(chatId);
                log.info("Начата обработка файла {} для теста '{}'", fileName, testName);

                try {
                    // Загрузка и обработка файла
                    File tempFile = downloadFile(doc.getFileId());
                    List<Question> questions = parseDocxFile(tempFile);

                    if (!tempFile.delete()) {
                        log.warn("Не удалось удалить временный файл: {}", tempFile.getPath());
                    }

                    if (questions.isEmpty()) {
                        return createMessage(chatId,
                                "Не удалось извлечь вопросы. Проверьте формат документа.", true);
                    }

                    // Создание теста
                    Test test = new Test();
                    test.setTitle(testName);
                    test.setDescription("Создано: " + user.getUsername());

                    testCreationService.createTest(user, test, questions);
                    log.info("Тест '{}' создан, вопросов: {}", testName, questions.size());

                    resetSession(chatId);
                    return createSuccessMessage(chatId,
                            "✅ Тест \"%s\" создан! Вопросов: %d".formatted(testName, questions.size()));

                } catch (IOException e) {
                    log.error("Ошибка обработки файла", e);
                    return createMessage(chatId,
                            "⚠️ Ошибка при обработке файла: " + e.getMessage(), true);
                }
            }

            // 4. Если не в процессе создания теста
            return null;

        } catch (Exception e) {
            log.error("Неожиданная ошибка при обработке запроса", e);
            resetSession(chatId);
            return createMessage(chatId,
                    "⚠️ Произошла непредвиденная ошибка. Попробуйте позже.", true);
        }
    }

// Вспомогательные методы:

    private SendMessage createMessage(Long chatId, String text, boolean withMainMenu) {
        SendMessage message = new SendMessage(chatId.toString(), text);
        if (withMainMenu) {
            message.setReplyMarkup(createMainMenuKeyboard());
        }
        return message;
    }

    private SendMessage createSuccessMessage(Long chatId, String text) {
        SendMessage message = new SendMessage(chatId.toString(), text);
        message.setReplyMarkup(createMainMenuKeyboard());
        return message;
    }

    private ReplyKeyboardMarkup createMainMenuKeyboard() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("Создать тест");
        row1.add("Пройти тест");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("Мой профиль");

        rows.add(row1);
        rows.add(row2);

        keyboard.setKeyboard(rows);
        keyboard.setResizeKeyboard(true);
        return keyboard;
    }

    public void startTestCreation(Long chatId, User user) {
        // Устанавливаем флаг ожидания названия
        setAwaitingTestName(chatId);
        // Просим ввести название
        SendMessage msg = promptTestName(chatId);
        testBot.executeMessage(msg);
    }

    @Transactional
    public String getUserCreatedTestsInfo(User user) {
        // Получаем все результаты тестов для данного пользователя
        List<TestResult> completedTests = testResultService.getCompletedTestsByUser(user.getId());

        // Формируем строку с результатами тестов
        StringBuilder resultBuilder = new StringBuilder();

        DecimalFormat df = new DecimalFormat("#.##");  // Форматирование процента

        for (TestResult result : completedTests) {
            Test test = result.getTest();
            if (test != null) {
                // Вычисляем процент
                double percentage = (result.getScore() * 100.0) / result.getMaxScore();
                resultBuilder.append(test.getTitle())  // Название теста
                        .append(" - Бал: ").append(result.getScore())  // Баллы пользователя
                        .append(" / ").append(result.getMaxScore())  // Максимальный балл
                        .append(" - ").append(df.format(percentage))  // Процент
                        .append("%\n");
            }
        }

        // Если нет пройденных тестов
        if (completedTests.isEmpty()) {
            return "❌ Вы еще не прошли ни одного теста.";
        }

        return resultBuilder.toString();
    }

    private File downloadFile(String fileId) throws IOException {
        // 1. Получаем информацию о файле от Telegram API
        String getFileUrl = "https://api.telegram.org/bot" + botToken + "/getFile?file_id=" + fileId;
        URL url = new URL(getFileUrl);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");

        // 2. Читаем ответ от Telegram API
        String response;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            response = reader.lines().collect(Collectors.joining());
        }

        // 3. Извлекаем file_path из JSON-ответа
        Pattern pattern = Pattern.compile("\"file_path\"\\s*:\\s*\"(.*?)\"");
        Matcher matcher = pattern.matcher(response);
        if (!matcher.find()) {
            throw new IOException("Не удалось получить путь к файлу из ответа Telegram");
        }
        String filePath = matcher.group(1);

        // 4. Формируем URL для скачивания файла
        String fileDownloadUrl = "https://api.telegram.org/file/bot" + botToken + "/" + filePath;
        URL downloadUrl = new URL(fileDownloadUrl);

        // 5. Создаем временный файл
        File tempFile = File.createTempFile("test_", ".docx");
        try (InputStream in = downloadUrl.openStream()) {
            Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        // 6. Проверяем, что файл действительно DOCX
        try (FileInputStream fis = new FileInputStream(tempFile);
             XWPFDocument doc = new XWPFDocument(fis)) {
            // Если файл открылся без ошибок - он валидный
        } catch (Exception e) {
            tempFile.delete();
            throw new IOException("Недопустимый формат файла DOCX", e);
        }

        return tempFile;
    }
    private List<Question> parseDocxFile(File file) throws IOException {
        List<Question> questions = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {

            // Собираем все непустые строки
            List<String> lines = document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            Question currentQuestion = null;
            List<AnswerOption> currentOptions = null;
            StringBuilder questionText = new StringBuilder();

            for (String line : lines) {
                // 1) Новый вопрос: "1. Что-то?"
                if (line.matches("^\\d+\\.\\s*.*\\?$")) {
                    // Сохраняем предыдущий
                    if (currentQuestion != null) {
                        currentQuestion.setText(questionText.toString().trim());
                        currentQuestion.setAnswerOptions(currentOptions);
                        questions.add(currentQuestion);
                    }
                    // Начинаем новый
                    currentQuestion = new Question();
                    currentOptions = new ArrayList<>();
                    questionText.setLength(0);
                    // Убираем номер и точку
                    questionText.append(line.replaceFirst("^\\d+\\.\\s*", ""));
                    continue;
                }

                // 2) Вариант ответа: "1. Текст..."
                if (line.matches("^\\d+\\.\\s+.*")) {
                    String text = line.replaceFirst("^\\d+\\.\\s+", "");
                    AnswerOption opt = new AnswerOption();
                    opt.setQuestion(currentQuestion);
                    opt.setText(text);
                    opt.setOptionNumber(currentOptions.size() + 1);
                    opt.setIsCorrect(false);
                    currentOptions.add(opt);
                    continue;
                }

                // 3) Строка "Ответ X"
                if (line.matches("^(Ответ|Правильный ответ)[\\s:]*\\d+.*")) {
                    // Извлекаем цифру
                    int correct = Integer.parseInt(line.replaceAll("\\D+", ""));
                    if (currentOptions != null && correct >= 1 && correct <= currentOptions.size()) {
                        currentOptions.get(correct - 1).setIsCorrect(true);
                    }
                    continue;
                }

                // 4) Продолжение текста вопроса
                if (currentQuestion != null) {
                    questionText.append(" ").append(line);
                }
            }

            // Сохраняем последний вопрос
            if (currentQuestion != null) {
                currentQuestion.setText(questionText.toString().trim());
                currentQuestion.setAnswerOptions(currentOptions);
                questions.add(currentQuestion);
            }
        } catch (Exception e) {
            log.error("Ошибка при парсинге DOCX файла", e);
            throw new IOException("Ошибка при разборе файла DOCX", e);
        }

        log.info("Из файла извлечено {} вопросов", questions.size());
        return questions;
    }
}
