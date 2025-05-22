package com.example.TelegramTestBot.controller;

import com.example.TelegramTestBot.bot.TestBot;
import com.example.TelegramTestBot.model.*;
import com.example.TelegramTestBot.service.*;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.example.TelegramTestBot.controller.ProfileController.kRow;

@Slf4j
@Component
public class TestCreatorController {

    private final TestCreationService testCreationService;
    private final TestService testService;
    private final ManualTestCreatorController manualTestCreator;

    @Value("${telegram.bot.token}")
    private String botToken;

    private TestBot testBot;

    private final Map<Long, String> pendingTestNames = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> pendingShowAnswers = new ConcurrentHashMap<>();
    private final Set<Long> awaitingName = ConcurrentHashMap.newKeySet();
    private final Set<Long> awaitingModeChoice = ConcurrentHashMap.newKeySet();
    private final Set<Long> awaitingShowChoice = ConcurrentHashMap.newKeySet();
    private final Set<Long> awaitingDocument = ConcurrentHashMap.newKeySet();

    @Autowired
    public TestCreatorController(TestCreationService testCreationService,
                                 TestService testService,
                                 ManualTestCreatorController manualTestCreator) {
        this.testCreationService = testCreationService;
        this.testService = testService;
        this.manualTestCreator = manualTestCreator;
    }

    @Autowired
    public void setTestBot(@Lazy TestBot testBot) {
        this.testBot = testBot;
    }

    private static final String RULES =
            "📄 *Как бот понимает файл*\n" +
                    "• Вопрос — строка, заканчивающаяся «?\"\n" +
                    "• Далее - варианты, каждая строка — отдельный вариант.\n" +
                    "• «1)», «a)», тире, буллеты игнорируются.\n" +
                    "• Пометьте правильный вариант знаком «+» *или* словами «Правильный», «Ответ».\n" +
                    "• Можно отдельной строкой «Правильный 2».\n" +
                    "Пример:\n" +
                    "Сколько будет 2+2?\n" +
                    "+ 4\n" +
                    "5\n" +
                    "3";

    public void startTestCreation(Long chat) {
        awaitingName.add(chat);
        testBot.executeMessage(kb(chat, "Введите имя теста:", List.of("Отмена")));
    }

    public SendMessage handleUpdate(Update up, User user) {
        if (!up.hasMessage()) return null;
        Message msg = up.getMessage();
        long chat = msg.getChatId();

        if (awaitingDocument.contains(chat))
            return handleDocument(chat, msg, user);

        if (!msg.hasText()) return null;
        String text = msg.getText().trim();

        if ("отмена".equalsIgnoreCase(text))
            return cancel(chat, user);

        if (awaitingName.contains(chat))
            return handleTitle(chat, text);

        if (awaitingModeChoice.contains(chat))
            return handleModeChoice(chat, text, user);

        if (awaitingShowChoice.contains(chat))
            return handleShowChoice(chat, text);

        return null;
    }

    private SendMessage handleTitle(Long chat, String title) {
        if (title.isBlank())
            return kb(chat, "Название не может быть пустым.", List.of("Отмена"));
        if (title.length() > 100)
            return kb(chat, "Название слишком длинное (макс. 100 символов).", List.of("Отмена"));

        awaitingName.remove(chat);
        awaitingModeChoice.add(chat);
        pendingTestNames.put(chat, title);

        return kb(chat, "Как хотите добавить вопросы?",
                List.of("Вручную", "Загрузить DOCX", "Отмена"));
    }

    private SendMessage handleModeChoice(Long chat, String text, User user) {
        switch (text.toLowerCase()) {
            case "вручную" -> {
                awaitingModeChoice.remove(chat);
                String title = pendingTestNames.get(chat);
                SendMessage first = manualTestCreator.start(chat, user, title);
                reset(chat);
                return first;
            }
            case "загрузить docx" -> {
                awaitingModeChoice.remove(chat);
                awaitingShowChoice.add(chat);
                return promptShowChoice(chat);
            }
            default -> {
                return kb(chat, "Пожалуйста, выберите кнопку:",
                        List.of("Вручную", "Загрузить DOCX", "Отмена"));
            }
        }
    }

