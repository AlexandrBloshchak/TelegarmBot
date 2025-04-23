package com.example.TelegramTestBot.repository;

import com.example.TelegramTestBot.model.Question;
import com.example.TelegramTestBot.model.Test;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    /**
     * Возвращает все варианты вопросов (с опциями) по test_id, без сортировки.
     */
    @EntityGraph(attributePaths = {"answerOptions"})
    List<Question> findByTestId(Long testId);

    /**
     * То же, но в порядке возрастания id вопроса.
     */
    @EntityGraph(attributePaths = {"answerOptions"})
    @Query("SELECT q FROM Question q WHERE q.test.id = :testId ORDER BY q.id")
    List<Question> findQuestionsByTestIdOrdered(@Param("testId") Long testId);

    /**
     * Возвращает все вопросы (с опциями) для конкретного объекта Test.
     */
    @EntityGraph(attributePaths = {"answerOptions"})
    List<Question> findAllByTest(Test test);

    /**
     * Количество вопросов в тесте.
     */
    @Query("SELECT COUNT(q) FROM Question q WHERE q.test.id = :testId")
    long countByTestId(@Param("testId") Long testId);
}
