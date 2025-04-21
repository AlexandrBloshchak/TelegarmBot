package com.example.TelegramTestBot.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor  // Lombok сгенерирует public AnswerOption() {}
@Entity
@Table(name = "answer_option")
public class AnswerOption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String text;
    private Integer optionNumber;

    @Column(name = "is_correct")
    private Boolean isCorrect = false;

    @ManyToOne
    @JoinColumn(name = "question_id")
    private Question question;

    // Конструктор для удобства создания
    public AnswerOption(String text, Integer optionNumber, Boolean isCorrect) {
        this.text = text;
        this.optionNumber = optionNumber;
        this.isCorrect = isCorrect;
    }
}
