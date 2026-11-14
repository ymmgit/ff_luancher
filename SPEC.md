# Project Specification: Minimalist Wave Launcher with On-Demand Widget Deck

## 1. Project Overview
Build an ultra-lightweight, minimalist Android Launcher using **Kotlin** and **Jetpack Compose** inspired by Niagara Launcher. 
- **Primary Screen (Home):** Clean vertical list of Favorite apps + right-edge A每Z wave alphabet scroller with haptic feedback.
- **Secondary Screen (Widget & Utility Deck):** A non-scrollable, zero-idle-RAM overlay screen triggered by a subtle, user-customizable edge pill (defaulting to upper-right edge). Contains 3 fixed-slot `AppWidgetHostView` containers filling the full panel height. (Revision, Phase 3: the native Quick Toggle bar was cut 〞 Android 12+ system Quick Settings already covers those controls without extra permissions.)

## 2. Tech Stack & Architecture
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3)
- **Architecture:** MVVM with `ViewModel`, Kotlin Coroutines, and `StateFlow`
- **Persistence:** 
  - **Jetpack DataStore (Preferences):** Stores Edge Trigger configuration parameters and launcher preferences.
  - **Room Database:** Stores favorite/hidden app package names, custom sorting, and bound `appWidgetId` slot configurations.
- **Activity Config:** Single-Activity architecture (`launchMode="singleTask"`, `clearTaskOnLaunch="true"`, `stateNotNeeded="true"`) registered with `Intent.CATEGORY_HOME` and `Intent.CATEGORY_DEFAULT`.

## 3. Core Modules & Detailed Requirements

### Module 1: Primary Home Screen & Wave Scroller
1. **App Repository:** Query launchable apps via `LauncherApps` and `PackageManager`. Listen to package install/uninstall/change broadcasts via `BroadcastReceiver` to update the in-memory app list dynamically.
2. **Favorites View:** Display user-pinned favorite apps in a clean vertical column on the left/center area.
3. **A每Z Wave Scroller (Right Edge, Lower-Mid Zone):**
   - Occupies roughly 35% to 85% of screen height on the right edge.
   - Custom `Modifier.pointerInput` detecting vertical drag (`detectVerticalDragGestures`).
   - Maps touch Y-coordinate to alphabetical sections (A每Z) plus a bottom `? Settings` node.
   - Renders a subtle wave protrusion animation toward the left so the user's thumb does not obscure the active letter, triggering `HapticFeedbackType.TextHandleMove` on letter change.

### Module 2: Subtle Edge Trigger (Customizable Placement & Gesture Exclusion)
1. **User-Configurable Parameters (Persisted in DataStore):**
   - `triggerEdgeSide`: Enum (`RIGHT` [default], `LEFT`).
   - `triggerVerticalBias`: Float (`0.05f` to `0.90f`, default `0.22f` 〞 upper-right edge, safely above the A每Z scroller zone).
   - `triggerHeightDp`: Int (`32` to `100`, default `56`).
   - `triggerWidthDp`: Int (`3` to `12`, default `5` for visual pill; hit target padded to `24dp` for reliable touch).
   - `triggerOpacity`: Float (`0.0f` [invisible] to `1.0f`, default `0.35f`).
   - `triggerMode`: Enum (`TAP_ONLY`, `SWIPE_ONLY`, `TAP_AND_SWIPE` [default]).
2. **Dynamic Full-Screen Gesture Exclusion (`SystemGestureExclusionRects`):**
   - Whenever `triggerMode` includes swipe (`SWIPE_ONLY` or `TAP_AND_SWIPE`), dynamically compute the exact pixel bounding box (`android.graphics.Rect`) of the Edge Trigger in `Modifier.onGloballyPositioned` and pass it to `LocalView.current.setSystemGestureExclusionRects(listOf(rect))`.
   - Enforce Android's maximum `200dp` vertical exclusion limit so inward swipes on the trigger never accidentally fire the system-wide Edge Back gesture.
   - If `triggerMode == TAP_ONLY`, clear the exclusion rect (`emptyList()`) so normal system back gestures work unimpeded across the entire edge.
