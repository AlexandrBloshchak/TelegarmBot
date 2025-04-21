package com.example.TelegramTestBot.repository;

import com.example.TelegramTestBot.model.AnswerOption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AnswerOptionRepository extends JpaRepository<AnswerOption, Long> {
    List<AnswerOption> findByQuestionId(Long questionId);  // Получение вариантов ответов для вопроса
}
