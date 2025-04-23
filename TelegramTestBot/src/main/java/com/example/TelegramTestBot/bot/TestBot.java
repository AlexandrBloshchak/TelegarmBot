package com.example.TelegramTestBot.bot;

import com.example.TelegramTestBot.controller.AuthController;
import com.example.TelegramTestBot.controller.TestCreatorController;
import com.example.TelegramTestBot.controller.TestParticipantController;
import com.example.TelegramTestBot.model.User;
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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class TestBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final String botToken;

    private final AuthController authController;
    private final TestParticipantController participantController;
    private final TestCreatorController   creatorController;
    private final SessionService          sessionService;

    public TestBot(@Value("${telegram.bot.username}") String botUsername,
                   @Value("${telegram.bot.token}")    String botToken,
                   AuthController authController,
                   @Lazy TestParticipantController participantController,
                   @Lazy TestCreatorController   creatorController,
                   SessionService sessionService) {

        this.botUsername         = botUsername;
        this.botToken            = botToken;
        this.authController      = authController;
        this.participantController = participantController;
        this.creatorController     = creatorController;
        this.sessionService        = sessionService;
    }

    /* ------------------------------------------------------------------ */

    @Override public String getBotUsername() { return botUsername; }
    @Override public String getBotToken()    { return botToken;    }

    /* ============================= MAIN ============================== */

    @Override
    public void onUpdateReceived(Update update) {

        try {
            /* 1. Нас интересуют только сообщения */
            if (!update.hasMessage()) return;

            Message msg   = update.getMessage();
            Long    chat  = msg.getChatId();
            String  text  = msg.hasText() ? msg.getText().trim() : "";

            /* 2. Обрабатываем аутентификацию / регистрацию */
            SendMessage authResp = authController.handleAuth(update);
            if (authResp != null) {
                executeMessage(authResp);

                /* 2.1. Успех → меню */
                if (authResp.getText().startsWith("✅")) {
                    Optional<UserSession> s = sessionService.getSession(chat);
                    if (s.isPresent()) sendMainMenu(chat, s.get().getUser());
                    else               sendError(chat);
                }
                return;
            }

            /* 3. Пользователь НЕ залогинен? → приветственный экран */
            if (sessionService.getSession(chat).isEmpty()) {
                if (text.equalsIgnoreCase("/login") || text.equalsIgnoreCase("войти")) {
                    executeMessage(authController.startLoginProcess(chat));
                } else if (text.equalsIgnoreCase("/registr") || text.equalsIgnoreCase("зарегистрироваться")) {
                    executeMessage(authController.startRegistrationProcess(chat));
                } else {
                    sendWelcome(chat);
                }
                return;
            }

            /* 4. Пользователь авторизован */
            UserSession session = sessionService.getSession(chat).orElseThrow();

            /* 4.1. Выход */
            if ("выйти из аккаунта".equalsIgnoreCase(text)) {
                sessionService.removeSession(chat);
                executeMessage(new SendMessage(chat.toString(), "✅ Вы вышли из аккаунта."));
                sendWelcome(chat);
                return;
            }

            /* 4.2. Дальнейшая логика */
            handleAuthenticated(update, session);

        } catch (Exception e) {
            log.error("Error processing update", e);
            if (update.hasMessage())
                sendError(update.getMessage().getChatId());
        }
    }

    /* ============================= AUTH USER ============================== */

    private void handleAuthenticated(Update update, UserSession session) {

        Long   chat = update.getMessage().getChatId();
        User   user = session.getUser();
        String text = update.getMessage().hasText() ? update.getMessage().getText().trim() : "";

        switch (text.toLowerCase()) {
            case "главное меню" -> { sendMainMenu(chat, user); return; }
            case "мой профиль"  -> { showProfile(chat, user); return; }
            case "создать тест" -> { creatorController.startTestCreation(chat, user); return; }
            case "пройти тест"  -> {
                BotApiMethod<?> resp = participantController.handleUpdate(update, user);
                if (resp != null) executeMessage((SendMessage) resp);
                return;
            }
        }

        /* --- Логика создания / прохождения тестов -------- */
        if (creatorController.isAwaitingTestName(chat) ||
                creatorController.isAwaitingDocument(chat) ||
                update.getMessage().hasDocument()) {

            SendMessage resp = creatorController.handleUpdate(update, user);
            if (resp != null) executeMessage(resp);
            return;
        }

        BotApiMethod<?> part = participantController.handleUpdate(update, user);
        if (part != null) { executeMessage((SendMessage) part); return; }

        /* --- fallback: просто меню */
        sendMainMenu(chat, user);
    }

    /* ============================= UI HELPERS ============================== */

    /* ---------- sendWelcome ------------ */
    private void sendWelcome(Long chat) {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        kb.setOneTimeKeyboard(true);

        KeyboardRow row = new KeyboardRow();
        row.add("Войти");
        row.add("Зарегистрироваться");
        kb.setKeyboard(List.of(row));

        SendMessage msg = new SendMessage(chat.toString(), "Добро пожаловать! Выберите действие:");
        msg.setReplyMarkup(kb);

        executeMessage(msg);
    }

    /* ---------- sendMainMenu ------------ */
    private void sendMainMenu(Long chat, User user) {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);

        KeyboardRow r1 = new KeyboardRow();
        r1.add("Создать тест");
        r1.add("Пройти тест");

        KeyboardRow r2 = new KeyboardRow();
        r2.add("Мой профиль");
        r2.add("Выйти из аккаунта");

        kb.setKeyboard(List.of(r1, r2));

        SendMessage msg = new SendMessage(
                chat.toString(),
                "👋 Привет, " + user.getFullName() + "! Выберите действие:"
        );
        msg.setReplyMarkup(kb);

        executeMessage(msg);
    }


    private void showProfile(Long chat, User user) {
        String created = creatorController.getUserCreatedTestsInfo(user);
        int    passed  = participantController.getCompletedTestsCount(user);

        String msg = "👤 Профиль:\n" +
                "Имя: "   + user.getFullName() + '\n' +
                "Логин: " + user.getUsername() + "\n\n" +
                "📊 Созданные тесты:\n" + created + "\n\n" +
                "✅ " + (passed > 0 ? "Пройденных тестов: " + passed
                : "Вы еще не прошли ни одного теста");

        executeMessage(new SendMessage(chat.toString(), msg));
    }

    /* ------------------------------------------------------------------ */

    private void sendError(Long chat) {
        executeMessage(new SendMessage(chat.toString(),
                "⚠️ Произошла ошибка. Пожалуйста, попробуйте позже."));
    }

    public void executeMessage(SendMessage msg) {
        try { execute(msg); }
        catch (TelegramApiException e) { log.error("Error sending message", e); }
    }
}
