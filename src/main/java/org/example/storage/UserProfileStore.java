package org.example.storage;

import org.example.config.AppConfig;
import org.example.model.ContentPreference;
import org.example.model.MediaItem;
import org.example.model.UserProfile;
import org.telegram.telegrambots.meta.api.objects.User;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UserProfileStore {
    private static final int RECENT_SHOWN_LIMIT = 30;
    private final String jdbcUrl;

    public UserProfileStore(AppConfig config) throws IOException {
        Path dbPath = config.dataDirectory().resolve("filmfinder.db");
        this.jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        initSchema();
    }

    public synchronized UserProfile getOrCreate(User user) {
        UserProfile profile = loadProfile(user.getId());
        if (profile == null) {
            profile = new UserProfile();
            profile.setUserId(user.getId());
            profile.setLanguage("ru-RU");
            insertUser(profile);
        }
        profile.setFirstName(user.getFirstName());
        profile.setUsername(user.getUserName());
        updateUserBasics(profile);
        return profile;
    }


    public synchronized UserProfile getByUserId(long userId) {
        UserProfile profile = loadProfile(userId);
        if (profile == null) {
            throw new IllegalArgumentException("Unknown user id: " + userId);
        }
        return profile;
    }

    public synchronized void saveSettings(UserProfile profile) {
        String sql = "UPDATE users SET preferred_content=?, min_rating=?, year_from=?, year_to=?, language=?, notifications_enabled=?, onboarding_completed=?, setup_step=? WHERE user_id=?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, profile.getContentPreference().name());
            ps.setDouble(2, profile.getMinRating());
            if (profile.getYearFrom() == null) ps.setNull(3, java.sql.Types.INTEGER); else ps.setInt(3, profile.getYearFrom());
            if (profile.getYearTo() == null) ps.setNull(4, java.sql.Types.INTEGER); else ps.setInt(4, profile.getYearTo());
            ps.setString(5, profile.getLanguage());
            ps.setInt(6, profile.isNotificationsEnabled() ? 1 : 0);
            ps.setInt(7, profile.isOnboardingCompleted() ? 1 : 0);
            ps.setString(8, profile.getSetupStep());
            ps.setLong(9, profile.getUserId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save settings", e);
        }
    }

    public synchronized void togglePreferredGenre(UserProfile profile, int genreId) {
        if (profile.getPreferredGenres().contains(genreId)) {
            profile.getPreferredGenres().remove(genreId);
            deletePair("preferred_genres", profile.getUserId(), genreId);
            applyGenreDelta(profile, genreId, -6);
        } else {
            profile.getPreferredGenres().add(genreId);
            insertGenrePair("preferred_genres", profile.getUserId(), genreId);
            applyGenreDelta(profile, genreId, 6);
        }
        saveGenreWeights(profile);
    }

    public synchronized void addFavorite(UserProfile profile, MediaItem item) {
        if (profile.getFavorites().add(item.getStorageKey())) {
            rememberTitle(profile, item);
            insertPair("favorites", profile.getUserId(), item.getStorageKey());
            applyGenreDelta(profile, item, 4);
            saveGenreWeights(profile);
            insertAction(profile.getUserId(), item.getStorageKey(), "favorite");
        }
    }

    public synchronized void removeFavorite(UserProfile profile, MediaItem item) {
        if (profile.getFavorites().remove(item.getStorageKey())) {
            deletePair("favorites", profile.getUserId(), item.getStorageKey());
            applyGenreDelta(profile, item, -4);
            saveGenreWeights(profile);
            insertAction(profile.getUserId(), item.getStorageKey(), "unfavorite");
        }
    }

    public synchronized void addWatchlist(UserProfile profile, MediaItem item) {
        if (profile.getWatchlist().add(item.getStorageKey())) {
            rememberTitle(profile, item);
            insertPair("watchlist", profile.getUserId(), item.getStorageKey());
            insertAction(profile.getUserId(), item.getStorageKey(), "watchlist_add");
        }
    }

    public synchronized void removeWatchlist(UserProfile profile, MediaItem item) {
        if (profile.getWatchlist().remove(item.getStorageKey())) {
            deletePair("watchlist", profile.getUserId(), item.getStorageKey());
            insertAction(profile.getUserId(), item.getStorageKey(), "watchlist_remove");
        }
    }

    public synchronized void like(UserProfile profile, MediaItem item) {
        rememberTitle(profile, item);
        if (profile.getDisliked().contains(item.getStorageKey())) {
            undislike(profile, item);
        }
        if (profile.getLiked().add(item.getStorageKey())) {
            insertPair("liked", profile.getUserId(), item.getStorageKey());
            profile.getSeen().add(item.getStorageKey());
            insertPair("seen", profile.getUserId(), item.getStorageKey());
            applyGenreDelta(profile, item, 2);
            saveGenreWeights(profile);
            insertAction(profile.getUserId(), item.getStorageKey(), "like");
        }
    }

    public synchronized void unlike(UserProfile profile, MediaItem item) {
        if (profile.getLiked().remove(item.getStorageKey())) {
            deletePair("liked", profile.getUserId(), item.getStorageKey());
            applyGenreDelta(profile, item, -2);
            saveGenreWeights(profile);
            insertAction(profile.getUserId(), item.getStorageKey(), "unlike");
        }
    }

    public synchronized void dislike(UserProfile profile, MediaItem item) {
        rememberTitle(profile, item);
        if (profile.getLiked().contains(item.getStorageKey())) {
            unlike(profile, item);
        }
        if (profile.getDisliked().add(item.getStorageKey())) {
            insertPair("disliked", profile.getUserId(), item.getStorageKey());
            applyGenreDelta(profile, item, -4);
            saveGenreWeights(profile);
            insertAction(profile.getUserId(), item.getStorageKey(), "dislike");
        }
    }

    public synchronized void undislike(UserProfile profile, MediaItem item) {
        if (profile.getDisliked().remove(item.getStorageKey())) {
            deletePair("disliked", profile.getUserId(), item.getStorageKey());
            applyGenreDelta(profile, item, 4);
            saveGenreWeights(profile);
            insertAction(profile.getUserId(), item.getStorageKey(), "undislike");
        }
    }

    public synchronized void markSeen(UserProfile profile, MediaItem item) {
        rememberTitle(profile, item);
        if (profile.getSeen().add(item.getStorageKey())) {
            insertPair("seen", profile.getUserId(), item.getStorageKey());
            applyGenreDelta(profile, item, 1);
            saveGenreWeights(profile);
            insertAction(profile.getUserId(), item.getStorageKey(), "seen");
        }
    }

    public synchronized void unmarkSeen(UserProfile profile, MediaItem item) {
        if (profile.getSeen().remove(item.getStorageKey())) {
            deletePair("seen", profile.getUserId(), item.getStorageKey());
            applyGenreDelta(profile, item, -1);
            saveGenreWeights(profile);
            insertAction(profile.getUserId(), item.getStorageKey(), "unseen");
        }
    }

    public synchronized void markShown(UserProfile profile, MediaItem item) {
        rememberTitle(profile, item);
        profile.getRecentShown().add(item.getStorageKey());
        String insert = "INSERT INTO recent_shown(user_id, item_key, shown_at) VALUES (?, ?, ?)";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(insert)) {
            ps.setLong(1, profile.getUserId());
            ps.setString(2, item.getStorageKey());
            ps.setLong(3, Instant.now().getEpochSecond());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to mark shown", e);
        }
        trimRecentShown(profile.getUserId());
    }

    public synchronized List<Integer> topGenres(UserProfile profile, int limit) {
        return profile.getGenreWeights().entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue(Comparator.reverseOrder()))
                .filter(entry -> entry.getValue() > 0)
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    public synchronized boolean shouldExclude(UserProfile profile, MediaItem item) {
        String key = item.getStorageKey();
        return profile.getSeen().contains(key) || profile.getDisliked().contains(key) || profile.getRecentShown().contains(key);
    }

    public synchronized Map<String, String> loadTitles(Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", keys.stream().map(k -> "?").toList());
        String sql = "SELECT item_key, title FROM cached_titles WHERE item_key IN (" + placeholders + ")";
        Map<String, String> result = new LinkedHashMap<>();
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = 1;
            for (String key : keys) ps.setString(index++, key);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("item_key"), rs.getString("title"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load titles", e);
        }
        return result;
    }

    public synchronized void rememberTitle(UserProfile profile, MediaItem item) {
        profile.getItemTitles().put(item.getStorageKey(), item.getTitle());
        String sql = "INSERT INTO cached_titles(item_key, title, media_type, updated_at) VALUES (?, ?, ?, ?) ON CONFLICT(item_key) DO UPDATE SET title=excluded.title, media_type=excluded.media_type, updated_at=excluded.updated_at";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, item.getStorageKey());
            ps.setString(2, item.getTitle());
            ps.setString(3, item.getMediaType().apiValue());
            ps.setLong(4, Instant.now().getEpochSecond());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remember title", e);
        }
    }

    private UserProfile loadProfile(long userId) {
        String userSql = "SELECT * FROM users WHERE user_id = ?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(userSql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                UserProfile profile = new UserProfile();
                profile.setUserId(userId);
                profile.setUsername(rs.getString("username"));
                profile.setFirstName(rs.getString("first_name"));
                profile.setContentPreference(ContentPreference.fromValue(rs.getString("preferred_content")));
                profile.setMinRating(rs.getDouble("min_rating"));
                int yf = rs.getInt("year_from");
                profile.setYearFrom(rs.wasNull() ? null : yf);
                int yt = rs.getInt("year_to");
                profile.setYearTo(rs.wasNull() ? null : yt);
                profile.setLanguage(rs.getString("language") == null ? "ru-RU" : rs.getString("language"));
                profile.setNotificationsEnabled(rs.getInt("notifications_enabled") == 1);
                profile.setOnboardingCompleted(rs.getInt("onboarding_completed") == 1);
                profile.setSetupStep(rs.getString("setup_step"));
                profile.setFavorites(loadKeySet(connection, "favorites", userId));
                profile.setWatchlist(loadKeySet(connection, "watchlist", userId));
                profile.setLiked(loadKeySet(connection, "liked", userId));
                profile.setDisliked(loadKeySet(connection, "disliked", userId));
                profile.setSeen(loadKeySet(connection, "seen", userId));
                profile.setRecentShown(loadRecentShown(connection, userId));
                profile.setPreferredGenres(loadGenreSet(connection, "preferred_genres", userId));
                profile.setGenreWeights(loadGenreWeights(connection, userId));
                return profile;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load profile", e);
        }
    }

    private Set<String> loadKeySet(Connection connection, String table, long userId) throws SQLException {
        Set<String> result = new HashSet<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT item_key FROM " + table + " WHERE user_id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(rs.getString(1));
            }
        }
        return result;
    }

    private Set<String> loadRecentShown(Connection connection, long userId) throws SQLException {
        Set<String> result = new HashSet<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT item_key FROM recent_shown WHERE user_id = ? ORDER BY shown_at DESC LIMIT ?")) {
            ps.setLong(1, userId);
            ps.setInt(2, RECENT_SHOWN_LIMIT);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(rs.getString(1));
            }
        }
        return result;
    }

    private Set<Integer> loadGenreSet(Connection connection, String table, long userId) throws SQLException {
        Set<Integer> result = new HashSet<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT genre_id FROM " + table + " WHERE user_id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(rs.getInt(1));
            }
        }
        return result;
    }

    private Map<Integer, Integer> loadGenreWeights(Connection connection, long userId) throws SQLException {
        Map<Integer, Integer> result = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT genre_id, weight FROM genre_weights WHERE user_id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.put(rs.getInt(1), rs.getInt(2));
            }
        }
        return result;
    }

    private void saveGenreWeights(UserProfile profile) {
        try (Connection connection = connect()) {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM genre_weights WHERE user_id = ?")) {
                delete.setLong(1, profile.getUserId());
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO genre_weights(user_id, genre_id, weight) VALUES (?, ?, ?)")) {
                for (Map.Entry<Integer, Integer> entry : profile.getGenreWeights().entrySet()) {
                    insert.setLong(1, profile.getUserId());
                    insert.setInt(2, entry.getKey());
                    insert.setInt(3, entry.getValue());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save genre weights", e);
        }
    }

    private void applyGenreDelta(UserProfile profile, MediaItem item, int delta) {
        if (item.getGenreIds() == null) return;
        for (Integer genreId : item.getGenreIds()) {
            applyGenreDelta(profile, genreId, delta);
        }
    }

    private void applyGenreDelta(UserProfile profile, int genreId, int delta) {
        int newValue = profile.getGenreWeights().getOrDefault(genreId, 0) + delta;
        if (newValue == 0) profile.getGenreWeights().remove(genreId); else profile.getGenreWeights().put(genreId, newValue);
    }

    private void insertPair(String table, long userId, String itemKey) {
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement("INSERT OR IGNORE INTO " + table + "(user_id, item_key) VALUES (?, ?)")) {
            ps.setLong(1, userId);
            ps.setString(2, itemKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert pair into " + table, e);
        }
    }

    private void deletePair(String table, long userId, String itemKey) {
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement("DELETE FROM " + table + " WHERE user_id = ? AND item_key = ?")) {
            ps.setLong(1, userId);
            ps.setString(2, itemKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete pair from " + table, e);
        }
    }

    private void insertGenrePair(String table, long userId, int genreId) {
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement("INSERT OR IGNORE INTO " + table + "(user_id, genre_id) VALUES (?, ?)")) {
            ps.setLong(1, userId);
            ps.setInt(2, genreId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert genre pair", e);
        }
    }

    private void deletePair(String table, long userId, int genreId) {
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement("DELETE FROM " + table + " WHERE user_id = ? AND genre_id = ?")) {
            ps.setLong(1, userId);
            ps.setInt(2, genreId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete genre pair", e);
        }
    }

    private void insertUser(UserProfile profile) {
        String sql = "INSERT OR IGNORE INTO users(user_id, username, first_name, preferred_content, min_rating, language, notifications_enabled, onboarding_completed) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, profile.getUserId());
            ps.setString(2, profile.getUsername());
            ps.setString(3, profile.getFirstName());
            ps.setString(4, profile.getContentPreference().name());
            ps.setDouble(5, profile.getMinRating());
            ps.setString(6, profile.getLanguage());
            ps.setInt(7, profile.isNotificationsEnabled() ? 1 : 0);
            ps.setInt(8, profile.isOnboardingCompleted() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert user", e);
        }
    }

    private void updateUserBasics(UserProfile profile) {
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement("UPDATE users SET username = ?, first_name = ? WHERE user_id = ?")) {
            ps.setString(1, profile.getUsername());
            ps.setString(2, profile.getFirstName());
            ps.setLong(3, profile.getUserId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update user basics", e);
        }
    }

    private void insertAction(long userId, String itemKey, String actionType) {
        String sql = "INSERT INTO user_actions(user_id, item_key, action_type, created_at) VALUES (?, ?, ?, ?)";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, itemKey);
            ps.setString(3, actionType);
            ps.setLong(4, Instant.now().getEpochSecond());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert action", e);
        }
    }

    private void trimRecentShown(long userId) {
        String sql = "DELETE FROM recent_shown WHERE user_id = ? AND rowid NOT IN (SELECT rowid FROM recent_shown WHERE user_id = ? ORDER BY shown_at DESC LIMIT ?)";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, userId);
            ps.setInt(3, RECENT_SHOWN_LIMIT);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to trim recent shown", e);
        }
    }

    private void initSchema() {
        List<String> ddl = List.of(
                "CREATE TABLE IF NOT EXISTS users(user_id INTEGER PRIMARY KEY, username TEXT, first_name TEXT, preferred_content TEXT DEFAULT 'ALL', min_rating REAL DEFAULT 0, year_from INTEGER, year_to INTEGER, language TEXT DEFAULT 'ru-RU', notifications_enabled INTEGER DEFAULT 0, onboarding_completed INTEGER DEFAULT 0, setup_step TEXT)",
                "CREATE TABLE IF NOT EXISTS favorites(user_id INTEGER NOT NULL, item_key TEXT NOT NULL, PRIMARY KEY(user_id, item_key))",
                "CREATE TABLE IF NOT EXISTS watchlist(user_id INTEGER NOT NULL, item_key TEXT NOT NULL, PRIMARY KEY(user_id, item_key))",
                "CREATE TABLE IF NOT EXISTS seen(user_id INTEGER NOT NULL, item_key TEXT NOT NULL, PRIMARY KEY(user_id, item_key))",
                "CREATE TABLE IF NOT EXISTS liked(user_id INTEGER NOT NULL, item_key TEXT NOT NULL, PRIMARY KEY(user_id, item_key))",
                "CREATE TABLE IF NOT EXISTS disliked(user_id INTEGER NOT NULL, item_key TEXT NOT NULL, PRIMARY KEY(user_id, item_key))",
                "CREATE TABLE IF NOT EXISTS preferred_genres(user_id INTEGER NOT NULL, genre_id INTEGER NOT NULL, PRIMARY KEY(user_id, genre_id))",
                "CREATE TABLE IF NOT EXISTS genre_weights(user_id INTEGER NOT NULL, genre_id INTEGER NOT NULL, weight INTEGER NOT NULL, PRIMARY KEY(user_id, genre_id))",
                "CREATE TABLE IF NOT EXISTS cached_titles(item_key TEXT PRIMARY KEY, title TEXT NOT NULL, media_type TEXT, updated_at INTEGER)",
                "CREATE TABLE IF NOT EXISTS user_actions(id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, item_key TEXT NOT NULL, action_type TEXT NOT NULL, created_at INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS recent_shown(user_id INTEGER NOT NULL, item_key TEXT NOT NULL, shown_at INTEGER NOT NULL)"
        );
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            for (String sql : ddl) statement.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize SQLite schema", e);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }
}
