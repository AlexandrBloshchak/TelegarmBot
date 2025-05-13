package com.example.TelegramTestBot.service;

import com.example.TelegramTestBot.model.User;
import com.example.TelegramTestBot.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** Дополнительный кэш: chatId → userId (можно убрать, если не нужен) */
    private final Map<Long, Long> loggedInUsers = new ConcurrentHashMap<>();

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<User> findByChatId(Long chatId) {
        return userRepository.findByChatId(chatId);
    }

    /** Сброс авторизации без явного “выхода” пользователя (по таймауту и т.д.) */
    @Transactional
    public void unlogin(Long chatId) {
        userRepository.findByChatId(chatId).ifPresent(user -> {
            user.setAuthenticated(false);
            user.setChatId(null);
            userRepository.save(user);
            loggedInUsers.remove(chatId);
        });
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);   // нужен соответствующий метод в UserRepository
    }
    @Transactional
    public boolean authenticate(String login, String password, Long chatId) {
        return userRepository.findByUsername(login)
                .filter(u -> passwordEncoder.matches(password, u.getPassword()))
                .map(u -> {
                    u.setAuthenticated(true);   // пользователь «в системе»
                    u.setChatId(chatId);        // сохраняем chatId
                    userRepository.save(u);     // фиксация изменений
                    loggedInUsers.put(chatId, u.getId());
                    return true;
                })
                .orElse(false);
    }

    /** Корректный выход из аккаунта */
    @Transactional
    public void logout(Long chatId) {
        userRepository.findByChatId(chatId).ifPresent(user -> {
            user.setAuthenticated(false);
            user.setChatId(null);
            userRepository.save(user);
            loggedInUsers.remove(chatId);
        });
    }

    /**
     * Получить авторизованного пользователя по chatId.
     * Вернёт пустой Optional, если пользователь не найден или не авторизован.
     */
    public Optional<User> getAuthenticatedUser(Long chatId) {
        return userRepository.findByChatId(chatId)
                .filter(User::isAuthenticated);
    }

    public boolean userExists(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    /**
     * Регистрация нового пользователя + мгновенный логин.
     */
    @Transactional
    public User register(String username,
                         String password,
                         String fullName,
                         Long chatId) {

        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Логин уже занят.");
        }

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .fullName(fullName)
                .chatId(chatId)
                .authenticated(true)
                .build();

        User saved = userRepository.save(user);
        loggedInUsers.put(chatId, saved.getId());
        return saved;
    }
}
