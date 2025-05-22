package com.example.TelegramTestBot.bot;

import com.example.TelegramTestBot.controller.*;
import com.example.TelegramTestBot.model.*;
import com.example.TelegramTestBot.model.User;
import com.example.TelegramTestBot.service.TestService;
import com.example.TelegramTestBot.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class TestBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final String botToken;
    private final ManualTestCreatorController manualCreator;
    private final AuthController            authController;
    private final TestCreatorController     creatorController;
    private final TestParticipantController participantController;
    private final TestEditorController      editorController;
    private final TestService               testService;
    private final UserService               userService;
    private final SessionService            sessionService;
    private final ProfileController profileController;
    private final Map<Long, Test> pendingTestActions = new ConcurrentHashMap<>();
    private final Set<Long> awaitingTestSelection   = ConcurrentHashMap.newKeySet();
    private final Set<Long> awaitingUserSelection   = ConcurrentHashMap.newKeySet();

    public TestBot(
            @Value("${telegram.bot.username}") String botUsername,
            @Value("${telegram.bot.token}")    String botToken, ManualTestCreatorController manualCreator,
            AuthController               authController,
            @Lazy TestCreatorController  creatorController,
            @Lazy TestParticipantController participantController,
            @Lazy TestEditorController   editorController,
            TestService                  testService,
            UserService                  userService,
            SessionService               sessionService, ProfileController profileController) {

        this.botUsername         = botUsername;
        this.botToken            = botToken;
        this.manualCreator = manualCreator;
        this.authController      = authController;
        this.creatorController   = creatorController;
        this.participantController = participantController;
        this.editorController    = editorController;
        this.testService         = testService;
        this.userService         = userService;
        this.sessionService      = sessionService;
        this.profileController = profileController;
    }
    @Override public String getBotUsername() { return botUsername; }
    @Override public String getBotToken()    { return botToken;    }
    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage()) return;

        Message msg = update.getMessage();
        long chatId = msg.getChatId();
        String text = msg.hasText() ? msg.getText().trim() : "";

        // 1) Обработка входа/регистрации (AuthController)
        SendMessage authResponse = authController.handleAuth(update);
        if (authResponse != null) {
            executeMessage(authResponse);
            // После успешного входа/регистрации показываем меню
            String respText = authResponse.getText().toLowerCase();
            if (respText.contains("успешно вошли") || respText.contains("регистрация завершена")) {
                sessionService.getSession(chatId)
                        .ifPresent(s -> sendMainMenu(chatId, s.getUser()));
            }
            return;
        }

        Optional<UserSession> maybeSession = sessionService.getSession(chatId);
        if (maybeSession.isEmpty()) {
            if (msg.hasText()) {
                switch (text.toLowerCase()) {
                    case "войти" -> executeMessage(authController.startLoginProcess(chatId));
                    case "зарегистрироваться" -> executeMessage(authController.startRegistrationProcess(chatId));
                    default -> sendWelcome(chatId);
                }
            } else {
                sendWelcome(chatId);
            }
            return;
        }

        User user = maybeSession.get().getUser();
        BotApiMethod<?> participantResp = participantController.handleUpdate(update, user);
        if (participantResp != null) {
            if (participantResp instanceof SendMessage) {
                executeMessage((SendMessage) participantResp);
            } else {
                try {
                    execute((BotApiMethod<?>) participantResp);
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
            }
            return;
        }
        SendMessage manual = manualCreator.handle(update, user);
        if (manual != null) {
            executeMessage(manual);
            return;
        }
        SendMessage creationResponse = creatorController.handleUpdate(update, user);
        if (creationResponse != null) {
            executeMessage(creationResponse);
            return;
        }
        if (profileController.isInProfileMenu(chatId)) {
            executeMessage(profileController.handleProfileMenu(update, user));
            return;
        }
        if (profileController.isInProfileEdit(chatId)) {
            executeMessage(profileController.handleProfileEdit(update, user));
            return;
        }
        if (editorController.isInside(chatId)) {
            SendMessage editorResponse = editorController.handle(update);
            if (editorResponse != null) {
                executeMessage(editorResponse);
            }
            return;
        }
        if (awaitingTestSelection.contains(chatId) && msg.hasText()) {
            awaitingTestSelection.remove(chatId);    // <— убираем режим выбора теста
            onTestChosen(chatId, user, text);
            return;
        }
        if (awaitingUserSelection.contains(chatId) && update.getMessage().hasText()) {
            awaitingUserSelection.remove(chatId);
            showDetailedStatsFor(chatId, pendingTestActions.get(chatId), text);
            return;
        }
        if (pendingTestActions.containsKey(chatId) && msg.hasText()) {
            handleTestActions(chatId, user, text);
            return;
        }
        if (msg.hasText()) {
            switch (text.toLowerCase()) {
                case "мои тесты" -> showUserTests(chatId, user);
                case "создать тест" -> {
                    creatorController.startTestCreation(chatId);
                }
                case "мой профиль" -> {
                    SendMessage m = profileController.startProfileMenu(chatId, user);
                    executeMessage(m);
                }
                case "выйти из аккаунта" -> {
                    sessionService.invalidateSession(chatId);

                    pendingTestActions.remove(chatId);
                    awaitingTestSelection.remove(chatId);
                    awaitingUserSelection.remove(chatId);

                    sendWelcome(chatId);
                }
                default -> sendMainMenu(chatId, user);
            }
        }
    }
    private void onTestChosen(long chatId, User u, String btnText) {
        String pureTitle = btnText.replace(" > Редактор", "");
        testService.findByTitleAndUser(pureTitle, u)
                .ifPresentOrElse(test -> {
                    pendingTestActions.put(chatId, test);
                    showTestActions(chatId, test);
                }, () -> executeMessage(
                        new SendMessage(String.valueOf(chatId), "Тест не найден.")
                ));
    }
    private void handleTestActions(long chat, User u, String txt) {
        Test t = pendingTestActions.get(chat);
        switch (txt.toLowerCase()) {
            case "удалить тест"       -> {
                testService.deleteTest(t);
                pendingTestActions.remove(chat);
                executeMessage(new SendMessage(chat+"","✅ Тест удалён."));
                sendMainMenu(chat, u);
            }
            case "изменить тест"      -> editTest(chat, t);
            case "статистика по тесту"-> showTestStatistics(chat, t);
            case "главное меню"       -> { pendingTestActions.remove(chat); sendMainMenu(chat, u); }
            default                   -> executeMessage(new SendMessage(chat+"",
                    "Неизвестная команда."));
        }
    }
    private void editTest(long chat, Test t) {
        SendMessage m = editorController.startEditor(chat, t);
        executeMessage(m);
    }
    private void showTestStatistics(long chatId, Test test) {
        List<UserResult> parts = testService.getTestParticipants(test);
        if (parts.isEmpty()) {
            executeMessage(new SendMessage(String.valueOf(chatId),
                    "Ещё никто не прошёл этот тест."));
            return;
        }

        StringBuilder body = new StringBuilder("*Результаты — ")
                .append(test.getTitle()).append("*\n");

        List<KeyboardRow> rows = new ArrayList<>();

        for (UserResult ur : parts) {

            String display   = ur.getDisplayName();
            String cleanName = display.replace(" (автор)", "");

            body.append(display)
                    .append(" — ").append(ur.getScore())
                    .append(" из ").append(ur.getMaxScore())
                    .append("\n");

            rows.add(kRow(cleanName));
        }

        rows.add(kRow("Главное меню"));

        SendMessage msg = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(body.toString())
                .parseMode("Markdown")
                .replyMarkup(keyboard(rows))
                .build();

        executeMessage(msg);
        awaitingUserSelection.add(chatId);
    }

    private void showDetailedStatsFor(long chatId, Test test, String displayName) {
        // Ищем по fullName или по username — зависит от того, что у вас в UserService
        Optional<User> userOpt = userService.findByFullName(displayName)
                .or(() -> userService.findByUsername(displayName));
        if (userOpt.isEmpty()) {
            executeMessage(new SendMessage(String.valueOf(chatId),
                    "Пользователь «" + displayName + "» не найден."));
            return;
        }
        User user = userOpt.get();

        // Берём общий результат
        List<TestResult> trOpt = testService.getResultsByTestAndUser(test, user);
        if (trOpt.isEmpty()) {
            executeMessage(new SendMessage(String.valueOf(chatId),
                    "У «" + displayName + "» нет результатов по «" + test.getTitle() + "»."));
            return;
        }
        TestResult tr = trOpt.get(0);

        // Достаём подробности
        List<DetailedResult> details = testService.getDetailedResults(tr);

        StringBuilder sb = new StringBuilder("*Детализация «")
                .append(test.getTitle()).append("» — ").append(displayName).append("*\n\n");
        for (DetailedResult dr : details) {
            sb.append(dr.getQuestionIndex()).append(". ")
                    .append(dr.getQuestion().getText()).append("\n")
                    .append("Ваш ответ: ").append(dr.getUserAnswer()).append("\n")
                    .append("Правильный: ").append(dr.getCorrectAnswer()).append("\n")
                    .append("Баллы: ").append(dr.getPoints()).append("\n\n");
        }

        SendMessage detailMsg = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(sb.toString())
                .parseMode("Markdown")
                .replyMarkup(keyboard(kRow("Главное меню")))
                .build();
        executeMessage(detailMsg);
    }
    private void sendWelcome(Long chat) {
        ReplyKeyboardMarkup kb = keyboard(kRow("Войти","Зарегистрироваться"));
        SendMessage m = new SendMessage(chat+"","Добро пожаловать!");
        m.setReplyMarkup(kb);
        executeMessage(m);
    }
    private void sendMainMenu(Long chat, User u) {
        ReplyKeyboardMarkup kb = keyboard(
                kRow("Создать тест","Пройти тест"),
                kRow("Мой профиль","Мои тесты","Выйти из аккаунта"));
        SendMessage m = new SendMessage(chat+"","👋 Привет, "+u.getFullName()+"!");
        m.setReplyMarkup(kb);
        executeMessage(m);
    }
    private void showUserTests(long chatId, User u) {
        List<Test> tests = testService.getTestsCreatedByUser(u);
        if (tests.isEmpty()) {
            executeMessage(new SendMessage(String.valueOf(chatId),
                    "У вас пока нет созданных тестов."));
            return;
        }

        // 1) Текстовая сводка
        StringBuilder body = new StringBuilder("*Ваши тесты:*\n\n");
        for (Test t : tests) {
            long count = testService.getQuestionCount(t);
            body.append("• ")
                    .append(t.getTitle())
                    .append(" — вопросов: ")
                    .append(count)
                    .append("\n");
        }
        executeMessage(SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(body.toString())
                .parseMode("Markdown")
                .build());

        // 2) Кнопки
        List<KeyboardRow> rows = new ArrayList<>();
        for (Test t : tests) {
            rows.add(kRow(t.getTitle() + " > Редактор"));
        }
        rows.add(kRow("Главное меню"));

        ReplyKeyboardMarkup kb = keyboard(rows);
        SendMessage ask = new SendMessage(String.valueOf(chatId),
                "Выберите тест для редактирования:");
        ask.setReplyMarkup(kb);
        executeMessage(ask);

        awaitingTestSelection.add(chatId);
    }
    private void showTestActions(long chat, Test t) {
        ReplyKeyboardMarkup kb = keyboard(
                kRow("Изменить тест","Статистика по тесту"),
                kRow("Удалить тест"),
                kRow("Главное меню"));
        SendMessage m = SendMessage.builder()
                .chatId(chat+"")
                .text("Тест: *"+t.getTitle()+"*")
                .parseMode("Markdown")
                .replyMarkup(kb)
                .build();
        executeMessage(m);
    }
    private static KeyboardRow kRow(String... lbl) {
        KeyboardRow r = new KeyboardRow();
        Arrays.stream(lbl).forEach(r::add);
        return r;
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
    public void executeMessage(SendMessage m) {
        try { execute(m); }
        catch (TelegramApiException e) { log.error("Send failed", e); }
    }
}
