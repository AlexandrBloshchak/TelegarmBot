package com.example.TelegramTestBot.dto;

import com.example.TelegramTestBot.model.Question;
import com.example.TelegramTestBot.model.Test;

public class QuestionDto {
    private String questionText;
    private String correctAnswer;

    public QuestionDto() {}

    public QuestionDto(String questionText, String correctAnswer) {
        this.questionText = questionText;
        this.correctAnswer = correctAnswer;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    // Обновленный метод для создания Question с привязкой к Test
    public Question toQuestion(Test test) {
        Question question = new Question();
        question.setText(this.questionText);      // Устанавливаем текст вопроса
        question.setCorrectAnswer(this.correctAnswer); // Устанавливаем правильный ответ
        question.setTest(test);  // Привязываем тест к вопросу
        return question;
    }
}
