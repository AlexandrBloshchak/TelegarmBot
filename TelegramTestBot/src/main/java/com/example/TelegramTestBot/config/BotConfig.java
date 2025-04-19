package com.example.TelegramTestBot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import com.example.TelegramTestBot.bot.TestBot;

@Configuration
public class BotConfig {

    @Bean
    public TelegramBotsApi telegramBotsApi(TestBot bot) throws TelegramApiException {
        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(bot); // 👈 важно!
        return api;
    }
}
