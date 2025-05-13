package com.example.TelegramTestBot.controller;

import com.example.TelegramTestBot.bot.TestBot;
import com.example.TelegramTestBot.model.*;
import com.example.TelegramTestBot.service.TestCreationService;
import com.example.TelegramTestBot.service.TestResultService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
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

@Slf4j
@Component
public class TestCreatorController {

    /* ─────────────────────────── DI ────────────────────────── */

    private final TestCreationService testCreationService;
    private final TestResultService   testResultService;

    @Value("${telegram.bot.token}")          private String botToken;
    private TestBot                          testBot;       // ленивое внедрение, чтобы обойти цикл

    /* ─────────────────────────── state ─────────────────────── */

    private final Map<Long,String>  pendingTestNames  = new ConcurrentHashMap<>();
    private final Set<Long>         awaitingName      = ConcurrentHashMap.newKeySet();
    private final Set<Long>         awaitingDocument  = ConcurrentHashMap.newKeySet();

    /* ─────────────────────────── ctor ─────────────────────── */

    @Autowired
    public TestCreatorController(TestCreationService testCreationService,
                                 TestResultService   testResultService) {
        this.testCreationService = testCreationService;
        this.testResultService   = testResultService;
    }
    @Autowired
    public void setTestBot(@Lazy TestBot testBot) { this.testBot = testBot; }

    /* ═════════════════════ public API ═════════════════════ */

    public void startTestCreation(Long chat) {
        awaitingName.add(chat);
        testBot.executeMessage(promptTestName(chat));
    }

    /** Главный обработчик входящих апдейтов, пока пользователь создаёт тест */
    public SendMessage handleUpdate(Update up, User user) {
        if (!up.hasMessage()) return null;

        Message msg   = up.getMessage();
        long    chat  = msg.getChatId();
        String  text  = msg.hasText() ? msg.getText().trim() : "";

        /* 0) отмена в любой момент */
        if ("Отмена".equalsIgnoreCase(text)) return cancel(chat);

        /* 1) ждём название */
        if (awaitingName.contains(chat)) return handleTitle(chat, text);

        /* 2) ждём файл */
        if (awaitingDocument.contains(chat)) return handleDocument(chat, msg, user);

        return null;        // не наше сообщение
    }

    /* ───────────────────────── приватная логика ───────────────────────── */

    private SendMessage handleTitle(Long chat, String title) {
        if (title.isBlank())
            return promptTestName(chat);

        if (title.length() > 100)
            return simple(chat, "Название слишком длинное (макс. 100 символов).");

        awaitingName.remove(chat);
        awaitingDocument.add(chat);
        pendingTestNames.put(chat, title);

        log.info("Пользователь указал имя теста «{}»", title);
        return promptDocument(chat);
    }

    private SendMessage handleDocument(Long chat, Message msg, User user) {
        if (!msg.hasDocument())
            return simple(chat,"Пришлите DOCX-файл с вопросами.");

        Document doc = msg.getDocument();
        if (!doc.getFileName().toLowerCase().endsWith(".docx"))
            return simple(chat,"Нужен именно *.docx* файл.");

        if (doc.getFileSize() > 20_000_000)
            return simple(chat,"Файл больше 20 МБ.");

        String testName = pendingTestNames.get(chat);

        try {
            /* 1) скачиваем и парсим */
            File tmp               = downloadFile(doc.getFileId());
            List<Question> qList   = parseDocxFile(tmp);
            tmp.delete();

            if (qList.isEmpty())
                return simple(chat,"Не удалось извлечь вопросы. Проверьте формат документа.");

            /* 2) сохраняем тест */
            Test t = new Test();
            t.setTitle(testName);
            t.setDescription("Создано: "+user.getUsername());
            testCreationService.createTest(user,t,qList);

            reset(chat);
            return success(chat,"✅ Тест «%s» создан! Вопросов: %d".formatted(testName, qList.size()));

        } catch (IOException e) {
            log.error("Ошибка обработки файла",e);
            reset(chat);
            return simple(chat,"⚠️ Не удалось обработать файл: "+e.getMessage());
        }
    }

    /* ──────────────── helpers / UI ─────────────── */

    private SendMessage promptTestName(Long c) {
        return kb(c, "Введите имя теста:", List.of("Отмена"));
    }
    private SendMessage promptDocument(Long c) {
        return kb(c,
                "Название сохранено. Теперь загрузите DOCX с вопросами.",
                List.of("Отмена"));
    }
    private SendMessage simple  (Long c, String t){ return new SendMessage(c.toString(), t); }
    private SendMessage success (Long c, String t){ return kb(c, t, List.of("Главное меню")); }

