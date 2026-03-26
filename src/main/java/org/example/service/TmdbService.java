package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.AppConfig;
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
    private final Map<Integer, String> movieGenres;
    private final Map<Integer, String> tvGenres;

    public TmdbService(AppConfig config) throws IOException, InterruptedException {
        this.config = config;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        this.mapper = new ObjectMapper();
        this.movieGenres = fetchGenreMap(MediaType.MOVIE);
        this.tvGenres = fetchGenreMap(MediaType.TV);
    }

    public List<MediaItem> trending() throws IOException, InterruptedException {
        JsonNode root = getJson("/trending/all/week?language=" + enc(config.tmdbLanguage()));
        return parseResults(root.path("results"), 8);
    }

    public List<MediaItem> search(String query) throws IOException, InterruptedException {
        JsonNode root = getJson("/search/multi?query=" + enc(query) + "&language=" + enc(config.tmdbLanguage()) + "&include_adult=false");
        return parseResults(root.path("results"), 8);
    }

    public MediaItem details(MediaType type, long id) throws IOException, InterruptedException {
        JsonNode root = getJson("/" + type.apiValue() + "/" + id + "?language=" + enc(config.tmdbLanguage()));
        return parseDetails(root, type);
    }

    public List<MediaItem> similar(MediaType type, long id) throws IOException, InterruptedException {
        JsonNode root = getJson("/" + type.apiValue() + "/" + id + "/similar?language=" + enc(config.tmdbLanguage()));
        return parseResultsWithKnownType(root.path("results"), type, 6);
    }

    public List<MediaItem> recommendByGenres(List<Integer> genreIds) throws IOException, InterruptedException {
        if (genreIds == null || genreIds.isEmpty()) {
            return trending();
        }
        String joined = genreIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<MediaItem> combined = new ArrayList<>();
        combined.addAll(parseResultsWithKnownType(getJson("/discover/movie?language=" + enc(config.tmdbLanguage()) + "&sort_by=popularity.desc&include_adult=false&with_genres=" + joined).path("results"), MediaType.MOVIE, 6));
        combined.addAll(parseResultsWithKnownType(getJson("/discover/tv?language=" + enc(config.tmdbLanguage()) + "&sort_by=popularity.desc&with_genres=" + joined).path("results"), MediaType.TV, 6));
        return combined.stream().distinct().limit(10).toList();
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

    private Map<Integer, String> fetchGenreMap(MediaType type) throws IOException, InterruptedException {
        JsonNode root = getJson("/genre/" + type.apiValue() + "/list?language=" + enc(config.tmdbLanguage()));
        Map<Integer, String> result = new HashMap<>();
        for (JsonNode node : root.path("genres")) {
            result.put(node.path("id").asInt(), node.path("name").asText(""));
        }
        return result;
    }

    private List<MediaItem> parseResults(JsonNode results, int limit) {
        List<MediaItem> items = new ArrayList<>();
        for (JsonNode node : results) {
            String mediaTypeValue = node.path("media_type").asText();
            if (!"movie".equals(mediaTypeValue) && !"tv".equals(mediaTypeValue)) {
                continue;
            }
            items.add(parseSummary(node, MediaType.fromApi(mediaTypeValue)));
            if (items.size() >= limit) {
                break;
            }
        }
        return items;
    }

    private List<MediaItem> parseResultsWithKnownType(JsonNode results, MediaType type, int limit) {
        List<MediaItem> items = new ArrayList<>();
        for (JsonNode node : results) {
            items.add(parseSummary(node, type));
            if (items.size() >= limit) {
                break;
            }
        }
        return items;
    }

    private MediaItem parseSummary(JsonNode node, MediaType type) {
        MediaItem item = new MediaItem();
        item.setId(node.path("id").asLong());
        item.setMediaType(type);
        item.setTitle(type == MediaType.MOVIE ? node.path("title").asText("") : node.path("name").asText(""));
        item.setOverview(node.path("overview").asText("Описание отсутствует."));
        item.setReleaseDate(type == MediaType.MOVIE ? node.path("release_date").asText("") : node.path("first_air_date").asText(""));
        item.setVoteAverage(node.path("vote_average").asDouble(0.0));
        item.setPosterUrl(imageUrl(node.path("poster_path").asText(null)));
        item.setBackdropUrl(imageUrl(node.path("backdrop_path").asText(null)));
        List<Integer> genreIds = new ArrayList<>();
        List<String> genreNames = new ArrayList<>();
        Map<Integer, String> lookup = type == MediaType.MOVIE ? movieGenres : tvGenres;
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

    private MediaItem parseDetails(JsonNode node, MediaType type) {
        MediaItem item = new MediaItem();
        item.setId(node.path("id").asLong());
        item.setMediaType(type);
        item.setTitle(type == MediaType.MOVIE ? node.path("title").asText("") : node.path("name").asText(""));
        item.setOverview(node.path("overview").asText("Описание отсутствует."));
        item.setReleaseDate(type == MediaType.MOVIE ? node.path("release_date").asText("") : node.path("first_air_date").asText(""));
        item.setVoteAverage(node.path("vote_average").asDouble(0.0));
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
