package com.example.TelegramTestBot.service;

import com.example.TelegramTestBot.model.User;
import com.example.TelegramTestBot.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean authenticate(String username, String password) {
        return authenticate(username, password, null);
    }

    public boolean authenticate(String username, String rawPassword, Long chatId) {
        return userRepository.findByUsername(username)
                .map(user -> {
                    boolean match = passwordEncoder.matches(rawPassword, user.getPassword());
                    if (match && chatId != null && user.getChatId() == null) {
                        user.setChatId(chatId);
                        userRepository.save(user);
                    }
                    return match;
                })
                .orElse(false);
    }

    public Optional<User> getAuthenticatedUser(Long chatId) {
        return userRepository.findByChatId(chatId);
    }

    public boolean userExists(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    public void register(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);
    }
}
