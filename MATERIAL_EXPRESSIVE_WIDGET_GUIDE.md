# Методическое руководство: Material You & Material 3 Expressive в виджетах Salvation

Данный документ представляет собой полное архитектурно-дизайнерское руководство по проектированию и разработке Android-виджетов (`AppWidget`) для музыкального плеера **Salvation** на основе спецификаций **Material Design 3 (Material You)** и **Material 3 Expressive (M3 Expressive, Google I/O 2024 / Android 15+)**.

---

## 1. Философия и каноны Material 3 Expressive

В 2024–2025 гг. Google представил эволюцию Material Design — **Material 3 Expressive**. Если классический Material You был сфокусирован на динамической палитре (Monet), то Expressive делает акцент на **тактильности, геометрической эмоциональности, органических формах и функциональном контрасте**.

### Ключевые каноны:
1. **Геометрическая выразительность (Expressive Shapes & Squircles)**:
   - Отказ от скучных прямоугольников в пользу **сквирклов** (squircle — скругленный суперэллипс) и **капсул (pills)**.
   - Иерархия скруглений строго детерминирована: внешний контейнер виджета — `28dp` или `32dp`, вложенные обложки — `16dp`–`24dp`, кнопки действий — `999dp` (pill) или идеальный круг `48dp` (FAB).
   - Внутренний радиус элементов вычисляется по системному правилу Android 12+:  
     $$R_{inner} = \max(0, R_{outer} - Padding)$$  
     Пример: $32\text{dp} - 16\text{dp} = 16\text{dp}$.
