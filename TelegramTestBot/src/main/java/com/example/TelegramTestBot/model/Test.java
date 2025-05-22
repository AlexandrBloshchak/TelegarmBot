package com.example.TelegramTestBot.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "test")
public class Test {

    public enum TestStatus {
        DRAFT,
        PUBLISHED,
        CLOSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TestStatus status = TestStatus.DRAFT;

    @Column(name = "show_answers", nullable = false)
    private Boolean showAnswers = true;   // по умолчанию ответы открыты

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @OneToMany(mappedBy = "test",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<Question> questions = new ArrayList<>();

    @OneToMany(mappedBy = "test",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<TestResult> results = new ArrayList<>();

    public Test() {}

    public Long getId()                      { return id; }
    public String getTitle()                 { return title; }
    public void setTitle(String title)       { this.title = title; }

    public String getDescription()           { return description; }
    public void setDescription(String d)     { this.description = d; }

    public TestStatus getStatus()            { return status; }
    public void setStatus(TestStatus s)      { this.status = s; }

    public Boolean getShowAnswers()          { return showAnswers; }
    public void   setShowAnswers(Boolean f)  { this.showAnswers = f; }

    public User getCreator()                 { return creator; }
    public void setCreator(User c)           { this.creator = c; }

    public List<Question> getQuestions()     { return questions; }
    public List<TestResult> getResults()     { return results; }

    public void addQuestion(Question q) {
        questions.add(q);
        q.setTest(this);
    }
    public void removeQuestion(Question q) {
        questions.remove(q);
        q.setTest(null);
    }
}
