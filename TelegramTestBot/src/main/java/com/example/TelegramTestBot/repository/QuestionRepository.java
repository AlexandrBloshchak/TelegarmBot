package com.example.TelegramTestBot.repository;

import com.example.TelegramTestBot.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByTestId(Long testId);

    @Query("SELECT q FROM Question q WHERE q.test.id = :testId ORDER BY q.id")
    List<Question> findQuestionsByTestIdOrdered(@Param("testId") Long testId);

    long countByTestId(Long testId);
}