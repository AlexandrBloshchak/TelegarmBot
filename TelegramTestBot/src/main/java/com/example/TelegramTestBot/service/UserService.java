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

    private final Map<Long, Long> loggedInUsers = new ConcurrentHashMap<>();

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<User> findByFullName(String fullName) {
        return userRepository.findByFullNameIgnoreCase(fullName);
    }
    public boolean existsByUsername(String username) {
        return userRepository.findByUsername(username)
                .isPresent();
    }
    @Transactional
    public void updatePassword(User user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
    @Transactional
    public User save(User user) {
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    @Transactional
    public boolean authenticate(String login, String password, Long chatId) {
        return userRepository.findByUsername(login)
                .filter(u -> passwordEncoder.matches(password, u.getPassword()))
                .map(u -> {
                    u.setAuthenticated(true);
                    u.setChatId(chatId);
                    userRepository.save(u);
                    loggedInUsers.put(chatId, u.getId());
                    return true;
                })
                .orElse(false);
    }
    public Optional<User> getAuthenticatedUser(Long chatId) {
        return userRepository.findByChatId(chatId)
                .filter(User::isAuthenticated);
    }
    @Transactional
    public void logout(Long chatId) {
        userRepository.findByChatId(chatId).ifPresent(u -> {
            u.setAuthenticated(false);
            u.setChatId(null);
            userRepository.save(u);
        });
        loggedInUsers.remove(chatId);
    }
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
