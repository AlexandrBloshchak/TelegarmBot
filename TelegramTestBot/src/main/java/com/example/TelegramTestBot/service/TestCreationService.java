package com.example.TelegramTestBot.service;
import com.example.TelegramTestBot.model.Question;
import com.example.TelegramTestBot.model.Test;
import com.example.TelegramTestBot.model.User;
import com.example.TelegramTestBot.repository.AnswerOptionRepository;
import com.example.TelegramTestBot.repository.QuestionRepository;
import com.example.TelegramTestBot.repository.TestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TestCreationService {
    private final TestRepository testRepository;

    public TestCreationService(TestRepository testRepository,
                               QuestionRepository questionRepository,
                               AnswerOptionRepository answerOptionRepository) {
        this.testRepository = testRepository;
    }

    @Transactional
    public void createTest(User creator, Test test, List<Question> questions) {
        test.setCreator(creator);
        test.setStatus(Test.TestStatus.DRAFT);
        for (Question q : questions) {
            test.addQuestion(q);
        }
        testRepository.save(test);
    }
    @Transactional(readOnly = true)
    public List<Test> getTestsCreatedByUser(User creator) {
        return testRepository.findByCreator(creator);
    }
}