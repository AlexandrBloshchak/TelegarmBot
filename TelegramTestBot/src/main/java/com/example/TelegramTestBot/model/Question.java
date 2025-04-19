package com.example.TelegramTestBot.model;

import lombok.Data;
import jakarta.persistence.*;

@Data
@Entity
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String text;
    private String correctAnswer;
    private String options; // JSON массив вариантов

    @ManyToOne
    private Test test;
}