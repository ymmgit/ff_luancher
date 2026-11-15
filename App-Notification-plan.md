# Plan: Notification Arrow Indicator, Inline Panel, System-Level Dismiss

Target version: 1.3.0 (core), later phases in 1.3.x / 1.4.0
Package: com.ff.launcher | Test device: Xiaomi Mi 8 (52ab3f9e)
Status: Design locked, ready for P0

---

## 1. Goal and Principles

**Goal:** When an app (Gmail, WhatsApp, etc.) has notifications, show a small down-arrow
next to its name. Tapping the arrow opens a panel with those notifications. The user
swipes a notification away to dismiss it at system level (it also disappears from the
Android notification shade) and, where the app supports it, mark it as read.

| Principle | Meaning |
|---|---|
| Calm by default | One small arrow per app. No numbers, no dots, no looping animation. |
| Glanceable | See which apps need attention in under a second. |
| Fast to act | Read, open, or clear from the home screen without opening the app. |
| Bounded | At most 3 cards visible by default. Many notifications never flood the screen. |
| Opt-in | Off until the user grants notification access. Zero cost if never enabled. |
| Private | Counts and keys only in memory. Message text is read on demand and never stored or logged. |
| Lightweight | No database, no polling, no network, no new dependencies. |

---

## 2. Locked Decisions

| # | Decision | Result |
|---|---|---|
| D1 | Swipe behavior | Left and right are identical: dismiss at system level, and mark as read if the notification offers that action |
| D2 | Indicator | Down arrow only. No number, no dot. |
| D3 | Panel height | 3 cards, then a "Show N more" row for the rest |
| D4 | Mark-as-read setting | "Also mark as read when dismissing", default ON, user can turn off |
| D5 | Hide message text | Included as a setting, default OFF |
| D6 | Notified-apps strip | Deferred to an optional later phase (P4) |

---

## 3. Reference: How Other Launchers Handle It

Not verified against current versions. Treat as design input.

### Pixel Launcher (AOSP Launcher3)
| Point | Confidence |
|---|---|
| Shows a plain notification dot on the icon, no count on the home screen | High |
| Long-press popup has a notification header with the count and cards that can be swiped away or tapped to open | High |
| Data comes from a NotificationListenerService inside the launcher. Third-party launchers need the user to grant Notification access. | High |
| Ignores ongoing notifications and group summaries, and respects the per-channel "allow notification dot" setting | Medium to high |

Adopted: binary indicator for calm, respect channel badge settings (`Ranking.canShowBadge()`),
swipe-to-dismiss cards.
Different: Pixel hides the list behind a long-press. We use a visible tap target (the arrow)
for discoverability and one-handed speed.

### Niagara Launcher
Optional notification indicators and a notification view, both dependent on Notification
access (medium confidence). Exact visuals and gestures: low confidence, closed source.

### How to confirm (optional, about 30 minutes)
1. AOSP Launcher3 source: search for `NotificationListener`, `NotificationInfo`, `BadgeInfo`,
   and the popup notification container classes. File names change between versions.
2. Install Niagara on the Mi 8, enable its notifications, send yourself a Gmail and a WhatsApp
   message, and note: indicator position, how the list opens, whether swipe dismisses in the shade.

Conclusion from the field: every launcher that shows notification state uses a listener
service and an opt-in permission. There is no listener-free approach.

---

## 4. Feasibility and Constraints

| Item | Detail | Handling |
|---|---|---|
| Mechanism | `NotificationListenerService` is the only cross-app method | Build on it |
| Permission | "Notification access" is a special settings toggle, not a runtime dialog | Explainer screen, then deep link to system settings |
| Android 13+ sideload | Sideloaded APKs may have the toggle greyed out until "Allow restricted settings" is set in App info | Show exact steps in the permission flow. Not an issue on the Mi 8 (Android 10/11). |
| Process residency | While access is granted, the system binds the service and keeps the process alive. This is an exception to the earlier "no always-running services" rule. | Deliberate, measured exception with a resource budget (Section 10) |
| MIUI | Aggressive battery or autostart settings can unbind the listener | Rebind logic and an in-app help note |
| Store policy | Play Store requires a justified core purpose for listener use. Launcher notification state is legitimate. | Keep the data-use statement accurate |
| minSdk 26 | Semantic mark-as-read actions exist from API 28 | On API 26-27, swipe = dismiss only |

---

## 5. Behavior Specification

### 5.1 What counts toward the arrow

