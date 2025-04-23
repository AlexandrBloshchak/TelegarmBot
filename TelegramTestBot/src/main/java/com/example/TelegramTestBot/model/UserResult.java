package com.example.TelegramTestBot.model;

import lombok.Data;

/**
 * DTO для отображения результатов пользователей по тесту
 */
@Data
public class UserResult {
    private String displayName;
    private String testName;
    private Integer score;
    private Integer maxScore;
    /** Логин или имя пользователя */
    private String username;
    private Long userId;
    private double percentage;

    public UserResult(String displayName, double percentage, Long userId) {
        this.displayName = displayName;
        this.percentage  = percentage;
        this.userId      = userId;
    }
    public UserResult(String testName, Integer score, Integer maxScore) {
        this.testName = testName;
        this.score = score;
        this.maxScore = maxScore;
        this.percentage = (score * 100.0) / maxScore;
    }
    public String getTestName() { return testName; }
    public void setTestName(String testName) { this.testName = testName; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public Integer getMaxScore() { return maxScore; }
    public void setMaxScore(Integer maxScore) { this.maxScore = maxScore; }

    public Long getUserId() {
        return userId;
    }
    public String getDisplayName() { return displayName; }
    public UserResult(String displayName, String username, double percentage) {
        this.displayName = displayName;
        this.username = username;
        this.percentage = percentage;
    }
}