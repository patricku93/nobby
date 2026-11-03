# NoBby v2 — "Sticker Diary" re-skin (design)

Date: 2026-11-03
Status: approved by owner 2026-11-03
Builds on: `2026-10-29-nobby-ring-tracker-design.md` (v1, shipped at `acc0231`)
Design source of truth: claude.ai/design project "NoBby Design System"
(local copy `.superpowers/design/bundle/`, report `.superpowers/design/design-language-report.md`)

## 1. Goal

Replace the v1 look ("a 2016 app") with the approved "Sticker Diary" design
language: warm paper, cocoa ink, one strong hero colour, a bead-bracelet
countdown, pill shapes, rounded type, and the ring mascot NoBby. Behaviour,
data, alarms, calendar sync and the domain engine do not change.

## 2. Owner decisions (2026-11-03)

| Topic | Decision |
|---|---|
| Direction | "Sticker Diary" approved as designed. |
| Copy | German, casual "du", short words is the primary voice. English stays as the second locale with the same friendly tone. |
| Mascot | "More of it": every moment listed in §7, still at most one mascot per screen. |
| Hero colour | Berry `#B03A62` with white labels stays the default. |
| Themes | Ship 5 cute preset colour themes (Berry, Lilac, Peach, Matcha, Blueberry) × Light / Dark / Auto. Replaces the v1 palettes (Rose, Lavender, Mint, Ocean, Graphite). |

## 3. Scope

In: theme system, typography, shapes, every Compose screen and component,
mascot assets and moments, Done celebration sheet, Glance widget styling,
notification large icon, launcher icon, copy pass in DE + EN, plus three v1.1
fixes that touch the redesigned surfaces (§11).

Out: new features, new data, schedule/alarm/calendar logic, widget config
activity, cloud, iOS.

## 4. Theme system

- `domain/model/ThemeSpec.kt`: `enum class Palette { BERRY, LILAC, PEACH, MATCHA, BLUEBERRY }`, default `BERRY`. `ThemeMode` unchanged (SYSTEM shown as "Auto").
- Migration in `ThemeStore` when reading the stored name: `ROSE→BERRY`, `LAVENDER→LILAC`, `MINT→MATCHA`, `OCEAN→BLUEBERRY`, `GRAPHITE→BERRY`, unknown → `BERRY`. Writing always uses the new names.
- `ui/theme/Palettes.kt` is rewritten: no derived/alpha-blended colours. Each preset is a hand-specified table of all 41 Material 3 roles for light and dark, copied verbatim from `tokens.json` `presets.<id>.<light|dark>` (Berry = `color.light` / `color.dark`).
- `ui/theme/NobbyColors.kt` (new): an immutable `NobbyExtraColors` (wearBand, pauseBand, sticker/butter, mascotBody, mascotCheek, mascotRim, overdue, overdueContainer) provided through a `staticCompositionLocalOf` and read via `NobbyTheme.extra`. Values per preset × mode, from the same token tables.
- Semantics that hold in every preset: `primary` = wear phase + primary action, `secondary` = pause, `error` = overdue (tomato family), `inverseSurface` = selection. Never decoration-only colour. Preset exceptions from the bundle: Matcha's pause (`secondary`) is soft blue `#386D87` so it never reads as its green primary; Peach's `error` is the redder `#B92536` so overdue never reads as apricot.
- A unit test asserts every preset × mode passes the 30 contrast pairs listed in the design report (4.5:1 body, 3:1 large/UI), so a future colour edit cannot silently break contrast.
- Widget reads the same tables (`Palette.widgetColors(dark)`), replacing `Palette.accent()`.

## 5. Typography

- Fonts bundled in `res/font/` (offline, no Play Services dependency): Fredoka (Medium 500, SemiBold 600) and Nunito Sans (Regular 400, Bold 700), OFL. Obtaining them means downloading 4 TTFs (~100–300 KB each) from the Google Fonts GitHub repository (`github.com/google/fonts`, `ofl/fredoka`, `ofl/nunitosans`); the OFL licence text goes in `res/raw/` and Settings > About.
- `ui/theme/Type.kt`: full Material 3 `Typography` mapped to the bundle scale — display/headline/title/label use Fredoka, body uses Nunito Sans. Countdown number 96 sp Fredoka SemiBold with tabular figures.
- Widget keeps the system sans (Glance cannot load custom fonts): number 44 sp bold.

## 6. Shape, space, motion

- `ui/theme/Shapes.kt`: small 12 dp, medium 20 dp, large 28 dp, extraLarge 32 dp; buttons and chips fully rounded (pill).
- Spacing tokens (`NobbySpace`: 4/8/12/16/24/32/48 dp) replace ad-hoc dp literals in screens.
- Elevation: flat tonal surfaces; filled buttons get the 3 dp darker "sticker" bottom edge from the bundle (drawn with `drawBehind`).
- Motion: bead fill and Done sheet use the bundle durations/easings; all animations respect the system "remove animations" setting.

## 7. Mascot

