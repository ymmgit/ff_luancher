# FF Launcher: Master Development & QA Plan

## Global Context, Baseline, and Guardrails

**Target Package:** `com.ff.launcher`  
**Reference Device:** Xiaomi Mi 8 (`52ab3f9e`, 1080×2248)  
**Current Baseline:** v1.2.0 (commit `e3107d5`), hardening toward v1.2.1 and Stage 3 (v1.3.0 – v1.4.0)

### Project Status Baseline (v1.0 to v1.2.0)

| Area | Status |
|---|---|
| Home (favorites + A-Z), Smart Header, Wave Scroller, Edge Trigger, Widget Deck, Hidden apps, Onboarding | Done (v1.0 to v1.1) |
| Manage Favorites redesign (floating boxes, checkboxes, drag and drop) | Done |
| Scroller clamped to the last letter (no accidental jump into settings) | Done |
| Show/Hide second widget screen (`enabled` flag, widget state preserved) | Done, installed on device |
| Customizable Quick Action floating button (presets and any app, edge, bias, size) | Done, installed on device |
| Log and resource audit (RAM/CPU) | Done once on an earlier build; due for v1.2.0 re-baseline |

**Current Gap:** The v1.2.0 features have only had a smoke test (the app launches, PID alive). They require a full behavioral QA pass, a regression log audit, and a resource re-baseline (Milestone M1) before moving into Stage 3 expansion.

### Non-Negotiable Resource Budgets & Constraints

These constraints apply across Stage 2 verification, the M1 test pass, and all Stage 3 milestones:

| Metric | Budget / Rule |
|---|---|
| **APK Size** | $\le 15\text{ MB}$ (current v1.2.0 is $\approx 11.3\text{ MB}$; target $\le 12.5\text{ MB}$) |
| **Idle PSS (RAM)** | $\le 10\%$ above the v1.2.0 baseline |
| **Idle CPU** | $\approx 0\%$ (screen on, no interaction; zero polling or infinite animations) |
| **Cold Start (`TotalTime`)** | $\le 800\text{ ms}$ to first frame on the Mi 8 |
| **Frame Jank** | $\le 5\%$ janky frames during home scroll, drawer reveal, and drag |
| **Background Services** | None added; no heavy dependencies (e.g., no `material-icons-extended`) |
| **Data Survival** | All user data survives process death, reboot, and upgrades (Room & DataStore) |
| **Observability** | Every user-visible feature/action gets a log tag (`FF/<Module>`) and can be toggled/reset |

---

# Part 1: Stage 2 Plan — Show/Hide Second Widget Screen + Customizable Quick Action Button

## 1. Architecture and Feature Goals

### 1.1 Feature A: Show/Hide Second Widget Screen

**Goal:** The user can turn the second (widget deck) screen off and back on. Turning it off must never lose widget data.

| Requirement | Detail |
|---|---|
| R-A1 | A switch in the settings sheet controls whether the deck is reachable |
| R-A2 | OFF: the edge trigger is removed from composition, so swipe and tap do nothing |
| R-A3 | ON: the deck returns with the same slots, providers, and sizes (S/M/L) |
| R-A4 | The setting survives process death and reboot |
| R-A5 | A hidden deck always has a visible way back (the switch stays reachable in settings) |
| R-A6 | Hiding adds no background work and frees the UI cost of the trigger layer |

**Design Decision:** "Hide" only means "don't compose the trigger". Widget slots (Room `widget_slots`), host-view state, and trigger config (DataStore) are left untouched. Nothing needs to be re-saved or re-bound on re-enable.

### 1.2 Feature B: Customizable Quick Action Floating Button

**Goal:** A floating button on a screen edge that launches one user-chosen target with a single tap.

| Requirement | Detail |
|---|---|
| R-B1 | Target types: system tool presets (Camera, Alarm, Flashlight, AI Assistant) or any installed app |
| R-B2 | The user picks a target from a bottom sheet with presets and a searchable app list |
| R-B3 | The user can adjust edge side (left/right), vertical position (drag or slider), and size |
| R-B4 | The button can be switched on/off without losing its configuration |
| R-B5 | Flashlight icon reflects the real torch state, including changes from outside the launcher |
| R-B6 | Missing or uninstalled targets degrade gracefully (no crash, clear feedback) |
| R-B7 | Work-profile apps launch in the correct profile (via composite `componentKey`) |
| R-B8 | Haptic feedback on tap and drag start |

### 1.3 Component Map

```
LauncherRoot
 ├─ HomeScreen / SecondaryScreen (existing)
 ├─ EdgeTrigger            ← composed only if triggerConfig.enabled      (Feature A)
 ├─ QuickActionButton      ← composed only if quickActionConfig.enabled  (Feature B)
 ├─ QuickActionPickerSheet ← shown when ViewModel picker state is open
 └─ TriggerTuningSheet     ← holds the Feature A switch and Feature B settings card

LauncherViewModel
 ├─ TriggerPreferencesRepository      (DataStore)
 ├─ QuickActionPreferencesRepository  (DataStore)
 ├─ AppRepository                     (app list for picker and icon lookup)
 └─ CameraManager                     (torch toggle + callback)
```

### 1.4 Edge-Zone Priority (Conflict Rule)

On the same edge, priority from highest to lowest is:
1. Quick action button (its own bounds only)
2. Wave scroller (right edge)
3. Edge trigger pill
4. System back gesture (not blocked outside the exclusion rects)

---

## 2. Data Layer Specification

### 2.1 `TriggerPreferencesRepository`: `enabled` Flag

