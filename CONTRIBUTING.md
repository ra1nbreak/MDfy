# Как внести вклад в MDfy

Спасибо за интерес к проекту! 🎵

## С чего начать

1. **Fork** репозитория
2. Клонируй свой форк: `git clone https://github.com/YOUR_USERNAME/mdfy`
3. Создай ветку: `git checkout -b feature/my-feature`
4. Скопируй `local.properties.example` → `local.properties` и заполни ключи
5. Открой в Android Studio Hedgehog или новее

## Настройка окружения

- **Android Studio** Hedgehog (2023.1.1) или новее
- **JDK 17** (поставляется с Android Studio)
- **Android SDK** с API 35
- Ключи Spotify API и YouTube API (см. `local.properties.example`)

## Стиль кода

- Kotlin, без Java
- Clean Architecture: Data / Domain / UI — не смешивать слои
- Compose-first: никаких `Fragment`, только `@Composable`
- Комментарии на русском (проект русскоязычный)

## Процесс

1. Убедись, что issue под твою идею уже есть, или создай новый
2. Открывай PR в ветку `main`
3. Описывай что сделал и зачем

## Что сейчас нужно

Смотри [Дорожную карту в README](README.md#-дорожная-карта-планы-на-лето-2026).
