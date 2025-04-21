package com.example.TelegramTestBot.dto;

import com.example.TelegramTestBot.model.AnswerOption;
import com.example.TelegramTestBot.model.Question;
import com.example.TelegramTestBot.model.Test;
import java.util.ArrayList;
import java.util.List;

public class QuestionDto {
    private String questionText;
    private String correctAnswer;
    private List<String> options;

    // Конструкторы, геттеры и сеттеры...

    public Question toQuestion(Test test) {
        Question question = new Question();
        question.setText(this.questionText);
        question.setTest(test);

        List<AnswerOption> answerOptions = new ArrayList<>();
        if (options != null && !options.isEmpty()) {
            for (int i = 0; i < options.size(); i++) {
                boolean correct = correctAnswer.equals(String.valueOf(i + 1));
                AnswerOption option = new AnswerOption(options.get(i), i + 1, correct);
                answerOptions.add(option);
            }
        }
        question.setAnswerOptions(answerOptions);

        return question;
    }
}