3. **Activation:** Tapping or pulling inward on the Edge Trigger sets `isSecondScreenOpen = true` with a crisp haptic tick and a fast fade + slide-in transition.

### Module 3: Secondary Screen (Fixed-Screen Widget & Utility Deck)
1. **Non-Scrollable Layout (`Column`):**
   - Strictly fit within 1 screen height (no `LazyColumn` or `verticalScroll`) to eliminate nested scroll conflicts with widgets.
   - **Widget Slots (100% height):** 3 fixed slots divided by height weights (Small: 2, Medium: 3, Large: 4 〞 switchable per slot from its long-press menu, with `onSizeChanged` driving responsive widget relayout).
2. **Zero-Clutter Settings Entry (Revision, Phase 3):**
   - Long-pressing the panel background opens the settings sheet; every slot's long-press menu also contains "Tune Edge Trigger" and the slot-size selector.
   - (Revision, Phase 3: the native Quick Toggle dock was cut 〞 Android 12+ system Quick Settings already covers brightness/torch/ringer/etc., and replicating them would require `WRITE_SETTINGS`, camera and DND permissions plus ContentObserver overhead.)

### Module 4: Aggressive Widget Memory & Lifecycle Optimization
To prevent background RAM bloat and battery drain from `AppWidgetHostView`:
1. **On-Demand IPC Listening:**
   - Call `appWidgetHost.startListening()` **ONLY** when `isSecondScreenOpen == true` AND the Activity is in `RESUMED` state.
   - Immediately call `appWidgetHost.stopListening()` when `isSecondScreenOpen == false` or Activity pauses.
2. **Lazy Instantiation & Idle Purge:**
   - Do NOT instantiate `AppWidgetHostView` objects on cold launcher startup.
   - Instantiate views lazily upon first opening the Secondary Screen.
   - When closing the Secondary Screen, set hardware layer to `View.LAYER_TYPE_NONE` on hosted widget views to free GPU textures, and start a 5-minute coroutine timer. If the Secondary Screen remains closed for 5 minutes, detach and null out the `AppWidgetHostView` references for GC collection.

### Module 5: Full-Screen Navigation & Return Mechanics
Returning from the Secondary Screen (`isSecondScreenOpen = true` -> `false`) must support all modern full-screen gesture paradigms:
1. **System Edge Back Gesture:** Intercept via Compose `BackHandler(enabled = isSecondScreenOpen) { isSecondScreenOpen = false }`.
2. **Bottom-Up Home Swipe Gesture:** Override `MainActivity.onNewIntent(intent: Intent)`. When receiving `Intent.ACTION_MAIN` with `Intent.CATEGORY_HOME`, if `isSecondScreenOpen == true`, set it to `false` smoothly.
3. **Tap Outside / Edge Trigger Retap:** Tapping empty padding between widgets or tapping the Edge Trigger again closes the panel.
4. **Auto-Close on Widget Launch:** In `onStop()`, automatically reset `isSecondScreenOpen = false` so returning from a launched widget activity lands cleanly on the Primary Screen.

### Module 6: Configuration & Settings Flow
1. **Access Point:** Located at the very bottom node of the A每Z Wave Scroller (`? Launcher Settings`) or by long-pressing an empty space/widget frame on the Secondary Screen.
2. **Settings Capabilities:**
   - **Edge Trigger Tuning Panel (with Live Overlay Preview):**
     - Segmented button for Edge Side (`Left` / `Right`).
     - Slider for Vertical Position (`5%` to `90%` from top) with real-time visual preview on the screen edge.
     - Sliders for Pill Height (`32dp每100dp`), Pill Width (`3dp每12dp`), and Opacity (`0%每100%`).
     - Selector for Activation Mode (`Tap Only`, `Swipe Only`, `Tap & Swipe`).
   - **Widget Slot Manager:** Pick/replace/remove widgets for Slots 1每4 using `AppWidgetManager.ACTION_APPWIDGET_PICK` / `ACTION_APPWIDGET_BIND` and adjust slot height weight.
   - **Hidden Apps Manager:** hide apps from the home-screen long-press menu; unhide them from the settings sheet's "Hidden apps" list.
   - **App Management:** Multi-select lists for Favorites and Hidden Apps.