package com.example.TelegramTestBot.repository;

import com.example.TelegramTestBot.model.Test;
import com.example.TelegramTestBot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TestRepository extends JpaRepository<Test, Long> {
    List<Test> findByStatus(Test.TestStatus status);
    List<Test> findByCreatorId(Long creatorId);
    List<Test> findByTitleIgnoreCase(String title);
    @Query("SELECT t FROM Test t WHERE t.creator = :creator AND t.status = :status")
    List<Test> findByCreatorAndStatus(User creator, Test.TestStatus status);
    List<Test> findByCreator(User creator);
    @Query("SELECT t FROM Test t WHERE t.title LIKE %:keyword% OR t.description LIKE %:keyword%")
    List<Test> searchByKeyword(@Param("keyword") String keyword);

    boolean existsByTitleAndCreator(String title, User creator);
}