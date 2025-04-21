package com.example.TelegramTestBot.service;

import com.example.TelegramTestBot.model.AnswerOption;
import com.example.TelegramTestBot.repository.AnswerOptionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnswerOptionService {
    private final AnswerOptionRepository answerOptionRepository;

    public AnswerOptionService(AnswerOptionRepository answerOptionRepository) {
        this.answerOptionRepository = answerOptionRepository;
    }

    // Получаем все варианты ответов для вопроса
    public List<AnswerOption> getAnswersForQuestion(Long questionId) {
        return answerOptionRepository.findByQuestionId(questionId);  // Используем questionId для поиска
    }
}
