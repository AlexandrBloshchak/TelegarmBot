package com.example.TelegramTestBot.service;

import com.example.TelegramTestBot.model.User;
import com.example.TelegramTestBot.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User registerNewUser(Long chatId, String firstName, String lastName,
                                String middleName, String username, String password) {
        User user = new User();
        user.setChatId(chatId);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setMiddleName(middleName);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(User.Role.PARTICIPANT);
        return userRepository.save(user);
    }

    public User authenticateUser(Long chatId, String username, String password) {
        return userRepository.findByUsername(username)
                .filter(user -> passwordEncoder.matches(password, user.getPassword()))
                .map(user -> {
                    user.setChatId(chatId);
                    user.setAuthenticated(true);
                    return userRepository.save(user);
                })
                .orElse(null);
    }

    public User getAuthenticatedUser(Long chatId) {
        return userRepository.findByChatId(chatId)
                .filter(User::isAuthenticated)
                .orElse(null);
    }
}