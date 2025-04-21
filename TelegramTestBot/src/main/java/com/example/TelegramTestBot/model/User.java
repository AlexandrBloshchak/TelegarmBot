package com.example.TelegramTestBot.model;

import jakarta.persistence.*;
import lombok.Data;
import java.util.List;

@Data
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

    @Column(nullable = false)
    private String password;

    @Column(name = "full_name")
    private String fullName;

    private String role = "PARTICIPANT";

    @Column(name = "chat_id", unique = true)
    private Long chatId;

    @OneToMany(mappedBy = "creator", cascade = CascadeType.ALL)
    private List<Test> createdTests;

    public User() {}
}