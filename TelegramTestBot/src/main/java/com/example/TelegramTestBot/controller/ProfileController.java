package com.example.TelegramTestBot.controller;

import com.example.TelegramTestBot.model.User;
import com.example.TelegramTestBot.service.UserService;
import com.example.TelegramTestBot.bot.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;
    private final Set<Long> inProfileMenu = ConcurrentHashMap.newKeySet();
    private final Set<Long> inProfileEdit = ConcurrentHashMap.newKeySet();
    private final Map<Long, String> editField = new ConcurrentHashMap<>();
    public SendMessage startProfileMenu(long chatId, User user) {
        inProfileMenu.add(chatId);
        return SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text("👤 *Профиль*\nВыберите действие:")
                .parseMode("Markdown")
                .replyMarkup(keyboard(
                        kRow("Показать профиль", "Редактировать профиль"),
                        kRow("Удалить профиль", "Главное меню")
                ))
                .build();
    }
    public SendMessage handleProfileMenu(Update upd, User user) {
        long chatId = upd.getMessage().getChatId();
        String choice = upd.getMessage().getText().trim().toLowerCase();
        switch (choice) {
            case "показать профиль":
                return showProfile(chatId, user);
            case "удалить профиль":
                String dummyPass = passwordEncoder.encode(UUID.randomUUID().toString());
                user.setFullName("Удалённый пользователь");
                user.setUsername("deleted_" + user.getId());
                user.setPassword(dummyPass);
                user.setChatId(null);
                user.setAuthenticated(false);
                userService.save(user);

                sessionService.invalidateSession(chatId);
                inProfileMenu.remove(chatId);

                return SendMessage.builder()
                        .chatId(String.valueOf(chatId))
                        .text("✅ Прощай, Легенда, мы тебя не забудем")
                        .build();
            case "редактировать профиль":
                inProfileMenu.remove(chatId);
                inProfileEdit.add(chatId);
                return SendMessage.builder()
                        .chatId(String.valueOf(chatId))
                        .text("Что вы хотите изменить?")
                        .replyMarkup(keyboard(
                                kRow("Имя профиля", "Логин"),
                                kRow("Пароль"),
                                kRow("Отмена")
                        ))
                        .build();
            case "главное меню":
                inProfileMenu.remove(chatId);
                return SendMessage.builder()
                        .chatId(String.valueOf(chatId))
                        .text("Возвращаемся в главное меню.")
                        .build();
            default:
                return SendMessage.builder()
                        .chatId(String.valueOf(chatId))
                        .text("Неизвестная команда в профиле.")
                        .build();
        }
    }
    public SendMessage handleProfileEdit(Update upd, User user) {
        long chatId = upd.getMessage().getChatId();
        String txt = upd.getMessage().getText().trim();
        if (!editField.containsKey(chatId)) {
            String choice = txt.toLowerCase();
            switch (choice) {
                case "имя профиля":
                case "логин":
                    editField.put(chatId, choice);
                    return SendMessage.builder()
                            .chatId(String.valueOf(chatId))
                            .text("Введите новое значение для *" + txt + "*:")
                            .parseMode("Markdown")
                            .build();
                case "пароль":
                    editField.put(chatId, "password_old");
                    return SendMessage.builder()
                            .chatId(String.valueOf(chatId))
                            .text("Введите текущий пароль:")
                            .build();
                case "отмена":
                    resetToMenu(chatId);
                    return SendMessage.builder()
                            .chatId(String.valueOf(chatId))
                            .text("❌ Редактирование профиля отменено.")
                            .replyMarkup(menuKeyboard())
                            .build();
                default:
                    return SendMessage.builder()
                            .chatId(String.valueOf(chatId))
                            .text("Пожалуйста, выберите поле для редактирования.")
                            .build();
            }
        }
        String state = editField.get(chatId);
        try {
            switch (state) {
                case "имя профиля":
                    user.setFullName(txt);
                    userService.save(user);
                    break;
                case "логин":
                    if (userService.existsByUsername(txt) && !txt.equals(user.getUsername())) {
                        return SendMessage.builder()
                                .chatId(String.valueOf(chatId))
                                .text("❌ Логин уже занят. Введите другой логин:")
                                .build();
                    }
                    user.setUsername(txt);
                    userService.save(user);
                    break;
                case "password_old":
                    if (!passwordEncoder.matches(txt, user.getPassword())) {
                        return SendMessage.builder()
                                .chatId(String.valueOf(chatId))
                                .text("❌ Неверный пароль. Попробуйте ещё раз:")
                                .build();
                    }
                    editField.put(chatId, "password_new");
                    return SendMessage.builder()
                            .chatId(String.valueOf(chatId))
                            .text("Введите новый пароль:")
                            .build();
                case "password_new":
                    userService.updatePassword(user, txt);
                    break;
            }
        } catch (DataIntegrityViolationException ex) {
            resetToMenu(chatId);
            return SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text("❌ Ошибка при сохранении. Попробуйте позже.")
                    .replyMarkup(menuKeyboard())
                    .build();
        }
        editField.remove(chatId);
        resetToMenu(chatId);
        return SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text("✅ Успешно обновлено.")
                .parseMode("Markdown")
                .replyMarkup(menuKeyboard())
                .build();
    }
    private void resetToMenu(long chatId) {
        inProfileEdit.remove(chatId);
        inProfileMenu.add(chatId);
    }
    private ReplyKeyboardMarkup menuKeyboard() {
        return keyboard(
                kRow("Показать профиль", "Редактировать профиль"),
                kRow("Удалить профиль", "Главное меню")
        );
    }
    public boolean isInProfileMenu(long chatId) {
        return inProfileMenu.contains(chatId);
    }
    public boolean isInProfileEdit(long chatId) {
        return inProfileEdit.contains(chatId);
    }
    private SendMessage showProfile(long chatId, User user) {
        StringBuilder sb = new StringBuilder()
                .append("👤 *Ваш профиль:*\n\n")
                .append("*Имя:* ").append(escapeMarkdown(user.getFullName())).append("\n")
                .append("*Логин:* ").append(escapeMarkdown(user.getUsername())).append("\n")
                .append("*Роль:* ").append(escapeMarkdown(user.getRole())).append("\n")
                .append("*Chat ID:* ").append(user.getChatId());
        return SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(sb.toString())
                .parseMode("Markdown")
                .replyMarkup(keyboard(
                        kRow("Редактировать профиль", "Удалить профиль"),
                        kRow("Главное меню")
                ))
                .build();
    }
    static KeyboardRow kRow(String... btns) {
        KeyboardRow row = new KeyboardRow();
        for (String text : btns) row.add(new KeyboardButton(text));
        return row;
    }
    private static ReplyKeyboardMarkup keyboard(KeyboardRow... rows) {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        kb.setKeyboard(Arrays.asList(rows));
        return kb;
    }
    private String escapeMarkdown(String s) {
        return s == null ? "" : s.replace("_", "\\_")
                .replace("*", "\\*")
                .replace("[", "\\[")
                .replace("]", "\\]");
    }
}
