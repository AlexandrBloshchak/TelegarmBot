package com.example.TelegramTestBot.model;

import lombok.Data;
import jakarta.persistence.*;

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
}
