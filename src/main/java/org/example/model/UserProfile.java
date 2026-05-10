package org.example.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class UserProfile {
    private long userId;
    private String username;
    private String firstName;
    private Set<String> favorites = new HashSet<>();
    private Set<String> watchlist = new HashSet<>();
    private Set<String> liked = new HashSet<>();
    private Set<String> disliked = new HashSet<>();
    private Set<String> seen = new HashSet<>();
    private Set<String> recentShown = new HashSet<>();
    private Set<Integer> preferredGenres = new HashSet<>();
    private Map<Integer, Integer> genreWeights = new HashMap<>();
    private Map<String, String> itemTitles = new HashMap<>();
    private ContentPreference contentPreference = ContentPreference.ALL;
    private double minRating = 0.0;
    private double minPopularity = 0.0;
    private Integer yearFrom;
    private Integer yearTo;
    private String language = "ru-RU";
    private boolean notificationsEnabled = false;
    private boolean onboardingCompleted = false;
    private String setupStep;

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public Set<String> getFavorites() { return favorites; }
    public void setFavorites(Set<String> favorites) { this.favorites = favorites; }
    public Set<String> getWatchlist() { return watchlist; }
    public void setWatchlist(Set<String> watchlist) { this.watchlist = watchlist; }
    public Set<String> getLiked() { return liked; }
    public void setLiked(Set<String> liked) { this.liked = liked; }
    public Set<String> getDisliked() { return disliked; }
    public void setDisliked(Set<String> disliked) { this.disliked = disliked; }
    public Set<String> getSeen() { return seen; }
    public void setSeen(Set<String> seen) { this.seen = seen; }
    public Set<String> getRecentShown() { return recentShown; }
    public void setRecentShown(Set<String> recentShown) { this.recentShown = recentShown; }
    public Set<Integer> getPreferredGenres() { return preferredGenres; }
    public void setPreferredGenres(Set<Integer> preferredGenres) { this.preferredGenres = preferredGenres; }
    public Map<Integer, Integer> getGenreWeights() { return genreWeights; }
    public void setGenreWeights(Map<Integer, Integer> genreWeights) { this.genreWeights = genreWeights; }
    public Map<String, String> getItemTitles() { return itemTitles; }
    public void setItemTitles(Map<String, String> itemTitles) { this.itemTitles = itemTitles; }
    public ContentPreference getContentPreference() { return contentPreference; }
    public void setContentPreference(ContentPreference contentPreference) { this.contentPreference = contentPreference; }
    public double getMinRating() { return minRating; }
    public void setMinRating(double minRating) { this.minRating = minRating; }
    public double getMinPopularity() { return minPopularity; }
    public void setMinPopularity(double minPopularity) { this.minPopularity = minPopularity; }
    public Integer getYearFrom() { return yearFrom; }
    public void setYearFrom(Integer yearFrom) { this.yearFrom = yearFrom; }
    public Integer getYearTo() { return yearTo; }
    public void setYearTo(Integer yearTo) { this.yearTo = yearTo; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }
    public boolean isOnboardingCompleted() { return onboardingCompleted; }
    public void setOnboardingCompleted(boolean onboardingCompleted) { this.onboardingCompleted = onboardingCompleted; }
    public String getSetupStep() { return setupStep; }
    public void setSetupStep(String setupStep) { this.setupStep = setupStep; }
}
