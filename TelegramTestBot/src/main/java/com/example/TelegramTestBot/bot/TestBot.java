package com.example.TelegramTestBot.bot;

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
            AuthController            authController,
            @Lazy TestCreatorController   creatorController,
            @Lazy TestParticipantController participantController,
            TestService               testService,
            UserService               userService,
            SessionService            sessionService) {

        this.botUsername         = botUsername;
        this.botToken            = botToken;
        this.authController      = authController;
        this.creatorController   = creatorController;
        this.participantController = participantController;
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

        try {
            /* 1) обрабатываем только сообщения */
            if (!update.hasMessage()) return;

            Message msg  = update.getMessage();
            long    chat = msg.getChatId();
            String  txt  = msg.hasText() ? msg.getText().trim() : "";

            /* 2) auth / регистрация */
            SendMessage authAns = authController.handleAuth(update);
            if (authAns != null) {
                executeMessage(authAns);
                if (authAns.getText().startsWith("✅"))
                    sessionService.getSession(chat)
                            .ifPresent(s -> sendMainMenu(chat, s.getUser()));
                return;
            }

            /* 3) пользователь не авторизован → показ приветствия */
            if (sessionService.getSession(chat).isEmpty()) {
                if (txt.equalsIgnoreCase("/login") || txt.equalsIgnoreCase("войти"))
                    executeMessage(authController.startLoginProcess(chat));
                else if (txt.equalsIgnoreCase("/registr") || txt.equalsIgnoreCase("зарегистрироваться"))
                    executeMessage(authController.startRegistrationProcess(chat));
                else
                    sendWelcome(chat);
                return;
            }

            /* 4) пользователь авторизован */
            UserSession us = sessionService.getSession(chat).orElseThrow();
            User user      = us.getUser();

            /* 4.1) выход */
            if (txt.equalsIgnoreCase("выйти из аккаунта")) {
                sessionService.removeSession(chat);
                executeMessage(new SendMessage(String.valueOf(chat), "✅ Вы вышли."));
                sendWelcome(chat);
                return;
            }

            /* 4.2) «главное меню» / «вернуться…» */
            if (txt.equalsIgnoreCase("главное меню") || txt.equalsIgnoreCase("вернуться в меню")) {
                sendMainMenu(chat, user);
                return;
            }

            /* 4.3) «мой профиль» */
            if (txt.equalsIgnoreCase("мой профиль")) {
                showProfile(chat, user);
                return;
            }

            /* 4.4) «мои тесты» */
            if (txt.equalsIgnoreCase("мои тесты")) {
                showUserTests(chat, user);
                return;
            }

            /* 4.5) мы НА этапе выбора теста после «мои тесты» */
            if (awaitingTestSelection.remove(chat)) {
                onTestChosen(chat, user, txt);
                return;
            }

            /* 4.6) мы НА этапе выбора пользователя в статистике */
            if (awaitingUserSelection.remove(chat)) {
                pendingTestActions.computeIfPresent(chat,
                        (c, t) -> { showDetailedStatsFor(chat, t, txt); return t; });
                return;
            }

            /* 4.7) внутри редактора конкретного теста */
            if (pendingTestActions.containsKey(chat)) {
                handleTestActions(chat, user, txt);
                return;
            }

            /* 4.8) создание теста (ожидание имя / docx) */
            if (creatorController.isAwaitingTestName(chat) ||
                    creatorController.isAwaitingDocument(chat) ||
                    msg.hasDocument()) {
                SendMessage r = creatorController.handleUpdate(update, user);
                if (r != null) executeMessage(r);
                return;
            }

            /* 4.9) прохождение тестов */
            BotApiMethod<?> p = participantController.handleUpdate(update, user);
            if (p != null) { executeMessage((SendMessage) p); return; }

            /* 4.10) команды «создать» / «пройти» из главного меню */
            if (txt.equalsIgnoreCase("создать тест")) {
                creatorController.startTestCreation(chat, user);  return;
            }
            if (txt.equalsIgnoreCase("пройти тест")) {
                SendMessage r = (SendMessage) participantController.handleUpdate(update, user);
                if (r != null) executeMessage(r); return;
            }

            /* fallback */
            executeMessage(new SendMessage(String.valueOf(chat),
                    "Не понимаю команду. Выберите пункт меню или нажмите «Главное меню»."));

        } catch (Exception e) {
            log.error("update fail", e);
            if (update.hasMessage()) sendError(update.getMessage().getChatId());
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


    /* ――― шаг 1: вывод списка тестов ――― */
    private void showUserTests(Long chat, User u) {
        List<Test> list = testService.getTestsCreatedByUser(u);
        if (list.isEmpty()) {
            executeMessage(new SendMessage(String.valueOf(chat),
                    "У вас пока нет созданных тестов."));
            return;
        }
        List<KeyboardRow> rows = new ArrayList<>();
        for (Test t : list) {
            KeyboardRow r = new KeyboardRow();
            r.add(t.getTitle() + " ▸ Редактор");
            rows.add(r);
        }
        rows.add(kRow("Главное меню"));
        ReplyKeyboardMarkup kb = keyboard(rows);
        SendMessage msg = new SendMessage(chat.toString(), "Выберите тест:");
        msg.setReplyMarkup(kb);          // setReplyMarkup вернёт void — это нормально
        executeMessage(msg);
        awaitingTestSelection.add(chat);
    }

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
