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

    /* ────────── сервисы ────────── */
    private final TestService         testService;
    private final QuestionService     questionService;
    private final AnswerOptionService answerService;

    /* ────────── состояние ───────── */
    private final Map<Long, Test>  currentTest = new ConcurrentHashMap<>();
    private final Map<Long, Stage> stage       = new ConcurrentHashMap<>();

    private enum Stage { MENU, RENAME, ADD_Q, EDIT_Q, DELETE_Q,
        ADD_A, DELETE_A, SET_CORRECT }

    /* ═════════ PUBLIC ═════════ */

    public SendMessage startEditor(Long chat, Test t) {
        currentTest.put(chat, t);
        stage.put(chat, Stage.MENU);
        return menu(chat, t);
    }
    public boolean isInside(Long chat) { return currentTest.containsKey(chat); }

    public SendMessage handle(Update up) {
        long   chat = up.getMessage().getChatId();
        String txt  = up.getMessage().getText().trim();

        if (txt.equalsIgnoreCase("назад")) {
            stage.put(chat, Stage.MENU);
            return menu(chat, currentTest.get(chat));
        }
        return switch (stage.get(chat)) {
            case MENU        -> menuClick(chat, txt);
            case RENAME      -> rename(chat, txt);
            case ADD_Q       -> addQ(chat, txt);
            case EDIT_Q      -> editQ(chat, txt);
            case DELETE_Q    -> delQ(chat, txt);
            case ADD_A       -> addA(chat, txt);
            case DELETE_A    -> delA(chat, txt);
            case SET_CORRECT -> setCorrect(chat, txt);
        };
    }

    /* ═════ меню ═════ */

    private SendMessage menu(Long c, Test t) {
        return msg(c,
                "🛠 *Редактор:* " + t.getTitle() + "\nЧто делаем?",
                kb(row("Изменить название", "Удалить тест"),
                        row("Добавить вопрос", "Изменить вопрос"),
                        row("Удалить вопрос"),
                        row("Добавить вариант ответа", "Удалить вариант ответа"),
                        row("Изменить правильный ответ"),
                        row("Назад", "Главное меню")));
    }
    private SendMessage menuClick(Long c, String txt) {
        return switch (txt.toLowerCase()) {
            case "изменить название" -> { stage.put(c, Stage.RENAME); yield ask(c,"Новое название:"); }
            case "удалить тест"      -> { testService.deleteTest(currentTest.remove(c)); stage.remove(c);
                yield simple(c,"✅ Тест удалён."); }
            case "добавить вопрос"         -> { stage.put(c, Stage.ADD_Q);  yield ask(c,"Текст вопроса:"); }
            case "изменить вопрос"         -> { stage.put(c, Stage.EDIT_Q); yield ask(c,"№ | новый текст"); }
            case "удалить вопрос"          -> { stage.put(c, Stage.DELETE_Q);yield ask(c,"№ вопроса"); }
            case "добавить вариант ответа" -> { stage.put(c, Stage.ADD_A);  yield ask(c,"№ | текст | +"); }
            case "удалить вариант ответа"  -> { stage.put(c, Stage.DELETE_A);yield ask(c,"№ вопр | № варианта"); }
            case "изменить правильный ответ"->{ stage.put(c, Stage.SET_CORRECT);yield ask(c,"№ вопр | № варианта"); }
            case "главное меню"            -> { currentTest.remove(c); stage.remove(c);
                yield simple(c,"Вернулись в главное меню."); }
            default -> simple(c,"Не понимаю, используйте кнопки.");
        };
    }

    /* ═════ операции ═════ */

    private SendMessage rename(Long c,String t){ testService.renameTest(currentTest.get(c),t);
        stage.put(c,Stage.MENU); return simple(c,"✅ Название изменено."); }

    private SendMessage addQ(Long c,String t){ questionService.addQuestion(currentTest.get(c),t);
        stage.put(c,Stage.MENU); return simple(c,"➕ Вопрос добавлен."); }

    private SendMessage editQ(Long c,String s){
        String[] p=s.split("\\|",2); if(p.length<2) return ask(c,"№ | текст");
        int idx=Integer.parseInt(p[0].trim())-1;
        List<Question> qs=questionService.getQuestionsByTestId(currentTest.get(c).getId());
        if(idx<0||idx>=qs.size()) return simple(c,"Неверный №");
        questionService.updateQuestion(qs.get(idx),p[1].trim());
        stage.put(c,Stage.MENU); return simple(c,"✏️ Вопрос обновлён."); }

    private SendMessage delQ(Long c,String s){
        int idx=Integer.parseInt(s.trim())-1;
        List<Question> qs=questionService.getQuestionsByTestId(currentTest.get(c).getId());
        if(idx<0||idx>=qs.size()) return simple(c,"Неверный №");
        questionService.deleteQuestion(qs.get(idx).getId());
        stage.put(c,Stage.MENU); return simple(c,"🗑 Вопрос удалён."); }

    private SendMessage addA(Long c,String s){
        String[] p=s.split("\\|"); if(p.length<3) return ask(c,"№|текст|+");
        int qIdx=Integer.parseInt(p[0].trim())-1;
        boolean ok=p[2].trim().equals("+");
        List<Question> qs=questionService.getQuestionsByTestId(currentTest.get(c).getId());
        if(qIdx<0||qIdx>=qs.size()) return simple(c,"Неверный № вопроса");
        answerService.addAnswer(qs.get(qIdx),p[1].trim(),ok);
        stage.put(c,Stage.MENU); return simple(c,"➕ Добавлено."); }

    private SendMessage delA(Long c,String s){
        String[] p=s.split("\\|"); if(p.length<2) return ask(c,"№|№");
        int qIdx=Integer.parseInt(p[0].trim())-1;
        int aIdx=Integer.parseInt(p[1].trim());
        List<Question> qs=questionService.getQuestionsByTestId(currentTest.get(c).getId());
        if(qIdx<0||qIdx>=qs.size()) return simple(c,"Неверный № вопроса");
        answerService.deleteAnswer(qs.get(qIdx),aIdx);
        stage.put(c,Stage.MENU); return simple(c,"🗑 Удалено."); }

    private SendMessage setCorrect(Long c,String s){
        String[] p=s.split("\\|"); if(p.length<2) return ask(c,"№|№");
        int qIdx=Integer.parseInt(p[0].trim())-1;
        int aIdx=Integer.parseInt(p[1].trim());
        List<Question> qs=questionService.getQuestionsByTestId(currentTest.get(c).getId());
        if(qIdx<0||qIdx>=qs.size()) return simple(c,"Неверный № вопроса");
        answerService.setCorrectAnswer(qs.get(qIdx),aIdx);
        stage.put(c,Stage.MENU); return simple(c,"✅ Обновлено."); }

    private SendMessage ask(Long c, String t)   { return simple(c, "ℹ️ " + t); }
    private SendMessage simple(Long c, String t){ return new SendMessage(c.toString(), t); }

    private SendMessage msg(Long c, String t, ReplyKeyboardMarkup kb) {
        SendMessage m = new SendMessage(c.toString(), t);
        m.setParseMode("Markdown");
        m.setReplyMarkup(kb);
        return m;
    }

    /** Создаёт строку клавиатуры из произвольного количества подписей */
    private static KeyboardRow row(String... captions) {
        KeyboardRow r = new KeyboardRow();
        for (String cap : captions) {
            r.add(new KeyboardButton(cap));      // <-- оборачиваем строку в кнопку
        }
        return r;
    }

    /** Создаёт готовый ReplyKeyboardMarkup */
    private static ReplyKeyboardMarkup kb(KeyboardRow... rows) {
        ReplyKeyboardMarkup k = new ReplyKeyboardMarkup();
        k.setResizeKeyboard(true);
        k.setKeyboard(Arrays.asList(rows));
        return k;
    }
}
