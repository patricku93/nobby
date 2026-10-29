# NoBby — Birth-Control Ring Tracker (v1 design)

Date: 2026-10-29
Status: approved by user (brainstorm session)

## 1. Goal

An Android app that helps the user manage a contraceptive ring: remember when
to remove and insert it, nag hard when overdue, mirror the schedule into the
phone's calendar, and show the countdown on a home-screen widget. Replacement
for the discontinued "Conceptional Ring Reminder" app, with more features and a
polished, customisable look.

Android first. Native Kotlin + Jetpack Compose. Local data only, no account.
UI languages: English + German (follows system language).

## 2. Decisions made during brainstorming

| Topic | Decision |
|---|---|
| Stack | Kotlin 2.x, Jetpack Compose + Material 3, Glance (widget), Room, DataStore, Hilt, WorkManager, AlarmManager, kotlinx-datetime. minSdk 26, targetSdk 35. |
| Regimen | User-defined wear length + pause length in days (defaults 21 + 7). Pause 0 = continuous use (ring swap). |
| Data | Local only (Room + DataStore). No backend, no login. |
| Calendar | Android Calendar Provider (`CalendarContract`). Works with Google, Samsung and any other synced calendar. |
| Theming | 5 curated colour palettes × light / dark / system. Widget follows. |
| Logging | Every scheduled action must be confirmed ("Done"). Overdue = past event without confirmation. History kept. |
| Late confirmation | Dialog asks: shift following dates by the delay, or keep the planned schedule. |
| Notes | Per-day free-text notes in the in-app calendar. |
| Future methods | Generic phase-based schedule model; v1 ships only the Ring configuration. |
| App name | NoBby |

## 3. Time events — catalogue and v1 scope

| Event | Definition | v1 |
|---|---|---|
| Ring removal (`REMOVE`) | End of WEAR phase | yes |
| Ring insertion (`INSERT`) | End of PAUSE phase | yes |
| Ring swap (`SWAP`) | REMOVE + INSERT at the same moment (continuous use, or "skip pause") | yes |
| Overdue | Elapsed time since an unconfirmed event | yes |
| Cycle day / phase | "Day 12 of 28 · Wear week 2" or "Pause day 3" | yes |
| Safety thresholds | Pause > 7 days, or ring worn > 28 days → protection may be reduced (per product label). Setup warns when wear > 28 or pause > 7; overdue notifications switch to "use backup contraception" wording once crossed. | yes |
| Expected withdrawal bleed | ~2–3 days into pause | v2 |
| "Ring came out" 3-hour timer | ≤ 3 h out is fine; longer needs backup | v2 |
| Protection established | 7 days after very first insertion | v2 |
| Supply / refill reminder | Rings in stock | v2 |

## 4. Architecture

Single Gradle module `app`, package `com.nobby.ring`, layered packages:

```
domain/        pure Kotlin, no Android imports — models + schedule engine, JVM unit tested
  model/       Method, Phase, PhaseKind, ScheduledEvent, EventKind, EventLog, ReminderRule, Importance, ThemeSpec, WidgetConfig
  engine/      ScheduleEngine, OverdueCalculator, SafetyEvaluator, ShiftPolicy
data/
  db/          Room: EventLogDao, NoteDao, ReminderRuleDao, CalendarLinkDao
  prefs/       DataStore: MethodConfig (wear/pause/time-of-day/anchor), ThemeConfig, WidgetConfig, OnboardingDone
  calendar/    CalendarRepository over CalendarContract (list calendars, upsert/delete own events, search by title/regex)
  repo/        ScheduleRepository (combines prefs + log → current schedule), NotesRepository, ReminderRepository
notifications/ Channels, ReminderScheduler (AlarmManager exact), AlarmReceiver, BootReceiver, NotificationActions (Done/Snooze)
widget/        Glance widget, WidgetConfigActivity, WidgetUpdater
ui/            Compose: theme/, navigation/, onboarding/, home/, calendar/, history/, settings/, components/
di/            Hilt modules
```

Data flow: prefs + event log → `ScheduleRepository` → `Flow<Schedule>` → ViewModels, widget updater, reminder scheduler, calendar sync. Any change to config or log recomputes the schedule and fans out to the three sinks.

## 5. Domain model

- `Method(name, phases: List<Phase>, timeOfDay: LocalTime)`; `Phase(kind: WEAR | PAUSE, days: Int)`. Ring v1 = `[WEAR wear, PAUSE pause]`. A phase of 0 days is skipped.
- `anchor: LocalDateTime` = start of the current cycle (last confirmed INSERT, or the date entered in onboarding).
- `ScheduledEvent(id, kind: REMOVE | INSERT | SWAP, plannedAt, cycleIndex)`. Generated forward from the anchor for `horizonMonths` (default 12). Phase boundaries with a 0-day pause collapse REMOVE+INSERT into SWAP.
- `EventLog(eventId, kind, plannedAt, confirmedAt)`. One per confirmed event.
- `Overdue`: the earliest past `ScheduledEvent` without a log entry; exposes `delay: Duration` and `safetyBreached: Boolean`.
- `ShiftPolicy`: on late confirmation, `SHIFT` moves the anchor by the delay (all following events move); `KEEP` leaves the anchor.
- `SkipPause`: one-off flag turning the next REMOVE into a SWAP (next cycle starts at that moment).
- `ReminderRule(id, target: REMOVE | INSERT | ANY, offsetDays, timeOfDay?, offsetHours?, importance: LOW | NORMAL | HIGH | URGENT, enabled)`. Either days+time or hours is set.
- `OverdueConfig(repeatEveryHours = 2, snoozeHours = 1)`.

