# M1 Verification Report: FF Launcher v1.2.0 → v1.2.1

**Date:** 2026-11-15  
**Device:** Xiaomi Mi 8 (`52ab3f9e`), Screen: 1080×2160, Android 12  
**Build Info:** `versionName = 1.2.1`, `versionCode = 121`  
**APK Size:** **10.58 MB** (Budget: $\le 15.0\text{ MB}$, Target: $\le 12.5\text{ MB}$ — **PASS**)  

---

## 1. Architectural & Implementation Deliverables

| Requirement | Implementation Details | Status |
|---|---|---|
| **Second Screen Hide/Show (Feature A)** | Discrete toggle in Settings; closes deck if open; DataStore `KEY_ENABLED`; Room `widget_slots` intact. Helper text displayed when OFF. | **Implemented & Verified** |
| **Custom Quick Action Button (Feature B)** | Floating button with presets (Camera, Alarm, Flashlight, Assistant) & full app drawer selector; on-screen vertical drag repositioning; DataStore backed. | **Implemented & Verified** |
| **Search Panel & Keyboard Conflict (Open Q1)** | Added `!isAppSearchOpen` in `LauncherRoot.kt`. Quick Action button automatically disappears when search sheet or keyboard is active. | **Implemented & Verified** |
| **Default Edge Allocation (Open Q2)** | Quick Action button defaults to `LEFT` edge; `EdgeTrigger` & Wave Scroller default to `RIGHT` edge. Zero default collision. | **Implemented & Verified** |
| **Safe Torch Lifecycle (Open Q3)** | `CameraManager.TorchCallback` lifecycle tied strictly to ViewModel (`init` register, `onCleared()` unregister). Zero callback leaks. | **Implemented & Verified** |
| **Live Slider Preview** | Vertical bias and button size sliders in `TriggerTuningSheet.kt` now update live on-screen during gesture dragging. | **Implemented & Verified** |
| **Quick Action Reset** | One-tap "Reset to defaults" button added to `TriggerTuningSheet.kt` to restore factory placement and camera preset. | **Implemented & Verified** |
| **Comprehensive Logging** | Structured tags added: `FF/QuickActionPrefs`, `FF/TriggerPrefs`, `FF/QuickActionButton`, `FF/QuickActionPicker`, `LauncherViewModel`. | **Implemented & Verified** |

---

## 2. Test Verification Matrix (Checklist T1 – T7)

### T1. Show/Hide Second Widget Screen
- **H1** (Settings → Second screen OFF removes edge pill): **PASS** (`setTriggerEnabled: enabled=false`, pill omitted from composition)
- **H2** (Swipe right edge while OFF opens nothing): **PASS** (No gesture exclusion rect published)
- **H3** (Switch ON restores pill at configured position): **PASS** (`setTriggerEnabled: enabled=true`)
- **H4** (Widget deck opens with all slot widgets preserved): **PASS** (Room `widget_slots` preserved)
- **H5** (State survives process death): **PASS** (Persisted in `trigger_prefs.preferences_pb`)
- **H6** (State survives reboot): **PASS**
- **H7** (Rapid toggle 10x): **PASS** (Discrete switch directly commits without debounce leak)
- **H8** (Clear re-enable path): **PASS** (Switch remains reachable in Settings with helper message)

### T2. Quick Action Button
- **Q1** (Settings → Quick action ON renders button): **PASS** (Renders circular floating glassmorphic button)
- **Q2** (Presets: Camera, Alarm, Flashlight, Assistant): **PASS** (Hand-coded pure vector icons in `QuickActionIcons.kt`)
- **Q3** (Custom app selection via search): **PASS** (Renders app icon from 128px icon cache)
- **Q4** (Case-insensitive & search filtering): **PASS**
- **Q5** (On-screen vertical dragging): **PASS** (`detectVerticalDragGestures` with live preview and commit on release)
- **Q6** (Edge side Left ↔ Right toggle): **PASS** (Live repositioning with absolute alignment)
- **Q7** (Size slider preview & commit): **PASS** (`previewQuickActionSizeDp` + single commit at gesture end)
- **Q8** (Toggle OFF keeps configuration): **PASS**
- **Q9** (Process kill persistence): **PASS** (Persisted in `quick_action_prefs.preferences_pb`)
- **Q10** (Missing target graceful fallback): **PASS** (Fallback rocket icon / warning toast, no crash)
- **Q11** (Hidden app target): **PASS** (Direct component launch preserved)
- **Q12** (Work-profile app key): **PASS** (Composite `componentKey` with user serial)
- **Q13** (Haptic feedback on tap & drag): **PASS** (`TextHandleMove` on tap/drag, `LongPress` on customize)

### T3. Flashlight
- **F1** (Tap torch preset turns on): **PASS** (`CameraManager.setTorchMode`)
- **F2** (Tap again turns off): **PASS**
- **F3 / F4** (External shade toggle sync): **PASS** (`onTorchModeChanged` callback updates UI state)
- **F5** (Camera contention handling): **PASS** (`CameraAccessException` caught gracefully)
- **F6** (Background/foreground state consistency): **PASS**
- **F7** (No callback leak on lifecycle end): **PASS** (`unregisterTorchCallback` in `onCleared()`)

### T4. Input-Conflict Tests
- **C1** (Button vs Edge trigger same side): **PASS** (Button touch target bounds isolated)
- **C2** (Swipe edge outside button bounds): **PASS**
- **C3** (Wave scroller clamped to last letter): **PASS** (Clamped to avoid accidental jump to settings)
- **C4** (Wave scroller vs Quick action): **PASS** (Left vs Right default edge separation)
- **C5** (Search sheet & keyboard): **PASS** (Button hides when `isAppSearchOpen` is true)
- **C6** (Manage favorites overlay): **PASS** (Button hides during favorites management)
- **C7** (System back gesture): **PASS** (Exclusion rects kept within Android 200dp limit)

---

## 3. Resource & Performance Baseline

| Metric | Budget | Measured v1.2.1 | Result |
|---|---|---|---|
| **APK Size** | $\le 15.0\text{ MB}$ | **10.58 MB** | **PASS** (-4.42 MB under budget) |
| **Idle CPU** | $\approx 0\%$ | $\approx 0.0\text{ - }0.2\%$ | **PASS** |
| **External Dependencies Bloat** | 0 new libs | 0 added (`material-icons-extended` omitted) | **PASS** |
| **Background Services** | 0 services | 0 background services | **PASS** |
| **Data Safety** | 100% survival | 100% persisted across process death | **PASS** |

---

## 4. Stage 3 Roadmap Transition (Next Milestones)

With Milestone M1 verified and hardened in v1.2.1:
1. **Milestone M2 (v1.3.0-alpha)**:
   - Consolidated Settings Screen (D7): Unified screen replacing scattered sheets.
   - Search Header Settings Gear (E4): Quick access to settings from search panel.
   - Bundled Widget Re-Add Shortcuts (C5): Easy re-add buttons in empty widget slots.
2. **Milestone M3 (v1.3.0-beta)**:
   - Quick Action 2.0 (B1–B4): Multiple quick action buttons (1 to 3) and gesture variants.
3. **Milestone M4 (v1.4.0-alpha)**:
   - Home Customization (D1–D5): Icon sizing, label toggles, and compact layout options.
