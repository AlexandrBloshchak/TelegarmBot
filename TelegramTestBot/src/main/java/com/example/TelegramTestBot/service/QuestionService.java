package com.example.TelegramTestBot.service;

import com.example.TelegramTestBot.model.Question;
import com.example.TelegramTestBot.repository.QuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionService {

    private final QuestionRepository questionRepository;

    // Конструктор для внедрения репозитория
    public QuestionService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    @Transactional
    public Question save(Question question) {
        return questionRepository.save(question);  // Сохраняем вопрос в базе данных
    }
}
