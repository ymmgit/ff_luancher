# FF Launcher

An ultra-lightweight, minimalist Android home launcher built with **Kotlin** and **Jetpack Compose (Material 3)**, inspired by [Niagara Launcher](https://niagaralauncher.com/). 

FF Launcher combines a focused, one-handed home screen with an ergonomic right-edge alphabet scroller, a customizable edge trigger for full-screen widgets, and intelligent search.

See [CHANGELOG.md](CHANGELOG.md) for recent updates and bug fixes, and [SPEC.md](SPEC.md) for the technical architecture specification.

---

## Features

### 📱 Primary Home Screen
- **Minimalist Pinned Favorites**: Shows only your pinned favorite applications on the home screen for a distraction-free environment.
- **Unified Favorites Manager & Reordering**: Long-press any app in the drawer or home screen to open a consolidated Material 3 bottom sheet to pin/unpin apps and adjust their exact display sequence with live Room database persistence.
- **Wide Touch Targets**: App rows provide an expanded clickable surface (>80% screen width) for effortless, natural tapping.
- **A–Z Wave Scroller**: Docked to the right edge with zero-latency section jumping, ICU-backed international alphabet collating, dynamic wave bubble indicator, and haptic feedback (`HapticFeedbackType.TextHandleMove`).
- **Quick Settings Access**: Dedicated gear icon (⚙) at the bottom of the alphabet scroller opens Launcher Settings directly.
- **Dynamic App Drawer**: Pulling or touching the alphabet scroller reveals the full locale-collated app drawer, which lingers smoothly before collapsing back to favorites.

### 🔍 App Search & Recents
- **Instant Search**: Quick fuzzy/prefix search filtering installed applications in real time.
- **Recent Applications**: Automatically surfaces recently launched applications at the top of the search view.
- **Newly Installed Applications**: Highlights applications installed within the past 48 hours.
- **Intentional Pull-to-Search**: Pulling up deliberately at the bottom of the home screen opens the search sheet, backed by a guarded 64dp overscroll threshold.
- **Search Context Actions**: Long-press any app in search results to pin it to favorites or open the management options.

### 🧩 Widget Deck & Material 3 Picker
- **Edge-Triggered Secondary Screen**: A subtle, live-tunable edge pill opens the full-screen widget deck with an animated slide/fade transition.
- **In-App Material 3 Widget Picker**: Browse and add widgets categorized by parent application with icons, item counts, previews, and size badges.
- **Floating Over Wallpaper**: The widget deck and slot containers render with clean transparency, letting your wallpaper shine through.
- **Background App Masking**: Home screen favorites automatically fade out while the widget deck is open to prevent visual clutter behind transparent widgets.
- **Custom Slot Weights & Sizing**: Supports Small, Medium, and Large widget heights with live resizing and configuration activity handling.

### ⚙️ Edge Trigger & Launcher Settings
- **Live-Tunable Edge Pill**: Adjust side (Left/Right), vertical position (5%–90%), height, width, and opacity with real-time DataStore persistence.
- **System Gesture Exclusion**: Automatically registers Android gesture exclusion rects when swipe-to-open is active to prevent conflicts with the system back gesture.
- **Hidden Applications**: Hide unwanted applications from the launcher, with easy unhiding from Launcher Settings.
- **Default Launcher Guidance**: Non-intrusive re-engagement banner and first-run onboarding to set FF Launcher as the system default home app.

---

## Tech Stack & Architecture

- **Language & Framework**: Kotlin 2.1 · Jetpack Compose (BOM 2024.12, Material 3)
- **Architecture**: MVVM with unidirectional data flow (`ViewModel`, `StateFlow`, Coroutines)
- **Local Persistence**: 
  - Room 2.7 (Favorites sequence, hidden apps, widget slots)
  - Jetpack DataStore Preferences (Edge trigger configuration & onboarding state)
- **Modern Android APIs**: Activity Result API (`registerForActivityResult`), `LauncherApps` multi-user/work-profile support, `AppWidgetHost` with idle memory purging.
- **Design System**: Material Design 3 (dynamic color scheme tokens, rounded cards, elevated sheets).

---

## Requirements

- **Android Device**: Android 8.0+ (API level 26+; gesture exclusion supported on Android 10+)
- **Target SDK**: Android 15 (API level 35)
- **JDK**: Java 21

---

## Build & Installation

Clone the repository and build the debug APK using Gradle:

```bash
# Build debug APK
./gradlew assembleDebug

# Install via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

After installation:
1. Press the **Home** button on your device.
2. Select **FF Launcher** and choose **Always**.

---

## Project Structure

```
app/src/main/java/com/ff/launcher/
├── MainActivity.kt                      # Single-activity entry point, Activity Result contracts, system bars
├── data/
│   ├── AppInfo.kt                       # Application model & component key definitions
│   ├── AppRepository.kt                 # LauncherApps query, work profiles & package broadcast receiver
│   ├── FavoritesRepository.kt           # Room-backed favorite persistence & ordering
│   ├── OnboardingRepository.kt          # First-run & default home role state
│   ├── TriggerPreferencesRepository.kt  # DataStore-backed edge trigger parameters
│   └── db/
│       └── LauncherDatabase.kt          # Room database (favorites, hidden apps, widget slots)
├── ui/
│   ├── LauncherRoot.kt                  # Root composition (Home, Deck, Edge Trigger, Sheets)
│   ├── home/
│   │   ├── HomeScreen.kt                # Pinned favorites, dynamic drawer, overscroll search trigger
│   │   ├── AppSearchSheet.kt            # App search, recent applications, newly installed apps
│   │   ├── ManageFavoritesSheet.kt      # Unified favorites reordering & selection sheet
│   │   └── WaveScroller.kt              # Right-edge alphabet scroller, wave bubble, settings shortcut
│   ├── secondary/
│   │   ├── SecondaryScreen.kt           # Full-screen widget deck & slot containers
│   │   ├── WidgetPickerSheet.kt         # Material 3 widget picker grouped by application
│   │   └── TriggerTuningSheet.kt        # Launcher settings, trigger tuning & hidden apps sheet
│   ├── onboarding/
│   │   └── WelcomeOnboardingScreen.kt   # Welcome flow & default launcher prompt
│   ├── trigger/
│   │   └── EdgeTrigger.kt               # Tunable edge pill with gesture exclusion rects
│   └── theme/                           # Material 3 colors, typography, shapes & theme setup
├── viewmodel/
│   ├── HomeListState.kt                 # UI row models & alphabetical section collation
│   └── LauncherViewModel.kt             # Unidirectional state management & widget host interaction
└── widget/
    └── LauncherAppWidgetHost.kt         # Custom AppWidgetHost & lifecycle-managed view holders
```

---

## Documentation

- [CHANGELOG.md](CHANGELOG.md): History of feature updates, UX enhancements, and bug fixes.
- [SPEC.md](SPEC.md): Full technical specification and architecture roadmap.
