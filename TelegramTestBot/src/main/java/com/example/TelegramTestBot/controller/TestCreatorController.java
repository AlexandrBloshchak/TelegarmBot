package com.example.TelegramTestBot.controller;

import com.example.TelegramTestBot.model.AnswerOption;
import com.example.TelegramTestBot.model.Question;
import com.example.TelegramTestBot.model.Test;
import com.example.TelegramTestBot.model.User;
import com.example.TelegramTestBot.service.TestCreationService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
@Component
public class TestCreatorController {
    private static final Logger log = LoggerFactory.getLogger(TestCreatorController.class);
    private final TestCreationService testCreationService;
    @Value("${telegram.bot.token}")
    private String botToken;
    // Регулярное выражение для извлечения вопросов
    private static final Pattern QUESTION_PATTERN = Pattern.compile(
            "Задание №\\s*\\d+\\s*(.+?)\\s*" +
                    "1\\s+(.+?)\\s*" +
                    "2\\s+(.+?)\\s*" +
                    "3\\s+(.+?)\\s*" +
                    "Ответ\\s*(\\d)",
            Pattern.DOTALL
    );



    // Поля для хранения состояний чата
    private final Map<Long, String> pendingTestNames = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> awaitingTestName = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> awaitingDocument = new ConcurrentHashMap<>();

    public TestCreatorController(TestCreationService testCreationService) {
        this.testCreationService = testCreationService;
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
        return message;
    }

    // Метод возвращает сообщение с просьбой загрузить документ
    public SendMessage promptDocument(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Название теста установлено. Теперь загрузите файл DOCX с вопросами.");
        return message;
    }

    // Основной метод для обработки update
    public SendMessage handleUpdate(Update update, User user, String currentTestName) {
        try {
            if (!update.hasMessage()) {
                return new SendMessage("0", "Сообщение не содержит текста или документов.");
            }
            Message message = update.getMessage();
            Long chatId = message.getChatId();

            // Если ожидается имя теста
            if (isAwaitingTestName(chatId)) {
                if (message.getText() != null && !message.getText().trim().isEmpty()) {
                    String testName = message.getText().trim();
                    // Переключаемся на ожидание документа
                    setAwaitingDocument(chatId, testName);
                    return new SendMessage(chatId.toString(),
                            "Название теста \"" + testName + "\" установлено. Теперь загрузите файл DOCX с вопросами.");
                } else {
                    return new SendMessage(chatId.toString(), "Введите имя теста:");
                }
            }

            // Если ожидается документ
            if (isAwaitingDocument(chatId)) {
                if (message.hasDocument()) {
                    Document doc = message.getDocument();
                    if (!doc.getFileName().endsWith(".docx")) {
                        return new SendMessage(chatId.toString(), "Пожалуйста, отправьте файл в формате DOCX.");
                    }
                    String testName = pendingTestNames.get(chatId);
                    File tempFile = downloadFile(message);
                    log.info("ChatId {}: файл сохранён по пути {}", chatId, tempFile.getAbsolutePath());

                    // Парсим файл с вопросами
                    List<Question> questions = parseDocxFile(tempFile);
                    tempFile.delete();

                    if (questions.isEmpty()) {
                        log.warn("ChatId {}: вопросы не извлечены. Проверьте формат файла.", chatId);
                        return new SendMessage(chatId.toString(),
                                "Не удалось извлечь вопросы из файла. Проверьте формат документа.");
                    }

                    // Создаем тест
                    Test test = new Test();
                    test.setTitle(testName);
                    test.setDescription("Тест создан пользователем " + user.getUsername());
                    testCreationService.createTest(user, test, questions);
                    log.info("ChatId {}: тест '{}' успешно создан ({} вопросов)", chatId, test.getTitle(), questions.size());
                    resetSession(chatId);
                    return new SendMessage(chatId.toString(),
                            "✅ Тест \"" + test.getTitle() + "\" создан! Вопросов: " + questions.size());
                } else {
                    return new SendMessage(chatId.toString(),
                            "Ожидается загрузка файла DOCX с вопросами.");
                }
            }

            // Если ни одно состояние не активно, можно вернуть информацию об ошибке
            return new SendMessage(chatId.toString(),
                    "Ошибка состояния. Попробуйте ввести название теста заново.");

        } catch (IOException e) {
            log.error("ChatId {}: ошибка при обработке файла: {}", update.getMessage().getChatId(), e.getMessage());
            return new SendMessage("0", "Произошла ошибка при обработке файла.");
        }
    }

