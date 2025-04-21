package com.example.TelegramTestBot.service;

import com.example.TelegramTestBot.model.*;
import com.example.TelegramTestBot.repository.AnswerOptionRepository;
import com.example.TelegramTestBot.repository.QuestionRepository;
import com.example.TelegramTestBot.repository.TestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class TestService {
    private final TestRepository testRepository;
    private final QuestionService questionService;
    private final QuestionRepository questionRepository;
    private final AnswerOptionRepository answerOptionRepository;
    public TestService(TestRepository testRepository, QuestionService questionService, QuestionRepository questionRepository, AnswerOptionRepository answerOptionRepository) {
        this.testRepository = testRepository;
        this.questionService = questionService;
        this.questionRepository = questionRepository;
        this.answerOptionRepository = answerOptionRepository;
    }
    public List<Test> getTestsByTitle(String title) {
        return testRepository.findByTitleIgnoreCase(title);  // Ищем тесты по названию (игнорируя регистр)
    }

    @Transactional
    public Test createNewTest(User creator, String testTitle) {
        Test test = new Test();
        test.setCreator(creator);
        test.setTitle(testTitle);
        test.setStatus(Test.TestStatus.DRAFT);
        return testRepository.save(test);
    }
    public List<Question> getTestQuestionsByTitle(String title) {
        Test test = testRepository.findByTitleIgnoreCase(title)
                .stream().findFirst().orElse(null);  // Получаем первый тест по названию

        if (test != null) {
            return questionRepository.findByTestId(test.getId());  // Получаем вопросы для этого теста
        }
        return Collections.emptyList();  // Если тест не найден, возвращаем пустой список
    }
    public List<AnswerOption> getAnswersForQuestion(Question question) {
        return answerOptionRepository.findByQuestionId(question.getId());  // Получаем варианты ответов для вопроса
    }

    public List<Test> getAvailableTests(User user) {
        return testRepository.findAll();
    }

    @Transactional
    public void addQuestionsToTest(Long testId, List<Question> questions) {
        testRepository.findById(testId).ifPresent(test -> {
            for (Question question : questions) {
                question.setTest(test);
                questionService.save(question);
            }
            test.setStatus(Test.TestStatus.PUBLISHED);
            testRepository.save(test);
        });
    }

    @Transactional
    public Test saveTest(Test test) {
        return testRepository.save(test);
    }
    @Transactional(readOnly = true)
    public List<Question> getTestQuestions(Test test) {
        // Если список вопросов загружен лениво, можно явно запросить его,
        // либо использовать дополнительный запрос через questionService или репозиторий.
        return test.getQuestions();
    }

    @Transactional
    public void addQuestionToTest(Long testId, Question question) {
        testRepository.findById(testId).ifPresent(test -> {
            question.setTest(test);
            questionService.save(question);
            test.setStatus(Test.TestStatus.PUBLISHED);
            testRepository.save(test);
        });
    }
}