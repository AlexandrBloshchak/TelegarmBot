package com.example.TelegramTestBot.service;

import com.example.TelegramTestBot.model.TestResult;
import com.example.TelegramTestBot.repository.TestResultRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TestResultService {

    private final TestResultRepository testResultRepository;

    public TestResultService(TestResultRepository testResultRepository) {
        this.testResultRepository = testResultRepository;
    }

    /**
     * Получение всех результатов тестов для пользователя.
     * @param userId ID пользователя.
     * @return Список результатов тестов.
     */
    public List<TestResult> getCompletedTestsByUser(Long userId) {
        return testResultRepository.findByUserId(userId);
    }
}
