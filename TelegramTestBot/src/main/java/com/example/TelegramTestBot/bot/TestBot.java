package com.example.TelegramTestBot.bot;
import com.example.TelegramTestBot.controller.TestEditorController;

import com.example.TelegramTestBot.controller.AuthController;
import com.example.TelegramTestBot.controller.TestCreatorController;
import com.example.TelegramTestBot.controller.TestParticipantController;
import com.example.TelegramTestBot.model.*;
import com.example.TelegramTestBot.service.TestService;
import com.example.TelegramTestBot.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class TestBot extends TelegramLongPollingBot {

    /* ─────────────────────────── DI ────────────────────────── */

    private final String botUsername;
    private final String botToken;

    private final AuthController            authController;
    private final TestCreatorController     creatorController;
    private final TestParticipantController participantController;
    private final TestService               testService;
    private final UserService               userService;
    private final SessionService            sessionService;
    private final TestEditorController editorController;
    /* ─────────────────────────── state ─────────────────────── */

    /** чат → тест, для которого сейчас показываем редактор                   */
    private final Map<Long, Test> pendingTestActions = new ConcurrentHashMap<>();
    /** чаты, где ждём выбора теста (список после «Мои тесты»)                 */
    private final Set<Long> awaitingTestSelection   = ConcurrentHashMap.newKeySet();
    /** чаты, где ждём выбора пользователя в статистике                       */
    private final Set<Long> awaitingUserSelection   = ConcurrentHashMap.newKeySet();

    /* ─────────────────────────── ctor ─────────────────────── */

    public TestBot(
            @Value("${telegram.bot.username}") String botUsername,
            @Value("${telegram.bot.token}")    String botToken,
            AuthController               authController,
            @Lazy TestCreatorController  creatorController,
            @Lazy TestParticipantController participantController,
            @Lazy TestEditorController   editorController,          // 👈 НОВОЕ
            TestService                  testService,
            UserService                  userService,
            SessionService               sessionService) {

        this.botUsername         = botUsername;
        this.botToken            = botToken;
        this.authController      = authController;
        this.creatorController   = creatorController;
        this.participantController = participantController;
        this.editorController    = editorController;               // 👈 НОВОЕ
        this.testService         = testService;
        this.userService         = userService;
        this.sessionService      = sessionService;
    }


    /* ─────────────────────────── TG API idents ────────────── */

    @Override public String getBotUsername() { return botUsername; }
    @Override public String getBotToken()    { return botToken;    }

    /* ═════════════════════ main update handler ═════════════════════ */

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage()) return;

        Message msg   = update.getMessage();
        long    chat  = msg.getChatId();
        String  text  = msg.hasText() ? msg.getText().trim() : "";

        /* 1) авторизация / регистрация */
        SendMessage a = authController.handleAuth(update);
        if (a != null) { executeMessage(a); return; }

        /* 2) если пользователь ещё не залогинен */
        Optional<UserSession> maybe = sessionService.getSession(chat);
        if (maybe.isEmpty()) { sendWelcome(chat); return; }

        User user = maybe.get().getUser();

        /* 3) если мы внутри редактора ─ переадресуем контроллеру */
        if (editorController.isInside(chat)) {          // 👈 было isInsideEditor
            SendMessage ans = editorController.handle(update);   // 👈 было handleUpdate
            if (ans != null) executeMessage(ans);
            return;
        }


        /* 4) … дальше обычное меню бота … */
        switch (text.toLowerCase()) {
            case "мои тесты" -> showUserTests(chat, user);
            case "создать тест" -> creatorController.startTestCreation(chat);
            case "пройти тест"  -> executeMessage(
                    (SendMessage) participantController.handleUpdate(update,user));
            default            -> sendMainMenu(chat,user);
        }
    }

    /* ═════════════════════ UI helpers ═════════════════════ */

    private void sendWelcome(Long chat) {
        ReplyKeyboardMarkup kb = keyboard(kRow("Войти", "Зарегистрироваться"));
        SendMessage msg = new SendMessage(chat.toString(), "Добро пожаловать!");
        msg.setReplyMarkup(kb);          // setReplyMarkup вернёт void — это нормально
        executeMessage(msg);
    }

    private void sendMainMenu(Long chat, User u) {
        ReplyKeyboardMarkup kb = keyboard(
                kRow("Создать тест", "Пройти тест"),
                kRow("Мой профиль", "Мои тесты", "Выйти из аккаунта"));
        SendMessage msg = new SendMessage(chat.toString(), "👋 Привет, " + u.getFullName() + "!");
        msg.setReplyMarkup(kb);          // setReplyMarkup вернёт void — это нормально
        executeMessage(msg);
    }

    private void showProfile(long chat, User u) {
        // Получаем информацию о созданных пользователем тестах (например, это может быть вызов через TestService)
        String created = creatorController.getUserCreatedTestsInfo(u);

        // Получаем информацию о пройденных тестах
        List<TestResult> completedTests = testService.getCompletedTestsForUser(u);
        StringBuilder completedTestsInfo = new StringBuilder();

        if (completedTests.isEmpty()) {
            completedTestsInfo.append("Вы еще не прошли ни одного теста.");
        } else {
            completedTestsInfo.append("Пройденные тесты:\n");
            for (TestResult result : completedTests) {
                completedTestsInfo.append(result.getTest().getTitle())
                        .append(" — Баллы: ")
                        .append(result.getScore())
                        .append("\n");
            }
        }

        // Формируем тело сообщения
        String body = "👤 *Профиль*\n" +
                "Имя: "   + u.getFullName()  + "\n" +
                "Логин: " + u.getUsername()  + "\n\n" +
                "📊 Созданные тесты:\n" + created + "\n\n" +
                completedTestsInfo.toString();

        // Создаем объект SendMessage и включаем поддержку Markdown
        SendMessage msg = new SendMessage(String.valueOf(chat), body);
        msg.enableMarkdown(true);  // Включаем Markdown

        // Отправляем сообщение
        executeMessage(msg);
    }
    private void showUserTests(Long chat, User u) {

        List<Test> tests = testService.getTestsCreatedByUser(u);
        if (tests.isEmpty()) {
            executeMessage(new SendMessage(chat.toString(),
                    "У вас пока нет созданных тестов."));
            return;
        }

        /* 1) текстовая сводка */
        StringBuilder body = new StringBuilder("*Ваши тесты:*\n\n");
        for (Test t : tests) {
            body.append("• ").append(t.getTitle())
                    .append(" — вопросов: ").append(testService.getQuestionCount(t))
                    .append('\n');
        }
        executeMessage(SendMessage.builder()
                .chatId(chat.toString())
                .text(body.toString())
                .parseMode("Markdown")
                .build());

        /* 2) клавиатура */
        List<KeyboardRow> rows = new ArrayList<>();
        for (Test t : tests) rows.add(kRow(t.getTitle() + " ▸ Редактор"));
        rows.add(kRow("Главное меню"));

        ReplyKeyboardMarkup kb = keyboard(rows);
        SendMessage ask = new SendMessage(chat.toString(),
                "Выберите тест для редактирования:");
        ask.setReplyMarkup(kb);          // setReplyMarkup возвращает void
        executeMessage(ask);

        awaitingTestSelection.add(chat);
    }
    private KeyboardRow row(String b){ KeyboardRow r=new KeyboardRow(); r.add(b); return r; }


    /* ――― шаг 2: пользователь выбрал тест ――― */
    private void onTestChosen(long chat, User u, String btnText) {
        String pureTitle = btnText.replace(" ▸ Редактор", "");
        Optional<Test> opt = testService.findByTitleAndUser(pureTitle, u);
        if (opt.isEmpty()) {
            executeMessage(new SendMessage(String.valueOf(chat), "Тест не найден."));
            return;
        }
        Test t = opt.get();
        pendingTestActions.put(chat, t);
        showTestActions(chat, t);
    }
    private void showTestActions(long chat, Test t) {
        ReplyKeyboardMarkup kb = keyboard(
                kRow("Изменить тест", "Статистика по тесту"),
                kRow("Удалить тест"),
                kRow("Главное меню")
        );

        SendMessage msg = SendMessage.builder()
                .chatId(String.valueOf(chat))
                .text("Тест: *" + t.getTitle() + "*\nВыберите действие:")
                .parseMode("Markdown")      // аналог enableMarkdown(true)
                .replyMarkup(kb)
                .build();

        executeMessage(msg);
    }

    /* ――― шаг 4: обработка нажатий в меню действий ――― */
    private void handleTestActions(long chat, User u, String txt) {
        Test t = pendingTestActions.get(chat);
        switch (txt.toLowerCase()) {
            case "удалить тест" -> {
                testService.deleteTest(t);
                pendingTestActions.remove(chat);
                executeMessage(new SendMessage(String.valueOf(chat),
                        "✅ Тест удалён."));
                sendMainMenu(chat, u);
            }
            case "изменить тест" -> editTest(chat, t);
            case "статистика по тесту" -> showTestStatistics(chat, t);
            case "главное меню" -> { pendingTestActions.remove(chat); sendMainMenu(chat, u); }
            default -> executeMessage(new SendMessage(String.valueOf(chat),
                    "Неизвестная команда. Используйте кнопки снизу."));
        }
    }

    /* ――― редактор (шаг 4-а) ――― */
    private void editTest(Long chat, Test t) {
        ReplyKeyboardMarkup kb = keyboard(
                kRow("Добавить вопрос", "Изменить вопрос"),
                kRow("Удалить вопрос"),
                kRow("Добавить вариант ответа", "Удалить вариант ответа"),
                kRow("Изменить правильный ответ"),
                kRow("Главное меню"));
        SendMessage msg = new SendMessage(chat.toString(), "Редактирование теста «" + t.getTitle() + "»");
        msg.setReplyMarkup(kb);          // setReplyMarkup вернёт void — это нормально
        executeMessage(msg);
    }

    /* ――― статистика (шаг 4-b) ――― */
    private void showTestStatistics(long chat, Test t) {
        List<UserResult> parts = testService.getTestParticipants(t);
        if (parts.isEmpty()) {
            executeMessage(new SendMessage(String.valueOf(chat),
                    "Ещё никто не прошёл этот тест."));
            return;
        }

        StringBuilder body = new StringBuilder("*Результаты — " + t.getTitle() + "*\n");
        for (UserResult ur : parts) {
            body.append(ur.getUsername())
                    .append(ur.getUsername().equals(t.getCreator().getUsername()) ? " (автор)" : "")
                    .append(" — ").append(ur.getScore()).append('\n');
        }
        body.append("\nВыберите пользователя для детализации:");

        /* клавиатура с именами */
        List<KeyboardRow> rows = new ArrayList<>();
        for (UserResult ur : parts) rows.add(kRow(ur.getUsername()));
        rows.add(kRow("Главное меню"));

        SendMessage msg = SendMessage.builder()
                .chatId(String.valueOf(chat))
                .text(body.toString())
                .parseMode("Markdown")          // аналог enableMarkdown(true)
                .replyMarkup(keyboard(rows))
                .build();

        executeMessage(msg);
        awaitingUserSelection.add(chat);        // ждём выбор юзера
    }

    /* ――― деталка по определённому юзеру ――― */
    private void showDetailedStatsFor(long chat, Test t, String username) {
        Optional<User> uOpt = userService.findByUsername(username);
        if (uOpt.isEmpty()) {
            executeMessage(new SendMessage(String.valueOf(chat), "Пользователь не найден."));
            return;
        }
        User u = uOpt.get();
        List<TestResult> list = testService.getResultsByTestAndUser(t, u);
        if (list.isEmpty()) {
            executeMessage(new SendMessage(String.valueOf(chat), "Нет результатов."));
            return;
        }
        TestResult r = list.get(0);   // последний / лучший

        String msg = String.format(
                "*%s* — %s\nБаллов: %d / %d",
                t.getTitle(), u.getUsername(), r.getScore(), r.getMaxScore());

        SendMessage message = new SendMessage(String.valueOf(chat), msg);
        message.enableMarkdown(true); // Включаем Markdown для этого сообщения
        executeMessage(message);

    }

    /* ═══════════ misc helpers ═══════════ */

    private void sendError(long chat) {
        executeMessage(new SendMessage(String.valueOf(chat),
                "⚠️ Произошла ошибка, попробуйте позже."));
    }

    /** удобные фабрики клавиатур */
    private static KeyboardRow kRow(String... labels) {
        KeyboardRow row = new KeyboardRow();
        for (String label : labels) {
            row.add(new KeyboardButton(label));   // создаём кнопку и добавляем в строку
        }
        return row;
    }
    private static ReplyKeyboardMarkup keyboard(KeyboardRow... rows) {
        return keyboard(Arrays.asList(rows));
    }
    private static ReplyKeyboardMarkup keyboard(List<KeyboardRow> rows) {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        kb.setKeyboard(rows);
        return kb;
    }

    /* TG-SDK wrapper */
    public void executeMessage(SendMessage m) {
        try { execute(m); }
        catch (TelegramApiException e) { log.error("send fail", e); }
    }
}
