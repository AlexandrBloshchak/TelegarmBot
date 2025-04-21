package com.example.TelegramTestBot.controller;

import com.example.TelegramTestBot.model.Question;
import com.example.TelegramTestBot.model.Test;

import java.util.List;

// Вспомогательный класс для хранения состояния прохождения теста
public class TestSession {
    private final Test test;
    private final List<Question> questions;
    private int index = 0;
    private int correct = 0;

    TestSession(Test test, List<Question> questions) {
        this.test = test;
        this.questions = questions;
    }

    Question getCurrentQuestion() {
        return questions.get(index);
    }

    int getCurrentIndex() {
        return index;
    }

    void incrementCorrect() {
        correct++;
    }

    void nextQuestion() {
        index++;
    }

    boolean hasNext() {
        return index < questions.size();
    }

    int getCorrectCount() {
        return correct;
    }

    int getTotalQuestions() {
        return questions.size();
    }
}