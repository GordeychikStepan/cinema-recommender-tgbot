package org.example.util;

import org.example.model.ContentPreference;
import org.example.model.MediaItem;
import org.example.model.UserProfile;

public final class Texts {
    private Texts() {
    }

    public static String welcome() {
        return "Привет! Я FilmFinder — бот-рекомендатор фильмов и сериалов.\n\n" +
                "На нижней клавиатуре доступны основные действия:\n" +
                "• 🆕 New — новинки и популярное\n" +
                "• 🎯 Recommend — персональные рекомендации\n" +
                "• ❤️ Favorites — избранное\n" +
                "• 🕒 Watchlist — список «смотреть позже»\n" +
                "• ⚙️ Settings — настройки профиля\n\n" +
                "Можно просто написать название фильма или сериала для поиска.";
    }

    public static String help() {
        return "Команды:\n" +
                "/start — запуск бота\n" +
                "/help — помощь\n" +
                "/new — новинки и тренды\n" +
                "/recommend — персональные рекомендации\n" +
                "/favorites — избранное\n" +
                "/watchlist — смотреть позже\n" +
                "/settings — настройки профиля\n\n" +
                "Также можно отправить название фильма или сериала, например: Интерстеллар";
    }

    public static String mediaCard(MediaItem item) {
        String overview = item.getOverview();
        if (overview.length() > 700) {
            overview = overview.substring(0, 697) + "...";
        }
        return String.format("%s: <b>%s</b>\nГод: %s\nЖанры: %s\nРейтинг TMDb: %.1f\nПопулярность: %.1f\n\n%s",
                item.getMediaType().labelRu(),
                escape(item.getTitle()),
                escape(item.shortYear()),
                escape(item.genresText()),
                item.getVoteAverage(),
                item.getPopularity(),
                escape(overview));
    }

    public static String settingsSummary(UserProfile profile) {
        String years = profile.getYearFrom() == null && profile.getYearTo() == null
                ? "любые"
                : (profile.getYearFrom() == null ? "до " + profile.getYearTo() :
                profile.getYearTo() == null ? profile.getYearFrom() + "+" : profile.getYearFrom() + "–" + profile.getYearTo());
        String lang = "en-US".equals(profile.getLanguage()) ? "English" : "Русский";
        String genres = profile.getPreferredGenres().isEmpty() ? "не выбраны" : profile.getPreferredGenres().size() + " жанр(ов)";
        return "⚙️ Текущие настройки\n" +
                "• Любимые жанры: " + genres + "\n" +
                "• Тип контента: " + profile.getContentPreference().labelRu() + "\n" +
                "• Минимальный рейтинг: " + (profile.getMinRating() <= 0 ? "без фильтра" : profile.getMinRating()) + "\n" +
                "• Диапазон годов: " + years + "\n" +
                "• Язык карточек: " + lang + "\n" +
                "• Уведомления: " + (profile.isNotificationsEnabled() ? "включены" : "выключены");
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