| Item | Spec |
|---|---|
| Model | `TriggerConfig` gains `enabled: Boolean` |
| DataStore key | `KEY_ENABLED` (Boolean) |
| **Default** | `true`, so existing users see no change after upgrade **(verify)** |
| Read | Missing key maps to the default, so no migration is needed |
| Write | One DataStore edit per toggle. It must not rewrite side/position/size/opacity/mode. |
| Preview/commit | `enabled` is a discrete switch and bypasses the slider preview path |

**Acceptance:**
- Fresh install gives `enabled = true`.
- Upgrade from 1.1.x gives `enabled = true` with other fields unchanged.
- Toggling `enabled` does not alter any other persisted value.

### 2.2 `QuickActionPreferencesRepository`: Schema

| Field | Type | Meaning | Default / Note |
|---|---|---|---|
| `enabled` | Boolean | Button visible or not | **(verify)** whether this exists and its default |
| `type` | enum/String | `PRESET` or `APP` | Preset values: camera, alarm, flashlight, assistant |
| `componentKey` | String | `ComponentName#userSerial`, used when type = APP | Same format as `AppInfo.componentKey` |
| `label` | String | Display label and accessibility description | Cached so the UI doesn't need an app lookup |
| `edgeSide` | enum | `LEFT` or `RIGHT` | Pick the default that does not collide with the edge trigger **(verify)** |
| `verticalBias` | Float $0..1$ | Vertical position as a fraction of usable height | Clamped on read |
| `sizeDp` | Int | Button diameter | Clamped to a sane min/max on read **(verify range)** |

**Rules:**
- All reads must **sanitize**: clamp `verticalBias` and `sizeDp`, and fall back to a default type when the stored value is unknown.
- Writes go through a single `update { }` per user action. Drag and slider interaction use an in-memory preview, and only gesture end writes to disk (same pattern as the trigger tuning).
- Config is one object, exposed as a single `Flow` and mapped once with `distinctUntilChanged`. No per-field flows. Collect with `collectAsStateWithLifecycle` so nothing collects in the background.

### 2.3 Data-Safety Rules

- No Room schema change is needed for either feature.
- Hiding the second screen never deletes `widget_slots` rows.
- If the stored `componentKey` no longer resolves, **don't delete the config**. Keep it and show a fallback state, so a reinstalled app is picked up again.

---

## 3. Business Logic and ViewModel

All actions live in `LauncherViewModel` and are exposed as state plus functions. Composables don't touch system services.

### 3.1 State

| State | Source | Used By |
|---|---|---|
| `triggerConfig` (incl. `enabled`) | `TriggerPreferencesRepository` | `LauncherRoot`, `TriggerTuningSheet` |
| `quickActionConfig` | `QuickActionPreferencesRepository` | `QuickActionButton`, `TriggerTuningSheet` |
| `quickActionPickerVisible` | in-memory | `LauncherRoot`, `QuickActionPickerSheet` |
| `torchOn` | `CameraManager` torch callback | `QuickActionButton` icon |
| `quickActionPreview` (bias, size) | in-memory | live drag/slider feedback |

### 3.2 Actions

| Action | Behavior |
|---|---|
| `setSecondScreenEnabled(Boolean)` | Writes `enabled`. If the deck is open when turned off, close it first. |
| `setQuickActionEnabled(Boolean)` | Writes the enabled flag only |
| `openQuickActionPicker()` / `closeQuickActionPicker()` | Toggles picker state |
| `selectQuickActionPreset(preset)` | Writes type = `PRESET` + preset id + label, then closes the picker |
| `selectQuickActionApp(componentKey)` | Writes type = `APP` + key + label, then closes the picker |
| `updateQuickActionPlacement(...)` | Preview while dragging, commit at gesture end |
| `executeQuickAction()` | Dispatches by type, see 3.3 |
| `toggleFlashlight()` | See 3.4 |

### 3.3 `executeQuickAction()` Resolution

1. **Flashlight preset:** call `toggleFlashlight()`.
2. **Camera preset:** `MediaStore.ACTION_IMAGE_CAPTURE` or the default camera app. Catch `ActivityNotFoundException`, then show a "not available" message.
3. **Alarm preset:** `AlarmClock.ACTION_SHOW_ALARMS`, with the same catch.
4. **AI Assistant preset:** `Intent.ACTION_VOICE_COMMAND` or `ACTION_ASSIST`, with the same catch.
5. **App type:** resolve `componentKey` to `ComponentName` plus user.
   - Use `LauncherApps.startMainActivity(component, user, ...)` so work-profile apps launch correctly.
   - If it can't be resolved (uninstalled, disabled, suspended), log a **warning** (not an error), show a message, and offer to reopen the picker.
6. Every path logs the target and the outcome. All launches add `FLAG_ACTIVITY_NEW_TASK` where needed.

### 3.4 Flashlight

- `CameraManager.setTorchMode(cameraId, !torchOn)` using the back-facing camera id with `FLASH_INFO_AVAILABLE`. Cache the id.
- `registerTorchCallback` keeps `torchOn` in sync with the system (shade toggles, other apps).
- Register the callback only while it's useful: when the quick action is enabled and set to Flashlight, or while the ViewModel is alive. Unregister in `onCleared()` and when the action changes away from Flashlight. **(verify against current code)**
- Handle `CameraAccessException` (camera in use): warning log, no crash, icon stays consistent with the callback state.
- No camera permission is needed for `setTorchMode`.

### 3.5 Sheet State & Logging Rules

