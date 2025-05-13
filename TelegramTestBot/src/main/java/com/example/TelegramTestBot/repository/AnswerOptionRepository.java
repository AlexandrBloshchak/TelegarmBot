package com.example.TelegramTestBot.repository;

import com.example.TelegramTestBot.model.AnswerOption;
import com.example.TelegramTestBot.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnswerOptionRepository extends JpaRepository<AnswerOption, Long> {
    List<AnswerOption> findByQuestionId(Long questionId);
    List<AnswerOption> findByQuestionOrderByOptionNumber(Question question);
    void deleteByQuestionAndOptionNumber(Question question, Integer optionNumber);
}