Notifications are tracked per **package + user profile** (matches `componentKey` /
work-profile isolation). The arrow shows when an app has at least one **eligible** notification.

| Rule | Default |
|---|---|
| Ignore ongoing notifications and `FLAG_NO_CLEAR` (music, navigation, downloads, foreground services) | Always |
| Ignore categories: progress, transport, service, system, status | Always |
| Ignore the launcher's own notifications | Always |
| Ignore group summaries when children exist. If only a summary exists, treat it as one item. | Always |
| Ignore notifications whose channel disallows badges (`Ranking.canShowBadge()`) | Always |
| Ignore silent notifications (importance LOW or MIN) | Default on. Setting: "Include silent notifications". |
| Ignore muted apps | User-controlled |
| Ignore notifications from apps with no launcher entry | Always |

Because only dismissible items are eligible, the arrow never leads to an empty or
undismissable panel.

### 5.2 Edge cases
- An app with several launcher entries shows the arrow on each entry for that package.
- Notification arrives while home is open: arrow fades in (150ms).
- Dismissed in the shade or by the source app: arrow and panel update immediately.
- Access revoked or listener disconnected: arrows disappear, settings shows a re-enable hint, no crash.
- Do Not Disturb: the system already lowers importance, so the silent rule handles most cases.

---

## 6. UX Specification

### 6.1 Arrow
| Aspect | Spec |
|---|---|
| Glyph | Chevron-down, 14-16dp, a hand-coded `ImageVector` (no icon library) |
| Color | Accent color, single weight |
| Position | Immediately after the app label **(verify against the row layout)** |
| Hit area | 48dp, a separate tap target. Tapping the row still launches the app. |
| Motion | 150ms fade in/out. No pulse or bounce. Skipped if animation scale is 0. |
| Open state | Rotates to point up. Tapping again collapses the panel. |
| Accessibility | Row description "Gmail, has notifications". The arrow has the action "Show notifications". |

### 6.2 Inline panel
| Aspect | Spec |
|---|---|
| Placement | Expands inline directly under the tapped row (accordion). One panel open at a time. |
| Visible cards | The 3 newest: title, 1-2 lines of text, relative time ("2m") |
| More than 3 | A "Show N more" row. Tapping expands into an internally scrolling area capped at about 5 cards tall. The row becomes "Show less". |
| Clear all | A small text button in the footer when there are 2 or more cards. Dismisses every eligible notification for that app. |
| Tap a card | Opens its content intent (the conversation or email). Auto-cancel behavior is respected. |
| Auto-close | When the last card is gone, when the home folds, or when the user leaves the screen |
| Style | Rounded, semi-transparent cards matching the Manage Favorites floating-box style |
| Animation | A single `animateContentSize`. No per-card animation except swipe motion. |
| Scroll safety | If the panel opens near the bottom, scroll just enough to keep it fully visible |

### 6.3 Swipe gesture
- Swipe left or right past about 40% of the card width to commit. Otherwise it springs back.
- Revealed background text: "Mark read" if the notification has a mark-as-read action and the setting is on, otherwise "Dismiss". It never promises more than will happen.
- Haptic tick on commit using the existing haptic helper.
- No undo. Android has no API to restore a cancelled notification.
- Implementation: Material 3 `SwipeToDismissBox` (already available, no new dependency).

### 6.4 Where the arrow appears
| Surface | Arrow and panel |
|---|---|
| Home favorites | Yes |
| A-Z drawer rows | Yes |
| Search results and recent apps | Yes |
| Manage Favorites overlay | No (keep editing clean) |
| Quick action button | No |

Known limitation: with the home collapsed, notifications from unpinned apps are invisible
until the drawer is revealed. The optional notified-apps strip (P4) addresses this.

### 6.5 FF Launcher interactions
- **Edge conflicts:** The right edge is shared by the wave scroller, edge trigger and quick action
  button. The arrow sits beside the label, and panel cards have horizontal padding. Horizontal swipes
  must not start inside scroller or trigger zones.
- **Home fold timer:** An open panel pauses the 2.5s fold timer. It resumes when the panel closes.
- **List stability:** Lazy list items use stable keys (`componentKey`) so expanding one row doesn't disturb others.
- **Overscroll search:** Verify horizontal card swipes don't interfere with the pull-up search gesture.

---

## 7. System-Level Dismiss and Mark as Read

### 7.1 Dismiss (fully supported)
- `cancelNotification(key)` removes one notification. `cancelNotifications(keys)` handles Clear all.
- The notification disappears from the Android shade as well.
- The reverse direction also works: dismissal in the shade triggers `onNotificationRemoved`, which updates the store, so the arrow and panel update at once.

