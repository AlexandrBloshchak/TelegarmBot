package com.example.TelegramTestBot.service;

import com.example.TelegramTestBot.model.AnswerOption;
import com.example.TelegramTestBot.model.Question;
import com.example.TelegramTestBot.model.Test;
import com.example.TelegramTestBot.model.User;
import com.example.TelegramTestBot.repository.AnswerOptionRepository;
import com.example.TelegramTestBot.repository.TestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TestCreationService {
    private final TestService testService;
    private final QuestionService questionService;
    private final AnswerOptionRepository answerOptionRepository;
    private final TestRepository testRepository;

    public TestCreationService(
            TestService testService,
            QuestionService questionService,
            AnswerOptionRepository answerOptionRepository,
            TestRepository testRepository
    ) {
        this.testService = testService;
        this.questionService = questionService;
        this.answerOptionRepository = answerOptionRepository;
        this.testRepository = testRepository;
    }

    @Transactional
    public void createTest(User user, Test test, List<Question> questions) {
        // Сохраняем сам тест
        test.setCreator(user);
        Test savedTest = testRepository.save(test);

        // Сохраняем вопросы и (при необходимости) варианты ответов
        for (Question question : questions) {
            question.setTest(savedTest);

            // Двусторонняя связь: чтобы JPA увидел, к какому вопросу привязан каждый вариант
            for (AnswerOption option : question.getAnswerOptions()) {
                option.setQuestion(question);
            }

            // Сохраняем вопрос — благодаря cascade=ALL, варианты ответов попадут в БД автоматически
            Question savedQuestion = questionService.save(question);

        }
    }
}
