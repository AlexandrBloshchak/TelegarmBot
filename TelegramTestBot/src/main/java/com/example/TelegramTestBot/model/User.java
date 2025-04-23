package com.example.TelegramTestBot.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Маппинг на столбец login в БД
    @Column(name = "login", nullable = false, unique = true)
    @NotBlank
    private String username;

    @Column(nullable = false)
    @NotBlank
    private String password;

    // Маппинг на full_name в БД
    @Column(name = "full_name", nullable = false)
    @NotBlank
    private String fullName;

    @Column(nullable = false)
    @Builder.Default
    private String role = "USER";

    @Column(name = "chat_id", unique = true)
    private Long chatId;

    @Column(name = "is_authenticated")
    @Builder.Default
    private boolean authenticated = false;
}