- **Sheet exclusivity:** Only one of `{tuning sheet, picker sheet, search sheet, manage-favorites}` is open at a time. The picker is launched from the tuning sheet; after a selection, return to the tuning sheet. Back press closes the topmost sheet first.
- **Logging:** Emit one line per user-visible action (second screen enabled/disabled, quick action enabled/disabled, target selected, launched with success/failure, moved/committed, torch toggled, torch callback received).

---

## 4. UI / Presentation Layer & Performance Strategy

### 4.1 `LauncherRoot`

- Wrap `EdgeTrigger` in `if (triggerConfig.enabled)`. Widget host views continue to follow the existing lifecycle rule (listen only while the deck is open and the activity is resumed).
- Add `QuickActionButton` in the overlay layer **above** the home content and **below** sheets. Use `AnimatedVisibility` (fade and scale) driven by `quickActionConfig.enabled`.
- Hide or disable the button while the search sheet, keyboard, manage-favorites overlay, or onboarding is showing. **(verify and decide; record the behavior in the test report)**
- Show `QuickActionPickerSheet` from ViewModel state.
- Keep `systemGestureExclusionRects` within the existing 200dp cap, accounting for both the trigger and the button.

### 4.2 `QuickActionButton`

| Aspect | Spec |
|---|---|
| Shape | Circular, semi-transparent, floating shadow, consistent with the floating-box style |
| Position | Aligned to `edgeSide`, vertical position from `verticalBias`, kept clear of status bar and navigation insets |
| Gestures | Tap runs `executeQuickAction()`. Long-press then drag repositions, with a commit on release. Clamp so it never leaves the screen. |
| Feedback | Haptic on tap and on drag start. Subtle press scale. |
| Icon | `PRESET` uses a vector from `QuickActionIcons`. `APP` uses the cached app icon (from the 128px icon cache), with the Rocket vector as fallback. Flashlight swaps On/Off with `torchOn`. |
| Accessibility | `contentDescription` is the label. Minimum touch target is 48dp. |
| Cost | No infinite animations. It recomposes only on config, preview, or torch changes. |

### 4.3 `QuickActionPickerSheet`

- Material 3 modal bottom sheet.
- Top section: preset tiles (Camera, Alarm, Flashlight, AI Assistant) using the hand-coded `ImageVector` paths in `QuickActionIcons.kt` (built lazily and cached once; no PNGs or `material-icons-extended`).
- Below: a search field with a lazy, keyed (`componentKey`) searchable list of all launchable apps, reusing the already-loaded `AppRepository` list and icons (no second app query).
- Search is case-insensitive, matching label substrings. Chinese names should work via the existing ICU/pinyin logic if reusable. **(verify)**
- Selecting an item calls the matching ViewModel action and dismisses. Empty-search state shows a short message.

### 4.4 `TriggerTuningSheet` & Entry Points

- **Second widget screen section:** Switch "Show second widget screen", bound to `triggerConfig.enabled`. When OFF, trigger tuning controls are disabled or collapsed with helper text: *"Your widgets are saved and return when you turn this on."*
- **Quick action section (card):** Switch to enable button; current target row (icon + label) that opens the picker; controls for edge side (segmented), vertical position (slider), and size (slider) using preview-then-commit; "Reset" action to restore defaults. All strings reside in `strings.xml`.
- **Entry points:** Remains reachable from the scroller gear (and later via the search panel gear in Stage 3).

---

## 5. Stage 2 Implementation Status & Open Questions

| Step | Task | Status |
|---|---|---|
| 1 | Add `enabled` to `TriggerConfig` and `KEY_ENABLED` | Done, verify defaults |
| 2 | Create `QuickActionPreferencesRepository` (schema 2.2) | Done, verify clamps and defaults |
| 3 | Create `QuickActionIcons` (vector paths) | Done |
| 4 | ViewModel: config state, picker state, `executeQuickAction`, torch | Done, verify callback lifecycle |
| 5 | `QuickActionButton` & `QuickActionPickerSheet` | Done |
| 6 | `TriggerTuningSheet` switch and quick action card | Done |
| 7 | `LauncherRoot` integration, strings, and gesture exclusion | Done, verify overlap rules |
| 8 | **M1 Verification pass (Part 2 below), fixes, and tag v1.2.1** | **To do (Immediate Next Step)** |

**Open Stage 2 Questions to Confirm During M1:**
1. Should the quick action button hide while the keyboard or search sheet is open? (Confirm current code behavior.)
2. Which edge should the button use by default to avoid colliding with the edge trigger?
3. Should the torch callback live for the whole ViewModel lifetime, or only while Flashlight is selected?

---

# Part 2: M1 Test Script and Checklist (v1.2.0 $\rightarrow$ v1.2.1)

Confirm these three assumptions in Step 0:
- Package name is `com.ff.launcher`
- Mi 8 screen geometry is $1080 \times 2248$
- Log tags are discovered dynamically in Step 0.3 rather than assumed

---

## 0. Setup and Discovery (~10 min)

### 0.1 Session Variables and Helper

```powershell
cd d:\myproject\ff_luancher
$DEV = "52ab3f9e"
$PKG = "com.ff.launcher"
$OUT = "docs\m1"
New-Item -ItemType Directory -Force $OUT | Out-Null
function A { adb -s $DEV @args }

# Screen geometry (calibrate if wm size differs)
$W = 1080; $H = 2248
```

### 0.2 Device and Build Facts

