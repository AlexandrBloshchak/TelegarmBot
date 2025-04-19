package com.example.TelegramTestBot.model;

import lombok.Data;
import jakarta.persistence.*;

import java.util.List;

@Data
@Entity
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String text;             // Текст вопроса
    private String correctAnswer;    // Правильный ответ
    private String options;          // JSON массив вариантов (для множественного выбора)

    @ManyToOne
    private Test test;  // Связь с тестом

    // Конструктор для создания вопроса с текстом и правильным ответом
    public Question(String text, String correctAnswer, String options) {
        this.text = text;
        this.correctAnswer = correctAnswer;
        this.options = options;
    }

    // Пустой конструктор для JPA
    public Question() {
    }
}