    private SendMessage handleShowChoice(Long chat, String txt) {
        boolean show;
        switch (txt.toLowerCase()) {
            case "показывать ответы" -> show = true;
            case "скрыть ответы" -> show = false;
            default -> {
                return kb(chat, "Пожалуйста, выберите кнопку:",
                        List.of("Показывать ответы", "Скрыть ответы", "Отмена"));
            }
        }
        awaitingShowChoice.remove(chat);
        awaitingDocument.add(chat);
        pendingShowAnswers.put(chat, show);
        return promptDocument(chat);
    }

    private SendMessage handleDocument(Long chatId, Message msg, User user) {
        try {
            if (!msg.hasDocument())
                return error(chatId, "Пожалуйста, отправьте файл.");

            Document tgFile = msg.getDocument();
            String fileName = tgFile.getFileName().toLowerCase(Locale.ROOT);
            String ext = fileName.substring(fileName.lastIndexOf('.') + 1);

            if (!List.of("docx", "pdf", "txt").contains(ext))
                return error(chatId, "Поддерживаются DOCX, PDF, TXT.");

            if (tgFile.getFileSize() > 20_000_000)
                return error(chatId, "Файл слишком большой (макс. 20 МБ).");

            String title = pendingTestNames.get(chatId);
            Boolean show = pendingShowAnswers.get(chatId);
            if (title == null || show == null)
                return error(chatId, "Данные создания утеряны. Начните заново.");

            File tmp = null;
            try {
                tmp = downloadFile(tgFile.getFileId(), ext);
                List<String> lines = extractLines(tmp, ext);
                List<Question> qs = parseQuestions(lines);
                if (qs.isEmpty())
                    return error(chatId, "Не удалось найти вопросы. Проверьте файл.");

                Test test = new Test();
                test.setTitle(title);
                test.setDescription("Создано: " + user.getUsername());
                test.setShowAnswers(show);
                testCreationService.createTest(user, test, qs);

                return success(chatId,
                        String.format("✅ Тест «%s» создан! Вопросов: %d", title, qs.size()));
            } finally {
                if (tmp != null && tmp.exists()) tmp.delete();
                reset(chatId);
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке файла", e);
            return error(chatId, "Ошибка обработки файла.");
        }
    }

    private List<String> extractLines(File file, String ext) throws Exception {
        String raw;
        switch (ext) {
            case "docx" -> {
                try (XWPFDocument d = new XWPFDocument(new FileInputStream(file))) {
                    raw = d.getParagraphs().stream()
                            .map(p -> p.getText() + "\n")
                            .collect(Collectors.joining());
                }
            }
            case "pdf" -> {
                try (PDDocument pdf = PDDocument.load(file)) {
                    raw = new PDFTextStripper().getText(pdf);
                }
            }
            case "txt" -> raw = Files.readString(file.toPath());
            default -> throw new IllegalArgumentException("Формат не поддерживается: " + ext);
        }
        return Arrays.stream(raw.split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static boolean isCorrectMarker(String s) {
        String l = s.toLowerCase(Locale.ROOT);
        return l.contains("+") || l.contains("правильн") || l.startsWith("ответ");
    }

    private List<Question> parseQuestions(List<String> lines) {
        List<Question> result = new ArrayList<>();
        Question curQ = null;
        List<AnswerOption> opts = new ArrayList<>();

        for (String ln : lines) {
            if (ln.endsWith("?")) {
                if (curQ != null) {
                    if (opts.stream().noneMatch(AnswerOption::getIsCorrect) && !opts.isEmpty())
                        opts.get(0).setIsCorrect(true);
                    curQ.getAnswerOptions().addAll(opts);
                    result.add(curQ);
                }
                curQ = new Question();
                curQ.setText(ln);
                opts = new ArrayList<>();
                continue;
            }

            if (curQ == null) continue;

            if (ln.toLowerCase(Locale.ROOT).matches("^(ответ|правильн).*\\d+")) {
                Matcher m = Pattern.compile("(\\d+)").matcher(ln);
                if (m.find()) {
                    int idx = Integer.parseInt(m.group(1)) - 1;
                    if (idx >= 0 && idx < opts.size()) opts.get(idx).setIsCorrect(true);
                }
                if (opts.stream().noneMatch(AnswerOption::getIsCorrect) && !opts.isEmpty())
                    opts.get(0).setIsCorrect(true);
                curQ.getAnswerOptions().addAll(opts);
                result.add(curQ);
                curQ = null;
                opts = new ArrayList<>();
                continue;
            }

            String clean = ln.replaceFirst("^[\\p{L}\\p{N}]+[).\\s-]+", "")
                    .replaceAll("(?i)правильн.*|(?i)ответ.*|\\+", "")
                    .trim();
            if (clean.isEmpty()) continue;

            AnswerOption opt = new AnswerOption();
            opt.setText(clean);
            opt.setOptionNumber(opts.size() + 1);
            opt.setIsCorrect(isCorrectMarker(ln));
            opts.add(opt);

            if (opt.getIsCorrect()) {
                curQ.getAnswerOptions().addAll(opts);
                result.add(curQ);
                curQ = null;
                opts = new ArrayList<>();
            }
        }

        if (curQ != null) {
            if (opts.stream().noneMatch(AnswerOption::getIsCorrect) && !opts.isEmpty())
                opts.get(0).setIsCorrect(true);
            curQ.getAnswerOptions().addAll(opts);
            result.add(curQ);
        }
        return result;
    }

    private SendMessage promptShowChoice(Long c) {
        return kb(c, "Показывать участникам правильные ответы после прохождения?",
                List.of("Показывать ответы", "Скрыть ответы", "Отмена"));
    }

    private SendMessage promptDocument(Long c) {
        return kb(c, "Имя сохранено. Загрузите файл с вопросами.\n\n" + RULES,
                List.of("Отмена"));
    }

    private SendMessage cancel(Long chat, User user) {
        reset(chat);
        ReplyKeyboardMarkup mainKb = new ReplyKeyboardMarkup();
        mainKb.setResizeKeyboard(true);
        mainKb.setKeyboard(List.of(
                kRow("Создать тест", "Пройти тест"),
                kRow("Мой профиль", "Мои тесты", "Выйти из аккаунта")
        ));
        return SendMessage.builder()
                .chatId(chat.toString())
                .text("❌ Создание теста отменено.\n\n👋 Привет, " + user.getFullName() + "!")
                .replyMarkup(mainKb)
                .build();
    }

    private SendMessage success(Long c, String t) {
        return kb(c, t, List.of("Главное меню"));
    }

    private SendMessage error(Long c, String t) {
        log.error(t);
        return new SendMessage(c.toString(), "⚠️ " + t);
    }

    private SendMessage kb(Long chat, String text, List<String> btns) {
        KeyboardRow row = new KeyboardRow();
        btns.forEach(b -> row.add(new KeyboardButton(b)));
        ReplyKeyboardMarkup mk = new ReplyKeyboardMarkup();
        mk.setResizeKeyboard(true);
        mk.setKeyboard(List.of(row));
        return SendMessage.builder()
                .chatId(chat.toString())
                .text(text)
                .replyMarkup(mk)
                .build();
    }

    private void reset(Long chat) {
        awaitingName.remove(chat);
        awaitingModeChoice.remove(chat);
        awaitingShowChoice.remove(chat);
        awaitingDocument.remove(chat);
        pendingTestNames.remove(chat);
        pendingShowAnswers.remove(chat);
    }

    private File downloadFile(String fileId, String ext) throws IOException {
        String getUrl = "https://api.telegram.org/bot" + botToken + "/getFile?file_id=" + fileId;
        String body = new BufferedReader(new InputStreamReader(new URL(getUrl).openStream()))
                .lines().collect(Collectors.joining());
        Matcher m = Pattern.compile("\"file_path\":\"([^\"]+)\"").matcher(body);
        if (!m.find()) throw new IOException("file_path not found");
        String fileUrl = "https://api.telegram.org/file/bot" + botToken + "/" + m.group(1);
        File tmp = File.createTempFile("upload_", "." + ext);
        try (InputStream in = new URL(fileUrl).openStream()) {
            Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return tmp;
    }

    @Transactional
    public String getUserCreatedTestsInfo(User u) {
        List<Test> tests = testService.getTestsCreatedByUser(u);
        if (tests.isEmpty()) return "Вы ещё не создали ни одного теста.\n";
        StringBuilder sb = new StringBuilder();
        for (Test t : tests) {
            long cnt = testService.getQuestionCount(t);
            sb.append("• ").append(t.getTitle())
                    .append(" — вопросов: ").append(cnt).append("\n");
        }
        return sb.toString();
    }
}