```powershell
A shell getprop ro.build.version.release | Out-File $OUT\device.txt -Encoding utf8
A shell getprop ro.miui.ui.version.name   | Out-File $OUT\device.txt -Append -Encoding utf8
A shell wm size                           | Out-File $OUT\device.txt -Append -Encoding utf8
A shell dumpsys package $PKG | Select-String "versionName|versionCode|firstInstallTime" |
  Out-File $OUT\device.txt -Append -Encoding utf8
Get-Item app\build\outputs\apk\debug\app-debug.apk | Select-Object Length
```

Confirm `versionName` is `1.2.0`. If not, re-run `adb install -r` before continuing.

### 0.3 Discover Log Tags

```powershell
Get-ChildItem app\src\main\java -Recurse -Filter *.kt |
  Select-String -Pattern 'Log\.(d|i|w|e)\(|TAG\s*=' |
  Out-File $OUT\log_tags_in_source.txt -Encoding utf8
```

Fill in this table from the output. Any row with "none" is a logging gap and becomes a fix item:

| Area | Tag Found | Logs User Actions? |
|---|---|---|
| `MainActivity` / `AppEntryActivity` | | |
| `LauncherViewModel` | | |
| Trigger enabled toggle | | |
| `QuickActionPreferencesRepository` | | |
| `QuickActionButton` (tap, drag, long-press) | | |
| Flashlight toggle / torch callback | | |
| `QuickActionPickerSheet` (selection) | | |
| Widget bind / clear | | |

### 0.4 Prepare a Clean, Logged Session

```powershell
A logcat -G 16M                  # enlarge the buffer so nothing is dropped
A logcat -b all -c
A shell am force-stop $PKG
A shell am start -W -n "$PKG/.MainActivity" | Out-File $OUT\cold_start.txt -Encoding utf8
Start-Sleep 3
$appPid = (A shell pidof $PKG).Trim()
"pid at start: $appPid" | Out-File $OUT\pids.txt -Encoding utf8

function Mark-Pid { $p = (A shell pidof $PKG).Trim(); "pid: $p  @ $(Get-Date -Format s)" | Out-File $OUT\pids.txt -Append -Encoding utf8 }
```

Record `TotalTime` from `cold_start.txt` (budget $\le 800\text{ ms}$). Call `Mark-Pid` whenever the app is restarted.

### 0.5 Helper Input Functions

```powershell
function Tap($x,$y)                     { A shell input tap $x $y }
function Swipe($x1,$y1,$x2,$y2,$ms=300) { A shell input swipe $x1 $y1 $x2 $y2 $ms }
function LongPress($x,$y,$ms=900)       { A shell input swipe $x $y $x $y $ms }
function Snap($name)                    { A shell screencap -p /sdcard/$name.png; A pull /sdcard/$name.png $OUT\$name.png | Out-Null }
function UiDump($name)                  { A shell uiautomator dump /sdcard/$name.xml | Out-Null; A pull /sdcard/$name.xml $OUT\$name.xml | Out-Null }
```

Use `UiDump` and `Snap` to locate exact control coordinates rather than guessing.

---

## 1. Functional Checklist

Mark each case `[ ]` $\rightarrow$ `[PASS]` or `[FAIL]`, and attach a screenshot or log excerpt for every failure. Use `A logcat -d | Select-String <tag>` to confirm expected log lines.

### 1.1 Process-Death Helper (Used by H5, Q9)

```powershell
# am kill only works on backgrounded processes: put the app in the background first
A shell am start -a android.settings.SETTINGS | Out-Null
Start-Sleep 2
A shell am kill $PKG
A shell am start -W -n "$PKG/.MainActivity" | Out-Null
Start-Sleep 2; Mark-Pid
```

### T1. Show/Hide Second Widget Screen

| ID | Steps | Expected | Result |
|---|---|---|---|
| H1 | Open settings (tuning sheet), then switch **Second widget screen** OFF | Edge pill disappears immediately, with no restart needed. Log emitted. | [ ] |
| H2 | With it OFF, swipe from the right edge: `Swipe 1075 1100 300 1100` | Nothing opens. No crash. | [ ] |
| H3 | Switch it ON again | Pill returns at the previously configured side, position, and size | [ ] |
| H4 | Open the deck after H3 | Widgets in all 3 slots are identical to before OFF, including slot sizes (S/M/L) | [ ] |
| H5 | Set OFF, run process-death helper (1.1), then relaunch | Still OFF after process death | [ ] |
| H6 | Set OFF, reboot, then open the launcher | Still OFF. After switching ON, widgets are intact. | [ ] |
| H7 | Toggle OFF/ON 10 times quickly | No flicker, no leak (check PSS in section 3), no E-level log | [ ] |
| H8 | While OFF, check the settings sheet | Clear way to turn it back on, with no dead end | [ ] |

### T2. Quick Action Button

