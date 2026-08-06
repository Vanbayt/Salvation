# Полное руководство по архитектуре и окружению проекта Salvation

Данный документ содержит исчерпывающий контекст, архитектурное описание, реквизиты доступа и инструкции для разработки экосистемы **Salvation** (Frontend + Backend).

---

## 1. Реквизиты и данные окружения

### 🔑 Доступ по SSH к серверу
* **IP сервера**: `185.196.41.31`
* **Пользователь**: `root`
* **Пароль**: `fiF2S0XH`
* **Команда подключения**: `ssh root@185.196.41.31`

### 🐙 Git и репозитории GitHub
* **Пользователь Git**: `Vanbayt`
* **Email Git**: `jajjaj1008@gmail.com`
* **Фронтенд (Android)**:
  * Локальный путь: `/home/vanbayt/StudioProjects/Salvation`
  * Репозиторий GitHub: `https://github.com/vanbayt/Salvation.git`
  * Основная ветка: `main`
* **Бекенд (FastAPI)**:
  * Локальный путь: `/home/vanbayt/Документы/SalvationBack`
  * Репозиторий GitHub: `https://github.com/vanbayt/SalvationBack.git`
  * Основная ветка: `master`

---

## 2. Архитектура системы (High-Level Overview)

```
[ Android Client (Salvation App) ]
         │
         │ (HTTP REST / JSON / JWT Bearer Authorization)
         ▼
[ FastAPI Backend (SalvationBack) ] ── (AsyncPG) ──► [ PostgreSQL DB ]
         │
         ├── (Async Redis Protocol) ──────────────► [ Redis + Taskiq Broker ]
         │                                                  │
         ├── (Audio Streaming / Proxy)                      ▼
         │                                         [ Background Workers ]
         │                                      (yt-dlp, Yandex Music, FLAC)
         ▼
[ Storage & Cache (/opt/music/storage) ]
```

---

## 3. Архитектура Frontend (`Salvation`)

**Тип приложения**: Нативное Android-приложение на **Kotlin**.

### 3.1. Структура Gradle-модулей
* **`:app`**: Главный UI-модуль приложения.
  * **UI**: Фрагменты ViewSystem (`BaseFragment`, `LibraryFragment`, `FullPlayerFragment`, `OnlineSearchFragment`, `PlaylistFragment`, `DiscographyFragment`) + Jetpack Compose (`PlaylistDetailScreen`, `PlaylistComponents`).
  * **State**: ViewModels (`LibrarySearchViewModel`).
  * **Network**: Сетевые клиенты (`BackendApi.kt`, `NavidromeApi.kt`, `BackendModels.kt`).
* **`:hificore`**: Сервис фонового воспроизведения.
  * `PlayerService`: Управление Media3 `MediaSession`, системным уведомлением и жизненным циклом плеера.
* **`media3` (Кастомный локальный форк)**:
  * Подменяет стандартные зависимости AndroidX Media3 (`:lib-common`, `:lib-exoplayer`, `:lib-session`, `:lib-datasource`, `:lib-database`, `:lib-datasource-okhttp`).
* **`:misc:alacdecoder`**: Нативный и программный декодер аудиоформата ALAC.
* **`:misc:audiofxstub` / `:misc:audiofxstub2` / `:misc:audiofxfwd`**: Модули управления DSP и эффектами эквалайзера.
* **`:baselineprofile`**: Оптимизация холодного запуска через Baseline Profiles.

### 3.2. Основной стек технологий
* **Язык**: Kotlin
* **Интерфейс**: XML ViewSystem + Jetpack Compose
* **Медиа-сервис**: AndroidX Media3 (Local Fork)
* **Сеть**: OkHttp / Retrofit / Kotlinx Serialization
* **Асинхронность**: Coroutines & Flow

---

## 4. Архитектура Backend (`SalvationBack`)

**Тип приложения**: Асинхронный REST API микросервис на **Python 3.11+ (FastAPI)**.

### 4.1. Ключевые модули и структура
* **`main.py`**: Точка входа приложения.
  * Эндпоинты авторизации: JWT (Passlib Argon2).
  * CRUD для треков, альбомов, артистов, плейлистов и лайков.
  * Стриминг аудио через `StreamingResponse` (проксирование/кэширование).
  * Логирование аудита в JSONL (`loguru`).
* **`models.py`**: Модели SQLAlchemy 2.0.
  * Сущности: `User`, `Track`, `Artist`, `Album`, `Genre`, `Playlist`, `PlaylistTrack`, `Like`, `ExternalTrackID`.
* **`database.py`**: Подключение к PostgreSQL через `sqlalchemy.ext.asyncio` (`async_session`).
* **`broker.py` & `tasks.py`**: Очереди задач **Taskiq** + **Redis**.
  * `download_track_task`: Фоновое скачивание треков.
  * `upgrade_to_flac_task`: Обновление трека до Lossless FLAC.
  * `sync_artist_to_db_task`: Синхронизация дискографии.
* **`extractors.py`**: Извлечение аудиопотоков через `yt-dlp` и `ytmusicapi`.
* **`metadata_fetcher.py`**: Сбор и интеграция метаданных из Yandex Music, Spotify, YouTube.
* **`analyzer.py` / `cover_hunter.py`**: Анализ волны аудио, поиск и сжатие обложек (Pillow).
* **`fixer.py`, `doctor.py`, `heal_db.py`**: Утилиты проверки и восстановления целостности БД.

### 4.2. Пути на сервере (`/opt/music`)
* **Базовый каталог**: `/opt/music`
* **Кэш аудио**: `/opt/music/storage/cache`
* **Загрузки**: `/opt/music/storage/downloads`
* **Логи аудита**: `/opt/music/storage/logs/dsp_audit.jsonl`

---

## 5. Инструкции для работы в новых чатах

1. **При работе с Git**:
   - Пользователь: `Vanbayt`, Email: `jajjaj1008@gmail.com`.
   - Ветка фронтенда: `main`. Ветка бекенда: `master`.
2. **При работе с сервером**:
   - Подключайтесь по SSH: `ssh root@185.196.41.31` (пароль `fiF2S0XH`).
3. **При модификации фронтенда**:
   - Проверяйте связи между `:app`, `:hificore` и подменённым локальным `:media3`.
4. **При модификации бекенда**:
   - Все запросы к БД выполнять асинхронно через `AsyncSession`.
   - Фоновые длительные задачи выполнять через `Taskiq` задачи из `tasks.py`.
