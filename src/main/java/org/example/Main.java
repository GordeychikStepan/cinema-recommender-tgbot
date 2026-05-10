package org.example;

import org.example.bot.CinemaRecommenderBot;
import org.example.config.AppConfig;
import org.example.service.TmdbService;
import org.example.service.NotificationScheduler;
import org.example.storage.UserProfileStore;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class Main {
    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.load();
        TmdbService tmdbService = new TmdbService(config);
        UserProfileStore profileStore = new UserProfileStore(config);

        CinemaRecommenderBot bot = new CinemaRecommenderBot(config, tmdbService, profileStore);

        try (TelegramBotsLongPollingApplication application = new TelegramBotsLongPollingApplication();
             NotificationScheduler scheduler = new NotificationScheduler(tmdbService, profileStore, bot)) {
            application.registerBot(config.botToken(), bot);
            scheduler.start();
            System.out.println("Cinema recommender bot is running.");
            Thread.currentThread().join();
        } catch (TelegramApiException e) {
            System.err.println("Failed to start Telegram bot: " + e.getMessage());
            throw e;
        }
    }
}