| ID | Steps | Expected | Result |
|---|---|---|---|
| Q1 | Settings $\rightarrow$ Quick action $\rightarrow$ ON | Button appears on the chosen edge with the default action. Log emitted. | [ ] |
| Q2 | Pick each preset in turn: Camera, Alarm, Flashlight, AI Assistant | Icon updates. Tap launches the correct target. Log per launch. | [ ] |
| Q3 | Pick a custom app via search (e.g., Chrome) | Icon shows the app's icon or fallback. Tap launches it. | [ ] |
| Q4 | Search inside picker with partial/uppercase text, and a Chinese name if installed | Results are correct and the list scrolls smoothly | [ ] |
| Q5 | Drag the button vertically | Follows finger and persists on release. Verify after Q9. | [ ] |
| Q6 | Change edge (left $\leftrightarrow$ right) | Button moves live, with no stale ghost | [ ] |
| Q7 | Change size with the slider | Applies live. Only one DataStore write at gesture end (preview/commit). | [ ] |
| Q8 | Toggle the button OFF | Disappears and config is kept. Turning ON restores same app and position. | [ ] |
| Q9 | Process-kill (1.1), then relaunch | Button config persists (app, edge, bias, size) | [ ] |
| Q10 | Uninstall chosen custom app: `A shell pm uninstall <pkg>` | No crash. Button falls back gracefully. Logs a warning, not an error. | [ ] |
| Q11 | Hide the chosen app via launcher's "hide" action | Button still launches it, or behaves as defined. Record actual behavior. | [ ] |
| Q12 | Pick a work-profile app (if a work profile exists) | Launches in the right profile (`componentKey` with `userSerial`) | [ ] |
| Q13 | Haptic feedback on tap and drag start | Felt on tap and on drag start | [ ] |

### T3. Flashlight

| ID | Steps | Expected | Result |
|---|---|---|---|
| F1 | Set quick action to Flashlight, then tap | Torch on, icon in the ON state | [ ] |
| F2 | Tap again | Torch off, icon OFF | [ ] |
| F3 | Turn torch on from notification shade or Control Center | Launcher icon follows via `registerTorchCallback` | [ ] |
| F4 | Turn it off from the shade | Icon follows | [ ] |
| F5 | Open stock camera (holds camera), then return and tap torch | Handled with no crash. Warning log at most. | [ ] |
| F6 | Leave torch on, press Home, go to another app, and return | State is consistent with the real torch | [ ] |
| F7 | Switch quick action away from Flashlight while torch is on | No stuck torch, callback unregistered, no leaked-callback warning | [ ] |

### T4. Input-Conflict Tests (Right Edge Unless Stated)

| ID | Steps | Expected | Result |
|---|---|---|---|
| C1 | Quick action and edge trigger on same side. Tap and drag button. | Button wins in its own bounds. Edge pill is not triggered by accident. | [ ] |
| C2 | Swipe from the edge just above and below the button | Deck opens normally | [ ] |
| C3 | Drag A–Z scroller top to bottom: `Swipe 1060 400 1060 2200 800` | Highlight clamps on the last letter and never opens settings | [ ] |
| C4 | Use scroller while quick action is on the same edge | No overlap. If there is, record the overlap geometry. | [ ] |
| C5 | Open search panel and keyboard | Quick action button hides or does not block input field. Record which. | [ ] |
| C6 | Open Manage Favorites overlay | Button does not cover checkboxes or drag handles | [ ] |
| C7 | Android back gesture from the same edge as the button | System back gesture still works (`systemGestureExclusionRects` limits). | [ ] |

### T5. Rotation and System UI

| ID | Steps | Expected | Result |
|---|---|---|---|
| R1 | Rotate: `A shell settings put system accelerometer_rotation 0; A shell settings put system user_rotation 1` | Button stays on screen, no duplicate, config intact | [ ] |
| R2 | Return to portrait: `user_rotation 0` then `accelerometer_rotation 1` | Same state as before | [ ] |
| R3 | Switch to 3-button nav, then back to gestures (MIUI: Settings $\rightarrow$ Full screen) | Button stays above the nav bar and is not clipped | [ ] |
| R4 | Change display size/font: `A shell wm density 480` then `A shell wm density reset` | Layout holds. Always run `reset` afterwards. | [ ] |
| R5 | Enter split-screen if MIUI allows it | No crash. Button position is sane. | [ ] |

### T6. Missing-Target Edge Cases

| ID | Steps | Expected | Result |
|---|---|---|---|
| E1 | Pick Camera preset with camera app disabled: `A shell pm disable-user --user 0 <camera pkg>` | Toast or fallback, no crash. Re-enable app afterwards. | [ ] |
| E2 | Same for the alarm app | Same | [ ] |
| E3 | Same for the assistant (if none is installed) | Same | [ ] |
| E4 | Put a quick action at an extreme bias (top, bottom) | Button stays fully visible, clear of status bar and nav bar | [ ] |
| E5 | Tap the button 20 times rapidly | One launch per tap, with no ANR or duplicate activities | [ ] |

### T7. Regression of Earlier Work

| ID | Steps | Expected | Result |
|---|---|---|---|
| G1 | Open Manage Favorites. Drag-reorder, use arrows, toggle checkboxes. | Order and pins persist. Vertical scrollbar works. | [ ] |
| G2 | Smart Header: switch each mode (Combined, Clock, Calendar, Weather, Hidden) and units | Persists and renders correctly | [ ] |
| G3 | Remove a widget from a deck slot, then re-add bundled clock, calendar, weather | Works. If it doesn't, log it as a gap for M2/C5. | [ ] |
| G4 | Long-press an app, then use Favorite, Hide, App info, and Uninstall | Each works | [ ] |
| G5 | Overscroll pull-up opens the app search | Works consistently | [ ] |
| G6 | Home $\rightarrow$ another app $\rightarrow$ Home many times | Stable, with no extra tasks. `singleTask` holds. | [ ] |

---

## 2. Scripted Soak Session (~10 min)

Start the resource sampler (Section 3) first, then run:

