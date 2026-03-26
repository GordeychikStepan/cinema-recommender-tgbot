package org.example.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.config.AppConfig;
import org.example.model.MediaItem;
import org.example.model.UserProfile;
import org.telegram.telegrambots.meta.api.objects.User;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class UserProfileStore {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final Path storagePath;
    private final Map<Long, UserProfile> profiles;

    public UserProfileStore(AppConfig config) throws IOException {
        this.storagePath = config.dataDirectory().resolve("user-profiles.json");
        this.profiles = load();
    }

    public synchronized UserProfile getOrCreate(User user) {
        UserProfile profile = profiles.computeIfAbsent(user.getId(), id -> {
            UserProfile created = new UserProfile();
            created.setUserId(id);
            return created;
        });
        profile.setFirstName(user.getFirstName());
        profile.setUsername(user.getUserName());
        saveQuietly();
        return profile;
    }

    public synchronized void addFavorite(UserProfile profile, MediaItem item) {
        rememberTitle(profile, item);
        profile.getFavorites().add(item.getStorageKey());
        profile.getSeen().add(item.getStorageKey());
        applyGenreDelta(profile, item, 3);
        saveQuietly();
    }

    public synchronized void like(UserProfile profile, MediaItem item) {
        rememberTitle(profile, item);
        profile.getLiked().add(item.getStorageKey());
        profile.getDisliked().remove(item.getStorageKey());
        profile.getSeen().add(item.getStorageKey());
        applyGenreDelta(profile, item, 2);
        saveQuietly();
    }

    public synchronized void dislike(UserProfile profile, MediaItem item) {
        rememberTitle(profile, item);
        profile.getDisliked().add(item.getStorageKey());
        profile.getLiked().remove(item.getStorageKey());
        applyGenreDelta(profile, item, -2);
        saveQuietly();
    }

    public synchronized void markSeen(UserProfile profile, MediaItem item) {
        rememberTitle(profile, item);
        profile.getSeen().add(item.getStorageKey());
        applyGenreDelta(profile, item, 1);
        saveQuietly();
    }

    public synchronized boolean isFavorite(UserProfile profile, MediaItem item) {
        return profile.getFavorites().contains(item.getStorageKey());
    }

    public synchronized List<Integer> topGenres(UserProfile profile, int limit) {
        return profile.getGenreWeights().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<Integer, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }


    public synchronized void rememberTitle(UserProfile profile, MediaItem item) {
        profile.getItemTitles().put(item.getStorageKey(), item.getTitle());
    }

    public synchronized boolean shouldExclude(UserProfile profile, MediaItem item) {
        String key = item.getStorageKey();
        return profile.getSeen().contains(key) || profile.getDisliked().contains(key);
    }

    private void applyGenreDelta(UserProfile profile, MediaItem item, int delta) {
        if (item.getGenreIds() == null) {
            return;
        }
        for (Integer genreId : item.getGenreIds()) {
            int newValue = profile.getGenreWeights().getOrDefault(genreId, 0) + delta;
            profile.getGenreWeights().put(genreId, newValue);
        }
    }

    private Map<Long, UserProfile> load() throws IOException {
        if (!Files.exists(storagePath)) {
            return new HashMap<>();
        }
        return mapper.readValue(storagePath.toFile(), new TypeReference<>() {});
    }

    private void saveQuietly() {
        try {
            mapper.writeValue(storagePath.toFile(), profiles);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save user profiles", e);
        }
    }
}
