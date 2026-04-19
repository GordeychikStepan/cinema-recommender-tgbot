package org.example.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MediaItem {
    private long id;
    private MediaType mediaType;
    private String title;
    private String overview;
    private String releaseDate;
    private double voteAverage;
    private double popularity;
    private String posterUrl;
    private String backdropUrl;
    private List<Integer> genreIds = new ArrayList<>();
    private List<String> genres = new ArrayList<>();

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public MediaType getMediaType() { return mediaType; }
    public void setMediaType(MediaType mediaType) { this.mediaType = mediaType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getOverview() { return overview; }
    public void setOverview(String overview) { this.overview = overview; }
    public String getReleaseDate() { return releaseDate; }
    public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }
    public double getVoteAverage() { return voteAverage; }
    public void setVoteAverage(double voteAverage) { this.voteAverage = voteAverage; }
    public double getPopularity() { return popularity; }
    public void setPopularity(double popularity) { this.popularity = popularity; }
    public String getPosterUrl() { return posterUrl; }
    public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }
    public String getBackdropUrl() { return backdropUrl; }
    public void setBackdropUrl(String backdropUrl) { this.backdropUrl = backdropUrl; }
    public List<Integer> getGenreIds() { return genreIds; }
    public void setGenreIds(List<Integer> genreIds) { this.genreIds = genreIds; }
    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres; }

    public String getStorageKey() {
        return mediaType.apiValue() + ":" + id;
    }

    public String shortYear() {
        if (releaseDate == null || releaseDate.isBlank()) {
            return "—";
        }
        return releaseDate.length() >= 4 ? releaseDate.substring(0, 4) : releaseDate;
    }

    public Integer releaseYear() {
        try {
            return Integer.parseInt(shortYear());
        } catch (Exception ex) {
            return null;
        }
    }

    public int recencyScore() {
        Integer year = releaseYear();
        if (year == null) {
            return 0;
        }
        int currentYear = LocalDate.now().getYear();
        if (year >= currentYear - 1) return 4;
        if (year >= currentYear - 3) return 3;
        if (year >= currentYear - 6) return 2;
        if (year >= currentYear - 10) return 1;
        return 0;
    }

    public String genresText() {
        return genres == null || genres.isEmpty() ? "—" : String.join(", ", genres);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MediaItem item)) return false;
        return id == item.id && mediaType == item.mediaType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, mediaType);
    }
}