    private File downloadFile(Message message) throws IOException {
        // Получаем fileId загруженного документа
        String fileId = message.getDocument().getFileId();

        // Формируем URL для запроса getFile
        String getFileUrl = "https://api.telegram.org/bot" + botToken + "/getFile?file_id=" + fileId;
        URL url = new URL(getFileUrl);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");

        // Читаем ответ от Telegram API
        BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        con.disconnect();

        // Извлекаем file_path из JSON-ответа (пример ответа: {"ok":true,"result":{"file_path":"documents/file_10.docx",...}})
        String json = response.toString();
        Pattern pattern = Pattern.compile("\"file_path\"\\s*:\\s*\"(.*?)\"");
        Matcher matcher = pattern.matcher(json);
        String filePath = null;
        if (matcher.find()) {
            filePath = matcher.group(1);
        }
        if (filePath == null) {
            throw new IOException("Не удалось получить путь к файлу.");
        }

        // Формируем URL для скачивания файла
        String fileDownloadUrl = "https://api.telegram.org/file/bot" + botToken + "/" + filePath;
        URL downloadUrl = new URL(fileDownloadUrl);
        InputStream inputStream = downloadUrl.openStream();

        // Создаем временный файл с расширением .docx
        File tempFile = Files.createTempFile("test", ".docx").toFile();
        Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        inputStream.close();

        // Проверяем, действительно ли файл можно открыть с помощью Apache POI (DOCX)
        try (FileInputStream fis = new FileInputStream(tempFile);
             XWPFDocument doc = new XWPFDocument(fis)) {
            // Если файл открылся без ошибок, значит он корректен
        } catch (Exception e) {
            throw new IOException("Недопустимый формат файла DOCX.", e);
        }

        return tempFile;
    }


    private byte[] getTestDocxContent() {
        String sample = "Задание № 1\n" +
                "Какой язык программирования используется для Android?\n" +
                "1) Java\n" +
                "2) Python\n" +
                "3) C++\n" +
                "Ответ 1\n" +
                "Задание № 2\n" +
                "Что такое JVM?\n" +
                "1) Виртуальная машина Java\n" +
                "2) Компилятор Java\n" +
                "3) Редактор кода\n" +
                "Ответ 1";
        return sample.getBytes();
    }

    private List<Question> parseDocxFile(File file) throws IOException {
        List<Question> questions = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {

            StringBuilder fullText = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String paraText = paragraph.getText().trim();
                if (!paraText.isEmpty()) {
                    fullText.append(paraText).append("\n");
                }
            }
            log.info("Полученный текст из файла:\n{}", fullText.toString());

            // Парсинг по разделителю "Задание №"
            String[] parts = fullText.toString().split("Задание №");
            for (String part : parts) {
                if (part.trim().isEmpty()) continue;
                String block = "Задание №" + part;
                Matcher matcher = QUESTION_PATTERN.matcher(block);
                if (matcher.find()) {
                    // Создаём новый вопрос
                    Question question = new Question();
                    question.setText(matcher.group(1).trim());

                    // Получаем варианты ответов
                    String option1Text = matcher.group(2).trim();
                    String option2Text = matcher.group(3).trim();
                    String option3Text = matcher.group(4).trim();
                    int correctNumber = Integer.parseInt(matcher.group(5).trim());

                    // Формируем список вариантов ответов
                    List<AnswerOption> answerOptions = new ArrayList<>();

                    AnswerOption option1 = new AnswerOption(option1Text, 1, correctNumber == 1);
                    option1.setQuestion(question);
                    answerOptions.add(option1);

                    AnswerOption option2 = new AnswerOption(option2Text, 2, correctNumber == 2);
                    option2.setQuestion(question);
                    answerOptions.add(option2);

                    AnswerOption option3 = new AnswerOption(option3Text, 3, correctNumber == 3);
                    option3.setQuestion(question);
                    answerOptions.add(option3);

                    // Если в вашем классе Question предусмотрено хранение вариантов,
                    // установим их через сеттер:
                    question.setAnswerOptions(answerOptions);

                    questions.add(question);
                    log.info("Извлечён вопрос: '{}' (вариантов ответа: {})", question.getText(), answerOptions.size());
                } else {
                    log.warn("Не удалось распознать блок:\n{}", block);
                }
            }
        }
        return questions;
    }
}