### 7.2 Mark as read (best effort)
Android has **no universal mark-as-read API**. Cancelling does not tell the source app a chat
was read. Some apps attach a "Mark as read" action, which on API 28+ can carry
`Notification.Action.SEMANTIC_ACTION_MARK_AS_READ`.

Flow for one swipe:
1. If setting D4 is on, the API level is 28 or higher, and the notification has a mark-as-read action, fire that action's `PendingIntent`.
2. Call `cancelNotification(key)`.
3. If there is no such action, only step 2 happens.
4. `onNotificationRemoved` fires and the store updates.

Failure handling:
- If the `PendingIntent` is cancelled or throws, still cancel the notification and log a warning with no content.
- Apps that cancel the notification themselves after the action make step 2 a harmless no-op.

Caveats:
- In chat apps, firing mark-as-read can send **read receipts** (for example WhatsApp blue ticks). Setting D4 lets the user turn this off, making every swipe a plain dismiss.
- Gmail may offer only archive and delete actions. P0 records the actual result.
- Without the app's own action, the chat remains unread inside the app, the same as swiping in the shade.

---

## 8. Architecture

### 8.1 Components
| Component | Responsibility |
|---|---|
| `BadgeNotificationListener` (`NotificationListenerService`) | Receives posted/removed/ranking events, applies the eligibility rules, updates the store. Also serves on-demand actions: fetch, dismiss, markRead, launch. No UI, no Room, no `AppRepository`. |
| `NotificationBadgeStore` (app-scoped, in memory) | `StateFlow<Map<BadgeKey, Set<String>>>` holding **notification keys only** per package + user. The single source of truth. |
| `BadgePreferencesRepository` (DataStore) | Settings (Section 9) and the muted set |
| `LauncherViewModel` | Combines store and preferences into per-app lookups. Holds `expandedBadgeKey`, the transient card list for the open panel, access status, and action functions. |
| `NotificationArrow` composable | Stateless arrow with open/closed rotation |
| `NotificationPanel` composable | Cards, `SwipeToDismissBox`, Show more/less, Clear all |
| `NotificationAccessHelper` | Checks enabled listeners, builds the settings intents |

### 8.2 Data flow
```
System notification event
  -> BadgeNotificationListener (filter, track keys per package+user)
  -> NotificationBadgeStore (distinctUntilChanged, debounce ~200ms)
  -> LauncherViewModel (combine with preferences: scope, mute, silent)
  -> Row composable reads its own app's state -> NotificationArrow
Tap arrow -> ViewModel asks listener to fetch cards for that app's keys
  -> transient card list -> NotificationPanel
Swipe -> ViewModel -> listener: (mark-read action) + cancelNotification(key)
  -> onNotificationRemoved -> store -> UI
```

### 8.3 Models (conceptual)
- `BadgeKey`: packageName + userSerial.
- Store value: set of notification keys.
- `NotificationCard` (transient, never persisted): key, title, text, time, optional small icon, `isDismissible`, `hasMarkReadAction`.

### 8.4 Listener behavior
- **onListenerConnected:** read active notifications once to rebuild all key sets. No persistence needed, because state is rebuilt after process death or reboot.
- **onNotificationPosted / Removed / RankingUpdate:** recompute only the affected package, then publish.
- **onListenerDisconnected:** call `requestRebind`, and mark the store "unknown" so stale arrows aren't left showing.
- **On-demand content:** use `getActiveNotifications(keys)` for the panel's keys only. No full scan.
- **Service handle:** keep a weak reference for on-demand actions. When it's null, the UI shows "Notification access paused. Tap to reconnect".
- **Manifest:** service declared with `BIND_NOTIFICATION_LISTENER_SERVICE` permission and the listener intent filter. No other new permissions.

### 8.5 Persistence
- Settings and the muted set in DataStore (string set for muted apps).
- No Room change and no migration.
- Counts and keys are never persisted.

---

## 9. Settings

New "Notifications" section in the settings sheet. Also reachable from the search-panel gear
(per your earlier suggestion).

| Setting | Options | Default |
|---|---|---|
| Notification arrows | On / Off | Off until access is granted |
| Also mark as read when dismissing | On / Off | On |
| Hide message text in panel | On / Off | Off |
| Scope | All / Pinned only / Chosen apps | All |
| Include silent notifications | On / Off | Off |
| Muted apps | List with remove | Empty |
| Notified-apps strip (P4) | On / Off | Off |
| Reset to defaults | Button | |

