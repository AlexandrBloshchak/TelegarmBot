package com.example.TelegramTestBot.controller;

import com.example.TelegramTestBot.bot.TestBot;
import com.example.TelegramTestBot.model.*;
import com.example.TelegramTestBot.service.TestCreationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ManualTestCreatorController {

    private final TestCreationService testCreationService;
    public SendMessage start(Long chatId, User creator, String title) {
        Draft d = new Draft();
        d.test.setTitle(title);
        d.test.setCreator(creator);

        drafts.put(chatId, d);
        stages.put(chatId, Stage.ENTER_QTY_QUESTIONS);
        return plain(chatId, "Сколько будет вопросов?");
    }
    private enum Stage {
        ENTER_TITLE,
        CHOOSE_MODE,
        ENTER_QTY_QUESTIONS,
        ENTER_QUESTION_TEXT,
        ENTER_QTY_OPTIONS,
        ENTER_OPTION_TEXT,
        CHOOSE_CORRECT,
        DONE
    }
    private static class Draft {
        Test test = new Test();
        int totalQuestions;
        int currentQuestionIndex = 0;
        Question currentQuestion;
        int totalOptions;
        int currentOptionIndex = 0;

        List<Question> questions = new ArrayList<>();
    }
    private final Map<Long, Stage> stages   = new ConcurrentHashMap<>();
    private final Map<Long, Draft> drafts   = new ConcurrentHashMap<>();
    public SendMessage handle(Update up, User user) {
        if (!up.hasMessage()) return null;
        Message msg   = up.getMessage();
        Long    chat  = msg.getChatId();
        String  text  = msg.hasText() ? msg.getText().trim() : "";

        if ("отмена".equalsIgnoreCase(text)) {
            return cancel(chat, user);     // передаём user
        }

        Stage st = stages.get(chat);
        if (st == null) return null;

        Draft d  = drafts.get(chat);

        switch (st) {
            case ENTER_TITLE -> {
                if (text.isBlank()) return plain(chat, "Название не может быть пустым.");
                d.test.setTitle(text);
                stages.put(chat, Stage.CHOOSE_MODE);
                return btn(chat,
                        "Как хотите добавить вопросы?",
                        List.of("Вручную", "Загрузить DOCX", "Отмена"));
            }

            case CHOOSE_MODE -> {
                if ("загрузить docx".equalsIgnoreCase(text)) {
                    stages.remove(chat);
                    drafts.remove(chat);
                    return null;
                }
                if (!"вручную".equalsIgnoreCase(text))
                    return plain(chat,"Выберите кнопку.");

                stages.put(chat, Stage.ENTER_QTY_QUESTIONS);
                return plain(chat, "Сколько будет вопросов?");
            }

            case ENTER_QTY_QUESTIONS -> {
                int qty;
                try { qty = Integer.parseInt(text); }
                catch (NumberFormatException e){ return plain(chat,"Введите целое число."); }
                if (qty <= 0 || qty > 50) return plain(chat,"Допустимо 1-50.");
                d.totalQuestions = qty;
                d.currentQuestionIndex = 1;
                stages.put(chat, Stage.ENTER_QUESTION_TEXT);
                return plain(chat,
                        "Вопрос 1/"+
                                d.totalQuestions+
                                ". Введите текст вопроса:");
            }

            case ENTER_QUESTION_TEXT -> {
                if (text.isBlank()) return plain(chat,"Текст пустой – введите снова.");
                d.currentQuestion = new Question();
                d.currentQuestion.setText(text);
                stages.put(chat, Stage.ENTER_QTY_OPTIONS);
                return plain(chat,"Сколько вариантов ответа будет?");
            }

            /* 5. кол-во вариантов */
            case ENTER_QTY_OPTIONS -> {
                int n;
                try { n = Integer.parseInt(text); }
                catch (NumberFormatException e){ return plain(chat,"Введите число 2-10."); }
                if (n < 2 || n > 10) return plain(chat,"Допустимо 2-10.");
                d.totalOptions = n;
                d.currentOptionIndex = 1;
                stages.put(chat, Stage.ENTER_OPTION_TEXT);
                return plain(chat,
                        "Вариант 1/"+n+": введите текст ответа:");
            }

            /* 6. текст варианта */
            case ENTER_OPTION_TEXT -> {
                if (text.isBlank()) return plain(chat,"Текст пустой – попробуйте снова.");
                AnswerOption opt = new AnswerOption();
                opt.setText(text);
                opt.setOptionNumber(d.currentOptionIndex);
                opt.setIsCorrect(false);
                d.currentQuestion.addAnswerOption(opt);

                if (d.currentOptionIndex < d.totalOptions) {
                    d.currentOptionIndex++;
                    return plain(chat,
                            "Вариант "+d.currentOptionIndex+"/"+d.totalOptions+
                                    ": введите текст:");
                }
                stages.put(chat, Stage.CHOOSE_CORRECT);
                List<String> numbers = new ArrayList<>();
                for (int i = 1; i <= d.totalOptions; i++) numbers.add(String.valueOf(i));
                numbers.add("Отмена");
                return btn(chat,
                        "Какой вариант правильный? Выберите номер:",
                        numbers);
            }

            case CHOOSE_CORRECT -> {
                int correct;
                try { correct = Integer.parseInt(text); }
                catch (NumberFormatException e){ return plain(chat,"Нажмите номер на клавиатуре."); }
                if (correct < 1 || correct > d.totalOptions)
                    return plain(chat,"Номер вне диапазона.");

                d.currentQuestion.getAnswerOptions().get(correct - 1).setIsCorrect(true);
                d.questions.add(d.currentQuestion);

                if (d.currentQuestionIndex < d.totalQuestions) {
                    d.currentQuestionIndex++;
                    stages.put(chat, Stage.ENTER_QUESTION_TEXT);
                    return plain(chat,
                            "Вопрос "+d.currentQuestionIndex+"/"+d.totalQuestions+
                                    ". Введите текст вопроса:");
                } else {
                    stages.put(chat, Stage.DONE);
                    return finish(chat, d, user);
                }
            }

            default -> { return null; }
        }
    }
    private SendMessage finish(Long chat, Draft d, User u) {
        // сохраняем тест
        testCreationService.createTest(u, d.test, d.questions);

        // чистим
        stages.remove(chat);
        drafts.remove(chat);

        return btn(chat,
                String.format("✅ Тест «%s» создан! Вопросов: %d",
                        d.test.getTitle(), d.questions.size()),
                List.of("Главное меню"));
    }
    private SendMessage cancel(Long chat, User user) {
        stages.remove(chat);
        drafts.remove(chat);

        ReplyKeyboardMarkup mainKb = new ReplyKeyboardMarkup();
        mainKb.setResizeKeyboard(true);
        mainKb.setKeyboard(List.of(
                kRow("Создать тест","Пройти тест"),
                kRow("Мой профиль","Мои тесты","Выйти из аккаунта")
        ));

        return SendMessage.builder()
                .chatId(chat.toString())
                .text("❌ Создание теста отменено.\n\n👋 Привет, " + user.getFullName() + "!")
                .replyMarkup(mainKb)
                .build();
    }
    private SendMessage plain(Long c, String t) { return new SendMessage(c.toString(), t); }
    private SendMessage btn(Long chat, String text, List<String> btns) {
        KeyboardRow row = new KeyboardRow();
        btns.forEach(b -> row.add(new KeyboardButton(b)));
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        kb.setKeyboard(List.of(row));
        return SendMessage.builder()
                .chatId(chat.toString())
                .text(text)
                .replyMarkup(kb)
                .build();
    }
    private static KeyboardRow kRow(String... caps) {
        KeyboardRow r = new KeyboardRow();
        for (String c : caps) r.add(new KeyboardButton(c));
        return r;
    }
}
