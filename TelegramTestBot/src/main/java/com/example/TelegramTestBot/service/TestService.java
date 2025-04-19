package com.example.TelegramTestBot.service;

import com.example.TelegramTestBot.model.*;
import com.example.TelegramTestBot.repository.TestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TestService {
    private final TestRepository testRepository;
    private final QuestionService questionService;

    public TestService(TestRepository testRepository, QuestionService questionService) {
        this.testRepository = testRepository;
        this.questionService = questionService;
    }

    @Transactional
    public Test createNewTest(User creator, String testTitle) {
        Test test = new Test();
        test.setCreator(creator);
        test.setTitle(testTitle);
        test.setStatus(Test.TestStatus.DRAFT);  // Тест по умолчанию будет в статусе DRAFT
        return testRepository.save(test);
    }

    public List<Test> getAvailableTests(User user) {
        if ("CREATOR".equals(user.getRole())) {
            return testRepository.findByCreatorId(user.getId());
        }
        return testRepository.findByStatus(Test.TestStatus.PUBLISHED);
    }

    @Transactional
    public void addQuestionsToTest(Long testId, List<Question> questions) {
        testRepository.findById(testId).ifPresent(test -> {
            for (Question question : questions) {
                question.setTest(test);  // Привязываем вопрос к тесту
                questionService.save(question);  // Сохраняем вопрос
            }
            test.setStatus(Test.TestStatus.PUBLISHED);  // После добавления вопросов можно опубликовать тест
            testRepository.save(test);
        });
    }

    @Transactional
    public Test saveTest(Test test) {
        return testRepository.save(test);  // Save and return the test object
    }


    public TestResult evaluateTest(Test test, User user, List<String> answers) {
        int score = 0;
        List<Question> questions = test.getQuestions();

        for (int i = 0; i < questions.size(); i++) {
            if (questions.get(i).getCorrectAnswer().equals(answers.get(i))) {
                score++;
            }
        }

        TestResult result = new TestResult();
        result.setUser(user);
        result.setTest(test);
        result.setScore(score);
        result.setMaxScore(questions.size());
        result.setCompletionDate(LocalDateTime.now());

        return result;
    }

    // Сохранение вопроса в базе данных
    public void saveQuestion(Question question) {
        questionService.save(question);  // Используем QuestionService для сохранения
    }

    // Метод для парсинга файла и сохранения вопросов из файла
    public List<Question> parseFileAndSaveQuestions(String filePath, Test test) {
        List<Question> questions = parseFile(filePath);  // Парсим файл и получаем список вопросов

        for (Question question : questions) {
            question.setTest(test);  // Привязываем вопросы к тесту
            questionService.save(question);  // Сохраняем вопросы
        }
        return questions;
    }

    // Пример метода парсинга файла (заглушка для DOCX)
    private List<Question> parseFile(String filePath) {
        // Для реальной реализации добавьте логику парсинга файла в формат CSV/DOCX
        return List.of(
                new Question("Что такое Java?", "Программный язык", null),
                new Question("Что такое Spring?", "Фреймворк для разработки", null)
        );
    }
}
