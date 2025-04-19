package com.example.TelegramTestBot.controller;

import com.example.TelegramTestBot.dto.QuestionDto;
import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;

public class TestSession {
    private String testName; // Переименовано с testTitle на testName для соответствия
    private List<QuestionDto> questions = new ArrayList<>();
    private TestCreationState state = TestCreationState.NONE;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivity;

    public TestSession() {
        this.createdAt = LocalDateTime.now();
        updateLastActivity();
    }

    public enum TestCreationState {
        NONE,
        AWAITING_TITLE,
        AWAITING_FILE,
        COMPLETE
    }

    // Добавляем необходимые методы
    public boolean hasTestName() {
        return testName != null && !testName.trim().isEmpty();
    }

    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
        updateLastActivity();
    }

    // Остальные методы класса
    public List<QuestionDto> getQuestions() {
        return new ArrayList<>(questions);
    }

    public void addQuestion(QuestionDto question) {
        this.questions.add(question);
        updateLastActivity();
    }

    public TestCreationState getState() {
        return state;
    }

    public void setState(TestCreationState state) {
        this.state = state;
        updateLastActivity();
    }

    private void updateLastActivity() {
        this.lastActivity = LocalDateTime.now();
    }

    // Дополнительные полезные методы
    public boolean hasQuestions() {
        return !questions.isEmpty();
    }

    public int getQuestionCount() {
        return questions.size();
    }
}