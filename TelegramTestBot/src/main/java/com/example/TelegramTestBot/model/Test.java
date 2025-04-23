package com.example.TelegramTestBot.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "test")
public class Test {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Отображаемое имя теста */
    @Column(name = "name", nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    @ToString.Exclude
    private User creator;

    @OneToMany(
            mappedBy = "test",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @ToString.Exclude
    private List<Question> questions = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private TestStatus status = TestStatus.DRAFT;

    public enum TestStatus {
        DRAFT, PUBLISHED, ARCHIVED
    }

    /** Добавляем вопрос и автоматически ставим связь question.test */
    public void addQuestion(Question q) {
        questions.add(q);
        q.setTest(this);
    }
}
