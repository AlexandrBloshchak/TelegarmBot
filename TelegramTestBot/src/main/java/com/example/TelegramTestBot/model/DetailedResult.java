package com.example.TelegramTestBot.model;

import jakarta.persistence.*;

@Entity
@Table(name = "detailed_result")
public class DetailedResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_result_id", nullable = false)
    private TestResult testResult;

    @ManyToOne(fetch = FetchType.EAGER)      // <— стало EAGER
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;
    @Column(name = "question_index", nullable = false)
    private Integer questionIndex;
    @Column(name = "user_answer", nullable = false)
    private Integer userAnswer;
    @Column(name = "correct_answer", nullable = false)
    private Integer correctAnswer;

    /** Баллы за этот вопрос: 1 или 0 */
    @Column(nullable = false)
    private Integer points;

    public void setTestResult(TestResult testResult) { this.testResult = testResult; }

    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }

    public Integer getQuestionIndex() { return questionIndex; }
    public void setQuestionIndex(Integer questionIndex) { this.questionIndex = questionIndex; }

    public Integer getUserAnswer() { return userAnswer; }
    public void setUserAnswer(Integer userAnswer) { this.userAnswer = userAnswer; }

    public Integer getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(Integer correctAnswer) { this.correctAnswer = correctAnswer; }

    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }
}
