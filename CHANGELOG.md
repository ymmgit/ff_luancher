# Changelog

All notable changes, new features, and bug fixes for FF Launcher.

---

## [1.2.6] - 2026-11-15

### 🎛️ Default Home Widget Size Customization & Conditional Visibility
- **Selectable Widget Sizes (Small / Medium / Large)**:
  - Users can now choose the size of the default clock/smart glance widget on the home screen: **Small**, **Medium** (default), or **Large**.
  - **Dynamic Scaling**: Responsive typography, component proportions, emoji sizes, and layout paddings across all smart header views (`CombinedGlanceView`, `ClockFocusView`, `CalendarFocusView`, and `WeatherFocusView`).
    - **Small**: Compact layout with 34sp clock, tight paddings, and smaller secondary badges suited for minimalist setups.
    - **Medium**: Balanced default layout with 45sp clock and standard glance spacing.
    - **Large**: High-visibility glanceable layout with 58sp (up to 72sp in clock focus) clock, prominent typography, and generous touch targets.
- **Context-Aware Conditional Settings**:
  - The **Widget Size** selector is strictly displayed only when the default home widget is active.
  - When the user chooses to replace the home widget with a **third-party widget** (`hasCustomWidget == true`) or sets the header mode to **Hidden** (`HeaderMode.HIDDEN`), the widget size selector is automatically hidden from both [SettingsScreen.kt](file:///d:/myproject/ff_luancher/app/src/main/java/com/ff/launcher/ui/settings/SettingsScreen.kt) and the widget long-press configuration sheet in [HomeSmartHeader.kt](file:///d:/myproject/ff_luancher/app/src/main/java/com/ff/launcher/ui/home/HomeSmartHeader.kt).
- **Dual Selection Entry Points**:
  - Available in the standalone **Settings** activity under the Smart Header card.
  - Available directly on the home screen by **long-pressing the default clock widget** and opening the quick configuration sheet.

---

## [1.2.5] - 2026-11-15

### ⚙️ Standalone Settings Activity & App Drawer Visibility
- **Dedicated Standalone Activity**: Replaced the in-app bottom sheet (`TriggerTuningSheet`) with a full-fledged [SettingsActivity.kt](file:///d:/myproject/ff_luancher/app/src/main/java/com/ff/launcher/SettingsActivity.kt), backed by a dedicated [SettingsViewModel.kt](file:///d:/myproject/ff_luancher/app/src/main/java/com/ff/launcher/viewmodel/SettingsViewModel.kt) and Material 3 [SettingsScreen.kt](file:///d:/myproject/ff_luancher/app/src/main/java/com/ff/launcher/ui/settings/SettingsScreen.kt).
- **Recent Apps / Overview Resumption**: Assigned `android:taskAffinity="com.ff.launcher.settings"` and `excludeFromRecents="false"`. Preserved its task in `MainActivity.onResume()` so users can switch back and forth between Settings and other apps/home using the Android Recent Apps carousel.
- **Drawer Visibility Control**: Kept hidden in FF Launcher's own app drawer via package filtering (`AppRepository.kt`), while registering an intent filter with `ACTION_MAIN` and `CATEGORY_LAUNCHER` (`FF Launcher Settings`) so it surfaces in external launchers when the user switches to another launcher.
- **TopAppBar & Edge Navigation**: Added a modern Material 3 top app bar with title and back arrow icon, plus native back gesture support and seamless home widget replacement routing.

### 🌤️ Real-Time Weather Location & Permission System
- **Runtime Location Access in Settings & Widget Menu**: Added interactive location permission requesting in both [SettingsScreen.kt](file:///d:/myproject/ff_luancher/app/src/main/java/com/ff/launcher/ui/settings/SettingsScreen.kt) and the default clock widget long-press sheet in [HomeSmartHeader.kt](file:///d:/myproject/ff_luancher/app/src/main/java/com/ff/launcher/ui/home/HomeSmartHeader.kt). Users can grant `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` with one tap.
- **Accurate Singapore & Device Geolocation**: Enhanced [WeatherRepository.kt](file:///d:/myproject/ff_luancher/app/src/main/java/com/ff/launcher/data/glance/WeatherRepository.kt) with Android 11+ `LocationManager.getCurrentLocation`, reverse `Geocoder` resolution, and automatic timezone fallback (e.g. `Asia/Singapore` -> `1.3521, 103.8198`), eliminating the hardcoded California coordinates and immediately displaying **Singapore** weather.
- **Automatic Fallback Cache Invalidation**: Stale "Los Angeles" cache entries are automatically purged and refreshed on startup and permission grant.

---

## [1.2.1] - 2026-11-15

### 🛡️ Hardening, Performance & QA Polish (Master Plan Milestone M1)

#### 1. Quick Action & Screen Gestures
- **Customizable Low Opacity for Wallpaper Aesthetic**: Added an opacity slider (`15%` to `100%`) for the Quick Action floating button, defaulting to a subtle low opacity (`35%`) with delicate elevation so it preserves and respects the user's wallpaper aesthetic without clutter.
- **Left / Right Edge Placement**: Quick action floating button supports instant selection between Left and Right screen edges via Settings (`TriggerTuningSheet.kt`), defaulting to Left to naturally balance the right-side scroller.
- **On-Screen Vertical Drag Repositioning**: Users can now directly drag the Quick Action floating button along the screen edge to adjust vertical height in real-time, with instant haptic feedback and persistence on release.
- **Search Panel Overlay Protection**: Floating quick action button automatically hides whenever the app search sheet or software keyboard opens (`LauncherRoot.kt`), preventing any touch conflict or screen obstruction.
- **Reset to Defaults**: Added a one-tap "Reset to defaults" action in Launcher Settings (`TriggerTuningSheet.kt`) for quick action button placement and target configuration.
- **Live Preview Sliders**: Vertical position, size, and opacity sliders in `TriggerTuningSheet.kt` now update live on screen while sliding, committing to DataStore only upon gesture release.

#### 2. Stability & Resource Leak Prevention
- **Safe Torch Callback Lifecycle**: Explicitly bound `CameraManager.TorchCallback` lifecycle in `LauncherViewModel`, registering safely on startup and unregistering cleanly in `onCleared()` to eliminate any memory/callback leaks.
- **Discrete Switch Persistence**: Hiding the second widget screen now bypasses slider preview delays, cleanly closing the open widget deck if visible and writing immediately to DataStore.
- **Helpful Guidance State**: Added descriptive helper text in Settings when the second widget screen is hidden: *"Your widgets are saved and return when you turn this on."*

#### 3. Observability & Logging Audit
- **Full Tag Coverage**: Added structured logs (`FF/QuickActionPrefs`, `FF/TriggerPrefs`, `FF/QuickActionButton`, `FF/QuickActionPicker`, `LauncherViewModel`) across all user mutations, torch hardware events, and action launches.
- **Version & Build Verification**: Bumped `versionCode` to 121 and `versionName` to `1.2.1`. Tested APK size maintained at **10.58 MB** (well under the 15 MB budget).

---

## [1.2.0] - 2026-11-15

### 🚀 New Features: Screen Customization & Quick Action Floating Button

#### 1. Show / Hide Second Widget Screen
- **Safe Memory Preservation**: Added an option in Launcher Settings (`TriggerTuningSheet.kt`) to toggle the second widget screen ON or OFF.
- **Zero Data Loss**: When hidden, all widgets, slot layout weights, and configurations remain safely preserved in the database and memory.
- **Instant Restoration**: Switching it back ON immediately restores the edge trigger and the full widget deck with all widgets intact.

#### 2. Customizable Quick Action Floating Button (`QuickActionButton.kt`)
- **Ergonomic Floating Launcher**: A sleek, glassmorphic floating action button positioned on screen (defaulting to the left edge to balance the right-edge scroller).
- **One-Tap Quick Launch**: Launches the user's custom-assigned tool or application instantly with a haptic tap.
- **Long-Press Customization**: Long-pressing the floating button immediately opens the Quick Action Picker Sheet (`QuickActionPickerSheet.kt`).
- **Quick Tool Presets**:
  - 📸 **Camera**: One-tap launch to the device's default camera.
  - ⏰ **Alarm / Clock**: Instant access to system alarms and clock.
  - 🔦 **Flashlight**: Direct hardware torch toggle via `CameraManager`.
  - 🤖 **AI / Voice Assistant**: Direct trigger for Google Assistant / Gemini.
- **Universal App Customization**: Users can select ANY installed application on the device (e.g. Google Gemini, ChatGPT, WhatsApp, Chrome, WeChat, etc.).
- **Live App Icon Rendering**: When assigned to an installed app, the floating button dynamically displays the app's actual icon.
- **Full Position & Size Tuning**: Users can tune the edge side (Left/Right), vertical height bias (8% to 92%), and button size (38dp to 60dp) directly in Launcher Settings.

---

## [1.1.0] - 2026-11-15

### 🚀 New Features: Bundled Widgets & Home Smart Header

#### 1. Home Screen Native Smart Header (`HomeSmartHeader.kt`)
- **Top Placement**: Integrated a native Material 3 Smart Header at the top of the home screen, directly above pinned favorite applications.
- **Multiple Display Modes**:
  - **Combined At-a-Glance**: Compact side-by-side time, date, upcoming calendar event pill, and real-time weather summary.
  - **Clock Focus**: Large digital typography with contextual date and day of week.
  - **Calendar Focus**: Big numeric day display, month and weekday headers, and detailed upcoming event title and time.
  - **Weather Focus**: Large current temperature, condition emoji, high/low range, and condition description.
  - **Hidden**: Fully minimizes the header for users who prefer an empty top screen.
- **Long-Press Customization Sheet**: Long-pressing the Smart Header opens a Material 3 bottom sheet allowing instant switching between all header modes and toggling between Celsius (°C) and Fahrenheit (°F).
- **Settings Restoration (When Hidden/Removed)**:
  - **Launcher Settings (`TriggerTuningSheet.kt`)**: Added a dedicated "Home Screen Header & Widgets" section accessible anytime via the small gear icon (⚙) at the bottom of the alphabet wave scroller, allowing users to restore or switch widget modes (Combined, Clock, Calendar, Weather, or Hidden) and change temperature units.
  - **Search Panel Quick Action (`AppSearchSheet.kt`)**: Added a dedicated Settings gear button right in the search bar header so users can instantly open Launcher Settings directly from the search panel.
- **Interactive Deep-Links**: Tapping the time launches the system clock/alarm app; tapping the date/event opens the system calendar at the event timestamp; tapping weather opens weather search.

#### 2. Three Exported Standard Android AppWidgetProviders
- **Clock Widget (`ClockWidgetProvider.kt`)**: 2×1 digital clock and localized date utilizing `TextClock` for zero battery drain real-time updates.
- **Calendar Widget (`CalendarWidgetProvider.kt`)**: 3×2 date card with day badge, weekday, and the next upcoming event from `CalendarContract.Instances`.
- **Weather Widget (`WeatherWidgetProvider.kt`)**: 2×1 weather card with condition emoji, temperature, high/low temperature range, and condition description.
- **Cross-Launcher Compatibility**: All three widgets are standard `AppWidgetProvider`s registered in `AndroidManifest.xml` with XML metadata and RemoteViews layouts, making them accessible in FF Launcher's own widget deck as well as any other Android launcher (Nova, Niagara, Pixel Launcher, etc.).

#### 3. Data & Battery-Efficient Logic Layer
- **`TimeProvider.kt`**: Zero-polling, broadcast-driven system time listener responding to `ACTION_TIME_TICK`, `ACTION_TIME_CHANGED`, and `ACTION_TIMEZONE_CHANGED`.
- **`CalendarProvider.kt`**: Queries Android `CalendarContract` for events within a rolling 24-hour window, handling runtime `READ_CALENDAR` permission gracefully without crashes.
- **`WeatherRepository.kt`**: Connects to the free Open-Meteo REST API, maps WMO weather codes to localized descriptions and emojis, resolves coarse device location, and caches results in DataStore for 1 hour to conserve data and battery.
- **`HeaderPreferencesRepository.kt`**: Stores header layout mode and temperature unit preferences in Jetpack DataStore Preferences.

#### 4. Immersive Pinned Apps UI Redesign (`ManageFavoritesSheet.kt`)
- **Wallpaper Background Overlay**: Eliminated the solid bottom sheet container in favor of an immersive wallpaper overlay with a semi-transparent dark readability scrim.
- **Floating Semi-Transparent Boxes**: Wrapped every application item in a floating, rounded translucent card with glass borders and elevation.
- **Interactive Checkboxes**: Added a native Checkbox next to every app in the drawer list—pinned apps are checked, unpinned apps remain in the drawer.
- **Vertical Alphabet Wave Scroller**: Maintained the right-edge A–Z wave scroller for instant alphabetical section jumping across all installed applications.
- **Drag-and-Drop + Arrow Reordering**: In the Pinned Sequence view, users can drag and drop items via the `≡` handle for instant reordering with haptic feedback, while retaining Up and Down arrow buttons for tap-based adjustment.

#### 5. Alphabetical Scroller Downward Scroll Fix (`WaveScroller.kt`)
- **Strict Alphabet Range Clamping**: Decoupled the bottom gear icon (⚙) from the continuous alphabet drag gesture in `WaveScroller.kt`. Scrolling or dragging down the alphabet strip is strictly confined to `0 until sectionLabels.size` and firmly clamps to the last alphabetical apps (e.g. Z or #), completely preventing accidental navigation to the Settings page when scrolling to the bottom.
- **Independent Tap Target**: The gear icon is now an independent button at the bottom of the scroller that only activates upon a deliberate direct tap.

#### 6. Comprehensive Logging & AppEntry Task Isolation
- **Structured Activity & Function Logging**:
  - `MainActivity.kt`: Lifecycle traces (`onCreate`, `onResume`, `onPause`, `onStop`, `onDestroy`, `onNewIntent`, `handleHomeIntent`, and widget binding results).
  - `AppEntryActivity.kt`: Trampoline execution and redirection logging.
  - `LauncherViewModel.kt`: Function action logs (`launchApp`, `toggleFavorite`, `updateFavoriteOrder`, `moveFavorite`, `setHeaderMode`, `setHeaderUseFahrenheit`).
  - AppWidget Providers (`ClockWidgetProvider`, `CalendarWidgetProvider`, `WeatherWidgetProvider`): Broadcast receive and widget update logging.
- **Trampoline Task Affinity Isolation**:
  - Assigned distinct `taskAffinity="com.ff.launcher.appentry"` to `AppEntryActivity` in `AndroidManifest.xml` and switched termination from `finishAndRemoveTask()` to `finish()`.
  - Guarded `MainActivity.onResume()` task cleanup to ensure `MainActivity` never accidentally evicts its own task, guaranteeing seamless home screen navigation and launcher switcher visibility.

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
- **Home Button in Search Screen**: Fixed an issue where pressing the Android system Home button while inside the app search sheet failed to return to the home screen. Unified system `CATEGORY_HOME` intent handling to dismiss all open sheets (Search, Favorites, Settings, Widget Picker) and reset the home screen view.
- **Widget Initial Refresh (Blank Slot Fix)**: Fixed an issue where newly added widgets displayed blank until returning to the home screen and reopening the deck. Added pre-sized bind options, automatic post-layout size dispatch, and staggered `AppWidgetHost.startListening()` refreshes upon widget creation so new widgets immediately fetch their RemoteViews snapshot.
- **Excluded Launcher from Recent Apps**: Completely resolved launcher task retention in Android Recent Apps. Removed extraneous `category.LAUNCHER` filter, configured `autoRemoveFromRecents="true"`, `documentLaunchMode="never"`, `taskAffinity=""`, added `FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS` for widget configure activities, and actively evict stale standard tasks via `ActivityManager.appTasks.finishAndRemoveTask()`.
- **Material 3 Default Launcher Re-engagement Card**: Redesigned the default launcher prompt into an elevated Material Design 3 Card with an icon badge, typography hierarchy, dismiss button, and rounded `FilledTonalButton` action.
- **External Launcher App Drawer Entry**: Added a dedicated `AppEntryActivity` with `category.LAUNCHER` so other launchers (e.g. Niagara Launcher) display FF Launcher in their app drawers. When tapped, it redirects to the primary home activity and immediately finishes with `finishAndRemoveTask()` and `excludeFromRecents="true"`, ensuring no residual task appears in Recent Apps.

---

### 🛠️ Architecture & Code Modernization

- **Activity Result API**: Migrated deprecated `onActivityResult` in `MainActivity.kt` to modern `registerForActivityResult(StartActivityForResult())` contracts for widget picking, widget configuration, and default home prompts.
- **Room Database Cleanup**: Updated `LauncherDatabase` with explicit `fallbackToDestructiveMigration(dropAllTables = true)`.
- **Material 3 Alignment**: Upgraded bottom sheets, buttons, cards, segment pickers, and dialogs across all launcher screens to Google Material 3 standards.
