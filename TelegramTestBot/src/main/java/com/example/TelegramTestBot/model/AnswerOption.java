package com.example.TelegramTestBot.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "answer_option")
public class AnswerOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(name = "option_number")
    private Integer optionNumber;

    @Column(name = "is_correct")
    private Boolean isCorrect = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    public AnswerOption(String text, Integer optionNumber, Boolean isCorrect) {
        this.text = text;
        this.optionNumber = optionNumber;
        this.isCorrect = isCorrect;
    }
}
