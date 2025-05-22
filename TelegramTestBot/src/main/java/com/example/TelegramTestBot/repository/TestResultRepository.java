package com.example.TelegramTestBot.repository;

import com.example.TelegramTestBot.model.TestResult;
import com.example.TelegramTestBot.model.Test;
import com.example.TelegramTestBot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestResultRepository extends JpaRepository<TestResult, Long> {
    List<TestResult> findByTestAndUser(Test test, User user);
    List<TestResult> findByUserId(Long userId);
    List<TestResult> findByTest(Test test);
}