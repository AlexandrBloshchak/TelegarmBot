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
    private final TestResultRepository testResultRepository;
    private final TestRepository testRepository;
    private final Map<Long, TestSession> sessions = new ConcurrentHashMap<>();
    public String getUserPassedTestsInfo(User user) {
        // Получаем все результаты тестов пользователя по user_id
        List<TestResult> userResults = testResultRepository.findByUserId(user.getId());

        if (userResults.isEmpty()) {
            return "Вы еще не прошли ни одного теста";
        }

        StringBuilder sb = new StringBuilder();
        for (TestResult result : userResults) {
            // Если тест не загружен через JPA, загружаем его отдельно
            Test test = result.getTest();
            if (test == null) {
                test = testRepository.findById(result.getTestId()).orElse(null);
                if (test == null) continue;
            }

            sb.append("• ")
                    .append(test.getTitle())
                    .append("\nРезультат: ")
                    .append(result.getScore())
                    .append("/")
                    .append(result.getMaxScore())
                    .append(" (")
                    .append(String.format("%.1f", (result.getScore() * 100.0 / result.getMaxScore())))
                    .append("%)")
                    .append("\nДата: ")
                    .append(result.getCompletionDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
                    .append("\n\n");
        }

        return sb.toString();
    }
    public int getCompletedTestsCount(User user) {
        return testResultRepository.countByUserId(user.getId());
    }
    public BotApiMethod<?> handleUpdate(Update update, User user) {
        if (!update.hasMessage() || update.getMessage().getText() == null) {
            return null;
        }

        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        // Обработка ответа на текущий вопрос теста
        if (sessions.containsKey(chatId)) {
            return handleAnswer(chatId, text, user);
        }

        // Обработка команды начала теста
        if (text.equalsIgnoreCase("/starttest") || text.equalsIgnoreCase("пройти тест")) {
            return handleTestStartCommand(chatId, user);
        }

        // Обработка выбора конкретного теста
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
        return testService.getAvailableTests(user).stream()
                .filter(t -> t.getTitle().equalsIgnoreCase(text))
                .findFirst()
                .map(test -> startTestSession(chatId, test))
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
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(true);
        keyboard.setSelective(true);

        List<KeyboardRow> rows = tests.stream()
                .map(test -> {
                    KeyboardRow row = new KeyboardRow();
                    row.add(test.getTitle());
                    return row;
                })
                .collect(Collectors.toList());

        keyboard.setKeyboard(rows);

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("Выберите тест:")
                .replyMarkup(keyboard)
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

            // Записываем ответ пользователя (1-based)
            session.getUserAnswers().add(selectedOption + 1);

            // Проверяем правильность
            if (options.get(selectedOption).getIsCorrect()) {
                session.incrementCorrect();
            }

            // Если есть следующий вопрос — показываем его
            if (session.nextQuestion()) {
                return sendQuestion(chatId, session);
            } else {
                // Тест завершён — сохраняем результат в БД
                testService.recordTestResult(
                        user,
                        session.getTest(),
                        session.getCorrectCount(),
                        session.getTotalQuestions()
                );
                finishTestSession(chatId, session, user);
                return null;
            }
        } catch (NumberFormatException e) {
            return new SendMessage(chatId.toString(),
                    "Пожалуйста, введите номер варианта ответа.");
        }
    }
    public BotApiMethod<?> finishTestSession(Long chatId,
                                             TestSession session,
                                             User        user) {
        // 1) Убираем сессию
        sessions.remove(chatId);

        // 2) Считаем итоги
        Test   test    = session.getTest();
        int    total   = session.getTotalQuestions();
        int    correct = session.getCorrectCount();
        double perc    = total > 0 ? correct * 100.0 / total : 0.0;

        String header = String.format("*Результаты теста!* %d/%d (%.1f%%)",
                correct, total, perc);

        // 3) Собираем inline-клавиатуру с деталями
        List<List<InlineKeyboardButton>> inlineKb = new ArrayList<>();

        // 3.1) Заголовки
        inlineKb.add(List.of(
                InlineKeyboardButton.builder().text("№ Вопрос").callbackData("noop").build(),
                InlineKeyboardButton.builder().text("Ваш ответ").callbackData("noop").build(),
                InlineKeyboardButton.builder().text("Правильный").callbackData("noop").build(),
                InlineKeyboardButton.builder().text("Баллы").callbackData("noop").build()
        ));

        // 3.2) Строки вопросов
        for (int i = 0; i < total; i++) {
            Question q      = session.getAllQuestions().get(i);
            int      ua     = session.getUserAnswers().get(i);
            int      ca     = q.getAnswerOptions().stream()
                    .filter(AnswerOption::getIsCorrect)
                    .map(AnswerOption::getOptionNumber)
                    .findFirst().orElse(0);
            int      pt     = ua == ca ? 1 : 0;
            String   qText  = (i + 1) + ". " +
                    q.getText().replaceAll("(.{40})", "$1\n");

            inlineKb.add(List.of(
                    InlineKeyboardButton.builder()
                            .text(qText).callbackData("noop").build(),
                    InlineKeyboardButton.builder()
                            .text(String.valueOf(ua)).callbackData("noop").build(),
                    InlineKeyboardButton.builder()
                            .text(String.valueOf(ca)).callbackData("noop").build(),
                    InlineKeyboardButton.builder()
                            .text(String.valueOf(pt)).callbackData("noop").build()
            ));
        }

        // 3.3) Итоговая строка
        inlineKb.add(List.of(
                InlineKeyboardButton.builder()
                        .text(String.format("Итого: %d/%d (%.1f%%)", correct, total, perc))
                        .callbackData("noop")
                        .build()
        ));

        // 3.4) Сводка по пользователям
        List<UserResult> users = testService.getUserResults(test);
        if (!users.isEmpty()) {
            inlineKb.add(List.of(
                    InlineKeyboardButton.builder()
                            .text("Сводка по пользователям:")
                            .callbackData("noop").build()
            ));
            users.stream()
                    .sorted(Comparator.comparingDouble(UserResult::getPercentage).reversed())
                    .forEach(ur -> inlineKb.add(List.of(
                            InlineKeyboardButton.builder()
                                    .text(String.format("%s: %.1f%%",
                                            ur.getDisplayName(), ur.getPercentage()))
                                    .callbackData("noop")
                                    .build()
                    )));
        }

        InlineKeyboardMarkup inlineMarkup = InlineKeyboardMarkup.builder()
                .keyboard(inlineKb)
                .build();

        // 4) Первое сообщение — отчёт с inline-клавиатурой
        SendMessage report = SendMessage.builder()
                .chatId(chatId.toString())
                .text(header)
                .parseMode("Markdown")
                .replyMarkup(inlineMarkup)
                .build();
        testBot.executeMessage(report);

        // 5) Второе сообщение — reply-клавиатура для управления
        ReplyKeyboardMarkup replyKb = new ReplyKeyboardMarkup();
        replyKb.setResizeKeyboard(true);

        KeyboardRow row1 = new KeyboardRow();
        row1.add("Выйти в меню");
        KeyboardRow row2 = new KeyboardRow();
        row2.add("Пройти заново");
        replyKb.setKeyboard(List.of(row1, row2));

        SendMessage controls = SendMessage.builder()
                .chatId(chatId.toString())
                .text("Выберите действие:")
                .replyMarkup(replyKb)
                .build();

        // Возвращаем второе сообщение, его уже отправит TestBot.onUpdateReceived
        return controls;
    }

}