```powershell
function Soak($rounds) {
  for ($i = 1; $i -le $rounds; $i++) {
    # open deck from the right edge, then close
    Swipe ($W-5) 1100 300 1100 250; Start-Sleep 2
    A shell input keyevent KEYCODE_BACK; Start-Sleep 1
    # drag the scroller down, then up
    Swipe ($W-20) 400 ($W-20) 2200 700; Start-Sleep 1
    Swipe ($W-20) 2200 ($W-20) 400 700; Start-Sleep 3
    # pull-up search, then dismiss
    Swipe 540 1900 540 700 250; Start-Sleep 2
    A shell input keyevent KEYCODE_BACK; Start-Sleep 1
    # background and foreground
    A shell input keyevent KEYCODE_HOME; Start-Sleep 1
    "round $i done" | Out-File $OUT\soak_progress.txt -Append -Encoding utf8
  }
}
Soak 20
```

Interleave manual quick action taps and H-case toggles between rounds.

---

## 3. Resource Capture & Leak Checks

Run in a second PowerShell window during idle and active soak testing:

```powershell
function Sample-Resources($minutes, $everySec = 30) {
  $end = (Get-Date).AddMinutes($minutes)
  while ((Get-Date) -lt $end) {
    $ts = Get-Date -Format s
    $p  = (A shell pidof $PKG).Trim()
    "=== $ts pid=$p ===" | Out-File $OUT\res_mem.txt -Append -Encoding utf8
    A shell dumpsys meminfo $PKG | Select-String "TOTAL|Java Heap|Native Heap|Graphics|Views|Activities" |
      Out-File $OUT\res_mem.txt -Append -Encoding utf8
    "=== $ts ===" | Out-File $OUT\res_cpu.txt -Append -Encoding utf8
    A shell dumpsys cpuinfo | Select-String $PKG | Out-File $OUT\res_cpu.txt -Append -Encoding utf8
    Start-Sleep $everySec
  }
}

# 1) Idle baseline: screen on, home visible, no interaction
Sample-Resources 5 30
# 2) Active: run during Soak 20
Sample-Resources 10 30
```

Before and after the soak:

```powershell
A shell dumpsys gfxinfo $PKG reset | Out-Null            # run before soak
# ... after soak:
A shell dumpsys gfxinfo $PKG | Out-File $OUT\gfxinfo.txt -Encoding utf8
A shell dumpsys batterystats $PKG | Out-File $OUT\batterystats.txt -Encoding utf8
A shell dumpsys meminfo $PKG | Out-File $OUT\meminfo_end.txt -Encoding utf8
```

- **Leak check:** Compare `Views` and `Activities` counts from start to end of the soak (must return near starting values).
- **Torch/widget leak:** Toggle H7 ($\times 10$) and F-cases, then compare PSS. Unrecovered growth $> 10\%$ is a failure.

---

## 4. Log Capture, Triage, Persistence & Reboot

### 4.1 Dump and Extract Logs

```powershell
A logcat -b all -d -v threadtime | Out-File $OUT\logcat_full.txt -Encoding utf8

# 1) Hard failures, regardless of PID
Select-String -Path $OUT\logcat_full.txt -Pattern "FATAL EXCEPTION|AndroidRuntime|ANR in|am_anr|StrictMode|Process .* has died|Force finishing" |
  Out-File $OUT\log_critical.txt -Encoding utf8

# 2) All E and W lines
Select-String -Path $OUT\logcat_full.txt -Pattern "^\S+\s+\S+\s+\d+\s+\d+\s[EW]\s" |
  Out-File $OUT\log_ew_all.txt -Encoding utf8

# 3) Narrow to the app: its PIDs (from pids.txt) or the package name
$pidList = Get-Content $OUT\pids.txt | Select-String "\d+" | ForEach-Object { $_.Matches[0].Value } | Select-Object -Unique
$pattern = ($pidList | ForEach-Object { "\s$_\s+\d+\s[EW]\s" }) -join "|"
$pattern = "$pattern|$PKG"
Select-String -Path $OUT\log_ew_all.txt -Pattern $pattern | Out-File $OUT\log_ew_app.txt -Encoding utf8

# 4) Frame problems
Select-String -Path $OUT\logcat_full.txt -Pattern "Skipped \d+ frames|Davey|Choreographer" |
  Out-File $OUT\log_frames.txt -Encoding utf8
```

### 4.2 Triage Rules & Log Coverage Check

Classify every distinct line in `log_ew_app.txt` and `log_critical.txt` as `FIX`, `ACCEPT (with reason)`, or `SYSTEM NOISE`:
- Any `FATAL EXCEPTION`, ANR, or crash is a **release blocker**.
- Any app-tagged `E` line is a **FIX**.
- Any app-tagged `W` line is a **FIX**, unless it is an intended graceful fallback (Q10, E1–E3), which must be documented as `ACCEPT`.
- Verify that every user action (Launcher start/resume, Second screen toggle, Quick action toggle/select/launch/move, Torch toggle/callback, Widget bind/clear, Favorites reorder, Trigger config commit) emitted a log line. Unchecked actions become "add logging" tasks.

### 4.3 Persistence Verification & Reboot Test (Run Last)

```powershell
# Verify DataStore and Room files exist on disk
A shell run-as $PKG ls -R files/datastore databases 2>&1 | Out-File $OUT\persisted_files.txt -Encoding utf8

# Reboot test
A reboot
A wait-for-device; Start-Sleep 40
A shell input keyevent KEYCODE_WAKEUP; A shell input keyevent 82
A shell am start -W -n "$PKG/.MainActivity" | Out-Null; Mark-Pid
```

Re-check H6, Q9, and confirm the default-launcher role is still held when pressing Home.

