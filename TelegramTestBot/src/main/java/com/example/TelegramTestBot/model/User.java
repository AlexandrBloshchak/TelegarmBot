package com.example.TelegramTestBot.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;
    @Column(nullable = false, unique = true)
    private String login;
    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }
    @Column(nullable = false)
    private String password;

    @Column(name = "full_name")
    private String fullName;

    private String role;

    @Column(name = "chat_id", unique = true)
    private Long chatId;

    public User() {}

    // Getters and Setters
    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }

    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }

    public void setPassword(String password) { this.password = password; }

    public String getFullName() { return fullName; }

    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getRole() { return role; }

    public void setRole(String role) { this.role = role; }

    public Long getChatId() { return chatId; }

    public void setChatId(Long chatId) { this.chatId = chatId; }
}
