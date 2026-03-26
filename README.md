# Cinema Recommender Telegram Bot

Базовый Telegram-бот на Java для рекомендаций фильмов и сериалов.

## Что уже умеет
- поиск фильма или сериала по тексту;
- подборка новинок и трендов через TMDb;
- карточка с постером, описанием, жанрами и рейтингом;
- лайк / дизлайк / просмотрено / избранное;
- простые персональные рекомендации по любимым жанрам;
- похожие фильмы и сериалы.

## Стек
- Java 17
- Maven
- TelegramBots long polling
- TMDb API
- Jackson

## Настройка
Создай файл `config.properties` в корне проекта:

```properties
bot.token=YOUR_TELEGRAM_BOT_TOKEN
TMDB_API_KEY=YOUR_TMDB_API_KEY
TMDB_LANGUAGE=ru-RU
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
