# Changelog

All notable changes, new features, and bug fixes for FF Launcher.

---

## [1.0.0] - 2026-11-15

### 🚀 New Features

#### 1. Unified Favorites Manager & Sequence Reordering
- **Merged Management Sheet (`ManageFavoritesSheet.kt`)**: Consolidated favorite selection, app pinning/unpinning, and sequence ordering into a single Material Design 3 bottom sheet.
- **Interactive Sequence Reordering**: Added up/down reorder controls to adjust the exact order of pinned favorite apps on the home screen with instant Room database persistence.
- **Long-Press Direct Access**: Long-pressing any application either in the app drawer or on the home screen immediately opens the unified management sheet.
- **Clean Home Screen Visuals**: Removed unnecessary favorite indicator stars on the home screen for a clean, distraction-free aesthetic.
- **Enlarged Touch Targets**: Expanded app drawer clickable areas to span over 80% of the screen width for effortless one-handed launching.

#### 2. App Search, Recent Apps & Newly Installed Apps
- **Integrated Search Sheet (`AppSearchSheet.kt`)**: Added a fast, real-time fuzzy search sheet filtering installed applications by title.
- **Recent Applications**: Automatically tracks app launches and surfaces recently used applications at the top of the search view.
- **Newly Installed Applications**: Detects and highlights applications installed within the last 48 hours.
- **Overscroll Gesture to Search**: Pulling up deliberately at the bottom of the home screen opens the search sheet, complete with a visual "Pull up to search" guide card.
- **Search Context Menu**: Long-pressing any search result or recent app allows pinning/unpinning and opening app options.

#### 3. Material 3 Widget Picker & Enhanced Widget Deck
- **App-Grouped Widget Picker (`WidgetPickerSheet.kt`)**: Created a dedicated Material 3 widget selection sheet grouping available widgets by their parent application with app icons, widget count indicators, and size badges.
- **True Wallpaper Backdrop**: Made the secondary screen and widget slot surfaces transparent so widgets float directly over the device wallpaper without artificial dark backdrops.
- **Background Masking**: Automatically fades out home screen apps when the widget deck is open, preventing favorite apps from showing through transparent widgets.

---

### 🐛 Bug Fixes & UX Polish

- **False "No Favorites" Empty Guide Bug**: Fixed a race condition in the home state flow by using `SharingStarted.Eagerly` and strict empty checks (`favoriteCount == 0 && favoriteKeys.isEmpty() && rows.isNotEmpty()`), preventing the guide card from showing when favorites exist.
- **Widget Configuration Infinite Loop**: Replaced replay-based `MutableSharedFlow` with a buffered single-event `Channel<WidgetRequest>` in `LauncherViewModel`, preventing system widget configure activities (e.g. clock style pickers) from reopening in loops.
- **Widget Back Navigation**: Fixed an issue where pressing the Android system back button from an opened widget app returned directly to the home screen instead of the widget deck.
- **WaveScroller Gear Icon (⚙) Routing**: Corrected the bottom gear icon on the alphabetical WaveScroller from opening the widget screen (`onOpenDeck`) to opening **Launcher Settings** (`TriggerTuningSheet`).
- **Home Screen Scroll Inconsistency**: Implemented a stable `NestedScrollConnection` with an intentional 64dp pull-up threshold requiring the bottom to be visible, preventing normal scroll fling momentum from accidentally opening search.

---

### 🛠️ Architecture & Code Modernization

- **Activity Result API**: Migrated deprecated `onActivityResult` in `MainActivity.kt` to modern `registerForActivityResult(StartActivityForResult())` contracts for widget picking, widget configuration, and default home prompts.
- **Room Database Cleanup**: Updated `LauncherDatabase` with explicit `fallbackToDestructiveMigration(dropAllTables = true)`.
- **Material 3 Alignment**: Upgraded bottom sheets, buttons, cards, segment pickers, and dialogs across all launcher screens to Google Material 3 standards.
