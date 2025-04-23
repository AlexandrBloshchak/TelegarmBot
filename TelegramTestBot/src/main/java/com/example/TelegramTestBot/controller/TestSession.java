package com.example.TelegramTestBot.controller;

import com.example.TelegramTestBot.model.Question;
import com.example.TelegramTestBot.model.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestSession {
    private final Test test;
    private final List<Question> questions;
    private final List<Integer> userAnswers = new ArrayList<>();
    private int currentIndex = 0;
    private int correctCount = 0;

    public TestSession(Test test, List<Question> questions) {
        this.test = test;
        this.questions = new ArrayList<>(questions);
    }

    public Test getTest() { return test; }

    /** Перемешать вопросы перед началом */
    public void shuffleQuestions() {
        Collections.shuffle(questions);
        resetProgress();
    }

    /** Переход к следующему вопросу, возвращает true если ещё вопросы остались */
    public boolean nextQuestion() {
        return ++currentIndex < questions.size();
    }

    public void incrementCorrect() { correctCount++; }
    public int getCorrectCount() { return correctCount; }
    public int getTotalQuestions() { return questions.size(); }
    public Question getCurrentQuestion() { return questions.get(currentIndex); }
    public int getCurrentIndex() { return currentIndex; }
    public List<Question> getAllQuestions() { return questions; }
    public List<Integer> getUserAnswers() { return userAnswers; }

    /** Сбросить прогресс (для повторного прохождения) */
    public void resetProgress() {
        userAnswers.clear();
        currentIndex = 0;
        correctCount = 0;
    }

    /** Проверить, завершён ли тест */
    public boolean isCompleted() {
        // когда индекс выходит за границы, тест считается завершённым
        return currentIndex >= questions.size();
    }

    /** Удобный геттер для счёта */
    public int getScore() {
        return correctCount;
    }
}