## 6. Screens

1. **Onboarding** — 4 steps: (a) wear/pause lengths with safety warning; (b) last insertion date + time; (c) default reminders (pre-filled: 1 day before at 20:00 NORMAL, same day at time HIGH); (d) optional calendar connection + palette. Skippable where optional.
2. **Home** — ring-shaped progress, big "days remaining", next action label + date, Done button, overdue banner with Done/Snooze, "Skip pause" action, phase chip.
3. **Calendar** — month grid coloured by phase, markers for events and notes, note editor for the selected day.
4. **History** — list of confirmed events with planned vs actual time and delay.
5. **Settings** — Schedule (edit wear/pause/time/anchor → preview → apply + regenerate), Reminders (list, add/edit/delete, importance), Overdue (repeat interval, snooze), Calendar (connect/disconnect, choose calendar, clean-up tool), Widget (mode, rows), Appearance (palette, light/dark), About.

## 7. Notifications

- Four channels mapped from `Importance`: LOW (silent), NORMAL (default sound), HIGH (sound + heads-up), URGENT (alarm sound, vibration, heads-up, bypass DND where allowed).
- Reminder rules schedule exact alarms (`AlarmManager.setExactAndAllowWhileIdle`; requests `SCHEDULE_EXACT_ALARM` on API 31+, falls back to inexact if denied).
- Overdue: URGENT channel, repeats every `repeatEveryHours` until the event is confirmed; actions **Done** and **Snooze**. Text escalates when `safetyBreached`.
- `BootReceiver` and every schedule change call `ReminderScheduler.rescheduleAll()`.
- Runtime permission `POST_NOTIFICATIONS` (API 33+) requested during onboarding.

## 8. Calendar sync

- Permission `READ_CALENDAR` + `WRITE_CALENDAR`, requested when the user enables sync.
- User selects one writable calendar; its id is stored.
- Each `ScheduledEvent` within the horizon is mirrored as a timed event (30 min) with title "Ring entfernen" / "Ring einsetzen" / "Ring wechseln" (localised) and a marker line `nobby:<eventId>` in the description. `CalendarLinkDao` maps eventId → provider event id.
- Sync = diff: upsert events whose time/title changed, delete links for events no longer in the schedule, create missing.
- Clean-up tool: input title text or regex + date range → list matching events (title, date) → multi-select → delete. Runs against the chosen calendar only.

## 9. Widget

- Glance `SizeMode.Responsive` with 2×1, 2×2, 4×2 layouts.
- `WidgetConfig(mode: SINGLE | MULTI, rows: List<Row>)`, `Row ∈ {NEXT_REMOVE, NEXT_INSERT, NEXT_ANY, OVERDUE, CYCLE_DAY}`; MULTI shows up to 3 rows. Configured in the widget's config activity (also mirrored in Settings).
- Colours from the active palette + light/dark. Tap opens Home; 2×2 and 4×2 include a Done button when an event is due/overdue.
- Updated via `WidgetUpdater` on every schedule change plus a daily midnight refresh (WorkManager).

## 10. Theming

Palettes: Rose (default, close to the old app), Lavender, Mint, Ocean, Graphite. Each defines Material 3 light + dark colour schemes. Mode: light / dark / system. Stored in DataStore, applied at the root composable and in the widget.

## 11. Error handling

- Calendar: permission denied → sync disabled with explanation; provider errors surfaced as a snackbar and logged; sync is idempotent and can be re-run from Settings.
- Alarms: exact-alarm permission denied → inexact scheduling with an in-app warning banner.
- Config validation: wear 1–35, pause 0–14, horizon 1–24 months; warnings (not blocks) above the safety thresholds.
- Timezone / DST: all planned times stored as `LocalDateTime` + zone id at creation; converted to instants when scheduling.

## 12. Testing

- Domain (JVM, no Android): schedule generation for 21/7, 28/0, odd lengths; SWAP collapsing; skip-pause; late confirmation with SHIFT vs KEEP; overdue detection and safety thresholds; DST crossings.
- Data: Room DAO tests (Robolectric), DataStore round-trips, calendar diff logic with a fake provider.
- UI: Compose tests for onboarding completion and Home "Done" flow.
- Manual on emulator (Pixel 3a API 34): reminder fires, overdue repeats and clears on Done, widget renders in all sizes and modes, calendar events appear and are cleaned up.

## 13. Out of scope for v1

Pill/patch UI, cloud backup, wearables, bleed prediction, ring-out timer, supply tracking, iOS.
