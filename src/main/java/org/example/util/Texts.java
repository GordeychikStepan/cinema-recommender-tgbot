package org.example.util;

import org.example.model.MediaItem;

import java.util.List;

public final class Texts {
    private Texts() {}

    public static String welcome() {
        return "Привет! Я FilmFinder — бот-рекомендатор фильмов и сериалов.\n\n" +
                "Основные действия доступны на нижней клавиатуре:\n" +
                "• 🆕 New — свежие и популярные новинки\n" +
                "• 🎯 Recommend — персональные рекомендации\n" +
                "• ❤️ Favorites — избранное\n\n" +
                "Также можно просто написать название фильма или сериала для поиска.\n" +
                "После открытия карточки можно поставить 👍/👎, добавить в избранное и посмотреть похожее.";
    }

    public static String help() {
        return "Команды:\n" +
                "/start — запуск бота\n" +
                "/help — помощь\n" +
                "/new — подборка новинок и трендов\n" +
                "/recommend — рекомендации по твоим предпочтениям\n" +
                "/favorites — показать избранное\n\n" +
                "Также можно просто отправить название фильма или сериала, например: Интерстеллар";
    }

    public static String mediaCard(MediaItem item) {
        String overview = item.getOverview();
        if (overview.length() > 700) {
            overview = overview.substring(0, 697) + "...";
        }
        return String.format("%s: <b>%s</b>\nГод: %s\nЖанры: %s\nРейтинг TMDb: %.1f\n\n%s",
                item.getMediaType().labelRu(),
                escape(item.getTitle()),
                escape(item.shortYear()),
                escape(item.genresText()),
                item.getVoteAverage(),
                escape(overview));
    }

    public static String listBlock(String title, List<MediaItem> items) {
        StringBuilder sb = new StringBuilder(title).append("\n\n");
        for (int i = 0; i < items.size(); i++) {
            MediaItem item = items.get(i);
            sb.append(i + 1)
                    .append(". ")
                    .append(item.getTitle())
                    .append(" (").append(item.shortYear()).append(")")
                    .append(" — ").append(item.getMediaType().labelRu())
                    .append(" — рейтинг ").append(String.format("%.1f", item.getVoteAverage()))
                    .append("\n");
        }
        return sb.toString();
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