### 4.4 M1 Report Template (`docs/m1-report.md`) & Exit Criteria

```markdown
# M1 Report: v1.2.0 -> v1.2.1
Date / device / Android / MIUI:
Build: versionName, commit, APK size

## Results
T1 Hide second screen:   _/8
T2 Quick action:         _/13
T3 Flashlight:           _/7
T4 Conflicts:            _/7
T5 Rotation/System UI:   _/5
T6 Missing targets:      _/5
T7 Regression:           _/6

## Resource numbers (vs budget)
## Log audit: critical=_, app E=_, app W=_ (fixed/accepted/noise)
## Log coverage gaps:
## Bugs found: ID | severity | case | repro | status
## Decisions: (e.g., Q5 button hides over keyboard: yes/no)
```

**M1 Exit Criteria:**
- [ ] All cases in T1–T7 pass, or each failure has a fixed bug or documented rationale
- [ ] 0 crashes, 0 ANRs, and 0 app-tagged error logs in the full session
- [ ] Every app-tagged warning is fixed or documented as `ACCEPT`
- [ ] Log coverage table is complete, with missing logs added
- [ ] Resource budgets met and saved in `docs/perf-baseline.md`
- [ ] Reboot and process-death persistence confirmed
- [ ] Fixes committed, `CHANGELOG.md` updated, version bumped to **1.2.1**

---

# Part 3: Stage 3 Development Plan (10-Step Roadmap)

> **Stage 3 Vision ("Polish and Personalize"):** Make v1.2.x rock-solid, then deepen customization without breaking the "ultra-light" promise.

## 1. Stage 3 Goals

| # | Goal | Success Measure |
|---|---|---|
| G1 | Verify and stabilize v1.2.0 | 0 crashes/ANRs and 0 error-level logs in a 30-min scripted session (completed via Part 2 M1) |
| G2 | Deepen quick action customization | Multiple quick actions, plus gesture variants |
| G3 | Deepen home customization | User controls layout, icon size, and label visibility |
| G4 | Data safety | Backup and restore of all settings works across reinstall |
| G5 | Release readiness | Minified release build, automated tests, signed APK |
| G6 | Stay lightweight | All Global Resource Budgets met and documented |

---

## 2. Workstreams and Features

### WS-A. Stabilization and QA of v1.2.0 (Priority: Highest)
- Executed via the **Part 2: M1 Test Script and Checklist** above (Functional test matrix A1, Log audit pass A2, and Resource re-baseline A3 in `docs/perf-baseline.md`).

### WS-B. Quick Action 2.0 (Customization)

| ID | Feature | Description | Notes |
|---|---|---|---|
| B1 | Multiple quick actions | Allow 1 to 3 buttons, or one button that fans out into a small radial/stack menu on tap | Cap at 3 to 5 for simplicity and performance |
| B2 | Gesture variants | Tap = action 1, long-press = action 2, swipe-up = action 3 | Reuses the existing gesture code |
| B3 | Action types | App, system tool (camera, alarm, flashlight, assistant), plus new: Do Not Disturb toggle, Wi-Fi/Bluetooth settings shortcuts, open URL (e.g., web AI chat), dial a number | Only types that need no dangerous permissions |
| B4 | Custom label and icon | Pick the app icon, a built-in vector icon, or emoji/letter badge | Keep the hand-coded vector set small |
| B5 | Appearance | Opacity, auto-hide when idle, auto-dock to the edge, hide when the keyboard is open | |
| B6 | Visibility rules | Show only on the home screen, or also over the widget deck | |
| B7 | Reset and preset packs | One-tap reset. Suggested packs such as "AI Pack" (Google, Gemini, ChatGPT) and "Utility Pack" | Packs only include apps that are installed |

**Data impact:** Extend `QuickActionPreferencesRepository` from a single config to a list schema, with a migration that maps the existing single config to item #1.

### WS-C. Widget Screen Customization

| ID | Feature | Description |
|---|---|---|
| C1 | Hide/show polish | Short confirmation on hide ("Widgets are saved, re-enable anytime"). Add a re-enable entry point in the settings gear so a hidden screen is never "lost". |
| C2 | Slot count | Choose 1, 2, or 3 slots (the memory of the others is preserved) |
| C3 | Deck background | Choice of transparent, dim scrim, or blur-lite (only if the performance budget allows) |
| C4 | Trigger alternatives | Besides the edge pill, open the deck via a double-tap on empty space or a swipe from the screen edge (opt-in) |
| C5 | Widget re-add helper | Empty slot shows "Add clock / calendar / weather" shortcuts for the bundled providers |

### WS-D. Home and Drawer Customization

| ID | Feature | Description |
|---|---|---|
| D1 | Icon size and label toggle | Small/Medium/Large, labels on or off |
| D2 | Favorites style | List (current), or compact grid option |
| D3 | Sort and grouping | A-Z (current), recently used, most used (locally computed, no tracking) |
| D4 | Linger time | Setting for the 2.5s drawer-reveal linger |
| D5 | Theme options | Follow system light/dark, accent color picker (a few presets only) |
| D6 | Gestures on home | Double-tap to lock screen (via accessibility, opt-in only) or open the notification shade |
| D7 | Settings consolidation | One Settings screen with sections for Home, Widget Deck, Quick Action, Smart Header, Hidden Apps, Backup, and About (replaces scattered sheets) |

### WS-E. Search Upgrade

