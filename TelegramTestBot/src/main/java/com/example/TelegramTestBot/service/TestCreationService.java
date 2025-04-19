package com.example.TelegramTestBot.service;

import com.example.TelegramTestBot.model.Test;
import com.example.TelegramTestBot.model.User;
import com.example.TelegramTestBot.dto.QuestionDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TestCreationService {

    private final TestService testService;

    public TestCreationService(TestService testService) {
        this.testService = testService;
    }

    // Метод для обработки создания теста
    public Test createTest(User creator, String testTitle, List<QuestionDto> questions) {
        // Создаем новый тест
        Test test = testService.createNewTest(creator, testTitle);

        // Обрабатываем и сохраняем вопросы в тест
        for (QuestionDto questionDto : questions) {
            testService.addQuestionToTest(test.getId(), questionDto.toQuestion(test));
        }

        // Возвращаем созданный тест
        return test;
    }

}
