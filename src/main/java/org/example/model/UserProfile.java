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
    private Set<String> liked = new HashSet<>();
    private Set<String> disliked = new HashSet<>();
    private Set<String> seen = new HashSet<>();
    private Map<Integer, Integer> genreWeights = new HashMap<>();
    private Map<String, String> itemTitles = new HashMap<>();

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public Set<String> getFavorites() { return favorites; }
    public void setFavorites(Set<String> favorites) { this.favorites = favorites; }
    public Set<String> getLiked() { return liked; }
    public void setLiked(Set<String> liked) { this.liked = liked; }
    public Set<String> getDisliked() { return disliked; }
    public void setDisliked(Set<String> disliked) { this.disliked = disliked; }
    public Set<String> getSeen() { return seen; }
    public void setSeen(Set<String> seen) { this.seen = seen; }
    public Map<Integer, Integer> getGenreWeights() { return genreWeights; }
    public void setGenreWeights(Map<Integer, Integer> genreWeights) { this.genreWeights = genreWeights; }
    public Map<String, String> getItemTitles() { return itemTitles; }
    public void setItemTitles(Map<String, String> itemTitles) { this.itemTitles = itemTitles; }
}
