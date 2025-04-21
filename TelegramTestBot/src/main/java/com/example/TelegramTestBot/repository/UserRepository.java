package com.example.TelegramTestBot.repository;

import com.example.TelegramTestBot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByChatId(Long chatId);
    Optional<User> findByUsername(String username);
    Optional<User> findByLogin(String login);
    List<User> findByRole(String role);

    boolean existsByUsername(String username);
    boolean existsByLogin(String login);
    boolean existsByChatId(Long chatId);
}