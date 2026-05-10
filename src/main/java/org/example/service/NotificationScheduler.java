package org.example.service;

import org.example.bot.CinemaRecommenderBot;
import org.example.model.MediaItem;
import org.example.model.UserProfile;
import org.example.storage.UserProfileStore;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NotificationScheduler implements AutoCloseable {
    private static final LocalTime DEFAULT_NOTIFICATION_TIME = LocalTime.of(10, 0);
    private static final int DIGEST_LIMIT = 5;

    private final TmdbService tmdbService;
    private final UserProfileStore profileStore;
    private final CinemaRecommenderBot bot;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "filmfinder-notifications");
        thread.setDaemon(true);
        return thread;
    });

    public NotificationScheduler(TmdbService tmdbService, UserProfileStore profileStore, CinemaRecommenderBot bot) {
        this.tmdbService = tmdbService;
        this.profileStore = profileStore;
        this.bot = bot;
    }

    public void start() {
        long initialDelay = delayUntilNextRunSeconds();
        executor.scheduleAtFixedRate(this::safeRun, initialDelay, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
        System.out.println("FilmFinder notifications scheduled. First run in " + initialDelay + " seconds.");
    }

    public void runNowForDebug() {
        executor.execute(this::safeRun);
    }

    private long delayUntilNextRunSeconds() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.with(DEFAULT_NOTIFICATION_TIME);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return Math.max(30, Duration.between(now, next).getSeconds());
    }

    private void safeRun() {
        try {
            runDigest();
        } catch (Exception e) {
            System.err.println("Notification digest failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void runDigest() throws Exception {
        List<UserProfile> profiles = profileStore.notificationProfiles();
        for (UserProfile profile : profiles) {
            List<MediaItem> releases = tmdbService.recentReleases(
                    profile.getLanguage(),
                    profile.getContentPreference(),
                    profile.getMinRating(),
                    profile.getMinPopularity(),
                    profile.getYearFrom(),
                    profile.getYearTo(),
                    12
            );
            List<MediaItem> digest = new ArrayList<>();
            for (MediaItem item : releases) {
                if (profileStore.wasNotificationSent(profile.getUserId(), item.getStorageKey())) {
                    continue;
                }
                if (profileStore.shouldExclude(profile, item)) {
                    continue;
                }
                digest.add(item);
                if (digest.size() >= DIGEST_LIMIT) {
                    break;
                }
            }
            if (digest.isEmpty()) {
                continue;
            }
            bot.sendNotificationDigest(profile, digest);
            for (MediaItem item : digest) {
                profileStore.markNotificationSent(profile.getUserId(), item.getStorageKey());
                profileStore.saveCachedMedia(item, profile.getLanguage());
            }
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
