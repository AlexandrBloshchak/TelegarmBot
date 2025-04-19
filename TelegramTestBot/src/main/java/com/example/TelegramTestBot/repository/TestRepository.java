package com.example.TelegramTestBot.repository;

import com.example.TelegramTestBot.model.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TestRepository extends JpaRepository<Test, Long> {
    List<Test> findByStatus(Test.TestStatus status);
    List<Test> findByCreatorId(Long creatorId);
}