Long-press menu on an app gains "Mute notifications" / "Unmute notifications".

### Permission flow
1. Entry points: the Notifications section in settings, plus an optional dismissible card on home the first time. No forced onboarding step.
2. Explainer screen before the system page: what it does ("Shows an arrow on apps with notifications and lets you read and clear them"), what it doesn't ("Nothing leaves your phone, and message text isn't stored"), and the Android 13+ restricted-settings steps.
3. Button opens `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` (or the detail page for our component on API 30+).
4. On return (onResume), re-check access. Success shows "Notification arrows are on". Otherwise show a gentle hint and a retry button. Never nag.
5. Turning off: the in-app switch stops processing and clears state. Fully removing the binding requires revoking access in system settings, so provide a button to that page. P0 checks whether disabling the service component is a cleaner "off".

---

## 10. Performance and Resource Strategy

| Item | Strategy |
|---|---|
| Residency | The main cost. Measure in P0 and gate release on it. |
| Event handling | O(1) per event. Read only package, user, flags, category, importance and key. Don't parse extras or load icons. |
| Bursts | Debounce store updates (~200ms) so 50 notifications cause 1-2 UI updates |
| Recomposition | Each row reads only its own app's state. A change recomposes one row. |
| Panel | Lazy-composed, costs nothing when closed. Content fetched on tap only. |
| Assets | One cached vector arrow. No bitmaps. |
| Startup | The listener must not initialize the UI stack. Keep `Application.onCreate` light **(verify)**. |
| Off state | Feature off or never enabled: zero listener cost |

| Metric | Budget |
|---|---|
| Idle CPU with listener bound | about 0% |
| Idle PSS delta vs. pre-feature baseline | Set an explicit ceiling after the P0 measurement |
| 50-notification burst | 1-2 UI updates, no jank |
| APK size | No meaningful change (still at most 15 MB) |
| Disk or network from this feature | None |

---

## 11. Privacy

- Store holds notification keys and package names only.
- Titles and text are read only while a panel is open and live only in transient panel state, cleared on close.
- Honor `VISIBILITY_SECRET`: such notifications show as "Hidden content" with dismiss only.
- "Hide message text" shows the app name and "New notification" instead of content.
- Never log titles, text or extras. Logs may contain package names, counts and outcomes.
- State is cleared when the feature is turned off or access is revoked.
- No analytics. The explainer screen states all of this plainly.

---

## 12. Risks

| Risk | Mitigation |
|---|---|
| MIUI kills or unbinds the listener | `requestRebind`, re-check on resume, help note (enable Autostart, battery "No restrictions") |
| Keys drift from reality (missed removal event) | Full resync on connect and on app resume |
| App has no mark-as-read action | Plain dismiss, UI label reads "Dismiss" |
| Read receipts sent unintentionally | Setting D4, honest swipe label |
| Android 13+ greyed-out toggle | Restricted-settings steps in the flow |
| Summary-only or odd group behavior | Summary rule in 5.1, tested with Gmail and WhatsApp in P0 |
| Horizontal swipe conflicts with scroller, trigger, pull-up search | Padding, zone exclusion, test cases T10-T11 |
| Feature becomes clutter | Arrow only, 3-card cap, conservative defaults, no motion |
| Accidental dismissal (no undo) | 40% threshold, same behavior as the system shade |

---

## 13. Phases and Milestones

Estimates assume a single developer, part time.

| Phase | Scope | Duration | Exit criteria |
|---|---|---|---|
| **P0 Spike** | Minimal listener on the Mi 8. Record: how Gmail and WhatsApp group notifications, which apps expose `SEMANTIC_ACTION_MARK_AS_READ`, whether cancelling a summary removes its children, `canShowBadge` behavior, and the resident cost (RAM/CPU). Optionally compare Niagara. Test whether disabling the service component works as a clean "off". | 1-2 days | Counting rules confirmed, budgets set, "off" mechanism chosen |
| **P1 Core** | Listener, key store, preferences, permission flow and explainer, arrow on home/drawer/search, settings switch | 3-4 days | Arrow appears and clears within about a second of a notification change and stays in sync with the shade. No error logs. |
| **P2 Panel** | Inline panel, 3-card limit with Show more/less, symmetric swipe with system-level dismiss, Clear all, tap to open, fold-timer pause | 4 days | Dismissal in the panel removes it from the shade, and the reverse works |
| **P3 Control** | Mark-as-read on swipe plus setting, per-app mute, scope, silent toggle, hide-text, MIUI help note, rebind hardening | 3 days | Behavior matches Section 7.2, revoking access degrades gracefully |
| **P4 Strip (optional)** | Notified-apps strip for unpinned apps, using the same arrow and panel | 2-3 days | Home is unchanged when off |
| **P5 Hardening** | Full test matrix, resource and log audit, privacy check, CHANGELOG, README notes | 3 days | Budgets met, 0 crashes, 0 app-tagged errors |

