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

    public TestService(TestRepository testRepository) {
        this.testRepository = testRepository;
    }

    @Transactional
    public Test createNewTest(User creator) {
        Test test = new Test();
        test.setCreator(creator);
        return testRepository.save(test);
    }

    public List<Test> getAvailableTests(User user) {
        if (user.getRole() == User.Role.CREATOR) {
            return testRepository.findByCreatorId(user.getId());
        }
        return testRepository.findByStatus(Test.TestStatus.PUBLISHED);
    }

    public void addQuestionToTest(Long testId, Question question) {
        testRepository.findById(testId).ifPresent(test -> {
            test.getQuestions().add(question);
            testRepository.save(test);
        });
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
}