package org.example;

import org.example.bot.CinemaRecommenderBot;
import org.example.config.AppConfig;
import org.example.service.TmdbService;
import org.example.storage.UserProfileStore;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class Main {
    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.load();
        TmdbService tmdbService = new TmdbService(config);
        UserProfileStore profileStore = new UserProfileStore(config);

        try (TelegramBotsLongPollingApplication application = new TelegramBotsLongPollingApplication()) {
            application.registerBot(config.botToken(), new CinemaRecommenderBot(config, tmdbService, profileStore));
            System.out.println("Cinema recommender bot is running.");
            Thread.currentThread().join();
        } catch (TelegramApiException e) {
            System.err.println("Failed to start Telegram bot: " + e.getMessage());
            throw e;
        }
    }
}
