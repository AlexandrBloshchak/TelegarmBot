package com.example.TelegramTestBot.model;

import lombok.Data;
import jakarta.persistence.*;

@Data
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long chatId;
    private String firstName;
    private String lastName;
    private String middleName;
    private String username;
    private String password;
    private boolean isAuthenticated;

    @Enumerated(EnumType.STRING)
    private Role role;

    public enum Role {
        PARTICIPANT, CREATOR, ADMIN
    }
}