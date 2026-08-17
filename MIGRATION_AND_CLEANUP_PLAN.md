# План рефакторинга: Очистка мертвого кода и миграция UI на Jetpack Compose

В данном документе зафиксирован полный реестр файлов проекта **Salvation** (Android-клиент), подлежащих удалению, миграции с классического XML на **Jetpack Compose**, а также список исправлений технического долга.

---

## 1. Текущий архитектурный контекст

Проект **Salvation** развивается на базе плеера *Gramophone*. В настоящее время в приложении используется гибридная модель:
* **Jetpack Compose**:
  * Плеерная система: полноэкранный плеер (Full Player Sheet), мини-плеер (Mini Player), плавающий навигационный бар (Floating Bottom Nav Bar) и экран текстов песен ([`LyricsScreen.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/components/LyricsScreen.kt)) реализованы в Compose-оверлее [`MainActivity.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/MainActivity.kt).
  * Детали плейлиста: [`PlaylistDetailScreen.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/PlaylistDetailScreen.kt) внутри [`ComposeContainerFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/ComposeContainerFragment.kt).
  * Вкладка «Плейлисты» в медиатеке: `PlaylistsScreen` внутри [`LibraryPlaylistFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/LibraryPlaylistFragment.kt).
  * Настройки: единый Compose-экран [`SettingsFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/SettingsFragment.kt).
  * Шторки: [`AddToPlaylistBottomSheet.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/AddToPlaylistBottomSheet.kt), [`SleepTimerBottomSheet.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/SleepTimerBottomSheet.kt).
* **XML ViewSystem / RecyclerView**:
  * Экран авторизации ([`LoginActivity.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/LoginActivity.kt)).
  * Онлайн-поиск ([`OnlineSearchFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/OnlineSearchFragment.kt)).
  * Медиатека (вкладки треков, альбомов, артистов в [`LibraryFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/LibraryFragment.kt)).
  * Экран альбома ([`AlbumFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/AlbumFragment.kt)).
  * Экран артиста ([`ArtitsFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/ArtitsFragment.kt)).
  * Экран дискографии ([`DiscographyFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/DiscographyFragment.kt)).
  * Контекстное меню трека ([`PlayerMenuBottomSheet.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/PlayerMenuBottomSheet.kt)) и полная очередь ([`QueueBottomSheetFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/QueueBottomSheetFragment.kt)).

---

## 2. Список XML-файлов для переноса на Jetpack Compose

Данные файлы активно задействованы в приложении и подлежат поэтапному переписыванию на Jetpack Compose:

### 2.1. Основные экраны и активности
1. **[`app/src/main/res/layout/activity_login.xml`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/res/layout/activity_login.xml)**
   * **Где используется**: [`LoginActivity.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/LoginActivity.kt)
   * **Что содержит**: Форма ввода логина/пароля, индикатор статуса, кнопка входа.
   * **План миграции**: Создать `@Composable fun LoginScreen(...)`, перевести `LoginActivity` на `ComponentActivity.setContent { LoginScreen(...) }`.
2. **[`app/src/main/res/layout/fragment_online_search.xml`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/res/layout/fragment_online_search.xml)**
   * **Где используется**: [`OnlineSearchFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/OnlineSearchFragment.kt)
   * **Что содержит**: Поле поиска `EditText`, кнопка очистки, прогресс-бар, вкладки фильтрации (`TabLayout`), `RecyclerView`.
   * **План миграции**: Переписать на Compose `SearchTextField` + `PrimaryTabRow` + `LazyColumn` / `LazyRow`.
3. **[`app/src/main/res/layout/fragment_library.xml`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/res/layout/fragment_library.xml)** и **[`app/src/main/res/layout/fragment_library_songs.xml`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/res/layout/fragment_library_songs.xml)**
   * **Где используется**: [`LibraryFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/LibraryFragment.kt) и [`LibrarySongsFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/LibrarySongsFragment.kt)
   * **Что содержит**: Шапка с поиском и настройками, `TabLayout` + `ViewPager2` со страницами медиатеки, кнопка Shuffle, счетчик треков, сортировка.
   * **План миграции**: Объединить медиатеку в единый Compose-экран (`ScrollableTabRow` + `HorizontalPager`).
4. **[`app/src/main/res/layout/fragment_album.xml`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/res/layout/fragment_album.xml)**
   * **Где используется**: [`AlbumFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/AlbumFragment.kt), [`AlbumTrackFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/AlbumTrackFragment.kt), [`AlbumInfoFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/AlbumInfoFragment.kt), [`AlbumPaderAdapter.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/AlbumPaderAdapter.kt)
   * **Что содержит**: Обложка альбома, название, артист, кнопки Play/Like, ViewPager со списком треков и информацией.
   * **План миграции**: Переписать на `@Composable fun AlbumDetailScreen(...)` по аналогии с `PlaylistDetailScreen`.
5. **[`app/src/main/res/layout/fragment_artist.xml`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/res/layout/fragment_artist.xml)**
   * **Где используется**: [`ArtitsFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/ArtitsFragment.kt)
   * **Что содержит**: Шапка артиста с аватаром, кнопка «Дискография», горизонтальный RecyclerView альбомов, вертикальный RecyclerView топ-треков.
   * **План миграции**: Реализовать через `LazyColumn` со встроенным `LazyRow` (альбомы) и списком треков.
6. **[`app/src/main/res/layout/fragment_discography.xml`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/res/layout/fragment_discography.xml)**
   * **Где используется**: [`DiscographyFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/DiscographyFragment.kt), [`DiscographyListFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/DiscographyListFragment.kt)
   * **Что содержит**: Вкладки категорий («Альбомы», «Синглы и EP», «Сборники») + сетка 2x2.
   * **План миграции**: Переписать на `TabRow` + `LazyVerticalGrid(GridCells.Fixed(2))`.
7. **[`app/src/main/res/layout/activity_main.xml`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/res/layout/activity_main.xml)**
   * **Где используется**: [`MainActivity.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/MainActivity.kt)
   * **Что содержит**: `ViewPager2`, `FragmentContainerView`, `ComposeView`.
   * **План миграции**: После перевода экранов избавиться от Fragment-контейнера и ViewPager2, перейдя на нативный Compose-роутинг (`NavHost` / `AnimatedContent`).

### 2.2. Шторки и всплывающие меню
8. **[`app/src/main/res/layout/layout_bottom_sheet_player_menu.xml`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/res/layout/layout_bottom_sheet_player_menu.xml)**
   * **Где используется**: [`PlayerMenuBottomSheet.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/PlayerMenuBottomSheet.kt)
   * **Что содержит**: Шапка с метаданными трека, мини-очередь, действия («К артисту», «К альбому», «В плейлист», «Таймер сна», «Пожаловаться»).
   * **План миграции**: Переписать на `ModalBottomSheet` в Compose.
9. **[`app/src/main/res/layout/layout_bottom_sheet_queue.xml`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/res/layout/layout_bottom_sheet_queue.xml)**
   * **Где используется**: [`QueueBottomSheetFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/QueueBottomSheetFragment.kt)
   * **Что содержит**: Экран управления очередью воспроизведения с drag-and-drop сортировкой.
   * **План миграции**: Переписать на Compose с поддержкой reorderable списков.
10. **[`app/src/main/res/layout/layout_bottom_sheet_sort.xml`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/res/layout/layout_bottom_sheet_sort.xml)**
    * **Где используется**: [`LibrarySongsFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/LibrarySongsFragment.kt)
    * **Что содержит**: Выбор режима сортировки треков.
    * **План миграции**: Переписать на Compose `ModalBottomSheet` с радиокнопками.

### 2.3. XML элементов списков (удаляются при миграции на Compose)
После перевода вышеуказанных экранов на `LazyColumn`/`LazyVerticalGrid` эти макеты будут полностью заменены Compose-компонентами:
* `item_search_result.xml` — строка трека
* `item_album_card.xml` — горизонтальная/вертикальная карточка альбома
* `item_grid_card.xml` — карточка в сетке 2x2
* `item_search_artist.xml` — строка артиста с круглой аватаркой
* `item_top_result.xml` — баннер главного результата поиска
* `item_search_history.xml` — строка истории поисковых запросов
* `item_queue_track.xml` — строка трека в очереди воспроизведения

---

## 3. Полный список файлов для УДАЛЕНИЯ (Dead Code)

Эти файлы **не используются** в актуальном приложении и могут быть безопасно удалены без последствий.

### 3.1. Устаревшие макеты (`app/src/main/res/layout/` и `layout-w600dp-land/`) — 23 файла
1. `fragment_main.xml` *(пустой контейнер-обёртка для ComposeView в MainFragment)*
2. `fragment_playlist.xml` *(старый XML плейлиста Gramophone)*
3. `item_playlist.xml` *(старый элемент плейлиста Gramophone)*
4. `item_remote_track.xml` *(черновик DownloaderApi)*
5. `fragment_full_player.xml` *(макет от заброшенного FullPlayerFragment)*
6. `full_player.xml` *(старый XML плеера Gramophone FullBottomSheet)*
7. `layout-w600dp-land/full_player.xml` *(планшетный старый плеер)*
8. `bottom_sheet.xml` *(старый плеер Gramophone PlayerBottomSheet)*
9. `preview_player.xml` *(старый PreviewBottomSheet)*
10. `playlist_bottom_sheet.xml` *(старый PlaylistQueueSheet)*
11. `fragment_viewpager.xml` *(старый контейнер табов Gramophone ViewPagerFragment)*
12. `fragment_recyclerview.xml` *(старый шаблон AdapterFragment)*
13. `fragment_general_sub.xml` *(старый подфрагмент списков GeneralSubFragment)*
14. `fragment_search.xml` *(старый прототип поиска SearchFragment.kt)*
15. `fragment_info_song.xml` *(диалог свойств DetailDialogFragment)*
16. `adapter_list_card.xml` *(карточка трека старых адаптеров Gramophone)*
17. `adapter_list_card_larger.xml` *(крупная карточка старых адаптеров Gramophone)*
18. `adapter_grid_card.xml` *(сетка старых адаптеров Gramophone)*
19. `adapter_folder_card.xml` *(карточка папки MediaStore DetailedFolderAdapter)*
20. `adapter_blacklist_folder_card.xml` *(карточка папки черного списка BlacklistFolderAdapter)*
21. `dialog_new_playlist.xml` *(диалог создания локального m3u плейлиста PlaylistAdapter)*
22. `general_decor.xml` *(разделители списков BaseDecorAdapter)*
23. `fragment_blacklist_settings.xml` *(макет черного списка папок)*

### 3.2. Макеты старых настроек Preference — 11 файлов
*(Все настройки переписаны в Compose в `SettingsFragment.kt`)*
24. `fragment_top_settings.xml`
25. `dialog_about.xml`
26. `preference_basic.xml`
27. `preference_category_md.xml`
28. `preference_dropdown_md.xml`
29. `preference_icon.xml`
30. `preference_seekbar.xml`
31. `preference_switch.xml`
32. `preference_switch_widget.xml`
33. `tab_order_dialog.xml`
34. `tab_order_item.xml`
35. `tab_order_seperator.xml`

### 3.3. Меню (`app/src/main/res/menu/`) — 6 файлов
36. `bottom_nav_menu.xml` *(не используется, навигация в Compose)*
37. `playlist_subfragment_menu.xml` *(не используется)*
38. `home_menu.xml` *(использовался в старом ViewPagerFragment)*
39. `sort_menu.xml` *(использовался в старом BaseDecorAdapter)*
40. `more_menu.xml` *(старые меню трека Gramophone)*
41. `more_menu_less.xml` *(старые урезанные меню трека Gramophone)*

### 3.4. XML-дескрипторы настроек (`app/src/main/res/xml/`) — 10 файлов
42. `settings_about.xml`
43. `settings_appearance.xml`
44. `settings_audio.xml`
45. `settings_behavior.xml`
46. `settings_experimental.xml`
47. `settings_lyric.xml`
48. `settings_player.xml`
49. `settings_replaygain.xml`
50. `settings_top.xml`
51. `automotive_app_desc.xml` *(неиспользуемый дескриптор Android Auto)*

---

### 3.5. Исходный код Kotlin / Java (Dead Code) к удалению

#### 1. Неиспользуемые фрагменты и экраны:
* [`FullPlayerFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/FullPlayerFragment.kt) — старый полноэкранный плеер на XML (0 ссылок).
* [`FavoritesFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/FavoritesFragment.kt) — брошенный черновик без `onCreateView` (0 ссылок).
* [`SearchFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/SearchFragment.kt) — старый прототип поиска (заменен на `OnlineSearchFragment`).
* [`ViewPagerFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/ViewPagerFragment.kt) — старый контейнер вкладок Gramophone.
* [`AdapterFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/AdapterFragment.kt) — базовый фрагмент локальных списков.
* [`GeneralSubFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/GeneralSubFragment.kt) и [`ArtistSubFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/ArtistSubFragment.kt).
* [`DetailDialogFragment.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/fragments/DetailDialogFragment.kt) — диалог свойств трека MediaStore.

#### 2. Пакет старых настроек `app/src/main/java/org/akanework/gramophone/ui/fragments/settings/` (13 файлов):
* `AboutSettingsFragment.kt`
* `AppearanceSettingsFragment.kt`
* `AudioSettingsFragment.kt`
* `BehaviorSettingsFragment.kt`
* `BlacklistSettingsFragment.kt`
* `ContributorsSettingsFragment.kt`
* `ExperimentalSettingsFragment.kt`
* `LyricSettingsFragment.kt`
* `MainSettingsFragment.kt`
* `OssLicensesSettingsFragment.kt`
* `PlayerSettingsFragment.kt`
* `ReplayGainSettingsFragment.kt`
* `BaseSettingsActivity.kt`
* `BasePreferenceFragment.kt`

#### 3. Старые адаптеры и компоненты Gramophone:
* [`OnlineAlbumAdapter.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/adapters/OnlineAlbumAdapter.kt) *(0 ссылок, логика в OnlineSearchAdapter)*.
* [`PlayerBottomSheet.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/components/PlayerBottomSheet.kt), [`FullBottomSheet.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/components/FullBottomSheet.kt).
* [`PreviewBottomSheet.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/components/PreviewBottomSheet.kt), [`PlaylistQueueSheet.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/components/PlaylistQueueSheet.kt), [`EditSongAdapter.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/components/EditSongAdapter.kt).
* Локальные адаптеры MediaStore: [`SongAdapter.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/adapters/SongAdapter.kt), [`AlbumAdapter.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/adapters/AlbumAdapter.kt), [`ArtistAdapter.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/adapters/ArtistAdapter.kt), [`GenreAdapter.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/adapters/GenreAdapter.kt), [`DateAdapter.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/adapters/DateAdapter.kt), [`DetailedFolderAdapter.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/adapters/DetailedFolderAdapter.kt), [`BlacklistFolderAdapter.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/adapters/BlacklistFolderAdapter.kt), [`PlaylistAdapter.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/adapters/PlaylistAdapter.kt), [`BaseAdapter.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/adapters/BaseAdapter.kt), [`BaseDecorAdapter.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/adapters/BaseDecorAdapter.kt), [`Sorter.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/adapters/Sorter.kt), [`ViewPager2Adapter.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/adapters/ViewPager2Adapter.kt).
* [`TabOrderPreference.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/components/TabOrderPreference.kt), [`NoAppFallbackPreference.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/components/NoAppFallbackPreference.kt).
* [`ScrollingView2.java`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/components/ScrollingView2.java) *(2058 строк мертвого кода ScrollView)*.

#### 4. Неиспользуемые сетевые модули и утилиты:
* Пакет [`app/src/main/java/uk/akane/libphonograph/reader/api/`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/uk/akane/libphonograph/reader/api/):
  * `NavidromeApi.kt`, `NavidromeModels.kt`, `NetworkModule.kt` *(эксперименты с Navidrome)*
  * `BackendApi.kt`, `BackendModels.kt` *(заброшенные заглушки)*
* [`DownloaderApi.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/logic/api/DownloaderApi.kt) *(устаревший клиент `RemoteTrack`/`DownloaderClient`)*.
* [`IncrementalFlows.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/logic/utils/flows/IncrementalFlows.kt) *(1094 строки неиспользуемого диффинга)*.
* [`GramophoneShuffleOrder.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/logic/utils/GramophoneShuffleOrder.kt) *(заменен на `ShuffleUtils.balancedShuffle`)*.
* [`MyForegroundColorSpan.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/logic/ui/spans/MyForegroundColorSpan.kt), [`MyGradientSpan.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/logic/ui/spans/MyGradientSpan.kt).
* [`SdScanner.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/logic/utils/SdScanner.kt) *(сканер локальных SD-карт)*.
* [`Contributors.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/logic/utils/data/Contributors.kt), [`GitHubUser.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/logic/utils/data/GitHubUser.kt).

---

## 4. Изменения в `AndroidManifest.xml`

В файле [`app/src/main/AndroidManifest.xml`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/AndroidManifest.xml) необходимо:
1. **Удалить декларации старых Settings Activity** (строки 172–183):
   ```xml
   <!-- К УДАЛЕНИЮ: -->
   <activity android:name=".ui.fragments.settings.PlayerSettingsActivity" android:exported="false" />
   <activity android:name=".ui.fragments.settings.LyricSettingsActivity" android:exported="false" />
   <activity android:name=".ui.fragments.settings.ReplayGainSettingsActivity" android:exported="false" />
   <activity android:name=".ui.fragments.settings.AboutSettingsActivity" android:exported="false" />
   <activity android:name=".ui.fragments.settings.AudioSettingsActivity" android:exported="false" />
   <activity android:name=".ui.fragments.settings.AppearanceSettingsActivity" android:exported="false" />
   <activity android:name=".ui.fragments.settings.BehaviorSettingsActivity" android:exported="false" />
   <activity android:name=".ui.fragments.settings.MainSettingsActivity" android:exported="false" />
   <activity android:name=".ui.fragments.settings.ExperimentalSettingsActivity" android:exported="false" />
   <activity android:name=".ui.fragments.settings.ContributorsSettingsActivity" android:exported="false" />
   <activity android:name=".ui.fragments.settings.BlacklistSettingsActivity" android:exported="false" />
   <activity android:name=".ui.fragments.settings.OssLicensesSettingsActivity" android:exported="false" />
   ```
2. **Очистить неиспользуемые Intent Actions**:
   * Действие `org.akanework.gramophone.action.SHUFFLE` (если шорткат переводится на новый сервис).

---

## 5. Исправление опечаток в именах файлов (Техдолг)

Рекомендуется переименовать файлы для соблюдения конвенций и исключения путаницы:
1. `ArtitsFragment.kt` ➔ `ArtistFragment.kt`
2. `AlbumPaderAdapter.kt` ➔ `AlbumPagerAdapter.kt`
3. `LibraryCasheManager.kt` ➔ `LibraryCacheManager.kt`
4. `LikeCashe.kt` ➔ `LikeCache.kt`
5. `LibraryPlaylistFragment.kt` ➔ `LibraryPlaylistsFragment.kt` *(имя файла должно совпадать с классом внутри)*

---

## 6. Файлы, которые НЕЛЬЗЯ удалять (Системные исключения)

* **Виджет рабочего стола**:
  * [`app/src/main/res/layout/lyric_widget.xml`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/res/layout/lyric_widget.xml) и все 12 файлов `lyric_widget_text_*.xml`
  * [`app/src/main/res/xml/lyric_widget.xml`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/res/xml/lyric_widget.xml)
  * [`LyricWidgetProvider.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/LyricWidgetProvider.kt), [`Widget.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/ui/Widget.kt)
  *(Android AppWidget RemoteViews работают строго через XML-макеты)*.
* **Системные правила манифеста**:
  * [`app/src/main/res/xml/file_paths.xml`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/res/xml/file_paths.xml) *(FileProvider)*
  * [`app/src/main/res/xml/shortcuts.xml`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/res/xml/shortcuts.xml) *(App Shortcuts)*
  * [`app/src/main/res/xml/backup_rules.xml`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/res/xml/backup_rules.xml) и [`app/src/main/res/xml/data_extraction_rules.xml`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/res/xml/data_extraction_rules.xml)
* **Аудио-ядро и актуальный API**:
  * [`GramophonePlaybackService.kt`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/logic/GramophonePlaybackService.kt)
  * Пакет [`org/akanework/gramophone/logic/api/`](file:///home/vanbayt/StudioProjects/Salvation/app/src/main/java/org/akanework/gramophone/logic/api) (`NetworkClient`, `GramophoneApi`, `Track`, `Album`, `Artist`, `AuthManager`, `SearchResponse`).
  * Утилиты аудио: `AudioFormatDetector.kt`, `PlaybackLogger.kt`, `SmartPlaybackManager.kt`, `ReplayGainAudioProcessor.kt`, `MonoAudioProcessor.kt`, `LrcUtils.kt`, `LyricsRepository.kt`.

---

## 7. Рекомендуемый поэтапный план выполнения

```
[Этап 1: Безопасная очистка]
  ├── Удаление неиспользуемых XML (~35 файлов)
  ├── Удаление пакета settings/ и чистка AndroidManifest.xml
  ├── Удаление старых MediaStore-адаптеров и неиспользуемых утилит (IncrementalFlows, DownloaderApi, Navidrome)
  └── Исправление опечаток в именах файлов

[Этап 2: Миграция вспомогательных экранов и шторок]
  ├── Перенос LoginActivity на Compose
  ├── Перенос PlayerMenuBottomSheet и QueueBottomSheetFragment на Compose
  └── Удаление layout_bottom_sheet_*.xml

[Этап 3: Миграция основных контентных экранов]
  ├── Перенос AlbumFragment и ArtistFragment на Compose (удаление fragment_album.xml, fragment_artist.xml)
  ├── Перенос OnlineSearchFragment на Compose (удаление fragment_online_search.xml)
  ├── Перенос LibraryFragment и LibrarySongsFragment на Compose (удаление fragment_library*.xml)
  ├── Перенос DiscographyFragment на Compose (удаление fragment_discography.xml)
  └── Удаление неиспользуемых item_*.xml

[Этап 4: Финализация Compose Architecture]
  ├── Замена ViewPager2 и FragmentContainerView в MainActivity на единый Compose NavHost
  └── Удаление activity_main.xml и библиотеки ViewBinding
```
