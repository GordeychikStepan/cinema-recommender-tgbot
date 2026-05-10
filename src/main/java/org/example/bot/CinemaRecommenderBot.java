package org.example.bot;

import org.example.config.AppConfig;
import org.example.model.ContentPreference;
import org.example.model.MediaItem;
import org.example.model.MediaType;
import org.example.model.UserProfile;
import org.example.service.TmdbService;
import org.example.storage.UserProfileStore;
import org.example.util.Texts;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CinemaRecommenderBot implements LongPollingSingleThreadUpdateConsumer {
    private static final int PAGE_SIZE = 4;
    private static final List<Integer> ONBOARDING_GENRES = List.of(28, 12, 16, 35, 80, 18, 14, 27, 9648, 10749, 878, 53);

    private final TelegramClient telegramClient;
    private final TmdbService tmdbService;
    private final UserProfileStore profileStore;
    private final Map<String, MediaItem> sessionCache = new ConcurrentHashMap<>();
    private final Map<String, PagedListSession> pagedSessions = new ConcurrentHashMap<>();

    public CinemaRecommenderBot(AppConfig config, TmdbService tmdbService, UserProfileStore profileStore) {
        this.telegramClient = new OkHttpTelegramClient(config.botToken());
        this.tmdbService = tmdbService;
        this.profileStore = profileStore;
    }

    @Override
    public void consume(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                handleMessage(update.getMessage());
            }
        } catch (Exception e) {
            long chatId = extractChatId(update);
            if (chatId != 0) {
                safeSendText(chatId, "Произошла ошибка при обработке запроса: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }

    private void handleMessage(Message message) throws Exception {
        long chatId = message.getChatId();
        User user = message.getFrom();
        UserProfile profile = profileStore.getOrCreate(user);
        String text = message.getText().trim();

        if ("/start".equals(text)) {
            sendMainMenu(chatId, Texts.welcome());
            if (!profile.isOnboardingCompleted()) {
                startSettingsWizard(chatId, profile, false, null);
            }
            return;
        }
        if ("/help".equals(text)) {
            safeSendText(chatId, Texts.help());
            return;
        }
        if ("/settings".equals(text) || "⚙️ Settings".equals(text)) {
            startSettingsWizard(chatId, profile, false, null);
            return;
        }
        if ("/new".equals(text) || "🆕 New".equals(text)) {
            sendPagedMediaList(chatId, "🔥 Актуальные новинки и популярное", buildNewAndTrending(profile), profile, true);
            return;
        }
        if ("/favorites".equals(text) || "❤️ Favorites".equals(text)) {
            sendStoredCollection(chatId, "❤️ Избранное", profile.getFavorites(), profile, false);
            return;
        }
        if ("/watchlist".equals(text) || "🕒 Watchlist".equals(text)) {
            sendStoredCollection(chatId, "🕒 Смотреть позже", profile.getWatchlist(), profile, false);
            return;
        }
        if ("/recommend".equals(text) || "🎯 Recommend".equals(text)) {
            List<MediaItem> items = buildRecommendations(profile);
            sendPagedMediaList(chatId, "🎯 Рекомендации для тебя", items, profile, true);
            return;
        }
        if ("/notifytest".equals(text)) {
            List<MediaItem> items = tmdbService.recentReleases(profile.getLanguage(), profile.getContentPreference(), profile.getMinRating(), profile.getMinPopularity(), profile.getYearFrom(), profile.getYearTo(), 5);
            sendNotificationDigest(profile, items);
            return;
        }

        List<MediaItem> found = tmdbService.search(text, profile.getLanguage());
        if (found.isEmpty()) {
            safeSendText(chatId, "Ничего не найдено. Попробуй другое название.");
            return;
        }
        sendPagedMediaList(chatId, "🔎 Результаты поиска по запросу: " + text, found, profile, false);
    }

    private void handleCallback(CallbackQuery callback) throws Exception {
        String data = callback.getData();
        long chatId = callback.getMessage().getChatId();
        UserProfile profile = profileStore.getOrCreate(callback.getFrom());

        if (data.startsWith("page:")) {
            String[] parts = data.split(":");
            renderPage(chatId, parts[1], Integer.parseInt(parts[2]), callback.getMessage().getMessageId());
            return;
        }
        if (data.startsWith("settings:")) {
            handleSettingsCallback(chatId, callback, profile);
            return;
        }
        if (data.startsWith("details:")) {
            MediaItem item = getOrLoad(data.substring("details:".length()), profile.getLanguage());
            sendMediaCard(chatId, item, profile);
            return;
        }

        String[] parts = data.split(":", 3);
        if (parts.length < 3) {
            return;
        }
        String action = parts[0];
        MediaItem item = getOrLoad(parts[1] + ":" + parts[2], profile.getLanguage());

        switch (action) {
            case "fav" -> {
                profileStore.addFavorite(profile, item);
                safeSendText(chatId, "Добавил в избранное: " + item.getTitle());
            }
            case "unfav" -> {
                profileStore.removeFavorite(profile, item);
                safeSendText(chatId, "Убрал из избранного: " + item.getTitle());
            }
            case "watch" -> {
                profileStore.addWatchlist(profile, item);
                safeSendText(chatId, "Добавил в список «Смотреть позже»: " + item.getTitle());
            }
            case "unwatch" -> {
                profileStore.removeWatchlist(profile, item);
                safeSendText(chatId, "Убрал из списка «Смотреть позже»: " + item.getTitle());
            }
            case "like" -> {
                profileStore.like(profile, item);
                safeSendText(chatId, "Учёл лайк для: " + item.getTitle());
            }
            case "unlike" -> {
                profileStore.unlike(profile, item);
                safeSendText(chatId, "Лайк снят: " + item.getTitle());
            }
            case "dislike" -> {
                profileStore.dislike(profile, item);
                safeSendText(chatId, "Учёл дизлайк: " + item.getTitle());
            }
            case "undislike" -> {
                profileStore.undislike(profile, item);
                safeSendText(chatId, "Дизлайк снят: " + item.getTitle());
            }
            case "seen" -> {
                profileStore.markSeen(profile, item);
                safeSendText(chatId, "Отметил как просмотренное: " + item.getTitle());
            }
            case "unseen" -> {
                profileStore.unmarkSeen(profile, item);
                safeSendText(chatId, "Убрал отметку «просмотрено»: " + item.getTitle());
            }
            case "trailer" -> {
                sendTrailer(chatId, item, profile);
                answerCallbackRemoveSpinner(callback);
                return;
            }
            case "images" -> {
                sendGallery(chatId, item, profile);
                answerCallbackRemoveSpinner(callback);
                return;
            }
            case "similar" -> sendPagedMediaList(chatId, "🎬 Похожие на: " + item.getTitle(), tmdbService.similar(item.getMediaType(), item.getId(), profile.getLanguage()), profile, false);
            default -> {
                return;
            }
        }
        answerCallbackRemoveSpinner(callback);
        sendMediaCard(chatId, item, profile);
    }

    private void handleSettingsCallback(long chatId, CallbackQuery callback, UserProfile profile) throws Exception {
        String data = callback.getData();
        String[] parts = data.split(":");
        if (parts.length < 2) {
            return;
        }
        String step = parts[1];
        Integer messageId = callback.getMessage().getMessageId();

        switch (step) {
            case "genre" -> {
                profileStore.togglePreferredGenre(profile, Integer.parseInt(parts[2]));
                profile.setSetupStep("GENRES");
                profileStore.saveSettings(profile);
                renderSettingsStep(chatId, profile, messageId);
            }
            case "genresdone" -> {
                profile.setSetupStep("CONTENT");
                profileStore.saveSettings(profile);
                renderSettingsStep(chatId, profile, messageId);
            }
            case "content" -> {
                profile.setContentPreference(ContentPreference.fromValue(parts[2]));
                profile.setSetupStep("RATING");
                profileStore.saveSettings(profile);
                renderSettingsStep(chatId, profile, messageId);
            }
            case "rating" -> {
                profile.setMinRating(Double.parseDouble(parts[2]));
                profile.setSetupStep("POPULARITY");
                profileStore.saveSettings(profile);
                renderSettingsStep(chatId, profile, messageId);
            }
            case "popularity" -> {
                profile.setMinPopularity(Double.parseDouble(parts[2]));
                profile.setSetupStep("YEAR");
                profileStore.saveSettings(profile);
                renderSettingsStep(chatId, profile, messageId);
            }
            case "year" -> {
                applyYearPreset(profile, parts[2]);
                profile.setSetupStep("LANG");
                profileStore.saveSettings(profile);
                renderSettingsStep(chatId, profile, messageId);
            }
            case "lang" -> {
                profile.setLanguage(parts[2]);
                profile.setSetupStep("NOTIFY");
                profileStore.saveSettings(profile);
                renderSettingsStep(chatId, profile, messageId);
            }
            case "notify" -> {
                profile.setNotificationsEnabled("on".equals(parts[2]));
                profile.setOnboardingCompleted(true);
                profile.setSetupStep("DONE");
                profileStore.saveSettings(profile);
                renderSettingsStep(chatId, profile, messageId);
            }
            case "finish" -> {
                profile.setSetupStep(null);
                profileStore.saveSettings(profile);
                safeSendText(chatId, "Настройки сохранены. Теперь можно искать фильмы и получать рекомендации.");
            }
            case "restart" -> startSettingsWizard(chatId, profile, true, messageId);
            default -> {
            }
        }
        answerCallbackRemoveSpinner(callback);
    }

    private void startSettingsWizard(long chatId, UserProfile profile, boolean edit, Integer messageId) throws Exception {
        if (profile.getSetupStep() == null || "DONE".equals(profile.getSetupStep())) {
            profile.setSetupStep("GENRES");
            profileStore.saveSettings(profile);
        }
        renderSettingsStep(chatId, profile, edit ? messageId : null);
    }

    private void renderSettingsStep(long chatId, UserProfile profile, Integer messageId) throws Exception {
        String step = profile.getSetupStep();
        if (step == null) step = "GENRES";

        String text;
        InlineKeyboardMarkup keyboard;
        switch (step) {
            case "CONTENT" -> {
                text = Texts.settingsSummary(profile) + "\n\nШаг 2 из 7. Выбери тип контента:";
                keyboard = InlineKeyboardMarkup.builder()
                        .keyboardRow(new InlineKeyboardRow(
                                button("🎬 Фильмы", "settings:content:MOVIE"),
                                button("📺 Сериалы", "settings:content:TV")
                        ))
                        .keyboardRow(new InlineKeyboardRow(button("🎞 Всё", "settings:content:ALL")))
                        .build();
            }
            case "RATING" -> {
                text = Texts.settingsSummary(profile) + "\n\nШаг 3 из 7. Минимальный рейтинг TMDb:";
                keyboard = InlineKeyboardMarkup.builder()
                        .keyboardRow(new InlineKeyboardRow(button("Без фильтра", "settings:rating:0"), button("6+", "settings:rating:6"), button("7+", "settings:rating:7")))
                        .keyboardRow(new InlineKeyboardRow(button("8+", "settings:rating:8"), button("5+", "settings:rating:5")))
                        .build();
            }
            case "POPULARITY" -> {
                text = Texts.settingsSummary(profile) + "\n\nШаг 4 из 7. Минимальная популярность TMDb:\n" +
                        "Популярность — внутренний показатель TMDb. Чем выше число, тем чаще тайтл находится в активном интересе пользователей.";
                keyboard = InlineKeyboardMarkup.builder()
                        .keyboardRow(new InlineKeyboardRow(button("Без фильтра", "settings:popularity:0"), button("10+", "settings:popularity:10"), button("30+", "settings:popularity:30")))
                        .keyboardRow(new InlineKeyboardRow(button("50+", "settings:popularity:50"), button("100+", "settings:popularity:100")))
                        .build();
            }
            case "YEAR" -> {
                text = Texts.settingsSummary(profile) + "\n\nШаг 5 из 7. Выбери диапазон годов:";
                keyboard = InlineKeyboardMarkup.builder()
                        .keyboardRow(new InlineKeyboardRow(button("Любые", "settings:year:any"), button("2000–2009", "settings:year:2000_2009")))
                        .keyboardRow(new InlineKeyboardRow(button("2010–2019", "settings:year:2010_2019"), button("2020+", "settings:year:2020_plus")))
                        .keyboardRow(new InlineKeyboardRow(button("2015+", "settings:year:2015_plus")))
                        .build();
            }
            case "LANG" -> {
                text = Texts.settingsSummary(profile) + "\n\nШаг 6 из 7. Язык карточек:";
                keyboard = InlineKeyboardMarkup.builder()
                        .keyboardRow(new InlineKeyboardRow(button("Русский", "settings:lang:ru-RU"), button("English", "settings:lang:en-US")))
                        .build();
            }
            case "NOTIFY" -> {
                text = Texts.settingsSummary(profile) + "\n\nШаг 7 из 7. Включить уведомления о новинках?";
                keyboard = InlineKeyboardMarkup.builder()
                        .keyboardRow(new InlineKeyboardRow(button("Да", "settings:notify:on"), button("Нет", "settings:notify:off")))
                        .build();
            }
            case "DONE" -> {
                text = Texts.settingsSummary(profile) + "\n\nНастройка завершена.";
                keyboard = InlineKeyboardMarkup.builder()
                        .keyboardRow(new InlineKeyboardRow(button("Готово", "settings:finish"), button("Начать заново", "settings:restart")))
                        .build();
            }
            default -> {
                text = Texts.settingsSummary(profile) + "\n\nШаг 1 из 7. Выбери любимые жанры. Можно отметить несколько.";
                keyboard = buildGenresKeyboard(profile);
            }
        }

        if (messageId == null) {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .replyMarkup(keyboard)
                    .build();
            telegramClient.execute(message);
        } else {
            EditMessageText edit = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text(text)
                    .replyMarkup(keyboard)
                    .build();
            telegramClient.execute(edit);
        }
    }

    private InlineKeyboardMarkup buildGenresKeyboard(UserProfile profile) throws Exception {
        Map<Integer, String> genres = tmdbService.combinedGenres(profile.getLanguage());
        List<InlineKeyboardRow> rows = new ArrayList<>();
        List<InlineKeyboardButton> currentRow = new ArrayList<>();
        for (Integer genreId : ONBOARDING_GENRES) {
            String name = genres.get(genreId);
            if (name == null) continue;
            String label = profile.getPreferredGenres().contains(genreId) ? "✅ " + name : name;
            currentRow.add(button(label, "settings:genre:" + genreId));
            if (currentRow.size() == 2) {
                rows.add(new InlineKeyboardRow(currentRow));
                currentRow = new ArrayList<>();
            }
        }
        if (!currentRow.isEmpty()) rows.add(new InlineKeyboardRow(currentRow));
        rows.add(new InlineKeyboardRow(button("Далее", "settings:genresdone")));
        return new InlineKeyboardMarkup(rows);
    }

    private void applyYearPreset(UserProfile profile, String preset) {
        switch (preset) {
            case "2000_2009" -> {
                profile.setYearFrom(2000);
                profile.setYearTo(2009);
            }
            case "2010_2019" -> {
                profile.setYearFrom(2010);
                profile.setYearTo(2019);
            }
            case "2020_plus" -> {
                profile.setYearFrom(2020);
                profile.setYearTo(null);
            }
            case "2015_plus" -> {
                profile.setYearFrom(2015);
                profile.setYearTo(null);
            }
            default -> {
                profile.setYearFrom(null);
                profile.setYearTo(null);
            }
        }
    }


    private List<MediaItem> buildNewAndTrending(UserProfile profile) throws Exception {
        List<MediaItem> candidates = new ArrayList<>();
        candidates.addAll(tmdbService.recentReleases(profile.getLanguage(), profile.getContentPreference(), profile.getMinRating(), profile.getMinPopularity(), profile.getYearFrom(), profile.getYearTo(), 12));
        candidates.addAll(tmdbService.trending(profile.getLanguage()));
        Map<String, MediaItem> unique = new LinkedHashMap<>();
        for (MediaItem item : candidates) {
            unique.putIfAbsent(item.getStorageKey(), item);
        }
        return unique.values().stream()
                .filter(item -> matchesSettings(profile, item))
                .sorted(Comparator.comparingDouble(item -> -item.getPopularity()))
                .limit(20)
                .toList();
    }

    private List<MediaItem> buildRecommendations(UserProfile profile) throws Exception {
        String language = profile.getLanguage();
        List<Integer> topGenres = profileStore.topGenres(profile, 5);
        List<MediaItem> candidates = new ArrayList<>();
        candidates.addAll(tmdbService.discoverByGenres(topGenres, language, profile.getContentPreference(), profile.getMinRating(), profile.getMinPopularity(), profile.getYearFrom(), profile.getYearTo(), "popularity.desc", 12));
        candidates.addAll(tmdbService.recentReleases(language, profile.getContentPreference(), profile.getMinRating(), profile.getMinPopularity(), profile.getYearFrom(), profile.getYearTo(), 8));
        candidates.addAll(tmdbService.trending(language));

        Map<String, MediaItem> unique = new LinkedHashMap<>();
        for (MediaItem item : candidates) {
            unique.putIfAbsent(item.getStorageKey(), item);
        }

        return unique.values().stream()
                .filter(item -> matchesSettings(profile, item))
                .filter(item -> !profileStore.shouldExclude(profile, item))
                .sorted(Comparator.comparingDouble(item -> -recommendationScore(profile, item)))
                .limit(20)
                .toList();
    }

    private boolean matchesSettings(UserProfile profile, MediaItem item) {
        if (profile.getContentPreference() == ContentPreference.MOVIE && item.getMediaType() != MediaType.MOVIE) return false;
        if (profile.getContentPreference() == ContentPreference.TV && item.getMediaType() != MediaType.TV) return false;
        if (item.getVoteAverage() < profile.getMinRating()) return false;
        if (item.getPopularity() < profile.getMinPopularity()) return false;
        Integer year = item.releaseYear();
        if (profile.getYearFrom() != null && year != null && year < profile.getYearFrom()) return false;
        if (profile.getYearTo() != null && year != null && year > profile.getYearTo()) return false;
        return true;
    }

    private double recommendationScore(UserProfile profile, MediaItem item) {
        double genreScore = 0;
        for (Integer genreId : item.getGenreIds()) {
            genreScore += profile.getGenreWeights().getOrDefault(genreId, 0);
            if (profile.getPreferredGenres().contains(genreId)) {
                genreScore += 3;
            }
        }
        double ratingBonus = item.getVoteAverage() * 0.7;
        double popularityBonus = Math.min(item.getPopularity() / 40.0, 3.0);
        double recencyBonus = item.recencyScore();
        double watchlistBonus = profile.getWatchlist().contains(item.getStorageKey()) ? 2.5 : 0;
        double favoriteBonus = profile.getFavorites().contains(item.getStorageKey()) ? 6 : 0;
        double likeBonus = profile.getLiked().contains(item.getStorageKey()) ? 3 : 0;
        double dislikePenalty = profile.getDisliked().contains(item.getStorageKey()) ? 20 : 0;
        return genreScore + ratingBonus + popularityBonus + recencyBonus + favoriteBonus + likeBonus + watchlistBonus - dislikePenalty;
    }

    private MediaItem getOrLoad(String key, String language) throws Exception {
        MediaItem cached = sessionCache.get(key + "|" + language);
        if (cached != null && cached.hasDetailedInfo()) {
            return cached;
        }
        MediaItem persistentCached = profileStore.loadCachedMedia(key, language);
        if (persistentCached != null && persistentCached.hasDetailedInfo()) {
            sessionCache.put(key + "|" + language, persistentCached);
            return persistentCached;
        }
        String[] parts = key.split(":", 2);
        MediaType type = MediaType.fromApi(parts[0]);
        long id = Long.parseLong(parts[1]);
        MediaItem loaded = tmdbService.details(type, id, language);
        sessionCache.put(key + "|" + language, loaded);
        profileStore.saveCachedMedia(loaded, language);
        return loaded;
    }

    private void sendStoredCollection(long chatId, String title, Set<String> keys, UserProfile profile, boolean markShown) throws Exception {
        if (keys.isEmpty()) {
            safeSendText(chatId, title + " пока пусто.");
            return;
        }
        List<MediaItem> items = new ArrayList<>();
        for (String key : keys) {
            try {
                items.add(getOrLoad(key, profile.getLanguage()));
            } catch (Exception ignored) {
            }
        }
        if (items.isEmpty()) {
            safeSendText(chatId, title + " пока пусто.");
            return;
        }
        sendPagedMediaList(chatId, title, items, profile, markShown);
    }

    private void sendPagedMediaList(long chatId, String title, List<MediaItem> items, UserProfile profile, boolean markShown) throws Exception {
        if (items == null || items.isEmpty()) {
            safeSendText(chatId, "Ничего не найдено.");
            return;
        }
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        pagedSessions.put(token, new PagedListSession(title, items, profile.getUserId(), markShown));
        renderPage(chatId, token, 0, null);
    }

    private void renderPage(long chatId, String token, Integer page, Integer messageId) throws Exception {
        PagedListSession session = pagedSessions.get(token);
        if (session == null) {
            safeSendText(chatId, "Сессия списка истекла. Сформируй список заново.");
            return;
        }
        int totalPages = (int) Math.ceil((double) session.items().size() / PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int from = safePage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, session.items().size());
        List<MediaItem> pageItems = session.items().subList(from, to);

        if (session.markShown()) {
            UserProfile currentProfile = profileStore.getByUserId(session.userId());
            for (MediaItem item : pageItems) {
                profileStore.markShown(currentProfile, item);
            }
        }

        StringBuilder text = new StringBuilder(session.title())
                .append("\nСтраница ").append(safePage + 1).append(" из ").append(totalPages)
                .append("\n\n");

        int index = from + 1;
        for (MediaItem item : pageItems) {
            text.append(index++)
                    .append(". ")
                    .append(item.getTitle())
                    .append(" (").append(item.shortYear()).append(")")
                    .append(" — ").append(item.getMediaType().labelRu())
                    .append(" — рейтинг ").append(String.format("%.1f", item.getVoteAverage()))
                    .append("\n");
        }

        InlineKeyboardMarkup keyboard = buildPageKeyboard(token, safePage, totalPages, pageItems);
        if (messageId == null) {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId)
                    .text(text.toString())
                    .replyMarkup(keyboard)
                    .build();
            telegramClient.execute(message);
        } else {
            EditMessageText edit = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text(text.toString())
                    .replyMarkup(keyboard)
                    .build();
            telegramClient.execute(edit);
        }
    }

    private InlineKeyboardMarkup buildPageKeyboard(String token, int page, int totalPages, List<MediaItem> items) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (MediaItem item : items) {
            sessionCache.put(item.getStorageKey() + "|ru-RU", item);
            sessionCache.put(item.getStorageKey() + "|en-US", item);
            profileStore.saveCachedMedia(item, "ru-RU");
            String title = item.getTitle();
            if (title.length() > 30) title = title.substring(0, 27) + "...";
            rows.add(new InlineKeyboardRow(button("🎬 " + title, "details:" + item.getStorageKey())));
        }
        List<InlineKeyboardButton> pager = new ArrayList<>();
        if (page > 0) pager.add(button("⬅️ Назад", "page:" + token + ":" + (page - 1)));
        if (page < totalPages - 1) pager.add(button(page == 0 ? "Показать ещё ➡️" : "➡️ Далее", "page:" + token + ":" + (page + 1)));
        if (!pager.isEmpty()) rows.add(new InlineKeyboardRow(pager));
        return new InlineKeyboardMarkup(rows);
    }

    private void sendMediaCard(long chatId, MediaItem item, UserProfile profile) throws Exception {
        profileStore.rememberTitle(profile, item);
        profileStore.saveCachedMedia(item, profile.getLanguage());
        if (item.getPosterUrl() != null) {
            SendPhoto photo = SendPhoto.builder()
                    .chatId(chatId)
                    .photo(new InputFile(item.getPosterUrl()))
                    .caption(Texts.mediaCard(item))
                    .parseMode("HTML")
                    .replyMarkup(cardKeyboard(item, profile))
                    .build();
            telegramClient.execute(photo);
            return;
        }
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(Texts.mediaCard(item))
                .parseMode("HTML")
                .replyMarkup(cardKeyboard(item, profile))
                .build();
        telegramClient.execute(message);
    }

    private InlineKeyboardMarkup cardKeyboard(MediaItem item, UserProfile profile) {
        String key = item.getStorageKey();
        List<InlineKeyboardRow> rows = new ArrayList<>();

        rows.add(new InlineKeyboardRow(
                profile.getFavorites().contains(key) ? button("💔 Убрать избранное", "unfav:" + key) : button("❤️ Избранное", "fav:" + key),
                profile.getWatchlist().contains(key) ? button("❌ Убрать позже", "unwatch:" + key) : button("🕒 Смотреть позже", "watch:" + key)
        ));
        rows.add(new InlineKeyboardRow(
                profile.getSeen().contains(key) ? button("↩️ Не просмотрено", "unseen:" + key) : button("✅ Просмотрено", "seen:" + key)
        ));
        rows.add(new InlineKeyboardRow(
                profile.getLiked().contains(key) ? button("↩️ Убрать лайк", "unlike:" + key) : button("👍", "like:" + key),
                profile.getDisliked().contains(key) ? button("↩️ Убрать дизлайк", "undislike:" + key) : button("👎", "dislike:" + key),
                button("🎬 Похожие", "similar:" + key)
        ));
        rows.add(new InlineKeyboardRow(
                button("▶️ Трейлер", "trailer:" + key),
                button("🖼 Кадры", "images:" + key)
        ));
        return new InlineKeyboardMarkup(rows);
    }


    private void sendTrailer(long chatId, MediaItem item, UserProfile profile) throws Exception {
        Optional<String> trailerUrl = tmdbService.trailerUrl(item.getMediaType(), item.getId(), profile.getLanguage());
        if (trailerUrl.isEmpty()) {
            safeSendText(chatId, "Трейлер для «" + item.getTitle() + "» не найден.");
            return;
        }
        safeSendText(chatId, "▶️ Трейлер «" + item.getTitle() + "»: " + trailerUrl.get());
    }

    private void sendGallery(long chatId, MediaItem item, UserProfile profile) throws Exception {
        List<String> images = tmdbService.imageGallery(item.getMediaType(), item.getId(), profile.getLanguage(), 6);
        if (images.size() < 2) {
            safeSendText(chatId, "Кадров для «" + item.getTitle() + "» пока недостаточно.");
            return;
        }
        List<InputMedia> media = new ArrayList<>();
        for (int i = 0; i < Math.min(images.size(), 6); i++) {
            InputMediaPhoto photo = new InputMediaPhoto(images.get(i));
            if (i == 0) {
                photo.setCaption("🖼 Кадры: " + item.getTitle());
            }
            media.add(photo);
        }
        telegramClient.execute(SendMediaGroup.builder()
                .chatId(chatId)
                .medias(media)
                .build());
    }

    public void sendNotificationDigest(UserProfile profile, List<MediaItem> items) {
        if (profile == null || items == null || items.isEmpty()) {
            return;
        }
        StringBuilder text = new StringBuilder("🆕 Новинки FilmFinder по твоим настройкам\n\n");
        int index = 1;
        for (MediaItem item : items) {
            text.append(index++)
                    .append(". ")
                    .append(item.getTitle())
                    .append(" (").append(item.shortYear()).append(")")
                    .append(" — рейтинг ").append(String.format("%.1f", item.getVoteAverage()))
                    .append("\n");
        }
        text.append("\nОткрой карточки через кнопку 🆕 New или найди название в поиске.");
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(profile.getUserId())
                    .text(text.toString())
                    .replyMarkup(mainMenuKeyboard())
                    .build());
            for (MediaItem item : items.stream().limit(3).toList()) {
                sendMediaCard(profile.getUserId(), item, profile);
            }
        } catch (Exception e) {
            System.err.println("Failed to send notification digest to " + profile.getUserId() + ": " + e.getMessage());
        }
    }

    private InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder().text(text).callbackData(callbackData).build();
    }

    private void sendMainMenu(long chatId, String text) {
        SendMessage msg = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(mainMenuKeyboard())
                .build();
        try {
            telegramClient.execute(msg);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ReplyKeyboardMarkup mainMenuKeyboard() {
        KeyboardRow row1 = new KeyboardRow();
        row1.add("🆕 New");
        row1.add("🎯 Recommend");
        row1.add("❤️ Favorites");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("🕒 Watchlist");
        row2.add("⚙️ Settings");

        return ReplyKeyboardMarkup.builder()
                .keyboardRow(row1)
                .keyboardRow(row2)
                .resizeKeyboard(true)
                .isPersistent(true)
                .oneTimeKeyboard(false)
                .selective(false)
                .build();
    }

    private void safeSendText(long chatId, String text) {
        try {
            telegramClient.execute(SendMessage.builder().chatId(chatId).text(text).replyMarkup(mainMenuKeyboard()).build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void answerCallbackRemoveSpinner(CallbackQuery callback) {
        try {
            // no-op: spinner is removed automatically after sending a response/edit in most clients.
        } catch (Exception ignored) {
        }
    }

    private long extractChatId(Update update) {
        if (update == null) return 0;
        if (update.hasMessage()) return update.getMessage().getChatId();
        if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) {
            return update.getCallbackQuery().getMessage().getChatId();
        }
        return 0;
    }

    private record PagedListSession(String title, List<MediaItem> items, long userId, boolean markShown) {
    }
}
