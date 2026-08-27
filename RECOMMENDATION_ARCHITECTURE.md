# Полное Руководство по Архитектуре Алгоритма Рекомендаций и Динамических Пресетов (Salvation Recommendation Engine)

Данный документ содержит исчерпывающее техническое описание, спецификации API-запросов, структуру данных, схему базы данных PostgreSQL (`pgvector`) и псевдокод рекомендательного движка **Salvation**.

---

## 🎯 1. Главные Принципы Системы

1. **Независимость от массы пользователей**: Система работает **не на основе действия толпы** (как Яндекс Музыка или Spotify), а на основе **физических характеристик музыки** (темп, спектр, тональность, энергетика) и **глубоких тегов нишевых музыкальных коллекционеров** (Last.fm, Discogs, MusicBrainz).
2. **Многоисточниковый параллельный сбор**: Кандидаты собираются одновременно из 4 независимых глобальных музыкальных баз данных (100M+ песен).
3. **Динамическая контекстная генерация пресетов**: Пресеты — это живые карточки, трансформирующиеся под время суток, погоду, тип подключенных наушников/машины и физическую активность.
4. **Принцип «Контекстная Голова + Контрастный Хвост» (75/25)**: В ленте пресетов 75% карточек идеально отражают текущее окружение, а 25% в конце списка — создают безопасный контрастный переход на альтернативный любимый жанр (например, Метал во время ночной тишины).

---

## 📡 2. Спецификация Запросов к Внешним Сервисам

### 1. Deezer API (Поиск, ISRC, похожие артисты)
- **Base URL**: `https://api.deezer.com`
- **Запрос поиска трека**:
  ```http
  GET /search?q=artist:"Keiichi Okabe" track:"Alien Manifestation" HTTP/1.1
  Host: api.deezer.com
  ```
- **Запрос похожих артистов**:
  ```http
  GET /artist/1154563/related HTTP/1.1
  Host: api.deezer.com
  ```

### 2. Last.fm API (Микро-теги и похожие треки)
- **Base URL**: `http://ws.audioscrobbler.com/2.0/`
- **Запрос похожих треков**:
  ```http
  GET /2.0/?method=track.getSimilar&artist=Keiichi+Okabe&track=Alien+Manifestation&api_key={LASTFM_API_KEY}&format=json&limit=50 HTTP/1.1
  Host: ws.audioscrobbler.com
  ```
- **Запрос топ-тегов трека**:
  ```http
  GET /2.0/?method=track.getTopTags&artist=Keiichi+Okabe&track=Alien+Manifestation&api_key={LASTFM_API_KEY}&format=json HTTP/1.1
  Host: ws.audioscrobbler.com
  ```

### 3. MusicBrainz REST API (Метаданные и графы авторов)
- **Base URL**: `https://musicbrainz.org/ws/2/`
- **Запрос по ISRC коду**:
  ```http
  GET /ws/2/recording?query=isrc:JPA841700094&fmt=json HTTP/1.1
  Host: musicbrainz.org
  User-Agent: SalvationBackend/1.0 ( contact@salvation.app )
  ```

### 4. Open-Meteo Weather API (Контекст погоды)
- **Base URL**: `https://api.open-meteo.com/v1/`
- **Запрос текущей погоды**:
  ```http
  GET /v1/forecast?latitude=52.37&longitude=4.89&current_weather=true HTTP/1.1
  Host: api.open-meteo.com
  ```

---

## 🗄️ 3. Структура БД PostgreSQL (Векторный поиск `pgvector`)

```sql
-- Подключение расширения векторного поиска
CREATE EXTENSION IF NOT EXISTS vector;

-- Таблица векторов треков (32 измерений)
CREATE TABLE IF NOT EXISTS track_embeddings (
    track_id BIGINT PRIMARY KEY REFERENCES tracks(id) ON DELETE CASCADE,
    isrc VARCHAR(15) INDEX,
    bpm INT DEFAULT 120,
    energy FLOAT DEFAULT 0.5,
    valence FLOAT DEFAULT 0.5,
    instrumentalness FLOAT DEFAULT 0.5,
    embedding vector(32), -- 32-мерный float массив (128 байт)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- HNSW индекс для сверхбыстрого косинусного поиска за 1 мс
CREATE INDEX IF NOT EXISTS idx_track_embeddings_hnsw 
ON track_embeddings USING hnsw (embedding vector_cosine_ops);
```

---

## 💻 4. Псевдокод Серверного Движка на Go (`salvation-go`)

