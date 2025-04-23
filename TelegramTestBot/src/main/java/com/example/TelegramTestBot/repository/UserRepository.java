package com.example.TelegramTestBot.repository;

import com.example.TelegramTestBot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // Используем только findByUsername (никаких findByLogin!)
    Optional<User> findByUsername(String username);

    // Для проверки существования пользователя
    boolean existsByUsername(String username);

    // Другие методы
    Optional<User> findByChatId(Long chatId);
}