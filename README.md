# FilmFinder — Cinema Recommender Telegram Bot

Telegram-бот на Java для рекомендаций фильмов и сериалов с персонализацией, настройками профиля и хранением данных в SQLite.

## Что уже умеет
- поиск фильмов и сериалов по названию;
- подборка новинок и трендов через TMDb;
- персональные рекомендации с учётом жанров, рейтинга, популярности и новизны;
- onboarding-настройка при первом запуске;
- хранение профиля пользователя в SQLite;
- лайк / дизлайк / просмотрено / избранное / watchlist;
- удаление из списков и отмена лайка/дизлайка;
- карточки с постером и inline-кнопками;
- пагинация результатов поиска и рекомендаций.

## Стек
- Java 17+
- Maven
- TelegramBots long polling
- TMDb API
- Jackson
- SQLite JDBC

## Настройка
Создай файл `config.properties` в корне проекта:

```properties
bot.token=YOUR_TELEGRAM_BOT_TOKEN
tmdb.api.key=YOUR_TMDB_API_KEY
tmdb.language=ru-RU
app.data.dir=data
```

Либо задай переменные окружения:
- `BOT_TOKEN`
- `TMDB_API_KEY`
- `TMDB_LANGUAGE`
- `APP_DATA_DIR`

## Запуск
```bash
mvn compile exec:java
```

После первого запуска бот предложит выбрать:
- любимые жанры;
- тип контента;
- минимальный рейтинг;
- диапазон годов;
- язык карточек;
- включение уведомлений.
