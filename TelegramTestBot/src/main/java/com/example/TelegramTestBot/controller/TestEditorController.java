package com.example.TelegramTestBot.controller;

import com.example.TelegramTestBot.model.*;
import com.example.TelegramTestBot.service.AnswerOptionService;
import com.example.TelegramTestBot.service.QuestionService;
import com.example.TelegramTestBot.service.TestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class TestEditorController {
    private final TestService        testService;
    private final QuestionService    questionService;
    private final AnswerOptionService answerService;
    private enum Stage {
        MENU, RENAME, ADD_Q, EDIT_Q_SELECT, EDIT_Q_TEXT,
        DELETE_Q_SELECT, ADD_A_SELECT, ADD_A_TEXT,
        DELETE_A_SELECT, DELETE_A_OPTION,
        SET_CORRECT_SELECT, SET_CORRECT_OPTION
    }
    private final Map<Long, Test>  currentTest          = new ConcurrentHashMap<>();
    private final Map<Long, Stage> stage                = new ConcurrentHashMap<>();
    private final Map<Long, Integer> pendingQuestionIdx = new ConcurrentHashMap<>();
    private static final String CANCEL = "Отмена редактирования";
    public SendMessage startEditor(Long chat, Test t) {
        currentTest.put(chat, t);
        stage.put(chat, Stage.MENU);
        return menu(chat, t);
    }
    public boolean isInside(Long chat) { return currentTest.containsKey(chat); }
    public SendMessage handle(Update up) {
        long   chat = up.getMessage().getChatId();
        String txt  = up.getMessage().getText().trim();

        if ("назад".equalsIgnoreCase(txt)) {
            stage.put(chat, Stage.MENU);
            return menu(chat, currentTest.get(chat));
        }

        return switch (stage.get(chat)) {
            case MENU               -> menuClick(chat, txt);
            case RENAME             -> rename(chat, txt);
            case ADD_Q              -> addQ(chat, txt);
            case EDIT_Q_SELECT      -> editQSelect(chat, txt);
            case EDIT_Q_TEXT        -> editQText(chat, txt);
            case DELETE_Q_SELECT    -> deleteQSelect(chat, txt);
            case ADD_A_SELECT       -> handleAddASelect(chat, txt);
            case ADD_A_TEXT         -> handleAddAText(chat, txt);
            case DELETE_A_SELECT    -> deleteASelect(chat, txt);
            case DELETE_A_OPTION    -> deleteAOption(chat, txt);
            case SET_CORRECT_SELECT -> setCorrectSelect(chat, txt);
            case SET_CORRECT_OPTION -> setCorrectOption(chat, txt);
        };
    }
    private SendMessage menu(Long c, Test t) {
        return msg(c,
                "🛠 *Редактор:* " + t.getTitle() + "\nЧто делаем?",
                kb(
                        row("Изменить название","Удалить тест"),
                        row("Добавить вопрос","Изменить вопрос","Удалить вопрос"),
                        row("Добавить вариант ответа","Удалить вариант ответа"),
                        row("Изменить правильный ответ"),
                        row(t.getShowAnswers() ? "Скрыть ответы" : "Показывать ответы"),
                        row("Назад","Главное меню")
                ));
    }
    private SendMessage menuClick(Long c, String txt) {
        Test t = currentTest.get(c);

        switch (txt.toLowerCase()) {
            case "скрыть ответы", "показывать ответы" -> {
                boolean flag = !t.getShowAnswers();
                t.setShowAnswers(flag);
                testService.save(t);
                return menuWithPrefix(c,
                        flag ? "✅ Ответы будут показываться"
                                : "🙈 Ответы скрыты для участников", t);
            }
            case "изменить название" -> {
                stage.put(c, Stage.RENAME);
                return ask(c, "Новое название:");
            }
            case "удалить тест" -> {
                testService.deleteTest(currentTest.remove(c));
                stage.remove(c);
                return simple(c, "✅ Тест удалён.");
            }
            case "добавить вопрос" -> {
                stage.put(c, Stage.ADD_Q);
                return ask(c, "Текст вопроса:");
            }
            case "изменить вопрос" -> {
                stage.put(c, Stage.EDIT_Q_SELECT);
                return promptQuestionSelection(c, "Выберите вопрос для редактирования:");
            }
            case "удалить вопрос" -> {
                stage.put(c, Stage.DELETE_Q_SELECT);
                return promptQuestionSelection(c, "Выберите вопрос для удаления:");
            }
            case "добавить вариант ответа" -> {
                stage.put(c, Stage.ADD_A_SELECT);
                return promptQuestionSelection(c, "Выберите вопрос для нового варианта:");
            }
            case "удалить вариант ответа" -> {
                stage.put(c, Stage.DELETE_A_SELECT);
                return promptQuestionSelection(c, "Выберите вопрос, у которого удаляем вариант:");
            }
            case "изменить правильный ответ" -> {
                stage.put(c, Stage.SET_CORRECT_SELECT);
                return promptQuestionSelection(c, "Выберите вопрос для установки правильного ответа:");
            }
            case "главное меню" -> {
                currentTest.remove(c);
                stage.remove(c);
                return simple(c, "Вернулись в главное меню.");
            }
            default -> {
                return simple(c, "Не понимаю, используйте кнопки.");
            }
        }
    }
    private SendMessage rename(long c, String t) {
        Test test = currentTest.get(c);
        testService.renameTest(test, t);
        stage.put(c, Stage.MENU);
        return menuWithPrefix(c, "✅ Название изменено.", test);
    }
    private SendMessage addQ(long c, String t) {
        questionService.addQuestion(currentTest.get(c), t);
        stage.put(c, Stage.MENU);
        return menuWithPrefix(c, "➕ Вопрос добавлен.", currentTest.get(c));
    }
    private SendMessage editQSelect(long c, String txt) {
        if (CANCEL.equalsIgnoreCase(txt)) { stage.put(c, Stage.MENU); return menu(c,currentTest.get(c)); }

        int idx = idxFromCaption(txt);
        if (idx < 0) return simple(c, "Выберите вопрос кнопкой.");

        pendingQuestionIdx.put(c, idx);
        stage.put(c, Stage.EDIT_Q_TEXT);

        Question q = questionService.getQuestionsByTestId(currentTest.get(c).getId()).get(idx);
        return SendMessage.builder()
                .chatId(String.valueOf(c))
                .text("Текущий текст:\n" + q.getText() +
                        "\n\nВведите новый текст для вопроса №" + (idx+1) + ":")
                .build();
    }
    private SendMessage editQText(long c, String txt) {
        int idx = pendingQuestionIdx.remove(c);
        Question q = questionService.getQuestionsByTestId(currentTest.get(c).getId()).get(idx);
        questionService.updateQuestion(q, txt);
        stage.put(c, Stage.MENU);
        return menuWithPrefix(c, "✏️ Вопрос обновлён.", currentTest.get(c));
    }
    private SendMessage deleteQSelect(long c, String txt) {
        if (CANCEL.equalsIgnoreCase(txt)) { stage.put(c, Stage.MENU); return menu(c,currentTest.get(c)); }

        int idx = idxFromCaption(txt);
        if (idx < 0) return simple(c,"Выберите вопрос кнопкой.");

        Question q = questionService.getQuestionsByTestId(currentTest.get(c).getId()).get(idx);
        questionService.deleteQuestion(q.getId());

        stage.put(c, Stage.MENU);
        return menuWithPrefix(c, "🗑 Вопрос удалён.", currentTest.get(c));
    }
    private SendMessage handleAddASelect(Long c, String txt) {
        if (CANCEL.equalsIgnoreCase(txt)) { stage.put(c, Stage.MENU); return menu(c,currentTest.get(c)); }

        int qidx = idxFromCaption(txt);
        if (qidx < 0) return simple(c,"Выберите вопрос кнопкой.");

        pendingQuestionIdx.put(c, qidx);
        stage.put(c, Stage.ADD_A_TEXT);
        return ask(c,
                "Введите текст варианта и добавьте слово «правильный» или «верный»,\n" +
                        "если это правильный:");
    }
    private SendMessage handleAddAText(Long c, String txt) {
        int qidx = pendingQuestionIdx.remove(c);
        boolean correct = txt.toLowerCase().matches(".*(правильный|верный).*");
        String answer = txt.replaceAll("(?i)(правильный|верный)", "").trim();

        Question q = questionService.getQuestionsByTestId(currentTest.get(c).getId()).get(qidx);
        answerService.addAnswer(q, answer, correct);

        stage.put(c, Stage.MENU);
        return menu(c, currentTest.get(c));
    }
    private SendMessage deleteASelect(Long c, String txt) {
        if (CANCEL.equalsIgnoreCase(txt)) { stage.put(c, Stage.MENU); return menu(c,currentTest.get(c)); }

        int qidx = idxFromCaption(txt);
        if (qidx < 0) return simple(c,"Выберите вопрос кнопкой.");

        pendingQuestionIdx.put(c, qidx);
        stage.put(c, Stage.DELETE_A_OPTION);

        List<AnswerOption> opts = questionService.getQuestionsByTestId(currentTest.get(c).getId())
                .get(qidx).getAnswerOptions();
        List<KeyboardRow> rows = new ArrayList<>();
        for (int i = 1; i <= opts.size(); i++) rows.add(row(String.valueOf(i)));
        rows.add(row(CANCEL));

        return msg(c, "Выберите номер варианта для удаления:", keyboard(rows));
    }
    private SendMessage deleteAOption(long c, String txt) {
        if (CANCEL.equalsIgnoreCase(txt)) { stage.put(c, Stage.MENU); return menu(c,currentTest.get(c)); }

        int aidx = Integer.parseInt(txt.trim());
        int qidx = pendingQuestionIdx.get(c);

        Question q = questionService.getQuestionsByTestId(currentTest.get(c).getId()).get(qidx);
        answerService.deleteAnswer(q, aidx);

        pendingQuestionIdx.remove(c);
        stage.put(c, Stage.MENU);
        return menuWithPrefix(c, "🗑 Вариант ответа удалён.", currentTest.get(c));
    }
    private SendMessage setCorrectSelect(Long c, String txt) {
        if (CANCEL.equalsIgnoreCase(txt)) { stage.put(c, Stage.MENU); return menu(c,currentTest.get(c)); }

        int qidx = idxFromCaption(txt);
        if (qidx < 0) return simple(c,"Выберите вопрос кнопкой.");

        pendingQuestionIdx.put(c, qidx);
        stage.put(c, Stage.SET_CORRECT_OPTION);

        List<AnswerOption> opts = questionService.getQuestionsByTestId(currentTest.get(c).getId())
                .get(qidx).getAnswerOptions();
        List<KeyboardRow> rows = new ArrayList<>();
        for (int i = 1; i <= opts.size(); i++) rows.add(row(String.valueOf(i)));
        rows.add(row(CANCEL));

        return msg(c, "Выберите номер правильного варианта:", keyboard(rows));
    }
    private SendMessage setCorrectOption(long c, String txt) {
        if (CANCEL.equalsIgnoreCase(txt)) { stage.put(c, Stage.MENU); return menu(c,currentTest.get(c)); }

        int aidx = Integer.parseInt(txt.trim());
        int qidx = pendingQuestionIdx.get(c);

        Question q = questionService.getQuestionsByTestId(currentTest.get(c).getId()).get(qidx);
        answerService.setCorrectAnswer(q, aidx);

        pendingQuestionIdx.remove(c);
        stage.put(c, Stage.MENU);
        return menuWithPrefix(c, "✅ Правильный ответ установлен.", currentTest.get(c));
    }
    private int idxFromCaption(String cap) {
        if (cap.isEmpty() || !Character.isDigit(cap.charAt(0))) return -1;
        String num = cap.split("[).]", 2)[0].trim();
        return Integer.parseInt(num) - 1;          // 0-based
    }
    private SendMessage menuWithPrefix(long chat, String prefix, Test t) {
        return msg(chat,
                prefix + "\n\n🛠 *Редактор:* " + t.getTitle() + "\nЧто делаем?",
                kb(
                        row("Изменить название","Удалить тест"),
                        row("Добавить вопрос","Изменить вопрос","Удалить вопрос"),
                        row("Добавить вариант ответа","Удалить вариант ответа"),
                        row("Изменить правильный ответ"),
                        row("Назад","Главное меню")
                ));
    }
    private SendMessage ask(Long c, String t) { return simple(c,"ℹ️ "+t); }
    private SendMessage simple(Long c, String t) { return new SendMessage(String.valueOf(c), t); }
    private SendMessage msg(Long c, String t, ReplyKeyboardMarkup kb) {
        return SendMessage.builder()
                .chatId(String.valueOf(c))
                .text(t)
                .parseMode("Markdown")
                .replyMarkup(kb)
                .build();
    }
    private SendMessage promptQuestionSelection(Long c, String text) {
        List<Question> qs = questionService.getQuestionsByTestId(currentTest.get(c).getId());
        List<KeyboardRow> rows = new ArrayList<>();
        int n = 1;
        for (Question q : qs) {
            String cap = n + ") " + q.getText().replaceAll("\\R", " ").replaceAll(" {2,}", " ").strip();
            if (cap.length() > 50) cap = cap.substring(0, 47) + "…";
            rows.add(row(cap));
            n++;
        }
        rows.add(row(CANCEL));
        return msg(c, text, keyboard(rows));
    }
    private static KeyboardRow row(String... caps) {
        KeyboardRow r = new KeyboardRow();
        for (String cap : caps) r.add(new KeyboardButton(cap));
        return r;
    }
    private static ReplyKeyboardMarkup keyboard(List<KeyboardRow> rows) {
        ReplyKeyboardMarkup k = new ReplyKeyboardMarkup();
        k.setResizeKeyboard(true);
        k.setKeyboard(rows);
        return k;
    }
    private static ReplyKeyboardMarkup kb(KeyboardRow... rows) { return keyboard(Arrays.asList(rows)); }
}
