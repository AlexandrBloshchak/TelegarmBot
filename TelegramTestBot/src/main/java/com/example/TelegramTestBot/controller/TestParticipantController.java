package com.example.TelegramTestBot.controller;

import com.example.TelegramTestBot.bot.TestBot;
import com.example.TelegramTestBot.model.*;
import com.example.TelegramTestBot.repository.TestRepository;
import com.example.TelegramTestBot.repository.TestResultRepository;
import com.example.TelegramTestBot.service.AnswerOptionService;
import com.example.TelegramTestBot.service.QuestionService;
import com.example.TelegramTestBot.service.TestService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@Transactional
public class TestParticipantController {
    private final TestBot testBot;
    private final TestService testService;
    private final QuestionService questionService;
    private final AnswerOptionService answerOptionService;
    private static final String TEST_PREFIX = "📝 ";
    private final Map<Long, TestSession> sessions = new ConcurrentHashMap<>();
    private final Set<Long> awaitingTestChoice = ConcurrentHashMap.newKeySet();
    public BotApiMethod<?> handleUpdate(Update update, User user) {
        if (!update.hasMessage() || update.getMessage().getText() == null) {
            return null;
        }
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();
        if (sessions.containsKey(chatId)) {
            return handleAnswer(chatId, text, user);
        }
        if (text.equalsIgnoreCase("/starttest") || text.equalsIgnoreCase("пройти тест")) {
            return handleTestStartCommand(chatId, user);
        }
        return handleTestSelection(chatId, user, text);
    }
    public BotApiMethod<?> handleTestStartCommand(Long chatId, User user) {
        List<Test> tests = testService.getAvailableTests(user);
        if (tests.isEmpty()) {
            return new SendMessage(chatId.toString(), "Пока нет доступных тестов для прохождения.");
        }
        return createTestSelectionKeyboard(chatId, tests);
    }
    private BotApiMethod<?> handleTestSelection(Long chatId, User user, String text) {

        // работаем ТОЛЬКО если ждём выбор из списка
        if (!awaitingTestChoice.remove(chatId)) {
            return null;
        }

        // должен начинаться с префикса
        if (!text.startsWith(TEST_PREFIX)) {
            return null;
        }

        String title = text.substring(TEST_PREFIX.length()).trim();

        return testService.getAvailableTests(user).stream()
                .filter(t -> t.getTitle().equalsIgnoreCase(title))
                .findFirst()
                .map(t -> startTestSession(chatId, t))
                .orElse(null);
    }
    private BotApiMethod<?> startTestSession(Long chatId, Test test) {
        try {
            // Получаем список всех вопросов для выбранного теста
            List<Question> questions = questionService.getQuestionsByTestId(test.getId());

            if (questions.isEmpty()) {
                return new SendMessage(chatId.toString(), "В выбранном тесте нет вопросов.");
            }

            // Перемешиваем вопросы случайным образом
            Collections.shuffle(questions);

            // Создаём сессию для пользователя с перемешанными вопросами
            TestSession session = new TestSession(test, questions);
            sessions.put(chatId, session);

            // Отправляем первый вопрос
            return sendQuestion(chatId, session);
        } catch (Exception e) {
            log.error("Ошибка при запуске теста", e);
            return new SendMessage(chatId.toString(),
                    "Произошла ошибка при загрузке теста. Попробуйте позже.");
        }
    }
    private BotApiMethod<?> createTestSelectionKeyboard(Long chatId, List<Test> tests) {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        kb.setOneTimeKeyboard(true);

        List<KeyboardRow> rows = new ArrayList<>();
        for (Test t : tests) {
            rows.add(new KeyboardRow(List.of(new KeyboardButton(TEST_PREFIX + t.getTitle()))));
        }
        kb.setKeyboard(rows);

        awaitingTestChoice.add(chatId);          // <-- ставим флаг ожидания

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("Выберите тест:")
                .replyMarkup(kb)
                .build();
    }
    private BotApiMethod<?> sendQuestion(Long chatId, TestSession session) {
        Question currentQuestion = session.getCurrentQuestion();
        List<AnswerOption> options = answerOptionService.getAnswersForQuestion(currentQuestion);

        StringBuilder questionText = new StringBuilder()
                .append("Вопрос ").append(session.getCurrentIndex() + 1).append(" из ")
                .append(session.getTotalQuestions()).append(":\n\n")
                .append(currentQuestion.getText()).append("\n\n");

        for (int i = 0; i < options.size(); i++) {
            questionText.append(i + 1).append(") ").append(options.get(i).getText()).append("\n");
        }
        questionText.append("\nВыберите номер правильного ответа:");

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(true);

        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        for (int i = 1; i <= options.size(); i++) {
            row.add(String.valueOf(i));
            if (i % 3 == 0) {
                rows.add(row);
                row = new KeyboardRow();
            }
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        keyboard.setKeyboard(rows);

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(questionText.toString())
                .replyMarkup(keyboard)
                .build();
    }
    private BotApiMethod<?> handleAnswer(Long chatId, String answerText, User user) {
        TestSession session = sessions.get(chatId);
        if (session == null) {
            return new SendMessage(chatId.toString(), "Сессия тестирования не найдена. Начните тест заново.");
        }
        try {
            int selectedOption = Integer.parseInt(answerText.trim()) - 1;
            List<AnswerOption> options = answerOptionService.getAnswersForQuestion(session.getCurrentQuestion());
            if (selectedOption < 0 || selectedOption >= options.size()) {
                return new SendMessage(chatId.toString(),
                        "Неверный номер варианта. Пожалуйста, выберите номер из предложенных.");
            }
            session.getUserAnswers().add(selectedOption + 1);
            if (options.get(selectedOption).getIsCorrect()) {
                session.incrementCorrect();
            }
            if (session.nextQuestion()) {
                return sendQuestion(chatId, session);
            } else {
                testService.recordTestResult(
                        user,
                        session.getTest(),
                        session.getAllQuestions(),
                        session.getUserAnswers()
                );
                finishTestSession(chatId, session);
                return null;
            }
        } catch (NumberFormatException e) {
            return new SendMessage(chatId.toString(),
                    "Пожалуйста, введите номер варианта ответа.");
        }
    }
    public BotApiMethod<?> finishTestSession(Long chatId,
                                             TestSession session) {
        sessions.remove(chatId);
        Test   test    = session.getTest();
        int    total   = session.getTotalQuestions();
        int    correct = session.getCorrectCount();
        double perc    = total > 0 ? correct * 100.0 / total : 0.0;
        String header = String.format("*Результаты теста!* %d/%d (%.1f%%)",
                correct, total, perc);
        boolean show = test.getShowAnswers();
        List<List<InlineKeyboardButton>> kb = new ArrayList<>();
        kb.add(List.of(
                ib("Вопрос"),
                ib("Ваш"),
                show ? ib("Прав.") : ib("✓/✗"),
                show ? ib("Баллы") : ib(" ")
        ));
        for (int i = 0; i < total; i++) {

            Question q  = session.getAllQuestions().get(i);
            int ua      = session.getUserAnswers().get(i);       // выбранный участником
            int ca      = q.getAnswerOptions().stream()          // правильный
                    .filter(AnswerOption::getIsCorrect)
                    .map(AnswerOption::getOptionNumber)
                    .findFirst().orElse(0);
            int pt      = ua == ca ? 1 : 0;                      // балл

            String qText = (i + 1) + ". " +
                    q.getText()
                            .replaceAll("\\R", " ")              // убираем перевод строк
                            .replaceAll(" {2,}", " ")            // двойные пробелы
                            .replaceAll("(.{40})", "$1\n");      // перенос каждые 40 символов

            kb.add(List.of(
                    ib(qText),
                    ib(String.valueOf(ua)),
                    show ? ib(String.valueOf(ca))
                            : ib(ua == ca ? "✓" : "✗"),
                    show ? ib(String.valueOf(pt))
                            : ib(" ")
            ));
        }
        kb.add(List.of(
                ib(String.format("Итого: %d/%d (%.1f%%)", correct, total, perc))
        ));
        List<UserResult> users = testService.getUserResults(test);
        if (!users.isEmpty()) {
            kb.add(List.of(ib("Сводка по пользователям:")));
            users.stream()
                    .sorted(Comparator.comparingDouble(UserResult::getPercentage).reversed())
                    .forEach(ur -> kb.add(List.of(
                            ib(String.format("%s: %.1f%%",
                                    ur.getDisplayName(), ur.getPercentage()))
                    )));
        }

        InlineKeyboardMarkup inlineMarkup = InlineKeyboardMarkup.builder()
                .keyboard(kb)
                .build();
        SendMessage report = SendMessage.builder()
                .chatId(chatId.toString())
                .text(header)
                .parseMode("Markdown")
                .replyMarkup(inlineMarkup)
                .build();
        testBot.executeMessage(report);
        ReplyKeyboardMarkup replyKb = new ReplyKeyboardMarkup();
        replyKb.setResizeKeyboard(true);
        replyKb.setKeyboard(List.of(
                new KeyboardRow(List.of(new KeyboardButton("Выйти в меню"))),
                new KeyboardRow(List.of(new KeyboardButton("Пройти заново")))
        ));
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("Выберите действие:")
                .replyMarkup(replyKb)
                .build();
    }
    private InlineKeyboardButton ib(String text) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData("noop")
                .build();
    }
}