| ID | Feature | Description |
|---|---|---|
| E1 | Search shortcuts | Typing text offers "Search web" with a user-selectable engine |
| E2 | Pinyin/initials matching | Match initials (e.g., "wx") to Chinese app names using existing ICU/pinyin logic |
| E3 | Quick-calc and unit conversion | Optional, local-only |
| E4 | Settings entry | Gear icon in the search panel that opens Settings |

### WS-F. Data Safety

| ID | Feature | Description |
|---|---|---|
| F1 | Export/import settings | Single JSON file via the system file picker (favorites order, hidden apps, trigger config, quick actions, header prefs, widget slot providers) |
| F2 | Schema versioning | Version field in the export, with forward-compatible parsing |
| F3 | Room migration tests | Required before any DB schema change (DB v1 $\rightarrow$ v2 if needed for usage counts or new tables) |
| F4 | Auto-backup rules | Android auto-backup config includes DataStore and excludes caches |

### WS-G. Release Engineering and Quality

| ID | Task |
|---|---|
| G1 | Enable R8 minify and resource shrinking for release, with keep rules verified (Room, widgets) |
| G2 | Baseline Profile to improve cold start (only if the benchmark shows a gain) |
| G3 | Unit tests: ViewModel logic (bucketing, favorites reorder, quick action resolution), repositories (DataStore defaults and migration) |
| G4 | UI tests: Compose tests for Manage Favorites drag/reorder and the Quick Action picker |
| G5 | Lint and detekt/ktlint (lightweight), with warnings treated as errors for new code |
| G6 | Signing config, versioning scheme, `CHANGELOG` discipline |
| G7 | Cleanup: move the 16 `review_bundle*.xml` files out of the root, and fix the duplicate `HomeScreen` row in the docs |
| G8 | Crash-safe local logging: ring-buffer log file the user can share from Settings (opt-in, no network) |

---

## 3. Milestones and Timeline

Estimates assume a single developer working part-time.

| Milestone | Version | Scope | Duration | Exit Criteria |
|---|---|---|---|---|
| **M1: Verify** | `1.2.1` | WS-A (Part 2 Checklist), plus bug fixes found | 3 to 4 days | Test matrix passes, 0 error logs, perf baseline documented |
| **M2: Settings and Re-entry** | `1.3.0-alpha` | D7 (consolidated settings), E4 (search gear), C1, C5 | 1 week | User can always find and re-add widgets and toggle the deck, with no dead ends |
| **M3: Quick Action 2.0** | `1.3.0-beta` | WS-B (B1 to B4 first, B5 to B7 stretch) | 1.5 weeks | Multi-action and gesture variants work, migration from single config proven |
| **M4: Home Customization** | `1.4.0-alpha` | WS-D (D1 to D5), C2 to C3 | 1.5 weeks | All options persist and apply live, with no jank regression |
| **M5: Data Safety and Search** | `1.4.0-beta` | WS-F, WS-E | 1 week | Round-trip export/import verified after a clean reinstall |
| **M6: Release Hardening** | `1.4.0 RC` | WS-G, full regression, final perf audit | 1 week | Release build passes all tests, budgets met, signed APK |

**Dependencies:** M1 comes before everything. M2 (the consolidated settings screen) must precede M3 and M4 so new options have a unified home.

---

## 4. Risks and Mitigations (Consolidated)

| Risk | Impact | Mitigation |
|---|---|---|
| Xiaomi/MIUI background, overlay, or torch restrictions | Torch/quick action behaves differently from stock Android | Test on the Mi 8 explicitly, and document any MIUI settings users need to change (autostart, "display pop-up") |
| Gesture conflicts (edge trigger, quick action, scroller, system back gesture) | Frustrating input on the right edge | Enforce the Part 1 Section 1.4 edge-zone priority map and verify with T4 conflict tests (C1–C7) |
| Schema changes or uninstalled targets lose user data | Severe UX regression | Keep config on uninstall with warn-level fallback; run Room/DataStore migration tests before release; provide JSON export backup |
| Torch callback leak | Battery/resource drain | Register and unregister explicitly in ViewModel, verified in test F7 |
| Feature creep or icon/library additions bloat APK/RAM | Breaks core ultra-light promise | Enforce the Global Resource Budgets at every milestone; use hand-coded cached vectors; default to cutting features if budgets are threatened |
| Accessibility-based gestures (D6) raise permission/trust concerns | Policy/UX risk | Keep strictly opt-in, explain clearly, and consider deferring |
| Blur/effects cost performance on older devices | Frame jank | Keep optional and default off; gate on measured `dumpsys gfxinfo` frame stats |
| Settings screen becomes too complex | Poor UX | Consolidate into clear sections (D7), use sensible defaults, and provide "Reset to defaults" |

---

## 5. Out of Scope (Deferred)

- Icon pack support, notification badges, and a full app-folder system.
- Cloud sync or user accounts of any kind.
- Any analytics or network features beyond the existing weather call.
- Tablet/foldable-specific layouts.

---

## 6. Immediate Next Actions

1. **Run M1 (Part 2):** Execute the test script, log audit, and resource baseline on v1.2.0, answering the 3 open Stage 2 questions along the way.
2. **Release v1.2.1:** Fix all M1 findings, update `CHANGELOG.md`, and tag `v1.2.1`.
3. **Design Consolidated Settings (D7):** Lay out the unified Settings screen to unblock M2–M4.
4. **Confirm Stage 3 Scope Decisions:**
   - How many quick actions should be supported in Quick Action 2.0 (1 or up to 3)?
   - Is accessibility-based double-tap-to-lock acceptable, or should it be skipped?
   - Should light-theme support be added, or keep the launcher dark-only?