    private SendMessage kb(Long chat, String text, List<String> buttons) {

        /* строим одну строку клавиатуры */
        KeyboardRow row = new KeyboardRow();
        for (String caption : buttons) {
            row.add(new KeyboardButton(caption));     // ← оборачиваем строку в кнопку
        }

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setKeyboard(List.of(row));

        return SendMessage.builder()
                .chatId(chat.toString())
                .text(text)
                .replyMarkup(markup)
                .build();
    }

    private SendMessage cancel(Long chat){
        reset(chat);
        return kb(chat, "Создание теста отменено.", List.of("Главное меню"));
    }

    private void reset(Long chat){
        awaitingName.remove(chat);
        awaitingDocument.remove(chat);
        pendingTestNames.remove(chat);
    }

    /* ─────────────── служебные ─────────────── */

    private File downloadFile(String fileId) throws IOException {
        String getUrl  = "https://api.telegram.org/bot"+botToken+"/getFile?file_id="+fileId;
        String body    = new BufferedReader(new InputStreamReader(new URL(getUrl).openStream()))
                .lines().collect(Collectors.joining());
        Matcher m = Pattern.compile("\"file_path\":\"([^\"]+)\"").matcher(body);
        if (!m.find()) throw new IOException("file_path not found");
        String fileUrl = "https://api.telegram.org/file/bot"+botToken+"/"+m.group(1);

        File tmp = File.createTempFile("upload_",".docx");
        try (InputStream in = new URL(fileUrl).openStream()) {
            Files.copy(in,tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return tmp;
    }

    private List<Question> parseDocxFile(File f) throws IOException {
        List<Question> qList = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(f))) {

            List<String> lines = doc.getParagraphs().stream()
                    .map(XWPFParagraph::getText).map(String::trim)
                    .filter(s -> !s.isEmpty()).toList();

            Question curQ = null;
            List<AnswerOption> opts = null;
            StringBuilder qTxt = new StringBuilder();

            for (String ln : lines) {
                if (ln.matches("^\\d+\\.\\s*.*\\?$")) {                 // новый вопрос
                    if (curQ!=null) {
                        curQ.setText(qTxt.toString().trim());
                        curQ.setAnswerOptions(opts);
                        qList.add(curQ);
                    }
                    curQ  = new Question();
                    opts  = new ArrayList<>();
                    qTxt.setLength(0);
                    qTxt.append(ln.replaceFirst("^\\d+\\.\\s*",""));
                    continue;
                }
                if (ln.matches("^\\d+\\.\\s+.*")) {                      // вариант ответа
                    AnswerOption ao = new AnswerOption();
                    ao.setText(ln.replaceFirst("^\\d+\\.\\s+",""));
                    ao.setOptionNumber(opts.size()+1);
                    ao.setIsCorrect(false);
                    opts.add(ao);
                    continue;
                }
                if (ln.matches("^(Ответ|Правильный).*\\d+.*")) {        // строка "Ответ X"
                    int idx = Integer.parseInt(ln.replaceAll("\\D+",""));
                    if (idx>=1 && idx<=opts.size()) opts.get(idx-1).setIsCorrect(true);
                    continue;
                }
                qTxt.append(' ').append(ln);                            // хвост вопроса
            }
            if (curQ!=null){
                curQ.setText(qTxt.toString().trim());
                curQ.setAnswerOptions(opts);
                qList.add(curQ);
            }
        }
        return qList;
    }

    /* ─────────────── статистика пользователя ─────────────── */

    @Transactional
    public String getUserCreatedTestsInfo(User u){
        List<TestResult> res = testResultService.getCompletedTestsByUser(u.getId());
        if (res.isEmpty()) return "❌ Вы ещё не прошли ни одного теста.";

        DecimalFormat df = new DecimalFormat("#.#");
        StringBuilder sb = new StringBuilder();
        for (TestResult r:res){
            double pct = r.getScore()*100.0/r.getMaxScore();
            sb.append("• ").append(r.getTest().getTitle())
                    .append(" — ").append(r.getScore()).append('/')
                    .append(r.getMaxScore()).append(" (")
                    .append(df.format(pct)).append("%)\n");
        }
        return sb.toString();
    }
}
