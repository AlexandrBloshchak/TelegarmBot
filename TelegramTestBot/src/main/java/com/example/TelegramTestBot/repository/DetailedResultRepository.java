package com.example.TelegramTestBot.repository;

import com.example.TelegramTestBot.model.DetailedResult;
import com.example.TelegramTestBot.model.TestResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DetailedResultRepository extends JpaRepository<DetailedResult, Long> {
    List<DetailedResult> findByTestResultOrderByQuestionIndex(TestResult testResult);
}