```go
package recommendation

import (
	"context"
	"math"
	"strings"
	"sync"
	"golang.org/x/sync/errgroup"
)

type CandidateTrack struct {
	ISRC       string   `json:"isrc"`
	Title      string   `json:"title"`
	Artist     string   `json:"artist"`
	Source     string   `json:"source"`
	Score      float64  `json:"score"`
	Tags       []string `json:"tags"`
	BPM        int      `json:"bpm"`
	Energy     float64  `json:"energy"`
}

// 1. ПАРАЛЛЕЛЬНЫЙ СБОР КАНДИДАТОВ ИЗ 4 ИСТОЧНИКОВ (Goroutines)
func HarvestCandidatesParallel(ctx context.Context, seedArtist, seedTitle string) ([]CandidateTrack, error) {
	var g errgroup.Group
	var mu sync.Mutex
	rawCandidates := make([]CandidateTrack, 0, 160)

	// Source 1: Last.fm
	g.Go(func() error {
		res := fetchLastFmSimilar(ctx, seedArtist, seedTitle)
		mu.Lock()
		rawCandidates = append(rawCandidates, res...)
		mu.Unlock()
		return nil
	})

	// Source 2: YouTube Music Automix
	g.Go(func() error {
		res := fetchYTMAutomix(ctx, seedArtist, seedTitle)
		mu.Lock()
		rawCandidates = append(rawCandidates, res...)
		mu.Unlock()
		return nil
	})

	// Source 3: Deezer Related
	g.Go(func() error {
		res := fetchDeezerRelated(ctx, seedArtist, seedTitle)
		mu.Lock()
		rawCandidates = append(rawCandidates, res...)
		mu.Unlock()
		return nil
	})

	_ = g.Wait()
	return rawCandidates, nil
}

// 2. ДЕДУПЛИКАЦИЯ И КОНСЕНСУС-БОНУС
func DeduplicateAndMerge(candidates []CandidateTrack) []CandidateTrack {
	uniqueMap := make(map[string]*CandidateTrack)

	for _, cand := range candidates {
		key := strings.ToLower(cleanTitle(cand.Title) + "::" + cleanTitle(cand.Artist))
		if cand.ISRC != "" {
			key = "isrc::" + cand.ISRC
		}

		if existing, found := uniqueMap[key]; found {
			// Консенсус-бонус: если трек встретился в нескольких источниках!
			existing.Score += 500.0
		} else {
			cCopy := cand
			uniqueMap[key] = &cCopy
		}
	}

	result := make([]CandidateTrack, 0, len(uniqueMap))
	for _, v := range uniqueMap {
		result = append(result, *v)
	}
	return result
}

// 3. ФОРМИРОВАНИЕ ЛЕНТЫ ПРЕСЕТОВ (75/25 Голова + Контрастный Хвост)
func AssemblePresetListWithTail(ctx ContextParams, userFavorites []string) []PresetCard {
	presets := make([]PresetCard, 0, 6)

	// 75% КОНТЕКСТНАЯ ГОЛОВА (Время + Погода + Устройство)
	if ctx.IsNight && ctx.AudioDevice == "BT_CAR" {
		presets = append(presets, PresetCard{ID: "night_drive", Title: "🚗 Пятничный Ночной Драйв"})
	}
	if ctx.IsNight && ctx.AudioDevice == "HI_FI_DAC" {
		presets = append(presets, PresetCard{ID: "night_focus", Title: "🧠 Ночной Саундтрек-Фокус"})
	}
	if ctx.Weather == "rain" {
		presets = append(presets, PresetCard{ID: "rain_ambient", Title: "🌧️ Дождливый Атмосферный Микс"})
	}

	// 25% КОНТРАСТНЫЙ ХВОСТ (Альтернативный любимый жанр)
	presets = append(presets, PresetCard{
		ID:        "wildcard_metal",
		Title:     "🔥 Взрывной Симфо-Метал",
		IsContrast: true,
	})

	return presets
}
```

---

## 🎴 5. Матрица Динамического Контекста и Правило 75/25

### Сигналы контекста:
- **Время**: Утро (06-10), День (10-18), Вечер (18-22), Ночь (22-04).
- **Погода**: Дождь, Снег, Солнце.
- **Аудиовыход**: Bluetooth Car, Hi-Fi DAC, TWS Наушники.
- **Импульс сессии**: >3 быстрых скипов ➔ Выдвигает контрастную карточку на 1-е место.