- Six moods as VectorDrawables (`res/drawable/nobby_<mood>.xml`: happy, neutral, sleepy, worried, alert, yay) plus the props the bundle introduces for onboarding (wave, calendar, bell, palette), all recoloured at runtime via `NobbyExtraColors` (tint layers), so each preset gets its own mascot colours.
- Composable `Nobby(mood, size, prop?)` in `ui/components/`.
- Moments (one mascot per screen max):
  - Home: sticker on the ring — happy (wear), sleepy (pause), neutral (due today), worried (overdue), alert (safety threshold crossed).
  - Done celebration sheet (new): after any confirmation on Home or from the overdue card, a bottom sheet with "yay" + confetti doodles and a short line ("Erledigt! Gut gemacht 💗" / "Done! Nice one"), auto-dismisses after 4 s, tap or swipe to close. No undo button (owner decision 2026-11-03); the "Rückgängig" button in the bundle mockup is not implemented. Not shown for confirmations made from a notification.
  - Onboarding: one illustration per step (wave, calendar, bell, palette).
  - History empty state, Calendar selected day without note (small, subtle), Settings > About header.
  - Notification large icon: flat rim-less face exported as 96×96 PNGs, one per preset × mood (happy for reminders, worried for overdue and safety) = 10 bitmaps, chosen from the active preset. Small icon becomes a new monochrome ring glyph (`ic_stat_nobby`) — the current small icon is the launcher foreground, which Android renders as a blob.
  - Widget: flat face at 2×2 and 4×2.
- Accessibility: the mascot is decorative (`contentDescription = null`) everywhere except the Done sheet, where the line of text carries the meaning.

## 8. Components

| Component | Change |
|---|---|
| `RingProgress` → `BeadRing` | Bead bracelet: one bead per day of the current phase, filled beads = days done, today's bead highlighted; arc fallback above 35 beads. States: normal, due today, overdue, safety, pause. Number + label centred, mascot sticker on the rim. |
| Buttons | Primary filled pill with sticker edge, tonal, outlined, text, destructive; min height 52 dp. |
| Chips | Phase chip, filter/choice chips, theme preset chips with mini swatch. |
| `EventCard` + banners | Next-event card, due-today, overdue (with Done + Snooze), overdue + safety, permission banner, note card — per `components/cards-banners.html`. |
| `MonthGrid` | Continuous highlighter bands per phase (wear/pause) instead of outlined circles; event and note markers; selected day = inverseSurface. |
| `NumberStepper`, pickers, switches, settings rows, history rows | Restyled per bundle; time-picker header digits no longer clip (§11). |
| Top bars / nav bar | Paper background, Fredoka titles, pill indicator in the bottom bar; German labels must fit without wrapping at 360 dp width. |

## 9. Screens

All screens follow the bundle mockups (`screens/*.html`): Home, Calendar,
Onboarding (4 steps), Settings (grouped rows), Appearance, History, Reminders,
Schedule, Calendar sync + clean-up, Widget settings, dialogs (early/late
confirm, reminder edit). Appearance offers the 5 preset chips with live
preview of the mini Home, and Light / Dark / Auto. Onboarding step 4 offers
the same preset chips. Layouts must work in portrait and landscape at
360–430 dp width and with font scale 1.3.

## 10. Copy

- Pass over both `strings.xml` files for the casual, short voice; DE is written first, EN mirrors it. Key sets stay identical (enforced by the existing test).
- Fix the v1 "NICE" copy debts while touching the strings: use `plurals` for day/hour counts, no string concatenation in code.
- Safety wording keeps its current factual content; only tone and layout change.

## 11. v1.1 fixes included

1. Phase chip shows "day 9 of 7" while overdue → shows the overdue state instead ("2 Tage drüber").
2. Time-picker header digits clipped on API 34.
3. Phase display after a removal more than `wearDays` late.

## 12. Widget

Restyle per `components/widget.html` for 2×1, 2×2, 4×2 in Single and Multi,
light and dark, all 5 presets: solid `ColorProvider(day, night)` from the
preset tables, 24 dp corner radius, `LinearProgressIndicator` for the phase,
flat mascot at 2×2+, Done button only when due/overdue.

## 13. Launcher icon

New adaptive icon: NoBby face on a berry background, monochrome layer for
themed icons (API 33+). Fixed to Berry regardless of the chosen preset.

## 14. Testing

- Unit: ThemeStore migration (all 5 old names + unknown), preset role completeness, contrast test (§4), extra-colour lookup, plurals for 0/1/n in DE and EN.
- Robolectric/Compose: Home renders each ring state with the right mascot mood; Done sheet appears after confirm and dismisses; Appearance switches preset.
- Existing 98 tests stay green.
- Emulator (Pixel 3a API 34): every screen in light + dark, DE + EN, at least Berry + one other preset; widget all sizes; a reminder and an overdue notification with the new icons; landscape and font scale 1.3. Screenshots stored in `.superpowers/sdd/shots-v2/`.

## 15. Risks

- Hand-maintained 41-role tables × 10 → the contrast + completeness tests guard them.
- VectorDrawable tinting of multi-layer mascots: if per-layer runtime tint gets awkward, fall back to drawing the mascot in Compose `Canvas` from the same geometry as `nobby.js`.
- APK size grows by the fonts (~1 MB); acceptable.
