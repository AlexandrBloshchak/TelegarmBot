package com.example.TelegramTestBot.bot;

import com.example.TelegramTestBot.controller.AuthController;
import com.example.TelegramTestBot.controller.TestCreatorController;
import com.example.TelegramTestBot.controller.TestParticipantController;
import com.example.TelegramTestBot.model.User;
import com.example.TelegramTestBot.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class TestBot extends TelegramLongPollingBot {

    private final AuthController authController;
    private final UserService userService;
    private final TestCreatorController creatorController;
    private final TestParticipantController participantController;

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.bot.token}")
    private String botToken;

    public TestBot(AuthController authController,
                   UserService userService,
                   TestCreatorController creatorController,
                   TestParticipantController participantController) {
        this.authController = authController;
        this.userService = userService;
        this.creatorController = creatorController;
        this.participantController = participantController;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            // 1) CallbackQuery (если есть) — сразу в AuthController
            if (update.hasCallbackQuery()) {
                CallbackQuery cq = update.getCallbackQuery();
                Long chatId = cq.getMessage().getChatId();
                log.info("Получен callbackQuery: id = {}, data = {}", cq.getId(), cq.getData());
                SendMessage authResp = authController.handleAuth(update);
                execute(authResp);

                AnswerCallbackQuery answer = new AnswerCallbackQuery();
                answer.setCallbackQueryId(cq.getId());
                answer.setText("Получено");
                execute(answer);
                return;
            }

            // 2) Только сообщения нас интересуют дальше
            if (!update.hasMessage() || update.getMessage().getText() == null) {
                return;
            }

            Message message = update.getMessage();
            Long chatId = message.getChatId();
            String normText = message.getText().trim().toLowerCase();

            // 3) Авторизация
            Optional<User> authUserOpt = userService.getAuthenticatedUser(chatId);
            if (!authUserOpt.isPresent()) {
                // если /login, «войти» и т.п. — в AuthController
                if ("/login".equals(normText) || "войти".equals(normText) || "логин".equals(normText)
                        || "/registr".equals(normText) || "зарегистрироваться".equals(normText) || "регистрация".equals(normText)) {
                    SendMessage loginResp = authController.handleAuth(update);
                    if (loginResp != null) execute(loginResp);
                    return;
                }
                // иначе — просим авторизоваться
                execute(getUnauthorizedMessage(chatId));
                return;
            }
            User authUser = authUserOpt.get();

            // 4) Создание теста (если в сессии TestCreatorController)
            if (creatorController.isAwaitingTestName(chatId) || creatorController.isAwaitingDocument(chatId)) {
                SendMessage createResp = creatorController.handleUpdate(update, authUser, null);
                if (createResp != null) execute(createResp);
                return;
            }

            // 5) Если мы в середине логина через AuthController
            if (authController.isInAuthSession(chatId)) {
                SendMessage authResp = authController.handleAuth(update);
                if (authResp != null) execute(authResp);
                return;
            }

            // 6) Теперь — участник: показываем/выбираем/начинаем тест
            BotApiMethod<?> participantResp = participantController.handleUpdate(update, authUser);
            if (participantResp != null) {
                execute(participantResp);
                return;
            }

            // 7) Остальные команды
            if ("/reset".equals(normText)) {
                creatorController.resetSession(chatId);
                execute(new SendMessage(chatId.toString(), "Сессия сброшена. Введите /start для главного меню."));
                return;
            } else if ("/unlogin".equals(normText) || "выйти".equals(normText)) {
                userService.unlogin(chatId);
                execute(getUnauthorizedMessage(chatId));
                return;
            } else if ("/start".equals(normText)) {
                execute(mainMenu(chatId, "Добро пожаловать, " + authUser.getFullName() + "!\nВыберите действие:"));
                return;
            } else if (normText.startsWith("создать тест") || normText.startsWith("/createtest")) {
                // эта ветка уже не потребуется, если вы полностью перенесли логику в creatorController
                creatorController.setAwaitingTestName(chatId);
                execute(creatorController.promptTestName(chatId));
                return;
            }

            // 8) По умолчанию — главное меню
            execute(mainMenu(chatId, "Выберите действие, " + authUser.getFullName() + ":"));

        } catch (Exception e) {
            log.error("Ошибка при обработке команды: {}", e.getMessage(), e);
            Long chatId = update.hasMessage() ? update.getMessage().getChatId() : null;
            if (chatId != null) sendErrorMessage(chatId, "Произошла ошибка при обработке команды. Попробуйте позже.");
        }
    }

    private SendMessage mainMenu(Long chatId, String welcomeMessage) {
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(welcomeMessage)
                .replyMarkup(mainKeyboardAuthorized())
                .build();
    }

    private ReplyKeyboardMarkup mainKeyboardAuthorized() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add("Создать тест");
        row.add("Пройти тест");
        row.add("Выйти");
        rows.add(row);
        keyboard.setKeyboard(rows);
        keyboard.setResizeKeyboard(true);
        return keyboard;
    }

    private SendMessage getUnauthorizedMessage(Long chatId) {
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("Вы не авторизованы. Пожалуйста, введите /login, 'войти', 'логин' или нажмите 'Войти'.")
                .build();
    }

    private void sendErrorMessage(Long chatId, String message) {
        try {
            execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(message)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения об ошибке: {}", e.getMessage());
        }
    }
}
