package com.example.TelegramTestBot.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "question")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Текст вопроса */
    @Column(columnDefinition = "TEXT")
    private String text;

    /** Номер правильного ответа (если нужно иметь дублирующий готовый доступ) */
    @Column(name = "correct_answer")
    private Integer correctAnswer;

    /** Список вариантов — сохраняются каскадом вместе с вопросом */
    @OneToMany(
            mappedBy = "question",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER
    )
    private List<AnswerOption> answerOptions = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_id", nullable = false)
    @ToString.Exclude
    private Test test;

    public void addAnswerOption(AnswerOption opt) {
        answerOptions.add(opt);
        opt.setQuestion(this);
    }
}
