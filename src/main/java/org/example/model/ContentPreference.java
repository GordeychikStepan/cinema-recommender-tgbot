package org.example.model;

public enum ContentPreference {
    ALL("Всё", null),
    MOVIE("Только фильмы", MediaType.MOVIE),
    TV("Только сериалы", MediaType.TV);

    private final String labelRu;
    private final MediaType mediaType;

    ContentPreference(String labelRu, MediaType mediaType) {
        this.labelRu = labelRu;
        this.mediaType = mediaType;
    }

    public String labelRu() {
        return labelRu;
    }

    public MediaType mediaType() {
        return mediaType;
    }

    public static ContentPreference fromValue(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return ContentPreference.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ALL;
        }
    }
}
