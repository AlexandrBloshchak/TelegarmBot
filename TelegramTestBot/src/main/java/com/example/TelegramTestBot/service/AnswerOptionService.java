package com.example.TelegramTestBot.service;

import com.example.TelegramTestBot.model.AnswerOption;
import com.example.TelegramTestBot.model.Question;
import com.example.TelegramTestBot.repository.AnswerOptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AnswerOptionService {

    private final AnswerOptionRepository answerOptionRepository;

    public AnswerOptionService(AnswerOptionRepository answerOptionRepository) {
        this.answerOptionRepository = answerOptionRepository;
    }

    /**
     * Получаем все варианты ответов для вопроса по его идентификатору
     */
    @Transactional(readOnly = true)
    public List<AnswerOption> getAnswersForQuestion(Long questionId) {
        return answerOptionRepository.findByQuestionId(questionId);
    }

    /**
     * Перегруженный метод: принимаем объект Question
     */
    @Transactional(readOnly = true)
    public List<AnswerOption> getAnswersForQuestion(Question question) {
        return getAnswersForQuestion(question.getId());
    }
}