Suggested versions: P1-P3 as 1.3.0, P4 as 1.3.x or 1.4.0, P5 before tagging.

---

## 14. P0 Spike Checklist

For each of Gmail and WhatsApp (and one other messaging app if installed):
1. Send 1, then 3, notifications. Record how many `StatusBarNotification` entries appear and whether a summary exists.
2. Record each notification's flags, category, channel importance and `canShowBadge`.
3. List its actions and whether any has `SEMANTIC_ACTION_MARK_AS_READ`. Record action titles only, not content.
4. Fire the mark-as-read action, and note whether the app marks the chat read, and whether the notification cancels itself.
5. Cancel a summary and note whether children disappear.
6. Dismiss in the shade, and confirm `onNotificationRemoved` arrives promptly.
7. Background the app for 10+ minutes and lock the screen, then confirm the listener stays connected on MIUI.
8. Measure idle PSS and CPU with the listener bound versus the pre-feature baseline (the same `dumpsys` method as the M1 plan).

Output: a short findings table that settles the counting rules, the action matrix per app, and the resource ceiling.

---

## 15. Test Plan

| ID | Case | Expected |
|---|---|---|
| T1 | Grant access, then receive a Gmail email | Arrow appears within about 1s |
| T2 | Tap the arrow | Panel shows the email card, arrow rotates up |
| T3 | Swipe a card either direction | Gone from the panel and the system shade |
| T4 | Dismiss in the shade with the panel open | Card disappears, and the arrow clears when none remain |
| T5 | WhatsApp swipe, setting on | Gone from panel and shade. Chat marked read if WhatsApp supplies the action. |
| T6 | Same swipe, setting off | Plain dismiss, chat stays unread |
| T7 | 2 cards | No "Show more", and "Clear all" visible |
| T8 | 8 cards | 3 shown with "Show 5 more". Expanding scrolls internally, and the list isn't pushed off screen. "Show less" returns to 3. |
| T9 | Clear all | All eligible notifications removed, panel closes |
| T10 | Panel near the bottom of the list | Scrolls into view |
| T11 | Horizontal swipe near the scroller and edge trigger zones, and the pull-up search gesture | No accidental triggers |
| T12 | Ongoing notification (music) | No arrow, no card |
| T13 | Silent notification | No arrow by default, and arrow appears with the setting on |
| T14 | Muted app | No arrow. Unmute restores it. |
| T15 | Scope: pinned only, chosen apps | Arrows only where expected |
| T16 | Panel open during drawer linger | The home does not fold |
| T17 | Access revoked while running | Arrows clear, hint shown, no crash |
| T18 | Process kill, then relaunch, and reboot | Keys rebuild on connect, settings persist |
| T19 | Burst of 50 notifications | 1-2 UI updates, no jank |
| T20 | Hide-text on, and a secret-visibility notification | Generic text, content never shown |
| T21 | Logcat audit | No titles, text or extras anywhere |
| T22 | Regression: scroller, favorites, widget deck, quick action button | Unaffected |

Measurement uses the M1 method: `dumpsys meminfo`, `cpuinfo`, `gfxinfo`, `batterystats`, before the feature, with it on and idle, and during bursts.

---

## 16. Acceptance Criteria (Definition of Done)

- All T-cases pass, or each failure has a fix or a documented, accepted behavior.
- Dismiss works at system level in both directions (panel to shade, shade to panel).
- No notification text appears in logs or persisted storage.
- Resource budgets in Section 10 are met and recorded.
- 0 crashes, 0 ANRs, 0 app-tagged error logs in a full session.
- Feature fully off and invisible if the user never enables it.
- CHANGELOG updated, version bumped, README documents the permission and the privacy stance.

---

## 17. Out of Scope

- Numeric or dot badges (arrow only by decision D2).
- Reply-from-launcher, notification history, or notification snoozing.
- Per-channel controls inside FF Launcher (the system channel settings apply).
- Exact unread message counts. The arrow indicates presence, not volume.
- Cloud sync or analytics.