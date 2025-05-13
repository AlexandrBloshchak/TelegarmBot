package com.example.TelegramTestBot.service;

import com.example.TelegramTestBot.model.AnswerOption;
import com.example.TelegramTestBot.model.Question;
import com.example.TelegramTestBot.repository.AnswerOptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Сервис для управления вариантами ответов.
 */
@Service
@RequiredArgsConstructor
public class AnswerOptionService {

    private final AnswerOptionRepository answerOptionRepository;

    /* ─────────────── чтение ─────────────── */

    @Transactional(readOnly = true)
    public List<AnswerOption> getAnswersForQuestion(Long questionId) {
        return answerOptionRepository.findByQuestionId(questionId);
    }

    @Transactional(readOnly = true)
    public List<AnswerOption> getAnswersForQuestion(Question question) {
        return answerOptionRepository.findByQuestionOrderByOptionNumber(question);
    }

    /* ─────────────── создание ─────────────── */

    /**
     * Добавить вариант ответа.
     * @param question  вопрос-владелец
     * @param text      текст нового варианта
     * @param correct   делать ли его правильным
     */
    @Transactional
    public AnswerOption addAnswer(Question question,
                                  String   text,
                                  boolean  correct) {

        int nextNumber = getAnswersForQuestion(question).size() + 1;

        AnswerOption option = new AnswerOption(text, nextNumber, correct);
        option.setQuestion(question);

        // если добавляемый вариант отмечен как правильный - снимаем отметку с других
        if (correct) {
            setCorrectAnswer(question, nextNumber);
        }

        return answerOptionRepository.save(option);
    }

    /* ─────────────── удаление ─────────────── */

    /**
     * Удалить вариант ответа по его номеру (1-based).
     * После удаления происходит автоматическая перенумерация.
     */
    @Transactional
    public void deleteAnswer(Question question, int optionNumber) {

        answerOptionRepository.deleteByQuestionAndOptionNumber(question, optionNumber);

        // Перенумеровываем оставшиеся варианты «1…N»
        List<AnswerOption> remaining =
                answerOptionRepository.findByQuestionOrderByOptionNumber(question);

        int idx = 1;
        for (AnswerOption ao : remaining) {
            if (ao.getOptionNumber() != idx) {
                ao.setOptionNumber(idx);
                answerOptionRepository.save(ao);
            }
            idx++;
        }

        // если удалили правильный ответ – сбрасываем признак
        if (question.getCorrectAnswer() != null
                && question.getCorrectAnswer() == optionNumber) {
            question.setCorrectAnswer(null);
        }
    }

    /* ─────────────── изменение правильного ответа ─────────────── */

    /**
     * Сделать указан­ный номер правильным.
     * @param optionNumber  номер (1-based)
     */
    @Transactional
    public void setCorrectAnswer(Question question, int optionNumber) {

        List<AnswerOption> options =
                answerOptionRepository.findByQuestionOrderByOptionNumber(question);

        for (AnswerOption ao : options) {
            ao.setIsCorrect(ao.getOptionNumber() == optionNumber);
            answerOptionRepository.save(ao);
        }
        question.setCorrectAnswer(optionNumber);
    }
}
