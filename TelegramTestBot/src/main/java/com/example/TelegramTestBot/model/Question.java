package com.example.TelegramTestBot.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "question")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Текст вопроса
    private String text;

    // Двусторонняя связь с AnswerOption (вариантами ответов).
    // CascadeType.ALL гарантирует, что при сохранении Question сохраняются и связанные AnswerOption.
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AnswerOption> answerOptions = new ArrayList<>();

    // Связь с тестом, для которого создан вопрос.
    @ManyToOne
    @JoinColumn(name = "test_id")
    private Test test;
    private List<String> options;
    public List<String> getOptions() {
        return options;
    }
    // Дополнительный геттер, если требуется использовать getQuestionText() вместо getText()
    public String getQuestionText() {
        return text;
    }
}