2. **Тональная многослойность (Tonal Surface Elevation)**:
   - В современных виджетах **не используются грубые тени (drop shadows)** внутри самого макета, так как лаунчер Android автоматически накладывает системную тень на весь виджет.
   - Глубина создается за счет уровней контейнеров:
     * `surfaceContainerLowest` (#111318) — базовый фон темной темы.
     * `surfaceContainer` (#1D2026) — стандартное тело карточки виджета.
     * `surfaceContainerHigh` (#272B33) — приподнятые внутренние карточки (например, мини-плеер).
     * `surfaceContainerHighest` (#323640) — интерактивные чипы и поля ввода.
   - Тонкий контур `outlineVariant` (1dp с прозрачностью 20–30%) отделяет виджет от любого цвета обоев.
3. **Динамический цвет (Material You Dynamic Theming)**:
   - Использование ролей палитры Monet:
     * `primary` / `onPrimary`: Главная кнопка воспроизведения (Play/Pause).
     * `primaryContainer` / `onPrimaryContainer`: Активные бейджи, подсвеченная строка караоке, кнопка «Моя Волна».
     * `secondaryContainer`: Вторичные чипы и контекстные фильтры.
     * `tertiaryContainer`: Пресеты настроения и специфические музыкальные теги.
4. **Контрастная и функциональная типографика (Expressive Typescale)**:
   - Название трека: `Title Medium` / `Title Large` (жирное начертание 700/800, высокий контраст).
   - Исполнитель: `Body Medium` (приглушенный тон `onSurfaceVariant`).
   - Технические параметры (FLAC, битрейт, BPM, таймкоды): `Label Small` (SemiBold / AllCaps, моноширинный или плотный трекинг).
5. **Тактильность сенсорного ввода (Accessibility & Touch Targets)**:
   - Минимальная зона нажатия для любого интерактивного элемента — строго **48×48dp** (по стандартам доступности Android).
   - Главная кнопка воспроизведения — `48dp`–`56dp` с заливкой `primary`.
   - Кнопки переключения треков и лайка — `40dp`–`48dp` со скругленной зоной пульсации (ripple).

---

## 2. Специфика реализации в Android RemoteViews

Так как виджеты на домашнем экране отрисовываются системным процессом `SystemUI` через `RemoteViews`, к XML-разметке применяются жесткие платформенные ограничения:

1. **Разрешенные контейнеры**: `LinearLayout`, `RelativeLayout`, `FrameLayout`, `ViewFlipper`.
2. **Разрешенные элементы**: `ImageView`, `TextView`, `ImageButton`, `ProgressBar`, `View`, `ListView`, `GridView`.
3. **Запрещено в RemoteViews**: `ConstraintLayout`, `MaterialCardView`, кастомные View, `RecyclerView` (для списков используется `RemoteViewsFactory` / `ListView`).
4. **Ограничение памяти IPC (Binder Transaction Limit)**:
   - Общий объем передаваемых данных в `RemoteViews` не должен превышать **1 МБ**.
   - Обложки треков **ни в коем случае нельзя передавать в исходном разрешении**. Их необходимо масштабировать перед вызовом `setImageViewBitmap`:
     * Для 4×1: максимум $128 \times 128$ px.
     * Для 4×2: максимум $300 \times 300$ px.
     * Для микро-иконок: $96 \times 96$ px.
5. **Кэширование и реактивность**:
   - Обновления отправляются через `AppWidgetManager.partiallyUpdateAppWidget` при смене трека, паузе или смене статуса лайка.
   - Действия (`play/pause`, `next`, `prev`, `like`, `seekTo`) привязываются через `PendingIntent.getBroadcast` или `PendingIntent.getService` к `GramophonePlaybackService`.

---

## 3. Архитектура 6 концептов виджетов для Salvation

| Концепт | Формат | Сетка dp | Внешний радиус | Ключевая функциональность |
| :--- | :--- | :--- | :--- | :--- |
| **1. Compact Player** | 4×1 | $360 \times 88$ | 28dp | Обложка (16dp squircle), название, FLAC-бейдж, прогресс-бар, Лайк, Prev, Play/Pause, Next |
| **2. Hi-Fi Canvas** | 4×2 | $360 \times 180$ | 32dp | Большая обложка (24dp squircle), Hi-Res шильдик, `FLAC 24-bit/96kHz`, таймкоды, 5 кнопок |
| **3. Karaoke Lyrics 2.0** | 4×3 | $360 \times 270$ | 32dp | Мини-плеер в шапке, 4 строки текста, активная строка в капсуле `primaryContainer`, перевод |
| **4. Context Presets** | 4×2 | $360 \times 180$ | 32dp | Погода/время/наушники, кнопка «Запустить Мою Волну», пресеты рекомендаций 75/25 |
| **5. Queue & Up Next** | 4×3 | $360 \times 270$ | 32dp | Карточка текущего трека сверху, 3 строки следующих треков в очереди с кнопками запуска |
| **6. Quick Actions** | 2×1 | $360 \times 88$ | 28dp | 4 асимметричные плитки: «Любимое», «Моя волна», «Поиск», «Недавний альбом» |

---

## 4. Карта дизайн-токенов Material Expressive

### Цвета (Dark Theme Tonal Surface)
* `colorSurfaceContainer`: `#1D2026` (Базовый фон виджета)
* `colorSurfaceContainerHigh`: `#272B33` (Приподнятые карточки)
* `colorSurfaceContainerHighest`: `#333742` (Интерактивные плитки, скрабберы)
* `colorPrimary`: `#90B6F6` (Акцентная заливка кнопки Play)
* `colorOnPrimary`: `#0A2E66` (Иконка внутри Play)
* `colorPrimaryContainer`: `#1A3B70` (Фон активной строки караоке, кнопка Волны)
* `colorOnPrimaryContainer`: `#D4E3FF` (Текст на primaryContainer)
* `colorOutlineVariant`: `#3F4753` (Контур виджета с alpha 30%)
* `colorFavorite`: `#FF5370` (Красный акцент лайка)

### Скругления (Corner Radius)
* `corner_extra_large_increased`: `32dp` (Контейнеры 4×2, 4×3)
* `corner_extra_large`: `28dp` (Контейнеры 4×1, 2×1)
* `corner_large`: `16dp`–`24dp` (Обложки альбомов)
* `corner_medium`: `12dp` (Мини-обложки в очереди и чипы)
* `corner_full`: `999dp` (Pills и круглые кнопки)
