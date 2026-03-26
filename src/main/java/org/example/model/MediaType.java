package org.example.model;

public enum MediaType {
    MOVIE,
    TV;

    public String apiValue() {
        return this == MOVIE ? "movie" : "tv";
    }

    public String labelRu() {
        return this == MOVIE ? "Фильм" : "Сериал";
    }

    public static MediaType fromApi(String value) {
        return "tv".equalsIgnoreCase(value) ? TV : MOVIE;
    }
}
