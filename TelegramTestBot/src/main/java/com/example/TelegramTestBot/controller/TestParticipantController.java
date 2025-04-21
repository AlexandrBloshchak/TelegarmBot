package com.example.TelegramTestBot.controller;

import com.example.TelegramTestBot.model.AnswerOption;
import com.example.TelegramTestBot.model.Question;
import com.example.TelegramTestBot.model.Test;
import com.example.TelegramTestBot.model.User;
import com.example.TelegramTestBot.service.AnswerOptionService;
import com.example.TelegramTestBot.service.TestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class TestParticipantController {

    private final TestService testService;
    private final AnswerOptionService answerOptionService;

    // сессии прохождения теста: chatId → сессия
    private final Map<Long, TestSession> sessions = new ConcurrentHashMap<>();

    /**
     * Общая точка входа для всех сообщений от участника.
     * Если есть активная сессия — обрабатываем ответ, иначе — команды /starttest и выбор теста.
     */
    public BotApiMethod<?> handleUpdate(Update update, User user) {
        if (!update.hasMessage() || update.getMessage().getText() == null) {
            return null;
        }

        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        // 1) Если у нас уже есть активная сессия для этого чата — это ответ на вопрос
        if (sessions.containsKey(chatId)) {
            return handleAnswer(chatId, text);
        }

        // 2) Команда запуска теста
        if (text.equalsIgnoreCase("/starttest") || text.equalsIgnoreCase("пройти тест")) {
            List<Test> tests = testService.getAvailableTests(user);
            if (tests.isEmpty()) {
                return new SendMessage(chatId.toString(), "Пока нет ни одного теста для прохождения.");
            }
            return createTestSelectionKeyboard(chatId, tests);
        }

        // 3) Выбор теста по названию (строка-кнопка из клавиатуры)
        Test chosen = testService.getAvailableTests(user).stream()
                .filter(t -> t.getTitle().equalsIgnoreCase(text))
                .findFirst()
                .orElse(null);

        if (chosen != null) {
            return startTestSession(chatId, chosen);
        }

        // 4) Всё остальное — не наша зона, передаём дальше
        return null;
    }


    /** Старт сессии: сохраняем все вопросы и сразу шлём первый */
    private BotApiMethod<?> startTestSession(Long chatId, Test test) {
        List<Question> questions = testService.getTestQuestions(test);
        if (questions.isEmpty()) {
            return new SendMessage(chatId.toString(), "В выбранном тесте нет вопросов.");
        }
        TestSession session = new TestSession(test, questions);
        sessions.put(chatId, session);
        return sendQuestion(chatId, session);
    }
    /**
     * Строит ReplyKeyboardMarkup с кнопками-строками, где каждая строка — это название теста
     */
    private BotApiMethod<?> createTestSelectionKeyboard(Long chatId, List<Test> tests) {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        kb.setOneTimeKeyboard(false);
        kb.setSelective(false);

        List<KeyboardRow> rows = tests.stream()
                .map(test -> {
                    KeyboardRow row = new KeyboardRow();
                    row.add(test.getTitle());
                    return row;
                })
                .collect(Collectors.toList());

        kb.setKeyboard(rows);

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("Выберите тест:")
                .replyMarkup(kb)
                .build();
    }

    /** Шлёт текущий вопрос и динамическую клавиатуру с вариантами */
    private BotApiMethod<?> sendQuestion(Long chatId, TestSession session) {
        Question q = session.getCurrentQuestion();
        List<AnswerOption> opts = answerOptionService.getAnswersForQuestion(q.getId());

        StringBuilder sb = new StringBuilder()
                .append("Вопрос ").append(session.getCurrentIndex() + 1).append(":\n")
                .append(q.getText()).append("\n\n");

        for (int i = 0; i < opts.size(); i++) {
            sb.append(i + 1).append(") ").append(opts.get(i).getText()).append("\n");
        }
        sb.append("\nВыберите ответ:");

        // Строим клавиатуру ровно из opts.size() кнопок
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        KeyboardRow row = new KeyboardRow();
        for (int i = 1; i <= opts.size(); i++) {
            row.add(String.valueOf(i));
        }
        kb.setKeyboard(List.of(row));

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(sb.toString())
                .replyMarkup(kb)
                .build();
    }

    /** Обработка ответа пользователя — проверка, переход к следующему или завершение */
    private BotApiMethod<?> handleAnswer(Long chatId, String text) {
        TestSession session = sessions.get(chatId);
        int choice;
        try {
            choice = Integer.parseInt(text) - 1;
        } catch (NumberFormatException e) {
            return new SendMessage(chatId.toString(), "Пожалуйста, введите номер варианта.");
        }

        Question q = session.getCurrentQuestion();
        List<AnswerOption> opts = answerOptionService.getAnswersForQuestion(q.getId());

        if (choice >= 0 && choice < opts.size()) {
            if (Boolean.TRUE.equals(opts.get(choice).getIsCorrect())) {
                session.incrementCorrect();
            }
        } else {
            // Неверный номер
            return new SendMessage(chatId.toString(), "Неверный номер варианта, попробуйте ещё раз.");
        }

        session.nextQuestion();
        if (session.hasNext()) {
            return sendQuestion(chatId, session);
        } else {
            // Тест окончен
            int score = session.getCorrectCount();
            int total = session.getTotalQuestions();
            sessions.remove(chatId);
            return new SendMessage(chatId.toString(),
                    String.format("Тест завершён: %d из %d правильных.", score, total));
        }
    }


}
