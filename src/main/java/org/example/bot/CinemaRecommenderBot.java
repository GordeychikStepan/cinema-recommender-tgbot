package org.example.bot;

import org.example.config.AppConfig;
import org.example.model.MediaItem;
import org.example.model.MediaType;
import org.example.model.UserProfile;
import org.example.service.TmdbService;
import org.example.storage.UserProfileStore;
import org.example.util.Texts;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CinemaRecommenderBot implements LongPollingSingleThreadUpdateConsumer {
    private final TelegramClient telegramClient;
    private final TmdbService tmdbService;
    private final UserProfileStore profileStore;
    private final Map<String, MediaItem> sessionCache = new ConcurrentHashMap<>();

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

        if (text.startsWith("/start")) {
            sendMainMenu(chatId, Texts.welcome());
            return;
        }
        if (text.startsWith("/help")) {
            safeSendText(chatId, Texts.help());
            return;
        }
        if (text.startsWith("/new") || text.equals("🆕 New")) {
            sendMediaList(chatId, "🔥 Актуальные новинки и популярное", tmdbService.trending());
            return;
        }
        if (text.startsWith("/favorites") || text.equals("❤️ Favorites")) {
            sendFavorites(chatId, profile);
            return;
        }
        if (text.startsWith("/recommend") || text.equals("🎯 Recommend")) {
            List<MediaItem> items = tmdbService.recommendByGenres(profileStore.topGenres(profile, 3));
            items = items.stream().filter(item -> !profileStore.shouldExclude(profile, item)).limit(6).toList();
            if (items.isEmpty()) {
                items = tmdbService.trending();
            }
            sendMediaList(chatId, "🎯 Рекомендации для тебя", items);
            return;
        }

        List<MediaItem> found = tmdbService.search(text);
        if (found.isEmpty()) {
            safeSendText(chatId, "Ничего не найдено. Попробуй другое название.");
            return;
        }
        sendMediaList(chatId, "🔎 Результаты поиска по запросу: " + text, found);
    }

    private void handleCallback(CallbackQuery callback) throws Exception {
        String data = callback.getData();
        long chatId = callback.getMessage().getChatId();
        UserProfile profile = profileStore.getOrCreate(callback.getFrom());

        if (data.startsWith("details:")) {
            MediaItem item = getOrLoad(data.substring("details:".length()));
            sendMediaCard(chatId, item);
            return;
        }

        String[] parts = data.split(":", 3);
        if (parts.length < 3) {
            return;
        }
        String action = parts[0];
        MediaItem item = getOrLoad(parts[1] + ":" + parts[2]);
        switch (action) {
            case "fav" -> {
                profileStore.addFavorite(profile, item);
                safeSendText(chatId, "Добавил в избранное: " + item.getTitle());
            }
            case "like" -> {
                profileStore.like(profile, item);
                safeSendText(chatId, "Отлично, учёл лайк для: " + item.getTitle());
            }
            case "dislike" -> {
                profileStore.dislike(profile, item);
                safeSendText(chatId, "Понял, такие тайтлы буду показывать реже: " + item.getTitle());
            }
            case "seen" -> {
                profileStore.markSeen(profile, item);
                safeSendText(chatId, "Отметил как просмотренное: " + item.getTitle());
            }
            case "similar" -> sendMediaList(chatId, "🎬 Похожие на: " + item.getTitle(), tmdbService.similar(item.getMediaType(), item.getId()));
            default -> {
            }
        }
    }

    private MediaItem getOrLoad(String key) throws Exception {
        MediaItem cached = sessionCache.get(key);
        if (cached != null) {
            return cached;
        }
        String[] parts = key.split(":", 2);
        MediaType type = MediaType.fromApi(parts[0]);
        long id = Long.parseLong(parts[1]);
        MediaItem loaded = tmdbService.details(type, id);
        sessionCache.put(key, loaded);
        return loaded;
    }

    private void sendFavorites(long chatId, UserProfile profile) {
        if (profile.getFavorites().isEmpty()) {
            safeSendText(chatId, "Избранное пока пустое. Открой карточку фильма или сериала и нажми ❤️.");
            return;
        }
        StringBuilder sb = new StringBuilder("❤️ Твоё избранное:\n\n");
        int i = 1;
        for (String key : profile.getFavorites()) {
            String title = profile.getItemTitles().getOrDefault(key, key);
            sb.append(i++).append(". ").append(title).append(" [").append(key).append("]").append("\n");
        }
        safeSendText(chatId, sb.toString());
    }

    private void sendMediaList(long chatId, String title, List<MediaItem> items) throws Exception {
        if (items == null || items.isEmpty()) {
            safeSendText(chatId, "Ничего не найдено.");
            return;
        }
        safeSendText(chatId, Texts.listBlock(title, items));
        for (MediaItem item : items.stream().limit(5).toList()) {
            sessionCache.put(item.getStorageKey(), item);
            sendPreview(chatId, item);
        }
    }

    private void sendPreview(long chatId, MediaItem item) throws Exception {
        if (item.getPosterUrl() != null) {
            SendPhoto photo = SendPhoto.builder()
                    .chatId(chatId)
                    .photo(new InputFile(item.getPosterUrl()))
                    .caption(item.getTitle() + " (" + item.shortYear() + ")")
                    .replyMarkup(resultKeyboard(item))
                    .build();
            telegramClient.execute(photo);
        } else {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId)
                    .text(item.getTitle() + " (" + item.shortYear() + ")")
                    .replyMarkup(resultKeyboard(item))
                    .build();
            telegramClient.execute(message);
        }
    }

    private void sendMediaCard(long chatId, MediaItem item) throws Exception {
        if (item.getPosterUrl() != null) {
            SendPhoto photo = SendPhoto.builder()
                    .chatId(chatId)
                    .photo(new InputFile(item.getPosterUrl()))
                    .caption(Texts.mediaCard(item))
                    .parseMode("HTML")
                    .replyMarkup(cardKeyboard(item))
                    .build();
            telegramClient.execute(photo);
            return;
        }
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(Texts.mediaCard(item))
                .parseMode("HTML")
                .replyMarkup(cardKeyboard(item))
                .build();
        telegramClient.execute(message);
    }

    private InlineKeyboardMarkup resultKeyboard(MediaItem item) {
        InlineKeyboardButton details = InlineKeyboardButton.builder()
                .text("Открыть карточку")
                .callbackData("details:" + item.getStorageKey())
                .build();
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(details))
                .build();
    }

    private InlineKeyboardMarkup cardKeyboard(MediaItem item) {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        rows.add(new InlineKeyboardRow(
                button("❤️ Избранное", "fav:" + item.getStorageKey()),
                button("✅ Просмотрено", "seen:" + item.getStorageKey())
        ));

        rows.add(new InlineKeyboardRow(
                button("👍", "like:" + item.getStorageKey()),
                button("👎", "dislike:" + item.getStorageKey()),
                button("🎬 Похожие", "similar:" + item.getStorageKey())
        ));

        return new InlineKeyboardMarkup(rows);
    }

    private InlineKeyboardButton button(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }

    private void sendMainMenu(long chatId, String text) {
        try {
            telegramClient.execute(
                    SendMessage.builder()
                            .chatId(chatId)
                            .text(text)
                            .replyMarkup(mainMenuKeyboard())
                            .build()
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ReplyKeyboardMarkup mainMenuKeyboard() {
        return ReplyKeyboardMarkup.builder()
                .keyboardRow(new KeyboardRow("🆕 New", "🎯 Recommend", "❤️ Favorites"))
                .resizeKeyboard(true)
                .isPersistent(true)
                .oneTimeKeyboard(false)
                .selective(false)
                .build();
    }

    private void safeSendText(long chatId, String text) {
        try {
            telegramClient.execute(SendMessage.builder().chatId(chatId).text(text).build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private long extractChatId(Update update) {
        if (update.hasMessage()) {
            return update.getMessage().getChatId();
        }
        if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) {
            return update.getCallbackQuery().getMessage().getChatId();
        }
        return 0;
    }
}
