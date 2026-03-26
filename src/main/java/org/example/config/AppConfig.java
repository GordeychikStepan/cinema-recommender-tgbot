package org.example.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record AppConfig(String botToken, String tmdbApiKey, String tmdbLanguage, Path dataDirectory) {
    public static AppConfig load() throws IOException {
        Properties props = new Properties();
        Path propsPath = Path.of("config.properties");
        if (Files.exists(propsPath)) {
            try (InputStream in = Files.newInputStream(propsPath)) {
                props.load(in);
            }
        }

        String botToken = value("BOT_TOKEN", "bot.token", props);
        String tmdbApiKey = value("TMDB_API_KEY", "tmdb.api.key", props);
        String language = valueOrDefault("TMDB_LANGUAGE", "tmdb.language", props, "ru-RU");
        String dataDirRaw = valueOrDefault("APP_DATA_DIR", "app.data.dir", props, "data");

        if (isBlank(botToken) || isBlank(tmdbApiKey)) {
            throw new IllegalStateException("Set BOT_TOKEN and TMDB_API_KEY via environment variables or config.properties");
        }

        Path dataDir = Path.of(dataDirRaw);
        Files.createDirectories(dataDir);
        return new AppConfig(botToken, tmdbApiKey, language, dataDir);
    }

    private static String value(String envName, String propKey, Properties props) {
        String envValue = System.getenv(envName);
        return !isBlank(envValue) ? envValue.trim() : props.getProperty(propKey);
    }

    private static String valueOrDefault(String envName, String propKey, Properties props, String defaultValue) {
        String value = value(envName, propKey, props);
        return isBlank(value) ? defaultValue : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
