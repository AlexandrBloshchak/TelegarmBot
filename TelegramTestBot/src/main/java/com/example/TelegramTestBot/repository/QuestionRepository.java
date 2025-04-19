package com.example.TelegramTestBot.repository;

import com.example.TelegramTestBot.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    // Здесь можно добавить дополнительные запросы к базе данных, если потребуется
}
