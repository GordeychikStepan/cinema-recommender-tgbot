package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.AppConfig;
import org.example.model.ContentPreference;
import org.example.model.MediaItem;
import org.example.model.MediaType;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TmdbService {
    private static final String API_BASE = "https://api.themoviedb.org/3";
    private static final String IMAGE_BASE = "https://image.tmdb.org/t/p/w500";

    private final AppConfig config;
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final Map<String, Map<Integer, String>> genreCache = new HashMap<>();

    public TmdbService(AppConfig config) {
        this.config = config;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        this.mapper = new ObjectMapper();
    }

    public List<MediaItem> trending(String language) throws IOException, InterruptedException {
        JsonNode root = getJson("/trending/all/week?language=" + enc(language));
        return parseResults(root.path("results"), 12, language);
    }

    public List<MediaItem> search(String query, String language) throws IOException, InterruptedException {
        JsonNode root = getJson("/search/multi?query=" + enc(query) + "&language=" + enc(language) + "&include_adult=false");
        return parseResults(root.path("results"), 20, language);
    }

    public MediaItem details(MediaType type, long id, String language) throws IOException, InterruptedException {
        JsonNode root = getJson("/" + type.apiValue() + "/" + id + "?language=" + enc(language));
        return parseDetails(root, type, language);
    }

    public List<MediaItem> similar(MediaType type, long id, String language) throws IOException, InterruptedException {
        JsonNode root = getJson("/" + type.apiValue() + "/" + id + "/similar?language=" + enc(language));
        return parseResultsWithKnownType(root.path("results"), type, 18, language);
    }

    public List<MediaItem> discoverByGenres(List<Integer> genreIds,
                                            String language,
                                            ContentPreference contentPreference,
                                            double minRating,
                                            Integer yearFrom,
                                            Integer yearTo,
                                            String sortBy,
                                            int limit) throws IOException, InterruptedException {
        if (genreIds == null || genreIds.isEmpty()) {
            return List.of();
        }
        String joined = genreIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<MediaItem> combined = new ArrayList<>();
        if (contentPreference != ContentPreference.TV) {
            combined.addAll(discover(MediaType.MOVIE, joined, language, minRating, yearFrom, yearTo, sortBy, limit));
        }
        if (contentPreference != ContentPreference.MOVIE) {
            combined.addAll(discover(MediaType.TV, joined, language, minRating, yearFrom, yearTo, sortBy, limit));
        }
        return combined.stream().distinct().limit(limit * 2L).toList();
    }

    public List<MediaItem> recentReleases(String language,
                                          ContentPreference contentPreference,
                                          double minRating,
                                          Integer yearFrom,
                                          Integer yearTo,
                                          int limit) throws IOException, InterruptedException {
        List<MediaItem> combined = new ArrayList<>();
        if (contentPreference != ContentPreference.TV) {
            combined.addAll(discover(MediaType.MOVIE, null, language, minRating, yearFrom, yearTo, "release_date.desc", limit));
        }
        if (contentPreference != ContentPreference.MOVIE) {
            combined.addAll(discover(MediaType.TV, null, language, minRating, yearFrom, yearTo, "first_air_date.desc", limit));
        }
        return combined.stream().distinct().limit(limit * 2L).toList();
    }

    public Map<Integer, String> combinedGenres(String language) throws IOException, InterruptedException {
        Map<Integer, String> combined = new LinkedHashMap<>();
        combined.putAll(fetchGenreMap(MediaType.MOVIE, language));
        fetchGenreMap(MediaType.TV, language).forEach(combined::putIfAbsent);
        return combined;
    }

    private List<MediaItem> discover(MediaType type,
                                     String joinedGenres,
                                     String language,
                                     double minRating,
                                     Integer yearFrom,
                                     Integer yearTo,
                                     String sortBy,
                                     int limit) throws IOException, InterruptedException {
        StringBuilder path = new StringBuilder("/discover/").append(type.apiValue())
                .append("?language=").append(enc(language))
                .append("&include_adult=false")
                .append("&sort_by=").append(enc(sortBy));

        if (joinedGenres != null && !joinedGenres.isBlank()) {
            path.append("&with_genres=").append(enc(joinedGenres));
        }
        if (minRating > 0) {
            path.append("&vote_average.gte=").append(minRating);
        }
        if (yearFrom != null) {
            path.append(type == MediaType.MOVIE ? "&primary_release_date.gte=" : "&first_air_date.gte=")
                    .append(yearFrom).append("-01-01");
        }
        if (yearTo != null) {
            path.append(type == MediaType.MOVIE ? "&primary_release_date.lte=" : "&first_air_date.lte=")
                    .append(yearTo).append("-12-31");
        }
        JsonNode root = getJson(path.toString());
        return parseResultsWithKnownType(root.path("results"), type, limit, language);
    }

    private JsonNode getJson(String pathWithQuery) throws IOException, InterruptedException {
        String separator = pathWithQuery.contains("?") ? "&" : "?";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + pathWithQuery + separator + "api_key=" + enc(config.tmdbApiKey())))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IOException("TMDb request failed: HTTP " + response.statusCode() + " -> " + response.body());
        }
        return mapper.readTree(response.body());
    }

    private Map<Integer, String> fetchGenreMap(MediaType type, String language) throws IOException, InterruptedException {
        String key = type.apiValue() + "|" + language;
        Map<Integer, String> cached = genreCache.get(key);
        if (cached != null) {
            return cached;
        }
        JsonNode root = getJson("/genre/" + type.apiValue() + "/list?language=" + enc(language));
        Map<Integer, String> result = new LinkedHashMap<>();
        for (JsonNode node : root.path("genres")) {
            result.put(node.path("id").asInt(), node.path("name").asText(""));
        }
        genreCache.put(key, result);
        return result;
    }

    private List<MediaItem> parseResults(JsonNode results, int limit, String language) throws IOException, InterruptedException {
        List<MediaItem> items = new ArrayList<>();
        for (JsonNode node : results) {
            String mediaTypeValue = node.path("media_type").asText();
            if (!"movie".equals(mediaTypeValue) && !"tv".equals(mediaTypeValue)) {
                continue;
            }
            items.add(parseSummary(node, MediaType.fromApi(mediaTypeValue), language));
            if (items.size() >= limit) {
                break;
            }
        }
        return items;
    }

    private List<MediaItem> parseResultsWithKnownType(JsonNode results, MediaType type, int limit, String language) throws IOException, InterruptedException {
        List<MediaItem> items = new ArrayList<>();
        for (JsonNode node : results) {
            items.add(parseSummary(node, type, language));
            if (items.size() >= limit) {
                break;
            }
        }
        return items;
    }

    private MediaItem parseSummary(JsonNode node, MediaType type, String language) throws IOException, InterruptedException {
        MediaItem item = new MediaItem();
        item.setId(node.path("id").asLong());
        item.setMediaType(type);
        item.setTitle(type == MediaType.MOVIE ? node.path("title").asText("") : node.path("name").asText(""));
        item.setOverview(node.path("overview").asText("Описание отсутствует."));
        item.setReleaseDate(type == MediaType.MOVIE ? node.path("release_date").asText("") : node.path("first_air_date").asText(""));
        item.setVoteAverage(node.path("vote_average").asDouble(0.0));
        item.setPopularity(node.path("popularity").asDouble(0.0));
        item.setPosterUrl(imageUrl(node.path("poster_path").asText(null)));
        item.setBackdropUrl(imageUrl(node.path("backdrop_path").asText(null)));
        List<Integer> genreIds = new ArrayList<>();
        List<String> genreNames = new ArrayList<>();
        Map<Integer, String> lookup = fetchGenreMap(type, language);
        for (JsonNode genreId : node.path("genre_ids")) {
            int id = genreId.asInt();
            genreIds.add(id);
            String name = lookup.get(id);
            if (name != null && !name.isBlank()) {
                genreNames.add(name);
            }
        }
        item.setGenreIds(genreIds);
        item.setGenres(genreNames);
        return item;
    }

    private MediaItem parseDetails(JsonNode node, MediaType type, String language) throws IOException, InterruptedException {
        MediaItem item = new MediaItem();
        item.setId(node.path("id").asLong());
        item.setMediaType(type);
        item.setTitle(type == MediaType.MOVIE ? node.path("title").asText("") : node.path("name").asText(""));
        item.setOverview(node.path("overview").asText("Описание отсутствует."));
        item.setReleaseDate(type == MediaType.MOVIE ? node.path("release_date").asText("") : node.path("first_air_date").asText(""));
        item.setVoteAverage(node.path("vote_average").asDouble(0.0));
        item.setPopularity(node.path("popularity").asDouble(0.0));
        item.setPosterUrl(imageUrl(node.path("poster_path").asText(null)));
        item.setBackdropUrl(imageUrl(node.path("backdrop_path").asText(null)));
        List<Integer> genreIds = new ArrayList<>();
        List<String> genres = new ArrayList<>();
        for (JsonNode genreNode : node.path("genres")) {
            genreIds.add(genreNode.path("id").asInt());
            genres.add(genreNode.path("name").asText());
        }
        item.setGenreIds(genreIds);
        item.setGenres(genres);
        return item;
    }

    private String imageUrl(String path) {
        if (path == null || path.isBlank() || "null".equals(path)) {
            return null;
        }
        return IMAGE_BASE + path;
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
