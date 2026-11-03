# NoBby v2 "Sticker Diary" Re-skin Implementation Plan

> Executed task by task; steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the v1 look of the working NoBby Android app with the approved "Sticker Diary" design language (warm paper, cocoa ink, five colour presets, bead-bracelet countdown, Fredoka/Nunito Sans, the NoBby mascot, a Done celebration sheet), without changing behaviour, data, alarms, calendar sync or the domain engine except the three v1.1 fixes in spec §11.

**Architecture:** Colour comes from one generated Kotlin table (`ui/theme/PresetTables.kt`, produced by `design/tools/gen-kotlin.js` from `bundle/tokens.json`), wrapped by `Palettes.kt` (Material 3 `ColorScheme`) and `NobbyColors.kt` (`NobbyExtraColors` via `NobbyTheme.extra`). The mascot is drawn in Compose `Canvas` from the exact `nobby.js` path geometry (`ui/mascot/`), so every preset recolours it at runtime; the same painter renders notification bitmaps, while the Glance widget uses white VectorDrawable masks tinted per preset. Screens are split into a stateful `XScreen` (Hilt view model) and a stateless `XContent` so Robolectric + Compose UI tests can drive them.

**Tech Stack:** Kotlin 2.1.0, AGP 8.7.3, Compose BOM 2024.12.01 (ui 1.7.6, material3 1.3.1, material-icons-extended), Glance 1.1.1, Hilt 2.53.1, Room 2.6.1, DataStore 1.1.1, kotlinx-datetime 0.6.1, JUnit 4, Robolectric 4.14.1, compose ui-test-junit4 (added in Task 1), Node (any LTS) for the token generator.

**Spec:** `docs/superpowers/specs/2026-11-03-nobby-v2-sticker-diary-reskin-design.md` (approved 2026-11-03, no undo button on the Done sheet).
**Design source of truth:** `design/bundle/` (`tokens.json`, `assets/nobby.js`, `assets/nobby.css`, `components/*`, `screens/*`) and `design/design-language-report.md` ("v2.1 additions", the 30 contrast pairs). `design/` and `notes/` are local working folders and are not committed.

## Global Constraints

- Build environment, run in Git Bash from the repo root **before every Gradle call** (the machine PATH contains a stray `"` that kills the test JVM):
  `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"; export PATH="${PATH//\"/}"`
  If the first test run after this still fails to start the test JVM, run `./gradlew.bat --stop` once and retry.
- Verify command (must end in `BUILD SUCCESSFUL` before every commit):
  `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain`
- Single test class: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.<package>.<Class>" --console=plain`
- Commit after every task on branch `master` with a conventional message (`feat:`, `fix:`, `test:`, `chore:`, `docs:`). **No `Co-Authored-By` or any other attribution trailer** (the owner's rule disables attribution).
- `minSdk 26`, `targetSdk 35`, `compileSdk 35`, package `com.nobby.ring`. Do not change versions in `gradle/libs.versions.toml`; the only build change is adding the existing `compose-ui-test-junit4` entry as a test dependency (Task 1).
- Every user-visible string lives in `res/values/strings.xml` (EN) **and** `res/values-de/strings.xml` (DE) with identical `<string>` and `<plurals>` key sets (enforced by `StringsParityTest` from Task 3). DE is written first: casual "du", short words. Counts use `<plurals>`; no sentence is built by string concatenation in code.
- No behaviour, data, domain, alarm or calendar-logic changes, except the three v1.1 fixes of spec §11 (Task 4 and Task 1). Allowed UI wiring of existing behaviour: in-app Snooze on the overdue card calls the same `Notifier.cancelOverdue()` + `ReminderScheduler.armOverdueIn(snoozeHours)` path as the notification action; onboarding step 4 also saves the chosen `ThemeMode`.
- At most one mascot per screen and at most two doodles per screen. The ring sticker is hidden while the Done sheet is up.
- The mascot is decorative: no `contentDescription` anywhere; on the Done sheet the title text carries the meaning.
- Colours come only from the generated tables (`PresetTables.kt` via `Palette.roles(dark)`, `MaterialTheme.colorScheme`, `NobbyTheme.extra`). No hex literals in Kotlin screens or the widget. Hex values appear only in the generated file, in tests that pin token values, in the fixed-Berry launcher icon, and as `#FFFFFFFF` masks in widget VectorDrawables (tinted at runtime).
- `ContrastTest` (30 pairs × 5 presets × light/dark) and `PaletteSchemeTest` must stay green.
- Semantics in every preset: `primary` = wear + primary action, `secondary` = pause, `error` = overdue, `inverseSurface` = selection.
- Layouts work in portrait and landscape at 360–430 dp width and font scale 1.3; German labels must not wrap in the bottom bar at 360 dp.
- Every animation goes through `reducedMotion()` (Task 5): with animator scale 0 it becomes a 120 ms fade or a snap.
- `domain/` imports nothing from `android.*` / `androidx.*`. Immutable state (`data class` + `copy()`), files under 800 lines, functions under ~50 lines, no `println`; errors logged with `Log.e("NoBby", …)` only.
- The Glance widget uses the system sans (`FontFamily.SansSerif`), solid `ColorProvider`s, no bitmaps.

---

## File map

```
gradle/libs.versions.toml                       unchanged (compose-ui-test-junit4 already declared)
app/build.gradle.kts                            + testImplementation(platform(compose-bom)), compose-ui-test-junit4      (T1)
design/tools/gen-kotlin.js         NEW, git-ignored: tokens.json -> PresetTables.kt                          (T2)

app/src/main/res/
  font/fredoka_variable.ttf, font/nunito_sans_variable.ttf     NEW (OFL, Google Fonts repo)                          (T1)
  raw/ofl_fredoka.txt, raw/ofl_nunito_sans.txt                 NEW licence texts, shown in Settings > About            (T1)
  values/themes.xml, values/colors.xml, values-night/*         paper window background                                (T2)
  values/strings.xml, values-de/strings.xml                    full copy pass + plurals                               (T3, keys trimmed T9-T13)
  drawable/widget_nobby_*.xml                                  NEW white masks for the widget face                    (T14)
  drawable/ic_stat_nobby.xml, ic_launcher_foreground.xml, ic_launcher_monochrome.xml, mipmap-anydpi-v26/ic_launcher.xml,
  values/ic_launcher_background.xml                                                                                     (T15)

app/src/main/java/com/nobby/ring/
  domain/model/ThemeSpec.kt          Palette { BERRY, LILAC, PEACH, MATCHA, BLUEBERRY } + fromStored() migration        (T2)
  domain/engine/CycleCalculator.kt   v1.1 fix 3 (removal logged under the shifted id)                                   (T4)
  data/prefs/ThemeStore.kt           uses Palette.fromStored                                                            (T2)
  ui/theme/Type.kt                   Fredoka / Nunito Sans families, NobbyTypography, NobbyType.countdown              (T1)
  ui/theme/PresetTables.kt           GENERATED: PresetRoles (41 colours), PresetPair, PRESET_TABLE, PRESET_ROLE_NAMES   (T2)
  ui/theme/Palettes.kt               Palette.roles/colorScheme/swatch/presetId, PresetRoles.toColorScheme               (T2)
  ui/theme/NobbyColors.kt            NobbyExtraColors, LocalNobbyExtraColors, object NobbyTheme { extra }               (T2)
  ui/theme/Theme.kt                  NobbyTheme(theme) provides scheme, extras, typography, shapes                      (T2, T5)
  ui/theme/Shapes.kt, Space.kt, Motion.kt   NobbyShapes/NobbyRadius, NobbySpace, NobbyMotion + reducedMotion()          (T5)
  ui/components/Formatting.kt        labels, plural(), date formats                                                    (T2, T3, T8, T12)
  ui/components/Buttons.kt           NobbyButton(tone), Modifier.stickerEdge                                           (T5)
  ui/components/Chips.kt             PhaseChip, ChoiceChip, SegmentedChoice, nobbySegmentedColors                     (T5)
  ui/components/NobbyTopBar.kt       paper top bar                                                                     (T5)
  ui/components/Pickers.kt           PickerField; DatePickerField/TimePickerField/NumberStepper restyled               (T5)
  ui/mascot/*                        MascotMood/Prop + semantics, MascotColors, MascotGeometry, MascotPainter, Nobby,
                                     MascotBitmaps, DoneBurst                                                          (T6)
  ui/components/BeadRing.kt          bead bracelet ring (replaces RingProgress.kt)                                      (T7)
  ui/ring/RingModel.kt               ScheduleState -> tone, mood, beads, number, label                                 (T7)
  ui/components/Cards.kt, EventCards.kt, Banners.kt, SettingsRows.kt                                                   (T8)
  ui/home/PhaseChipModel.kt          v1.1 fix 1                                                                        (T4)
  ui/home/HomeScreen.kt, HomeViewModel.kt, DoneSheet.kt                                                                (T9)
  ui/navigation/NobbyNavHost.kt      restyled bottom bar                                                               (T9)
  ui/calendar/*                      bands, legend, day card, goToToday                                                (T10)
  ui/history/HistoryScreen.kt        rows + empty state                                                                (T11)
  ui/settings/*                      hub, About + licences, Appearance + PresetPicker, sub-screens restyled             (T12)
  ui/onboarding/*                    4 steps, mascot per step, mode                                                    (T13)
  widget/WidgetColors.kt, WidgetModel.kt, RingWidget.kt                                                                 (T14)
  notifications/Notifier.kt, NotificationIcons.kt                                                                       (T15)
```

Deleted along the way: `ui/components/RingProgress.kt` and `ui/components/EventCard.kt` (Task 9).

## Task order

1. Fonts + type scale (includes v1.1 fix 2)
2. Colour presets, generator, ThemeStore migration, theme wiring
3. Copy pass (DE first), plurals, key parity test
4. v1.1 fixes 1 and 3
5. Shapes/space/motion tokens, buttons, chips, top bar, pickers
6. NoBby mascot renderer
7. Bead ring + ring model
8. Cards, banners, settings rows
9. Home + Done celebration sheet + bottom bar
10. Calendar
11. History
12. Settings, Appearance, sub-screens
13. Onboarding
14. Widget
15. Notification icons + launcher icon
16. Emulator verification

## Decisions taken where the spec and bundle leave room

1. **Mascot rendering:** Compose `Canvas` from the exact `nobby.js` geometry (spec §15 fallback) instead of six multi-layer VectorDrawables, because a VectorDrawable takes one tint and every preset needs body, cheek, ink, rim and prop colours. The widget (Glance cannot run Compose drawing, bundle forbids bitmaps there) uses white VectorDrawable masks tinted per preset.
2. **Notification large icons:** the 10 preset × mood faces are rendered at runtime from the same `notifIcon()` geometry and cached, at the platform large-icon size, instead of shipping 96 px PNG files.
3. **Fonts:** `google/fonts` only has variable files (`Fredoka[wdth,wght].ttf` 159 184 B, `NunitoSans[YTLC,opsz,wdth,wght].ttf` 571 240 B); the app bundles those two and selects 500/600 and 400/700 with `FontVariation.weight`.
4. **Shapes:** the Material shape scale uses the spec's values (12/20/28/32 dp); explicitly styled components use the bundle radii (cards 24, sheets 28, tiles 16, pills).
5. **Done sheet:** an in-Home overlay (scrim + sheet, tap/swipe/4 s auto close, reduced motion = fade) rather than `ModalBottomSheet`, so it is deterministic in Robolectric tests; no "Rückgängig" (owner decision).
6. **Behaviour-neutral scope:** the overdue card's Snooze reuses the notification snooze path. Not built because they would add behaviour: the bundle's "Schon erledigt" (confirming before the due day), "Mehr Info", onboarding "Ich mache Pause", and the calendar-sync switch inside onboarding (step 4 shows a hint to Settings instead). Onboarding step 4 does save the chosen mode (bundle shows Hell/Dunkel/Auto there).
7. **Copy first:** the copy pass is Task 3 (before the screens) so every screen task only references final keys; keys still used by v1 screens are marked "legacy" and removed by the task that stops using them. The brief mentions an existing EN/DE parity test, but none exists in `app/src/test`; Task 3 adds `StringsParityTest` (strings, plurals, placeholders).
8. **v1.1 fix 2 root cause:** `displayLarge` was overridden to 96 sp and Material's `TimePicker` header uses `displayLarge`; Task 1 restores 57 sp and moves the countdown to `NobbyType.countdown`.
9. **v1.1 fix 3 root cause:** a shifted late removal is logged under the regenerated id with its original `plannedAt`; `CycleCalculator` now also matches the current cycle's REMOVE id (Task 4).
10. **Generator location:** `gen-kotlin.js` sits with the other design tools in git-ignored `design/tools/`; its output `PresetTables.kt` is committed and `TokenDriftTest` compares it with `tokens.json` whenever the bundle is present (skipped otherwise).
11. **`NobbyExtraColors`** carries the spec's fields (wearBand, pauseBand, sticker, mascotBody, mascotCheek, mascotRim, overdue, overdueContainer) plus `ink`, `shadow` and `dark`, all read from the same generated table.
12. **Bottom bar:** standard full-width `NavigationBar` on `surfaceContainerLowest` with the pill indicator in `primaryContainer` and filled/outlined icons, not the floating pill of the phone mock-up (keeps inset handling unchanged).

---

### Task 1: Bundled fonts and the v2 type scale (includes v1.1 fix 2)

The v1 `Type.kt` set `displayLarge` to 96 sp for the countdown. Material 3's `TimePicker` draws its header digits with `displayLarge`, which is why they clipped on API 34 (spec §11.2). v2 restores `displayLarge` to 57/64 and gives the countdown its own style.

**Files:**
- Create: `app/src/main/res/font/fredoka_variable.ttf`, `app/src/main/res/font/nunito_sans_variable.ttf`
- Create: `app/src/main/res/raw/ofl_fredoka.txt`, `app/src/main/res/raw/ofl_nunito_sans.txt`
- Modify: `app/build.gradle.kts` (test dependencies)
- Modify (replace): `app/src/main/java/com/nobby/ring/ui/theme/Type.kt`
- Modify: `app/src/main/java/com/nobby/ring/ui/home/HomeScreen.kt` (countdown style only)
- Test: `app/src/test/java/com/nobby/ring/ui/theme/TypographyTest.kt`, `app/src/test/java/com/nobby/ring/ui/theme/FontLoadTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `val Fredoka: FontFamily`, `val NunitoSans: FontFamily`, `val NobbyTypography: Typography`, `object NobbyType { val countdown: TextStyle; val countdownWord: TextStyle }` in package `com.nobby.ring.ui.theme`. Test infrastructure: `createComposeRule()` works in Robolectric unit tests.

- [ ] **Step 1: Download the fonts and licences (pinned commits)**

Both families exist in `github.com/google/fonts` only as variable fonts (no static instances), so the app uses one variable file per family with `FontVariation.weight(...)` (supported from API 26 = our minSdk). Android resource names must be lowercase, hence the renames.

```bash
mkdir -p app/src/main/res/font app/src/main/res/raw
curl -fL -o app/src/main/res/font/fredoka_variable.ttf \
  "https://raw.githubusercontent.com/google/fonts/a60a77e14f28abd4ef243a1b5dfc48df0cec5205/ofl/fredoka/Fredoka%5Bwdth%2Cwght%5D.ttf"
curl -fL -o app/src/main/res/font/nunito_sans_variable.ttf \
  "https://raw.githubusercontent.com/google/fonts/8b0a1d0f5983c89bc2b93f1b5fb55f9e252744b5/ofl/nunitosans/NunitoSans%5BYTLC%2Copsz%2Cwdth%2Cwght%5D.ttf"
curl -fL -o app/src/main/res/raw/ofl_fredoka.txt \
  "https://raw.githubusercontent.com/google/fonts/a60a77e14f28abd4ef243a1b5dfc48df0cec5205/ofl/fredoka/OFL.txt"
curl -fL -o app/src/main/res/raw/ofl_nunito_sans.txt \
  "https://raw.githubusercontent.com/google/fonts/8b0a1d0f5983c89bc2b93f1b5fb55f9e252744b5/ofl/nunitosans/OFL.txt"
wc -c app/src/main/res/font/*.ttf
sha256sum app/src/main/res/font/*.ttf
head -1 app/src/main/res/raw/ofl_fredoka.txt app/src/main/res/raw/ofl_nunito_sans.txt
```

Expected:
```
159184 app/src/main/res/font/fredoka_variable.ttf
571240 app/src/main/res/font/nunito_sans_variable.ttf
2ba02e68b152868aef9ba28e24b3648c7d457fe6f25c761f2c2c53fb61a73fc8 *app/src/main/res/font/fredoka_variable.ttf
f934d7142fb4784bf828da485b7dcbd90c0c80d514e9d49a5da0ed3a1ae2491d *app/src/main/res/font/nunito_sans_variable.ttf
==> app/src/main/res/raw/ofl_fredoka.txt <==
Copyright 2016 The Fredoka Project Authors (https://github.com/hafontia/Fredoka-One)
==> app/src/main/res/raw/ofl_nunito_sans.txt <==
Copyright 2016 The Nunito Sans Project Authors (https://github.com/Fonthausen/NunitoSans)
```
If a hash differs, stop and report: the pinned commit no longer serves that file.

- [ ] **Step 2: Add Compose UI test dependencies**

In `app/build.gradle.kts`, replace

```kotlin
    testImplementation(libs.junit)
```

with

```kotlin
    testImplementation(libs.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
```

(`compose-ui-test-junit4` is already declared in `gradle/libs.versions.toml`; `debugImplementation(libs.compose.ui.test.manifest)` already provides the test activity.)

- [ ] **Step 3: Write the failing tests**

`app/src/test/java/com/nobby/ring/ui/theme/TypographyTest.kt`:

```kotlin
package com.nobby.ring.ui.theme

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TypographyTest {
    private val t = NobbyTypography

    @Test
    fun `display, headline, titleLarge and labelLarge use Fredoka, the rest Nunito Sans`() {
        listOf(t.displayLarge, t.displayMedium, t.headlineLarge, t.headlineMedium, t.headlineSmall, t.titleLarge, t.labelLarge)
            .forEach { assertSame(Fredoka, it.fontFamily) }
        listOf(t.titleMedium, t.titleSmall, t.bodyLarge, t.bodyMedium, t.bodySmall, t.labelMedium, t.labelSmall)
            .forEach { assertSame(NunitoSans, it.fontFamily) }
    }

    @Test
    fun `displayLarge is 57 sp so the Material time picker header digits fit (v1_1 fix 2)`() {
        assertEquals(57.sp, t.displayLarge.fontSize)
        assertEquals(64.sp, t.displayLarge.lineHeight)
    }

    @Test
    fun `sizes and weights follow tokens json type`() {
        assertEquals(28.sp, t.headlineMedium.fontSize)
        assertEquals(FontWeight.SemiBold, t.headlineMedium.fontWeight)
        assertEquals(FontWeight.Medium, t.headlineSmall.fontWeight)
        assertEquals(22.sp, t.titleLarge.fontSize)
        assertEquals(FontWeight.Bold, t.titleMedium.fontWeight)
        assertEquals(16.sp, t.bodyLarge.fontSize)
        assertEquals(FontWeight.Normal, t.bodyLarge.fontWeight)
        assertEquals(FontWeight.Medium, t.labelLarge.fontWeight)
        assertEquals(11.sp, t.labelSmall.fontSize)
    }

    @Test
    fun `countdown is 96 sp Fredoka SemiBold with tabular figures`() {
        val c = NobbyType.countdown
        assertSame(Fredoka, c.fontFamily)
        assertEquals(96.sp, c.fontSize)
        assertEquals(96.sp, c.lineHeight)
        assertEquals(FontWeight.SemiBold, c.fontWeight)
        assertEquals("tnum", c.fontFeatureSettings)
    }
}
```

`app/src/test/java/com/nobby/ring/ui/theme/FontLoadTest.kt`:

```kotlin
package com.nobby.ring.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FontLoadTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `both bundled variable fonts load and render`() {
        compose.setContent {
            MaterialTheme(typography = NobbyTypography) {
                Column {
                    Text("21", style = NobbyType.countdown)
                    Text("Beipackzettel ß ä ö ü", style = MaterialTheme.typography.bodyLarge)
                    Text("Erledigt", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        compose.onNodeWithText("21").assertIsDisplayed()
        compose.onNodeWithText("Beipackzettel ß ä ö ü").assertIsDisplayed()
        compose.onNodeWithText("Erledigt").assertIsDisplayed()
    }
}
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.theme.*" --console=plain`
Expected: compilation FAILS with `Unresolved reference: Fredoka` / `NunitoSans` / `NobbyType`.

- [ ] **Step 5: Replace `Type.kt`**

```kotlin
@file:OptIn(ExperimentalTextApi::class)

package com.nobby.ring.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nobby.ring.R

/** One weight instance of a bundled variable font (Compose applies the `wght` axis on API 26+). */
private fun variable(resId: Int, weight: FontWeight) =
    Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

/** Fredoka (OFL, variable wght 300-700). The app uses Medium 500 and SemiBold 600. */
val Fredoka = FontFamily(
    variable(R.font.fredoka_variable, FontWeight.Medium),
    variable(R.font.fredoka_variable, FontWeight.SemiBold),
)

/** Nunito Sans (OFL, variable wght 200-1000). The app uses Regular 400 and Bold 700. */
val NunitoSans = FontFamily(
    variable(R.font.nunito_sans_variable, FontWeight.Normal),
    variable(R.font.nunito_sans_variable, FontWeight.Bold),
)

private fun style(family: FontFamily, size: Int, line: Int, weight: FontWeight, tracking: Float) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

/**
 * Material 3 scale mapped to tokens.json `type`: display, headline, titleLarge and labelLarge (buttons) in Fredoka,
 * everything read in sentences in Nunito Sans. displaySmall is not in the bundle and keeps M3's 36/44 in Fredoka.
 * displayLarge stays at 57 sp: the Material time picker draws its header digits with it (v1.1 fix 2).
 */
val NobbyTypography = Typography(
    displayLarge = style(Fredoka, 57, 64, FontWeight.SemiBold, -1f),
    displayMedium = style(Fredoka, 45, 52, FontWeight.SemiBold, -0.5f),
    displaySmall = style(Fredoka, 36, 44, FontWeight.SemiBold, 0f),
    headlineLarge = style(Fredoka, 32, 40, FontWeight.SemiBold, 0f),
    headlineMedium = style(Fredoka, 28, 36, FontWeight.SemiBold, 0f),
    headlineSmall = style(Fredoka, 24, 32, FontWeight.Medium, 0f),
    titleLarge = style(Fredoka, 22, 28, FontWeight.Medium, 0f),
    titleMedium = style(NunitoSans, 16, 24, FontWeight.Bold, 0.1f),
    titleSmall = style(NunitoSans, 14, 20, FontWeight.Bold, 0.1f),
    bodyLarge = style(NunitoSans, 16, 24, FontWeight.Normal, 0.2f),
    bodyMedium = style(NunitoSans, 14, 20, FontWeight.Normal, 0.2f),
    bodySmall = style(NunitoSans, 12, 16, FontWeight.Normal, 0.3f),
    labelLarge = style(Fredoka, 16, 24, FontWeight.Medium, 0.2f),
    labelMedium = style(NunitoSans, 12, 16, FontWeight.Bold, 0.4f),
    labelSmall = style(NunitoSans, 11, 16, FontWeight.Bold, 0.5f),
)

/** Custom styles outside the Material scale. */
object NobbyType {
    /** Home countdown number: 96/96 Fredoka SemiBold, -2 tracking, tabular figures. */
    val countdown: TextStyle = style(Fredoka, 96, 96, FontWeight.SemiBold, -2f).copy(fontFeatureSettings = "tnum")

    /** The word ("Heute") shown in the ring instead of a number when the event is due today. */
    val countdownWord: TextStyle = NobbyTypography.displayMedium
}
```

- [ ] **Step 6: Keep the v1 Home countdown at 96 sp**

In `app/src/main/java/com/nobby/ring/ui/home/HomeScreen.kt` replace

```kotlin
            if (days != 0) Text("${abs(days)}", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
```

with

```kotlin
            if (days != 0) Text("${abs(days)}", style = NobbyType.countdown, color = MaterialTheme.colorScheme.primary)
```

and add `import com.nobby.ring.ui.theme.NobbyType` to the imports.

- [ ] **Step 7: Run the tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.theme.*" --console=plain`
Expected: `BUILD SUCCESSFUL`, 5 tests passed.

If `FontLoadTest` fails with `IllegalStateException: Could not load font` (Robolectric font shadow), change `variable()` in `Type.kt` to

```kotlin
private fun variable(resId: Int, weight: FontWeight) = Font(
    resId, weight,
    loadingStrategy = androidx.compose.ui.text.font.FontLoadingStrategy.OptionalLocal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)
```

re-run, and note the fallback in the commit body (the emulator check in Task 16 confirms the fonts render on device).

- [ ] **Step 8: Verify and commit**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` → `BUILD SUCCESSFUL` (existing 98 tests + 5 new).

```bash
git add app/build.gradle.kts app/src/main/res/font app/src/main/res/raw app/src/main/java/com/nobby/ring/ui/theme/Type.kt \
  app/src/main/java/com/nobby/ring/ui/home/HomeScreen.kt app/src/test/java/com/nobby/ring/ui/theme
git commit -m "feat(theme): bundle Fredoka and Nunito Sans, v2 type scale" -m "displayLarge back to 57 sp fixes the clipped time-picker header (v1.1 fix 2); the countdown gets its own 96 sp style."
```

---

### Task 2: Five colour presets, token generator, ThemeStore migration, theme wiring

**Files:**
- Create (git-ignored, not committed): `design/tools/gen-kotlin.js`
- Create (generated, committed): `app/src/main/java/com/nobby/ring/ui/theme/PresetTables.kt`
- Modify (replace): `app/src/main/java/com/nobby/ring/domain/model/ThemeSpec.kt`, `app/src/main/java/com/nobby/ring/ui/theme/Palettes.kt`, `app/src/main/java/com/nobby/ring/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/nobby/ring/ui/theme/NobbyColors.kt`
- Modify: `app/src/main/java/com/nobby/ring/data/prefs/ThemeStore.kt`, `app/src/main/java/com/nobby/ring/ui/components/Formatting.kt`, `app/src/main/java/com/nobby/ring/ui/settings/AppearanceScreen.kt`, `app/src/main/java/com/nobby/ring/ui/onboarding/OnboardingScreen.kt`, `app/src/main/java/com/nobby/ring/ui/onboarding/OnboardingViewModel.kt`, `app/src/main/java/com/nobby/ring/widget/RingWidget.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-de/strings.xml` (palette names), `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/values/colors.xml`, `app/src/main/res/values-night/colors.xml`, `app/src/main/res/values-night/themes.xml`
- Modify: `app/src/test/java/com/nobby/ring/ui/onboarding/OnboardingViewModelTest.kt` (`Palette.MINT` → `Palette.MATCHA`)
- Test: `app/src/test/java/com/nobby/ring/data/ThemeStoreTest.kt`, `app/src/test/java/com/nobby/ring/ui/theme/PaletteSchemeTest.kt`, `app/src/test/java/com/nobby/ring/ui/theme/ContrastTest.kt`, `app/src/test/java/com/nobby/ring/ui/theme/TokenDriftTest.kt`

**Interfaces:**
- Consumes: `NobbyTypography` (Task 1).
- Produces:
  - `enum class Palette { BERRY, LILAC, PEACH, MATCHA, BLUEBERRY; companion object { fun fromStored(name: String?): Palette } }`, `data class ThemeSpec(val palette: Palette = Palette.BERRY, val mode: ThemeMode = ThemeMode.SYSTEM)`
  - Generated: `data class PresetRoles(41 Color properties named exactly like tokens.json keys) { fun byName(): Map<String, Color> }`, `data class PresetPair(val light: PresetRoles, val dark: PresetRoles)`, `val PRESET_TABLE: Map<String, PresetPair>`, `val PRESET_ROLE_NAMES: List<String>`, `const val TOKENS_VERSION: String`
  - `val Palette.presetId: String`, `fun Palette.roles(dark: Boolean): PresetRoles`, `fun Palette.colorScheme(dark: Boolean): ColorScheme`, `fun Palette.swatch(dark: Boolean): Color`, `fun PresetRoles.toColorScheme(dark: Boolean): ColorScheme`
  - `data class NobbyExtraColors(val dark: Boolean, val wearBand: Color, val pauseBand: Color, val sticker: Color, val mascotBody: Color, val mascotCheek: Color, val mascotRim: Color, val ink: Color, val shadow: Color, val overdue: Color, val overdueContainer: Color)`, `fun PresetRoles.extraColors(dark: Boolean): NobbyExtraColors`, `fun Palette.extraColors(dark: Boolean): NobbyExtraColors`, `val LocalNobbyExtraColors`, `object NobbyTheme { val extra: NobbyExtraColors }` (composable getter)
  - `@Composable fun NobbyTheme(theme: ThemeSpec, content: @Composable () -> Unit)` (unchanged signature)
  - String keys `palette_berry`, `palette_lilac`, `palette_peach`, `palette_matcha`, `palette_blueberry`

- [ ] **Step 1: Write the generator** `design/tools/gen-kotlin.js`

The local `design/` folder is not committed, so this script stays local like the other design tools; its output is committed. It refuses to write anything if `tokens.json` is inconsistent.

```js
/* Generates the Kotlin colour tables for the app from bundle/tokens.json, so hex values are
   never copied by hand and cannot drift from the design source of truth.
   Usage (from design):  node tools/gen-kotlin.js [outFile]
   Default outFile: ../../app/src/main/java/com/nobby/ring/ui/theme/PresetTables.kt
   Exit 1 (and write nothing) when tokens.json is inconsistent. */
'use strict';
const fs = require('fs');
const path = require('path');

const DESIGN = path.join(__dirname, '..');
const tokens = JSON.parse(fs.readFileSync(path.join(DESIGN, 'bundle', 'tokens.json'), 'utf8'));
const out = process.argv[2] ||
  path.join(DESIGN, '..', '..', 'app', 'src', 'main', 'java', 'com', 'nobby', 'ring', 'ui', 'theme', 'PresetTables.kt');

const ORDER = ['berry', 'lilac', 'peach', 'matcha', 'blueberry'];
const THEMES = ['light', 'dark'];
const errors = [];

const roles = Object.keys(tokens.presets.berry.light);
if (roles.length !== 41) errors.push(`berry.light has ${roles.length} roles, expected 41`);
if (Object.keys(tokens.presets).join() !== ORDER.join()) errors.push(`preset ids/order ${Object.keys(tokens.presets).join()} != ${ORDER.join()}`);
for (const id of ORDER) {
  for (const th of THEMES) {
    const t = tokens.presets[id] && tokens.presets[id][th];
    if (!t) { errors.push(`missing presets.${id}.${th}`); continue; }
    if (Object.keys(t).join() !== roles.join()) errors.push(`presets.${id}.${th} key set/order differs from berry.light`);
    for (const [k, v] of Object.entries(t)) if (!/^#[0-9A-Fa-f]{6}$/.test(v)) errors.push(`presets.${id}.${th}.${k} = ${v} is not #RRGGBB`);
  }
}
for (const th of THEMES) {
  for (const k of roles) {
    if (tokens.color[th][k] !== tokens.presets.berry[th][k]) errors.push(`color.${th}.${k} != presets.berry.${th}.${k}`);
  }
}
if (errors.length) {
  console.error(errors.join('\n'));
  process.exit(1);
}

const kColor = (hex) => `Color(0xFF${hex.slice(1).toUpperCase()})`;
const L = [];
L.push(`// GENERATED by design/tools/gen-kotlin.js from bundle/tokens.json ${tokens.$version}.`);
L.push('// Do not edit by hand: change tokens.json, run `node tools/gen-kotlin.js` in design and commit the result.');
L.push('package com.nobby.ring.ui.theme');
L.push('');
L.push('import androidx.compose.runtime.Immutable');
L.push('import androidx.compose.ui.graphics.Color');
L.push('');
L.push(`const val TOKENS_VERSION = "${tokens.$version}"`);
L.push('');
L.push('/** All 41 colour roles of one preset in one mode: 36 Material 3 roles (incl. scrim and shadow) + 5 NoBby roles. */');
L.push('@Immutable');
L.push('data class PresetRoles(');
for (const k of roles) L.push(`    val ${k}: Color,`);
L.push(') {');
L.push('    /** Role name exactly as in tokens.json, mapped to its colour. */');
L.push('    fun byName(): Map<String, Color> = mapOf(');
for (const k of roles) L.push(`        "${k}" to ${k},`);
L.push('    )');
L.push('}');
L.push('');
L.push('@Immutable');
L.push('data class PresetPair(val light: PresetRoles, val dark: PresetRoles)');
L.push('');
L.push('/** The 41 role names in tokens.json order. */');
L.push('val PRESET_ROLE_NAMES: List<String> = listOf(');
for (const k of roles) L.push(`    "${k}",`);
L.push(')');
L.push('');
L.push('/** Keyed by tokens.json preset id: berry, lilac, peach, matcha, blueberry. */');
L.push('val PRESET_TABLE: Map<String, PresetPair> = mapOf(');
for (const id of ORDER) {
  L.push(`    "${id}" to PresetPair(`);
  for (const th of THEMES) {
    L.push(`        ${th} = PresetRoles(`);
    for (const k of roles) L.push(`            ${k} = ${kColor(tokens.presets[id][th][k])},`);
    L.push('        ),');
  }
  L.push('    ),');
}
L.push(')');
L.push('');

fs.writeFileSync(out, L.join('\n'));
console.log(`Wrote ${out}: ${ORDER.length} presets x ${THEMES.length} modes x ${roles.length} roles (tokens ${tokens.$version})`);
```

- [ ] **Step 2: Generate `PresetTables.kt`**

```bash
(cd design && node tools/check-contrast.js && node tools/gen-kotlin.js)
wc -l app/src/main/java/com/nobby/ring/ui/theme/PresetTables.kt
sed -n '151,155p' app/src/main/java/com/nobby/ring/ui/theme/PresetTables.kt
```

Expected: `check-contrast.js` ends with `All 30 pairs pass for every preset in light and dark.`; then `Wrote …PresetTables.kt: 5 presets x 2 modes x 41 roles (tokens 2.1.0)`; `591 …PresetTables.kt`; and

```
    "berry" to PresetPair(
        light = PresetRoles(
            primary = Color(0xFFB03A62),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFBDDE5),
```

Never edit this file by hand; `TokenDriftTest` (Step 4) compares it with `tokens.json` whenever the bundle is present.

- [ ] **Step 3: Write the failing tests**

`app/src/test/java/com/nobby/ring/data/ThemeStoreTest.kt`:

```kotlin
package com.nobby.ring.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.data.prefs.ThemeStore
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.ThemeMode
import com.nobby.ring.domain.model.ThemeSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ThemeStoreTest {
    private val paletteKey = stringPreferencesKey("theme_palette")
    private lateinit var ds: DataStore<Preferences>
    private lateinit var store: ThemeStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ds = PreferenceDataStoreFactory.create { File(context.cacheDir, "theme-${System.nanoTime()}.preferences_pb") }
        store = ThemeStore(ds)
    }

    private suspend fun storeRaw(name: String) {
        ds.edit { it[paletteKey] = name }
    }

    @Test
    fun `all five v1 palette names migrate to the v2 presets`() = runTest {
        mapOf(
            "ROSE" to Palette.BERRY,
            "LAVENDER" to Palette.LILAC,
            "MINT" to Palette.MATCHA,
            "OCEAN" to Palette.BLUEBERRY,
            "GRAPHITE" to Palette.BERRY,
        ).forEach { (old, expected) ->
            storeRaw(old)
            assertEquals(old, expected, store.theme.first().palette)
        }
    }

    @Test
    fun `missing and unknown names fall back to berry`() = runTest {
        assertEquals(Palette.BERRY, store.theme.first().palette)
        storeRaw("NEON")
        assertEquals(Palette.BERRY, store.theme.first().palette)
    }

    @Test
    fun `v2 names round-trip and saving always writes the new name`() = runTest {
        storeRaw("LAVENDER")
        val migrated = store.theme.first()
        store.save(migrated.copy(mode = ThemeMode.DARK))
        assertEquals("LILAC", ds.data.first()[paletteKey])
        store.save(ThemeSpec(Palette.PEACH, ThemeMode.LIGHT))
        assertEquals(ThemeSpec(Palette.PEACH, ThemeMode.LIGHT), store.theme.first())
    }
}
```

`app/src/test/java/com/nobby/ring/ui/theme/PaletteSchemeTest.kt` (the hex values below are pinned from `tokens.json`):

```kotlin
package com.nobby.ring.ui.theme

import androidx.compose.ui.graphics.Color
import com.nobby.ring.domain.model.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteSchemeTest {
    private val modes = listOf(false, true)

    @Test
    fun `every preset defines all 41 opaque roles in light and dark`() {
        assertEquals(41, PRESET_ROLE_NAMES.size)
        for (p in Palette.entries) for (dark in modes) {
            val roles = p.roles(dark).byName()
            assertEquals("${p.presetId} dark=$dark", PRESET_ROLE_NAMES.toSet(), roles.keys)
            roles.forEach { (name, c) -> assertTrue("${p.presetId}.$name opaque", c.alpha == 1f) }
        }
    }

    @Test
    fun `colour scheme copies the table without derived colours`() {
        val r = Palette.PEACH.roles(true)
        val s = Palette.PEACH.colorScheme(true)
        assertEquals(r.primary, s.primary)
        assertEquals(r.secondaryContainer, s.secondaryContainer)
        assertEquals(r.surfaceContainerHighest, s.surfaceContainerHighest)
        assertEquals(r.surfaceDim, s.surfaceDim)
        assertEquals(r.inverseSurface, s.inverseSurface)
        assertEquals(r.outlineVariant, s.outlineVariant)
        assertEquals(Color.Transparent, s.surfaceTint)
    }

    @Test
    fun `berry is the default and matches the bundle hero colour`() {
        assertEquals(Palette.BERRY, com.nobby.ring.domain.model.ThemeSpec().palette)
        assertEquals(Color(0xFFB03A62), Palette.BERRY.roles(false).primary)
        assertEquals(Color(0xFFF5A6BE), Palette.BERRY.roles(true).primary)
    }

    @Test
    fun `matcha pause is soft blue and peach overdue is the redder tomato`() {
        assertEquals(Color(0xFF386D87), Palette.MATCHA.roles(false).secondary)
        assertEquals(Color(0xFF99CDEC), Palette.MATCHA.roles(true).secondary)
        assertEquals(Color(0xFFB92536), Palette.PEACH.roles(false).error)
        assertEquals(Color(0xFFFFB3AF), Palette.PEACH.roles(true).error)
    }

    @Test
    fun `extra colours come from the same preset table`() {
        val r = Palette.LILAC.roles(false)
        val x = Palette.LILAC.extraColors(false)
        assertFalse(x.dark)
        assertEquals(r.primaryContainer, x.wearBand)
        assertEquals(r.secondaryContainer, x.pauseBand)
        assertEquals(r.accent, x.sticker)
        assertEquals(r.mascot, x.mascotBody)
        assertEquals(r.mascotCheek, x.mascotCheek)
        assertEquals(r.stickerRim, x.mascotRim)
        assertEquals(r.ink, x.ink)
        assertEquals(r.error, x.overdue)
        assertEquals(r.errorContainer, x.overdueContainer)
        assertTrue(Palette.LILAC.extraColors(true).dark)
    }
}
```

`app/src/test/java/com/nobby/ring/ui/theme/ContrastTest.kt` (the pair list is `bundle/assets/contrast.js` `PAIRS`, camel-cased):

```kotlin
package com.nobby.ring.ui.theme

import androidx.compose.ui.graphics.Color
import com.nobby.ring.domain.model.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class ContrastTest {
    private val pairs = listOf(
        Triple("onSurface", "surface", 4.5), Triple("onSurface", "background", 4.5),
        Triple("onSurface", "surfaceContainerLowest", 4.5), Triple("onSurface", "surfaceContainerHighest", 4.5),
        Triple("onSurfaceVariant", "surface", 4.5), Triple("onSurfaceVariant", "background", 4.5),
        Triple("onSurfaceVariant", "surfaceContainerHigh", 4.5), Triple("onSurfaceVariant", "surfaceContainerHighest", 4.5),
        Triple("onPrimary", "primary", 4.5), Triple("onPrimaryContainer", "primaryContainer", 4.5),
        Triple("onSecondary", "secondary", 4.5), Triple("onSecondaryContainer", "secondaryContainer", 4.5),
        Triple("onTertiary", "tertiary", 4.5), Triple("onTertiaryContainer", "tertiaryContainer", 4.5),
        Triple("onError", "error", 4.5), Triple("onErrorContainer", "errorContainer", 4.5),
        Triple("inverseOnSurface", "inverseSurface", 4.5),
        Triple("primary", "surface", 4.5), Triple("primary", "background", 4.5), Triple("primary", "surfaceContainer", 4.5),
        Triple("secondary", "background", 4.5), Triple("tertiary", "background", 4.5), Triple("error", "background", 4.5),
        Triple("onSurface", "primaryContainer", 4.5), Triple("onSurface", "secondaryContainer", 4.5),
        Triple("onSurface", "errorContainer", 4.5),
        Triple("primary", "primaryContainer", 3.0), Triple("secondary", "secondaryContainer", 3.0),
        Triple("error", "errorContainer", 3.0), Triple("outline", "background", 3.0),
    )

    @Test
    fun `the pair list is the design report's 30 pairs`() = assertEquals(30, pairs.size)

    @Test
    fun `every preset passes all 30 WCAG pairs in light and dark`() {
        val failures = buildList {
            for (p in Palette.entries) for (dark in listOf(false, true)) {
                val roles = p.roles(dark).byName()
                for ((fg, bg, need) in pairs) {
                    val r = contrast(roles.getValue(fg), roles.getValue(bg))
                    if (r < need) add("${p.presetId} ${if (dark) "dark" else "light"} $fg on $bg = ${"%.2f".format(r)} < $need")
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun channel(v: Float): Double = v.toDouble().let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    private fun luminance(c: Color): Double = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    private fun contrast(a: Color, b: Color): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }
}
```

`app/src/test/java/com/nobby/ring/ui/theme/TokenDriftTest.kt`:

```kotlin
package com.nobby.ring.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.nobby.ring.domain.model.Palette
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Guards against hand edits of the generated tables. Skipped where the git-ignored design bundle is absent. */
@RunWith(RobolectricTestRunner::class)
class TokenDriftTest {
    @Test
    fun `generated preset tables equal tokens json`() {
        val file = File("../design/bundle/tokens.json")
        assumeTrue("design bundle not present", file.exists())
        val root = JSONObject(file.readText())
        assertEquals(root.getString("\$version"), TOKENS_VERSION)
        val presets = root.getJSONObject("presets")
        for (p in Palette.entries) for (dark in listOf(false, true)) {
            val json = presets.getJSONObject(p.presetId).getJSONObject(if (dark) "dark" else "light")
            val roles = p.roles(dark).byName()
            assertEquals(json.length(), roles.size)
            roles.forEach { (name, color) -> assertEquals("${p.presetId}.$name", json.getString(name).uppercase(), color.hex()) }
        }
    }

    private fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)
}
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.theme.*" --tests "com.nobby.ring.data.ThemeStoreTest" --console=plain`
Expected: compilation FAILS (`Unresolved reference: BERRY`, `roles`, `presetId`, `extraColors`).

- [ ] **Step 5: Replace `domain/model/ThemeSpec.kt`**

```kotlin
package com.nobby.ring.domain.model

/** The five v2 colour presets (tokens.json `presets`). BERRY is the default. */
enum class Palette {
    BERRY, LILAC, PEACH, MATCHA, BLUEBERRY;

    companion object {
        /**
         * Reads a stored palette name. v1 names migrate (ROSE->BERRY, LAVENDER->LILAC, MINT->MATCHA, OCEAN->BLUEBERRY,
         * GRAPHITE->BERRY); missing or unknown names fall back to BERRY. Writers always store the v2 name.
         */
        fun fromStored(name: String?): Palette = when (name) {
            null -> BERRY
            "ROSE" -> BERRY
            "LAVENDER" -> LILAC
            "MINT" -> MATCHA
            "OCEAN" -> BLUEBERRY
            "GRAPHITE" -> BERRY
            else -> entries.firstOrNull { it.name == name } ?: BERRY
        }
    }
}

/** SYSTEM is shown as "Auto". */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class ThemeSpec(val palette: Palette = Palette.BERRY, val mode: ThemeMode = ThemeMode.SYSTEM)
```

- [ ] **Step 6: Use the migration in `ThemeStore.kt`**

Replace

```kotlin
            palette = p[paletteKey]?.let { runCatching { Palette.valueOf(it) }.getOrNull() } ?: Palette.ROSE,
```

with

```kotlin
            palette = Palette.fromStored(p[paletteKey]),
```

(`save()` already writes `theme.palette.name`, i.e. the v2 name.)

- [ ] **Step 7: Replace `ui/theme/Palettes.kt`**

```kotlin
package com.nobby.ring.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.nobby.ring.domain.model.Palette

/** tokens.json preset id of this palette (berry, lilac, peach, matcha, blueberry). */
val Palette.presetId: String get() = name.lowercase()

/** The 41 hand-specified roles of this preset in one mode, generated from tokens.json. Nothing is derived. */
fun Palette.roles(dark: Boolean): PresetRoles = PRESET_TABLE.getValue(presetId).let { if (dark) it.dark else it.light }

fun Palette.colorScheme(dark: Boolean): ColorScheme = roles(dark).toColorScheme(dark)

/** Swatch for compact pickers: the preset's primary in the given mode. */
fun Palette.swatch(dark: Boolean): Color = roles(dark).primary

/** Material 3 scheme with every role from the table; surfaceTint is transparent so elevation stays flat. */
fun PresetRoles.toColorScheme(dark: Boolean): ColorScheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    inversePrimary = inversePrimary,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    surfaceTint = Color.Transparent,
    inverseSurface = inverseSurface,
    inverseOnSurface = inverseOnSurface,
    error = error,
    onError = onError,
    errorContainer = errorContainer,
    onErrorContainer = onErrorContainer,
    outline = outline,
    outlineVariant = outlineVariant,
    scrim = scrim,
    surfaceBright = surfaceBright,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainerLowest = surfaceContainerLowest,
    surfaceDim = surfaceDim,
)
```

- [ ] **Step 8: Create `ui/theme/NobbyColors.kt`**

```kotlin
package com.nobby.ring.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.nobby.ring.domain.model.Palette

/** NoBby roles outside Material 3, per preset and mode, from the same generated table as the ColorScheme. */
@Immutable
data class NobbyExtraColors(
    val dark: Boolean,
    /** Calendar wear highlighter band (primaryContainer). */
    val wearBand: Color,
    /** Calendar pause highlighter band (secondaryContainer). */
    val pauseBand: Color,
    /** Sticker accent: washi tape, sparkle, note dot (tokens `accent`, butter in berry). */
    val sticker: Color,
    val mascotBody: Color,
    val mascotCheek: Color,
    /** Die-cut sticker rim around the mascot and props (tokens `stickerRim`). */
    val mascotRim: Color,
    /** Cocoa line colour of the mascot and doodles (tokens `ink`, cocoa in both modes). */
    val ink: Color,
    val shadow: Color,
    val overdue: Color,
    val overdueContainer: Color,
)

fun PresetRoles.extraColors(dark: Boolean): NobbyExtraColors = NobbyExtraColors(
    dark = dark,
    wearBand = primaryContainer,
    pauseBand = secondaryContainer,
    sticker = accent,
    mascotBody = mascot,
    mascotCheek = mascotCheek,
    mascotRim = stickerRim,
    ink = ink,
    shadow = shadow,
    overdue = error,
    overdueContainer = errorContainer,
)

fun Palette.extraColors(dark: Boolean): NobbyExtraColors = roles(dark).extraColors(dark)

val LocalNobbyExtraColors = staticCompositionLocalOf { Palette.BERRY.extraColors(dark = false) }

/** Access point for NoBby extras, next to MaterialTheme: `NobbyTheme.extra.sticker`. */
object NobbyTheme {
    val extra: NobbyExtraColors
        @Composable @ReadOnlyComposable get() = LocalNobbyExtraColors.current
}
```

- [ ] **Step 9: Replace `ui/theme/Theme.kt`**

```kotlin
package com.nobby.ring.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.nobby.ring.domain.model.ThemeMode
import com.nobby.ring.domain.model.ThemeSpec

@Composable
fun ThemeSpec.isDark(): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun NobbyTheme(theme: ThemeSpec, content: @Composable () -> Unit) {
    val dark = theme.isDark()
    val scheme = remember(theme.palette, dark) { theme.palette.colorScheme(dark) }
    val extra = remember(theme.palette, dark) { theme.palette.extraColors(dark) }
    CompositionLocalProvider(LocalNobbyExtraColors provides extra) {
        MaterialTheme(colorScheme = scheme, typography = NobbyTypography, content = content)
    }
}
```

- [ ] **Step 10: Rename palette labels and call sites**

`ui/components/Formatting.kt`: replace

```kotlin
@Composable fun Palette.label(): String = stringResource(
    when (this) {
        Palette.ROSE -> R.string.palette_rose
        Palette.LAVENDER -> R.string.palette_lavender
        Palette.MINT -> R.string.palette_mint
        Palette.OCEAN -> R.string.palette_ocean
        Palette.GRAPHITE -> R.string.palette_graphite
    },
)
```

with

```kotlin
@Composable fun Palette.label(): String = stringResource(
    when (this) {
        Palette.BERRY -> R.string.palette_berry
        Palette.LILAC -> R.string.palette_lilac
        Palette.PEACH -> R.string.palette_peach
        Palette.MATCHA -> R.string.palette_matcha
        Palette.BLUEBERRY -> R.string.palette_blueberry
    },
)
```

`res/values/strings.xml`: replace

```xml
    <string name="palette_rose">Rose</string>
    <string name="palette_lavender">Lavender</string>
    <string name="palette_mint">Mint</string>
    <string name="palette_ocean">Ocean</string>
    <string name="palette_graphite">Graphite</string>
```

with

```xml
    <string name="palette_berry">Berry</string>
    <string name="palette_lilac">Lilac</string>
    <string name="palette_peach">Peach</string>
    <string name="palette_matcha">Matcha</string>
    <string name="palette_blueberry">Blueberry</string>
```

`res/values-de/strings.xml`: replace

```xml
    <string name="palette_rose">Rosé</string>
    <string name="palette_lavender">Lavendel</string>
    <string name="palette_mint">Minze</string>
    <string name="palette_ocean">Ozean</string>
    <string name="palette_graphite">Graphit</string>
```

with

```xml
    <string name="palette_berry">Beere</string>
    <string name="palette_lilac">Flieder</string>
    <string name="palette_peach">Pfirsich</string>
    <string name="palette_matcha">Matcha</string>
    <string name="palette_blueberry">Blaubeere</string>
```

`Palette.accent()` no longer exists. In `ui/settings/AppearanceScreen.kt` and `ui/onboarding/OnboardingScreen.kt` replace `p.accent(false)` with `p.swatch(false)` and the import `com.nobby.ring.ui.theme.accent` with `com.nobby.ring.ui.theme.swatch`. In `widget/RingWidget.kt` replace `val accent = theme.palette.accent(dark)` with `val accent = theme.palette.swatch(dark)` and the import `com.nobby.ring.ui.theme.accent` with `com.nobby.ring.ui.theme.swatch` (the widget is restyled properly in Task 14).

`ui/onboarding/OnboardingViewModel.kt`: replace `    val palette: Palette = Palette.ROSE,` with `    val palette: Palette = Palette.BERRY,`.

`app/src/test/java/com/nobby/ring/ui/onboarding/OnboardingViewModelTest.kt`: replace both `Palette.MINT` with `Palette.MATCHA`.

Check nothing old is left: `grep -rnE "ROSE|LAVENDER|Palette\.MINT|OCEAN|GRAPHITE|\.accent\(" app/src --include=*.kt` must print only the migration lines in `ThemeSpec.kt` and the `"MINT"` key in `ThemeStoreTest.kt`.

- [ ] **Step 11: Paper window background (no white flash on start)**

Create `app/src/main/res/values/colors.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- tokens.json presets.berry.light.background: the window shows this before Compose draws. -->
    <color name="nobby_paper">#FBF5EE</color>
</resources>
```

Create `app/src/main/res/values-night/colors.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- tokens.json presets.berry.dark.background -->
    <color name="nobby_paper">#1C1517</color>
</resources>
```

Replace `app/src/main/res/values/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.NoBby" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:windowLightStatusBar">true</item>
        <item name="android:windowBackground">@color/nobby_paper</item>
    </style>
</resources>
```

Create `app/src/main/res/values-night/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.NoBby" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowLightStatusBar">false</item>
        <item name="android:windowBackground">@color/nobby_paper</item>
    </style>
</resources>
```

- [ ] **Step 12: Run the tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.theme.*" --tests "com.nobby.ring.data.ThemeStoreTest" --tests "com.nobby.ring.ui.onboarding.*" --console=plain`
Expected: `BUILD SUCCESSFUL`; ContrastTest 2, PaletteSchemeTest 5, ThemeStoreTest 3, TokenDriftTest 1 (not skipped in this working copy), Onboarding tests green.

- [ ] **Step 13: Verify and commit**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/nobby/ring/domain/model/ThemeSpec.kt app/src/main/java/com/nobby/ring/data/prefs/ThemeStore.kt \
  app/src/main/java/com/nobby/ring/ui/theme app/src/main/java/com/nobby/ring/ui/components/Formatting.kt \
  app/src/main/java/com/nobby/ring/ui/settings/AppearanceScreen.kt app/src/main/java/com/nobby/ring/ui/onboarding \
  app/src/main/java/com/nobby/ring/widget/RingWidget.kt app/src/main/res/values app/src/main/res/values-de app/src/main/res/values-night \
  app/src/test/java/com/nobby/ring/data/ThemeStoreTest.kt app/src/test/java/com/nobby/ring/ui/theme \
  app/src/test/java/com/nobby/ring/ui/onboarding/OnboardingViewModelTest.kt
git commit -m "feat(theme): five generated colour presets with v1 palette migration" -m "PresetTables.kt is generated from tokens.json by design/tools/gen-kotlin.js; contrast, completeness and drift tests guard it."
```

---
### Task 3: Copy pass (DE first, casual "du"), plurals, key parity test

This task writes the final copy for the whole app once, including the keys that later tasks use, so screen tasks only reference keys. Keys still used by v1 screens that later tasks replace stay until those tasks remove them (marked "legacy" in comments). Four unused v1 keys go now: `home_next_on`, `confirm_now`, `action_skip`, `reminder_disabled`.

**Files:**
- Modify (replace): `app/src/main/res/values/strings.xml`, `app/src/main/res/values-de/strings.xml`
- Modify: `app/src/main/java/com/nobby/ring/ui/components/Formatting.kt`, `app/src/main/java/com/nobby/ring/ui/settings/RemindersScreen.kt`, `app/src/main/java/com/nobby/ring/ui/settings/ScheduleSettingsScreen.kt`, `app/src/main/java/com/nobby/ring/ui/settings/CalendarCleanupScreen.kt`, `app/src/main/java/com/nobby/ring/ui/onboarding/OnboardingScreen.kt`, `app/src/main/java/com/nobby/ring/widget/RingWidget.kt`, `app/src/main/java/com/nobby/ring/notifications/Notifier.kt`
- Test: `app/src/test/java/com/nobby/ring/i18n/StringsParityTest.kt`, `app/src/test/java/com/nobby/ring/i18n/PluralsTest.kt`

**Interfaces:**
- Consumes: palette keys from Task 2.
- Produces: `@Composable fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String` in `com.nobby.ring.ui.components`; all string/plural keys listed in the two files below (later tasks use exactly these names).

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/nobby/ring/i18n/StringsParityTest.kt` (plain JVM; Gradle runs unit tests with the `app/` module as working directory):

```kotlin
package com.nobby.ring.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringsParityTest {
    private data class Res(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    private fun load(path: String): Res {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        fun elements(tag: String) = doc.getElementsByTagName(tag).let { list -> (0 until list.length).map { list.item(it) as Element } }
        val strings = elements("string").associate { it.getAttribute("name") to it.textContent }
        val plurals = elements("plurals").associate { p ->
            val items = p.getElementsByTagName("item")
            p.getAttribute("name") to (0 until items.length).map { items.item(it) as Element }
                .associate { it.getAttribute("quantity") to it.textContent }
        }
        return Res(strings, plurals)
    }

    private val en = load("src/main/res/values/strings.xml")
    private val de = load("src/main/res/values-de/strings.xml")
    private val placeholder = Regex("%(\\d+\\$)?[sd]")
    private fun args(texts: Collection<String>) = texts.flatMap { t -> placeholder.findAll(t).map { it.value } }.toSet()

    @Test
    fun `string keys are identical in EN and DE`() = assertEquals(en.strings.keys.sorted(), de.strings.keys.sorted())

    @Test
    fun `plural keys are identical in EN and DE`() = assertEquals(en.plurals.keys.sorted(), de.plurals.keys.sorted())

    @Test
    fun `every plural has one and other in both languages`() {
        (en.plurals + de.plurals.mapKeys { "de:${it.key}" }).forEach { (name, items) ->
            assertTrue("$name needs one+other, has ${items.keys}", items.keys.containsAll(listOf("one", "other")))
        }
    }

    @Test
    fun `placeholders match between EN and DE`() {
        en.strings.forEach { (k, v) -> assertEquals("string $k", args(listOf(v)), args(listOf(de.strings.getValue(k)))) }
        en.plurals.forEach { (k, v) -> assertEquals("plural $k", args(v.values), args(de.plurals.getValue(k).values)) }
    }
}
```

`app/src/test/java/com/nobby/ring/i18n/PluralsTest.kt`:

```kotlin
package com.nobby.ring.i18n

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class PluralsTest {
    private fun q(id: Int, n: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().resources.getQuantityString(id, n, *args)

    @Test
    @Config(qualifiers = "en")
    fun `english plurals for 0, 1 and n`() {
        assertEquals("days until removal", q(R.plurals.ring_until_remove, 0))
        assertEquals("day until removal", q(R.plurals.ring_until_remove, 1))
        assertEquals("days until removal", q(R.plurals.ring_until_remove, 10))
        assertEquals("0 days overdue", q(R.plurals.card_overdue_days, 0, 0))
        assertEquals("1 day overdue", q(R.plurals.card_overdue_days, 1, 1))
        assertEquals("2 days overdue", q(R.plurals.card_overdue_days, 2, 2))
        assertEquals("in 1 day", q(R.plurals.widget_in_days, 1, 1))
        assertEquals("1 day before at 20:00", q(R.plurals.reminder_day_before, 1, 1, "20:00"))
    }

    @Test
    @Config(qualifiers = "de")
    fun `german plurals for 0, 1 and n`() {
        assertEquals("Tage bis Ring raus", q(R.plurals.ring_until_remove, 0))
        assertEquals("Tag bis Ring raus", q(R.plurals.ring_until_remove, 1))
        assertEquals("Tage bis Ring raus", q(R.plurals.ring_until_remove, 10))
        assertEquals("0 Tage überfällig", q(R.plurals.card_overdue_days, 0, 0))
        assertEquals("1 Tag überfällig", q(R.plurals.card_overdue_days, 1, 1))
        assertEquals("2 Tage überfällig", q(R.plurals.card_overdue_days, 2, 2))
        assertEquals("in 1 Tag", q(R.plurals.widget_in_days, 1, 1))
        assertEquals("in 3 Tagen", q(R.plurals.widget_in_days, 3, 3))
        assertEquals("2 Tage vorher um 20:00", q(R.plurals.reminder_day_before, 2, 2, "20:00"))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.i18n.*" --console=plain`
Expected: compilation FAILS (`Unresolved reference: plurals`, no `<plurals>` exist yet).

- [ ] **Step 3: Replace `app/src/main/res/values-de/strings.xml` (written first)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">NoBby</string>

    <!-- Navigation + Aktionen -->
    <string name="nav_home">Status</string>
    <string name="nav_calendar">Kalender</string>
    <string name="nav_history">Verlauf</string>
    <string name="nav_settings">Einstellungen</string>
    <string name="action_back">Zurück</string>
    <string name="action_next">Weiter</string>
    <string name="action_done">Erledigt</string>
    <string name="action_save">Speichern</string>
    <string name="action_cancel">Abbrechen</string>
    <string name="action_delete">Löschen</string>
    <string name="action_finish">Los geht\'s</string>
    <string name="action_allow">Erlauben</string>
    <string name="action_close">Schließen</string>
    <string name="stepper_minus">Weniger</string>
    <string name="stepper_plus">Mehr</string>

    <!-- Termine + Phasen -->
    <string name="event_remove">Ring entfernen</string>
    <string name="event_insert">Ring einsetzen</string>
    <string name="event_swap">Ring wechseln</string>
    <string name="event_remove_short">Ring raus</string>
    <string name="event_insert_short">Ring rein</string>
    <string name="event_swap_short">Ringwechsel</string>
    <string name="phase_wear">Ring drin</string>
    <string name="phase_pause">Pause</string>
    <string name="cycle_day">Tag %1$d von %2$d</string>
    <string name="phase_day">%1$s · Tag %2$d von %3$d</string>
    <plurals name="phase_over_days">
        <item quantity="one">%1$s · %2$d Tag drüber</item>
        <item quantity="other">%1$s · %2$d Tage drüber</item>
    </plurals>
    <plurals name="phase_over_hours">
        <item quantity="one">%1$s · %2$d Std. drüber</item>
        <item quantity="other">%1$s · %2$d Std. drüber</item>
    </plurals>

    <!-- Home: Ring -->
    <plurals name="ring_until_remove">
        <item quantity="one">Tag bis Ring raus</item>
        <item quantity="other">Tage bis Ring raus</item>
    </plurals>
    <plurals name="ring_until_insert">
        <item quantity="one">Tag bis Ring rein</item>
        <item quantity="other">Tage bis Ring rein</item>
    </plurals>
    <plurals name="ring_until_swap">
        <item quantity="one">Tag bis Ringwechsel</item>
        <item quantity="other">Tage bis Ringwechsel</item>
    </plurals>
    <plurals name="ring_overdue_days">
        <item quantity="one">Tag überfällig</item>
        <item quantity="other">Tage überfällig</item>
    </plurals>
    <plurals name="ring_overdue_hours">
        <item quantity="one">Std. überfällig</item>
        <item quantity="other">Std. überfällig</item>
    </plurals>
    <string name="ring_today">Heute</string>
    <string name="ring_at_time">um %1$s</string>
    <string name="ring_since">seit %1$s</string>
    <string name="ring_check_protection">Schutz prüfen</string>

    <!-- Home: Karten -->
    <plurals name="home_next_in_days">
        <item quantity="one">%1$s · in %2$d Tag</item>
        <item quantity="other">%1$s · in %2$d Tagen</item>
    </plurals>
    <string name="home_due_today">Heute: %1$s um %2$s</string>
    <plurals name="card_overdue_days">
        <item quantity="one">%1$d Tag überfällig</item>
        <item quantity="other">%1$d Tage überfällig</item>
    </plurals>
    <plurals name="card_overdue_hours">
        <item quantity="one">%1$d Std. überfällig</item>
        <item quantity="other">%1$d Std. überfällig</item>
    </plurals>
    <string name="home_mark_done">Erledigt</string>
    <string name="home_skip_pause">Pause überspringen</string>
    <string name="home_skip_pause_cancel">Pause doch machen</string>
    <string name="home_snooze">Später</string>
    <string name="home_overdue_body">Geplant war %1$s. Tippe auf Erledigt, sobald du es gemacht hast.</string>
    <string name="home_overdue_safety_wear">Der Ring ist seit mehr als 28 Tagen drin. Der Schutz kann verringert sein – lies den Beipackzettel; eventuell brauchst du eine zusätzliche Verhütungsmethode. Frag in der Arztpraxis oder Apotheke nach.</string>
    <string name="home_overdue_safety_pause">Die ringfreie Pause dauert länger als 7 Tage. Der Schutz kann verringert sein – lies den Beipackzettel; eventuell brauchst du eine zusätzliche Verhütungsmethode. Frag in der Arztpraxis oder Apotheke nach.</string>
    <!-- legacy v1 Home, entfernt in Task 9 -->
    <string name="home_days_remaining">Tage noch</string>
    <string name="home_day_remaining">Tag noch</string>
    <string name="home_today">heute</string>
    <string name="home_overdue_days">Tage überfällig</string>
    <string name="home_overdue_title">Überfällig: %1$s</string>

    <!-- Bestätigen -->
    <string name="confirm_title">Du warst spät dran</string>
    <string name="confirm_body">Geplant war %1$s. Sollen die nächsten Termine um %2$s rutschen oder bleiben?</string>
    <string name="confirm_title_early">Du warst früh dran</string>
    <string name="confirm_body_early">Geplant war %1$s. NoBby passt die nächsten Termine an, damit deine Pause im Rahmen bleibt.</string>
    <string name="confirm_shift">Termine verschieben</string>
    <string name="confirm_keep">Plan behalten</string>
    <string name="confirm_when">Wann hast du es gemacht?</string>

    <!-- Erledigt-Sheet -->
    <string name="done_title_remove">Yay, Ring ist raus!</string>
    <string name="done_title_insert">Yay, Ring ist drin!</string>
    <string name="done_title_swap">Yay, neuer Ring ist drin!</string>
    <string name="done_lead">Erledigt! Gut gemacht 💗</string>
    <string name="done_next">%1$s · %2$s · ich erinnere dich</string>
    <string name="done_autoclose">Schließt sich gleich von selbst</string>

    <!-- Berechtigungen -->
    <string name="exact_alarm_warning">Genaue Wecker sind aus. Erinnerungen kommen evtl. später.</string>
    <string name="notifications_disabled_warning">Mitteilungen sind aus. Erinnerungen erscheinen nicht.</string>

    <!-- Onboarding -->
    <string name="onb_step_first">Hallo! · Schritt %1$d von %2$d</string>
    <string name="onb_step">Schritt %1$d von %2$d</string>
    <string name="onb_rhythm_title">Dein Rhythmus</string>
    <string name="onb_rhythm_body">Wie lange bleibt der Ring drin, wie lange machst du Pause? Du kannst das später ändern.</string>
    <string name="onb_wear_days">Ring drin (Tage)</string>
    <string name="onb_wear_hint">meistens 21</string>
    <string name="onb_pause_days">Pause (Tage)</string>
    <string name="onb_pause_hint">0 = durchgehend</string>
    <string name="onb_time_of_day">Uhrzeit für Raus und Rein</string>
    <string name="onb_safety_hint">Laut Beipackzettel: mehr als 28 Tage tragen oder mehr als 7 Tage Pause kann den Schutz verringern. NoBby warnt dich dann.</string>
    <string name="onb_safety_wear">Mehr als 28 Tage tragen kann den Schutz verringern.</string>
    <string name="onb_safety_pause">Mehr als 7 Tage Pause kann den Schutz verringern.</string>
    <string name="onb_where_title">Wo stehst du?</string>
    <string name="onb_where_body">Wann hast du den Ring zuletzt eingesetzt? Daraus rechnet NoBby alles Weitere.</string>
    <string name="onb_anchor_title">Wann kam der aktuelle Ring rein?</string>
    <string name="onb_anchor_date_label">Eingesetzt am</string>
    <string name="onb_anchor_date">Datum</string>
    <string name="onb_anchor_time">Uhrzeit</string>
    <string name="onb_reminders_title">Ich sag Bescheid</string>
    <string name="onb_reminders_body">Wann soll NoBby dich erinnern? Alles lässt sich später ändern.</string>
    <string name="onb_reminder_line">%1$s (%2$s)</string>
    <plurals name="onb_overdue_line">
        <item quantity="one">Wenn überfällig: jede Stunde, bis du „Erledigt“ tippst</item>
        <item quantity="other">Wenn überfällig: alle %1$d Std., bis du „Erledigt“ tippst</item>
    </plurals>
    <string name="onb_permission_body">Damit ich pünktlich klingle, brauche ich Mitteilungen.</string>
    <string name="onb_look_title">Mach\'s dir schön</string>
    <string name="onb_look_body">Such dir eine Farbe aus. Die Pause bleibt immer gut zu erkennen.</string>
    <string name="onb_calendar_hint">Kalender-Sync kannst du später in den Einstellungen einschalten.</string>
    <string name="onb_calendar_connect">Kalender verbinden</string>
    <!-- legacy v1 Onboarding, entfernt in Task 13 -->
    <string name="onb_welcome_title">Hallo, ich bin NoBby</string>
    <string name="onb_welcome_body">Ich erinnere dich ans Rausnehmen und Einsetzen, halte deinen Kalender aktuell und melde mich, wenn du was vergisst.</string>
    <string name="onb_schedule_title">Dein Rhythmus</string>
    <string name="onb_notifications_permission">Mitteilungen erlauben</string>

    <!-- Erinnerungen -->
    <plurals name="reminder_day_before">
        <item quantity="one">%1$d Tag vorher um %2$s</item>
        <item quantity="other">%1$d Tage vorher um %2$s</item>
    </plurals>
    <string name="reminder_same_day">Am Tag selbst um %1$s</string>
    <plurals name="reminder_hours_before">
        <item quantity="one">%1$d Std. vorher</item>
        <item quantity="other">%1$d Std. vorher</item>
    </plurals>
    <string name="reminder_target_any">Alle Termine</string>
    <string name="reminder_target_remove">Nur Rausnehmen</string>
    <string name="reminder_target_insert">Nur Einsetzen</string>
    <string name="importance_low">Leise</string>
    <string name="importance_normal">Normal</string>
    <string name="importance_high">Laut</string>
    <string name="importance_urgent">Dringend</string>
    <string name="reminder_add">Erinnerung hinzufügen</string>
    <string name="reminder_edit">Erinnerung bearbeiten</string>
    <string name="reminder_mode_days">Tage vorher</string>
    <string name="reminder_mode_hours">Stunden vorher</string>
    <string name="reminder_enabled">An</string>
    <string name="reminder_row_meta">%1$s · %2$s</string>
    <string name="reminder_row_meta_off">%1$s · %2$s · aus</string>
    <string name="overdue_repeat">Wenn überfällig: erinnern alle (Std.)</string>
    <string name="overdue_snooze">Später heißt (Std.)</string>

    <!-- Mitteilungen -->
    <string name="channel_low">Leise Erinnerungen</string>
    <string name="channel_normal">Erinnerungen</string>
    <string name="channel_high">Wichtige Erinnerungen</string>
    <string name="channel_urgent">Überfällig-Alarme</string>
    <string name="notif_reminder_title">%1$s</string>
    <string name="notif_reminder_body">Geplant für %1$s</string>
    <string name="notif_overdue_title">Überfällig: %1$s</string>
    <string name="notif_overdue_body">Geplant war %1$s. Tippe auf Erledigt, sobald du es gemacht hast.</string>
    <string name="notif_overdue_body_with_safety">%1$s\n%2$s</string>
    <string name="notif_overdue_safety_wear">Der Ring ist seit mehr als 28 Tagen drin. Der Schutz kann verringert sein – lies den Beipackzettel; eventuell brauchst du eine zusätzliche Verhütungsmethode. Frag in der Arztpraxis oder Apotheke nach.</string>
    <string name="notif_overdue_safety_pause">Die ringfreie Pause dauert länger als 7 Tage. Der Schutz kann verringert sein – lies den Beipackzettel; eventuell brauchst du eine zusätzliche Verhütungsmethode. Frag in der Arztpraxis oder Apotheke nach.</string>
    <string name="notif_overdue_safety_short">Der Schutz kann verringert sein</string>

    <!-- Kalender -->
    <string name="calendar_today">Heute</string>
    <string name="calendar_month_title">%1$s %2$d</string>
    <string name="calendar_prev_month">Vorheriger Monat</string>
    <string name="calendar_next_month">Nächster Monat</string>
    <string name="legend_wear">Ring drin</string>
    <string name="legend_pause">Pause</string>
    <string name="legend_event">Raus / Rein</string>
    <string name="legend_note">Notiz</string>
    <string name="calendar_note_label">Notiz</string>
    <string name="calendar_notes_hint">Schreib hier etwas …</string>
    <string name="calendar_note_empty">Noch keine Notiz für diesen Tag.</string>
    <string name="calendar_cell_a11y">%1$s, %2$s</string>
    <!-- legacy v1 Kalender, entfernt in Task 10 -->
    <string name="calendar_notes_title">Notiz – %1$s</string>

    <!-- Verlauf -->
    <string name="history_empty_title">Noch nichts im Verlauf</string>
    <string name="history_empty_body">Sobald du „Erledigt“ tippst, landet es hier.</string>
    <string name="history_removed">Ring entfernt</string>
    <string name="history_inserted">Ring eingesetzt</string>
    <string name="history_swapped">Ring gewechselt</string>
    <string name="history_row_meta">%1$s · geplant %2$s</string>
    <string name="history_on_time">pünktlich</string>
    <string name="history_late">%1$s zu spät</string>
    <string name="history_early">%1$s zu früh</string>
    <!-- legacy v1 Verlauf, entfernt in Task 11 -->
    <string name="history_empty">Noch nichts im Verlauf.</string>

    <!-- Einstellungen -->
    <string name="settings_group_ring">Ring</string>
    <string name="settings_group_look">Aussehen</string>
    <string name="settings_group_more">Mehr</string>
    <string name="settings_schedule">Zyklus &amp; Zeitplan</string>
    <string name="settings_schedule_summary">%1$d + %2$d Tage · %3$s</string>
    <string name="settings_reminders">Erinnerungen</string>
    <plurals name="settings_reminders_summary">
        <item quantity="one">%1$d aktiv</item>
        <item quantity="other">%1$d aktiv</item>
    </plurals>
    <string name="settings_overdue">Wenn überfällig</string>
    <string name="settings_overdue_summary">alle %1$d Std. · später %2$d Std.</string>
    <string name="settings_appearance">Farbe &amp; Modus</string>
    <string name="settings_appearance_summary">%1$s · %2$s</string>
    <string name="settings_calendar">Kalender-Sync</string>
    <string name="settings_widget">Widget</string>
    <string name="settings_about">Über NoBby</string>
    <string name="settings_about_version">Version %1$s</string>
    <string name="settings_about_body">NoBby ist eine Erinnerungshilfe, keine medizinische Beratung. Halte dich an den Beipackzettel und frag im Zweifel in der Arztpraxis oder Apotheke.</string>
    <string name="settings_licenses">Schriftlizenzen (OFL)</string>
    <string name="schedule_preview_title">Vorschau</string>
    <string name="schedule_preview_body">So sehen deine nächsten Termine aus:</string>
    <string name="schedule_preview_row">%1$s — %2$s</string>
    <string name="schedule_apply">Übernehmen und Kalender aktualisieren</string>
    <string name="schedule_horizon">Vorausplanen (Monate)</string>
    <string name="appearance_palette">Farbe</string>
    <string name="appearance_mode">Modus</string>
    <string name="appearance_preview">Vorschau</string>
    <string name="mode_system">Auto</string>
    <string name="mode_light">Hell</string>
    <string name="mode_dark">Dunkel</string>
    <string name="palette_berry">Beere</string>
    <string name="palette_lilac">Flieder</string>
    <string name="palette_peach">Pfirsich</string>
    <string name="palette_matcha">Matcha</string>
    <string name="palette_blueberry">Blaubeere</string>

    <!-- Kalender-Sync -->
    <string name="calendar_sync_enable">In meinen Kalender eintragen</string>
    <string name="calendar_sync_choose">Kalender wählen</string>
    <string name="calendar_sync_permission">Für den Sync braucht NoBby Zugriff auf deinen Kalender.</string>
    <string name="calendar_sync_now">Jetzt abgleichen</string>
    <string name="calendar_sync_last">Zuletzt abgeglichen: %1$s</string>
    <string name="calendar_cleanup">Alte Einträge aufräumen</string>
    <string name="cleanup_query">Titel enthält / Regex</string>
    <string name="cleanup_from">Von</string>
    <string name="cleanup_to">Bis</string>
    <string name="cleanup_search">Suchen</string>
    <string name="cleanup_delete_selected">Ausgewählte löschen (%1$d)</string>
    <string name="cleanup_none">Keine passenden Einträge.</string>
    <string name="cleanup_delete_failed">Ein paar Einträge ließen sich nicht löschen.</string>
    <string name="cleanup_select_all">Alle auswählen</string>
    <plurals name="cleanup_confirm_title">
        <item quantity="one">%1$d Eintrag löschen?</item>
        <item quantity="other">%1$d Einträge löschen?</item>
    </plurals>
    <string name="cleanup_confirm_body">Das entfernt sie aus deinem Kalender. Rückgängig geht nicht.</string>

    <!-- Widget -->
    <string name="widget_mode">Layout</string>
    <string name="widget_mode_single">Ein großer Countdown</string>
    <string name="widget_mode_multi">Bis zu drei Zeilen</string>
    <string name="widget_rows">Zeilen</string>
    <string name="widget_row_next_any">Nächster Schritt</string>
    <string name="widget_row_next_remove">Nächstes Rausnehmen</string>
    <string name="widget_row_next_insert">Nächstes Einsetzen</string>
    <string name="widget_row_overdue">Überfällig</string>
    <string name="widget_row_cycle_day">Zyklustag</string>
    <string name="widget_description">Countdown bis zum nächsten Ring-Schritt</string>
    <string name="widget_none">Alles erledigt</string>
    <string name="widget_day_of">Tag %1$d/%2$d</string>
    <plurals name="widget_in_days">
        <item quantity="one">in %1$d Tag</item>
        <item quantity="other">in %1$d Tagen</item>
    </plurals>
    <plurals name="widget_overdue_days">
        <item quantity="one">%1$d Tag drüber</item>
        <item quantity="other">%1$d Tage drüber</item>
    </plurals>
    <plurals name="widget_overdue_hours">
        <item quantity="one">%1$d Std. drüber</item>
        <item quantity="other">%1$d Std. drüber</item>
    </plurals>

    <!-- Dauer -->
    <plurals name="duration_days">
        <item quantity="one">%1$d Tag</item>
        <item quantity="other">%1$d Tage</item>
    </plurals>
    <plurals name="duration_hours">
        <item quantity="one">%1$d Std.</item>
        <item quantity="other">%1$d Std.</item>
    </plurals>
    <string name="duration_minutes">%1$d Min.</string>
</resources>
```

- [ ] **Step 4: Replace `app/src/main/res/values/strings.xml` (EN mirrors DE)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">NoBby</string>

    <!-- navigation + actions -->
    <string name="nav_home">Status</string>
    <string name="nav_calendar">Calendar</string>
    <string name="nav_history">History</string>
    <string name="nav_settings">Settings</string>
    <string name="action_back">Back</string>
    <string name="action_next">Next</string>
    <string name="action_done">Done</string>
    <string name="action_save">Save</string>
    <string name="action_cancel">Cancel</string>
    <string name="action_delete">Delete</string>
    <string name="action_finish">Let\'s go</string>
    <string name="action_allow">Allow</string>
    <string name="action_close">Close</string>
    <string name="stepper_minus">Less</string>
    <string name="stepper_plus">More</string>

    <!-- events + phases -->
    <string name="event_remove">Remove ring</string>
    <string name="event_insert">Insert ring</string>
    <string name="event_swap">Swap ring</string>
    <string name="event_remove_short">Ring out</string>
    <string name="event_insert_short">Ring in</string>
    <string name="event_swap_short">Ring swap</string>
    <string name="phase_wear">Ring in</string>
    <string name="phase_pause">Break</string>
    <string name="cycle_day">Day %1$d of %2$d</string>
    <string name="phase_day">%1$s · day %2$d of %3$d</string>
    <plurals name="phase_over_days">
        <item quantity="one">%1$s · %2$d day over</item>
        <item quantity="other">%1$s · %2$d days over</item>
    </plurals>
    <plurals name="phase_over_hours">
        <item quantity="one">%1$s · %2$d h over</item>
        <item quantity="other">%1$s · %2$d h over</item>
    </plurals>

    <!-- home: ring -->
    <plurals name="ring_until_remove">
        <item quantity="one">day until removal</item>
        <item quantity="other">days until removal</item>
    </plurals>
    <plurals name="ring_until_insert">
        <item quantity="one">day until insertion</item>
        <item quantity="other">days until insertion</item>
    </plurals>
    <plurals name="ring_until_swap">
        <item quantity="one">day until swap</item>
        <item quantity="other">days until swap</item>
    </plurals>
    <plurals name="ring_overdue_days">
        <item quantity="one">day overdue</item>
        <item quantity="other">days overdue</item>
    </plurals>
    <plurals name="ring_overdue_hours">
        <item quantity="one">hour overdue</item>
        <item quantity="other">hours overdue</item>
    </plurals>
    <string name="ring_today">Today</string>
    <string name="ring_at_time">at %1$s</string>
    <string name="ring_since">since %1$s</string>
    <string name="ring_check_protection">Check protection</string>

    <!-- home: cards -->
    <plurals name="home_next_in_days">
        <item quantity="one">%1$s · in %2$d day</item>
        <item quantity="other">%1$s · in %2$d days</item>
    </plurals>
    <string name="home_due_today">Today: %1$s at %2$s</string>
    <plurals name="card_overdue_days">
        <item quantity="one">%1$d day overdue</item>
        <item quantity="other">%1$d days overdue</item>
    </plurals>
    <plurals name="card_overdue_hours">
        <item quantity="one">%1$d hour overdue</item>
        <item quantity="other">%1$d hours overdue</item>
    </plurals>
    <string name="home_mark_done">Done</string>
    <string name="home_skip_pause">Skip break</string>
    <string name="home_skip_pause_cancel">Keep the break</string>
    <string name="home_snooze">Snooze</string>
    <string name="home_overdue_body">Planned for %1$s. Tap Done as soon as you have.</string>
    <string name="home_overdue_safety_wear">The ring has been in for more than 28 days. Protection may be reduced — check your product leaflet; you may need backup contraception. Ask your doctor or pharmacist.</string>
    <string name="home_overdue_safety_pause">The ring-free break is longer than 7 days. Protection may be reduced — check your product leaflet; you may need backup contraception. Ask your doctor or pharmacist.</string>
    <!-- legacy v1 Home, removed in Task 9 -->
    <string name="home_days_remaining">days to go</string>
    <string name="home_day_remaining">day to go</string>
    <string name="home_today">today</string>
    <string name="home_overdue_days">days overdue</string>
    <string name="home_overdue_title">Overdue: %1$s</string>

    <!-- confirm -->
    <string name="confirm_title">You were late</string>
    <string name="confirm_body">This was planned for %1$s. Move the next dates by %2$s, or keep them?</string>
    <string name="confirm_title_early">You were early</string>
    <string name="confirm_body_early">This was planned for %1$s. NoBby adjusts the next dates so your break stays within the limit.</string>
    <string name="confirm_shift">Move dates</string>
    <string name="confirm_keep">Keep plan</string>
    <string name="confirm_when">When did you do it?</string>

    <!-- done sheet -->
    <string name="done_title_remove">Yay, ring\'s out!</string>
    <string name="done_title_insert">Yay, ring\'s in!</string>
    <string name="done_title_swap">Yay, new ring\'s in!</string>
    <string name="done_lead">Done! Nice one 💗</string>
    <string name="done_next">%1$s · %2$s · I\'ll remind you</string>
    <string name="done_autoclose">Closes by itself</string>

    <!-- permissions -->
    <string name="exact_alarm_warning">Exact alarms are off. Reminders may be late.</string>
    <string name="notifications_disabled_warning">Notifications are off. Reminders won\'t show.</string>

    <!-- onboarding -->
    <string name="onb_step_first">Hi! · Step %1$d of %2$d</string>
    <string name="onb_step">Step %1$d of %2$d</string>
    <string name="onb_rhythm_title">Your rhythm</string>
    <string name="onb_rhythm_body">How long does the ring stay in, how long is your break? You can change this later.</string>
    <string name="onb_wear_days">Ring in (days)</string>
    <string name="onb_wear_hint">usually 21</string>
    <string name="onb_pause_days">Break (days)</string>
    <string name="onb_pause_hint">0 = continuous</string>
    <string name="onb_time_of_day">Time for out and in</string>
    <string name="onb_safety_hint">Per the leaflet: more than 28 days of wear or a break longer than 7 days can reduce protection. NoBby warns you then.</string>
    <string name="onb_safety_wear">More than 28 days of wear can reduce protection.</string>
    <string name="onb_safety_pause">A break longer than 7 days can reduce protection.</string>
    <string name="onb_where_title">Where are you now?</string>
    <string name="onb_where_body">When did you last insert the ring? NoBby works out everything else from that.</string>
    <string name="onb_anchor_title">When did the current ring go in?</string>
    <string name="onb_anchor_date_label">Inserted on</string>
    <string name="onb_anchor_date">Date</string>
    <string name="onb_anchor_time">Time</string>
    <string name="onb_reminders_title">I\'ll let you know</string>
    <string name="onb_reminders_body">When should NoBby remind you? You can change it all later.</string>
    <string name="onb_reminder_line">%1$s (%2$s)</string>
    <plurals name="onb_overdue_line">
        <item quantity="one">If overdue: every hour until you tap Done</item>
        <item quantity="other">If overdue: every %1$d hours until you tap Done</item>
    </plurals>
    <string name="onb_permission_body">To ring on time, I need notifications.</string>
    <string name="onb_look_title">Make it yours</string>
    <string name="onb_look_body">Pick a colour. The break always stays easy to spot.</string>
    <string name="onb_calendar_hint">You can turn on calendar sync later in Settings.</string>
    <string name="onb_calendar_connect">Connect a calendar</string>
    <!-- legacy v1 onboarding, removed in Task 13 -->
    <string name="onb_welcome_title">Hi, I\'m NoBby</string>
    <string name="onb_welcome_body">I remind you when the ring goes out and in, keep your calendar up to date and nudge you if you forget.</string>
    <string name="onb_schedule_title">Your rhythm</string>
    <string name="onb_notifications_permission">Allow notifications</string>

    <!-- reminders -->
    <plurals name="reminder_day_before">
        <item quantity="one">%1$d day before at %2$s</item>
        <item quantity="other">%1$d days before at %2$s</item>
    </plurals>
    <string name="reminder_same_day">Same day at %1$s</string>
    <plurals name="reminder_hours_before">
        <item quantity="one">%1$d hour before</item>
        <item quantity="other">%1$d hours before</item>
    </plurals>
    <string name="reminder_target_any">All events</string>
    <string name="reminder_target_remove">Removal only</string>
    <string name="reminder_target_insert">Insertion only</string>
    <string name="importance_low">Quiet</string>
    <string name="importance_normal">Normal</string>
    <string name="importance_high">Loud</string>
    <string name="importance_urgent">Urgent</string>
    <string name="reminder_add">Add reminder</string>
    <string name="reminder_edit">Edit reminder</string>
    <string name="reminder_mode_days">Days before</string>
    <string name="reminder_mode_hours">Hours before</string>
    <string name="reminder_enabled">On</string>
    <string name="reminder_row_meta">%1$s · %2$s</string>
    <string name="reminder_row_meta_off">%1$s · %2$s · off</string>
    <string name="overdue_repeat">If overdue: remind every (hours)</string>
    <string name="overdue_snooze">Snooze means (hours)</string>

    <!-- notifications -->
    <string name="channel_low">Quiet reminders</string>
    <string name="channel_normal">Reminders</string>
    <string name="channel_high">Important reminders</string>
    <string name="channel_urgent">Overdue alerts</string>
    <string name="notif_reminder_title">%1$s</string>
    <string name="notif_reminder_body">Planned for %1$s</string>
    <string name="notif_overdue_title">Overdue: %1$s</string>
    <string name="notif_overdue_body">Planned for %1$s. Tap Done once you have.</string>
    <string name="notif_overdue_body_with_safety">%1$s\n%2$s</string>
    <string name="notif_overdue_safety_wear">The ring has been in for more than 28 days. Protection may be reduced — check your product leaflet; you may need backup contraception. Ask your doctor or pharmacist.</string>
    <string name="notif_overdue_safety_pause">The ring-free break is longer than 7 days. Protection may be reduced — check your product leaflet; you may need backup contraception. Ask your doctor or pharmacist.</string>
    <string name="notif_overdue_safety_short">Protection may be reduced</string>

    <!-- calendar -->
    <string name="calendar_today">Today</string>
    <string name="calendar_month_title">%1$s %2$d</string>
    <string name="calendar_prev_month">Previous month</string>
    <string name="calendar_next_month">Next month</string>
    <string name="legend_wear">Ring in</string>
    <string name="legend_pause">Break</string>
    <string name="legend_event">Out / in</string>
    <string name="legend_note">Note</string>
    <string name="calendar_note_label">Note</string>
    <string name="calendar_notes_hint">Write something…</string>
    <string name="calendar_note_empty">No note for this day yet.</string>
    <string name="calendar_cell_a11y">%1$s, %2$s</string>
    <!-- legacy v1 calendar, removed in Task 10 -->
    <string name="calendar_notes_title">Note – %1$s</string>

    <!-- history -->
    <string name="history_empty_title">Nothing here yet</string>
    <string name="history_empty_body">Once you tap Done, it shows up here.</string>
    <string name="history_removed">Ring removed</string>
    <string name="history_inserted">Ring inserted</string>
    <string name="history_swapped">Ring swapped</string>
    <string name="history_row_meta">%1$s · planned %2$s</string>
    <string name="history_on_time">on time</string>
    <string name="history_late">%1$s late</string>
    <string name="history_early">%1$s early</string>
    <!-- legacy v1 history, removed in Task 11 -->
    <string name="history_empty">Nothing here yet.</string>

    <!-- settings -->
    <string name="settings_group_ring">Ring</string>
    <string name="settings_group_look">Look</string>
    <string name="settings_group_more">More</string>
    <string name="settings_schedule">Cycle &amp; schedule</string>
    <string name="settings_schedule_summary">%1$d + %2$d days · %3$s</string>
    <string name="settings_reminders">Reminders</string>
    <plurals name="settings_reminders_summary">
        <item quantity="one">%1$d active</item>
        <item quantity="other">%1$d active</item>
    </plurals>
    <string name="settings_overdue">When overdue</string>
    <string name="settings_overdue_summary">every %1$d h · snooze %2$d h</string>
    <string name="settings_appearance">Colour &amp; mode</string>
    <string name="settings_appearance_summary">%1$s · %2$s</string>
    <string name="settings_calendar">Calendar sync</string>
    <string name="settings_widget">Widget</string>
    <string name="settings_about">About NoBby</string>
    <string name="settings_about_version">Version %1$s</string>
    <string name="settings_about_body">NoBby is a reminder tool, not medical advice. Follow your product leaflet and ask your doctor or pharmacist when in doubt.</string>
    <string name="settings_licenses">Font licences (OFL)</string>
    <string name="schedule_preview_title">Preview</string>
    <string name="schedule_preview_body">This is what your next dates look like:</string>
    <string name="schedule_preview_row">%1$s — %2$s</string>
    <string name="schedule_apply">Apply and update calendar</string>
    <string name="schedule_horizon">Plan ahead (months)</string>
    <string name="appearance_palette">Colour</string>
    <string name="appearance_mode">Mode</string>
    <string name="appearance_preview">Preview</string>
    <string name="mode_system">Auto</string>
    <string name="mode_light">Light</string>
    <string name="mode_dark">Dark</string>
    <string name="palette_berry">Berry</string>
    <string name="palette_lilac">Lilac</string>
    <string name="palette_peach">Peach</string>
    <string name="palette_matcha">Matcha</string>
    <string name="palette_blueberry">Blueberry</string>

    <!-- calendar sync -->
    <string name="calendar_sync_enable">Add to my calendar</string>
    <string name="calendar_sync_choose">Choose calendar</string>
    <string name="calendar_sync_permission">NoBby needs calendar access to sync.</string>
    <string name="calendar_sync_now">Sync now</string>
    <string name="calendar_sync_last">Last synced: %1$s</string>
    <string name="calendar_cleanup">Clean up old entries</string>
    <string name="cleanup_query">Title contains / regex</string>
    <string name="cleanup_from">From</string>
    <string name="cleanup_to">To</string>
    <string name="cleanup_search">Search</string>
    <string name="cleanup_delete_selected">Delete selected (%1$d)</string>
    <string name="cleanup_none">No matching entries.</string>
    <string name="cleanup_delete_failed">A few entries could not be deleted.</string>
    <string name="cleanup_select_all">Select all</string>
    <plurals name="cleanup_confirm_title">
        <item quantity="one">Delete %1$d entry?</item>
        <item quantity="other">Delete %1$d entries?</item>
    </plurals>
    <string name="cleanup_confirm_body">This removes them from your calendar. There is no undo.</string>

    <!-- widget -->
    <string name="widget_mode">Layout</string>
    <string name="widget_mode_single">One big countdown</string>
    <string name="widget_mode_multi">Up to three rows</string>
    <string name="widget_rows">Rows</string>
    <string name="widget_row_next_any">Next step</string>
    <string name="widget_row_next_remove">Next removal</string>
    <string name="widget_row_next_insert">Next insertion</string>
    <string name="widget_row_overdue">Overdue</string>
    <string name="widget_row_cycle_day">Cycle day</string>
    <string name="widget_description">Countdown to your next ring step</string>
    <string name="widget_none">All done</string>
    <string name="widget_day_of">Day %1$d/%2$d</string>
    <plurals name="widget_in_days">
        <item quantity="one">in %1$d day</item>
        <item quantity="other">in %1$d days</item>
    </plurals>
    <plurals name="widget_overdue_days">
        <item quantity="one">%1$d day over</item>
        <item quantity="other">%1$d days over</item>
    </plurals>
    <plurals name="widget_overdue_hours">
        <item quantity="one">%1$d h over</item>
        <item quantity="other">%1$d h over</item>
    </plurals>

    <!-- durations -->
    <plurals name="duration_days">
        <item quantity="one">%1$d day</item>
        <item quantity="other">%1$d days</item>
    </plurals>
    <plurals name="duration_hours">
        <item quantity="one">%1$d h</item>
        <item quantity="other">%1$d h</item>
    </plurals>
    <string name="duration_minutes">%1$d min</string>
</resources>
```

Note on `onb_overdue_line`: the `one` item has no `%1$d` ("every hour"), the `other` item has it; the parity test compares the union of placeholders, which is `{%1$d}` in both languages.

- [ ] **Step 5: Add the `plural()` helper and switch durations to plurals (`Formatting.kt`)**

Add these imports to `ui/components/Formatting.kt`: `import androidx.annotation.PluralsRes`.

Add after the `formatDuration(d: Duration)` composable:

```kotlin
/** Quantity string for composables; recomposes on configuration (locale) changes. */
@Composable
fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String {
    LocalConfiguration.current
    return LocalContext.current.resources.getQuantityString(id, count, *args)
}
```

Replace the body of `formatDuration(d: Duration, res: Resources)`:

```kotlin
fun formatDuration(d: Duration, res: Resources): String {
    val days = d.inWholeDays.toInt()
    val hours = (d.inWholeHours % 24).toInt()
    val minutes = d.inWholeMinutes % 60
    return buildList {
        if (days > 0) add(res.getQuantityString(R.plurals.duration_days, days, days))
        if (hours > 0) add(res.getQuantityString(R.plurals.duration_hours, hours, hours))
        if (days == 0 && minutes > 0) add(res.getString(R.string.duration_minutes, minutes))
    }.joinToString(" ").ifEmpty { res.getString(R.string.duration_minutes, 0L) }
}
```

(Joining already-localised unit parts with a space is a list of values, not sentence concatenation.)

- [ ] **Step 6: Update the call sites whose keys became plurals or lost concatenation**

`ui/settings/RemindersScreen.kt` — replace

```kotlin
                val off = if (rule.enabled) "" else " · " + stringResource(R.string.reminder_disabled)
                ListItem(
                    headlineContent = { Text(ruleText(rule)) },
                    supportingContent = { Text("${targetText(rule.target)} · ${rule.importance.label()}$off") },
```

with

```kotlin
                val meta = stringResource(
                    if (rule.enabled) R.string.reminder_row_meta else R.string.reminder_row_meta_off,
                    targetText(rule.target),
                    rule.importance.label(),
                )
                ListItem(
                    headlineContent = { Text(ruleText(rule)) },
                    supportingContent = { Text(meta) },
```

and replace

```kotlin
    rule.offsetHours != null -> stringResource(R.string.reminder_hours_before, rule.offsetHours)
    rule.offsetDays == 0 -> stringResource(R.string.reminder_same_day, formatTime(rule.timeOfDay ?: LocalTime(20, 0)))
    else -> stringResource(R.string.reminder_day_before, rule.offsetDays, formatTime(rule.timeOfDay ?: LocalTime(20, 0)))
```

with

```kotlin
    rule.offsetHours != null -> plural(R.plurals.reminder_hours_before, rule.offsetHours, rule.offsetHours)
    rule.offsetDays == 0 -> stringResource(R.string.reminder_same_day, formatTime(rule.timeOfDay ?: LocalTime(20, 0)))
    else -> plural(R.plurals.reminder_day_before, rule.offsetDays, rule.offsetDays, formatTime(rule.timeOfDay ?: LocalTime(20, 0)))
```

and add `import com.nobby.ring.ui.components.plural`.

`ui/settings/ScheduleSettingsScreen.kt` — replace

```kotlin
                    d.preview.forEach { e -> Text("• ${e.kind.label()} — ${formatDateTime(e.plannedAt)}") }
```

with

```kotlin
                    d.preview.forEach { e -> Text(stringResource(R.string.schedule_preview_row, e.kind.label(), formatDateTime(e.plannedAt))) }
```

`ui/settings/CalendarCleanupScreen.kt` — replace

```kotlin
            title = { Text(stringResource(R.string.cleanup_confirm_title, ui.selectedResults.size)) },
```

with

```kotlin
            title = { Text(plural(R.plurals.cleanup_confirm_title, ui.selectedResults.size, ui.selectedResults.size)) },
```

and add `import com.nobby.ring.ui.components.plural`.

`ui/onboarding/OnboardingScreen.kt` — replace

```kotlin
            val text = if (rule.offsetDays == 0) stringResource(R.string.reminder_same_day, formatTime(rule.timeOfDay!!))
            else stringResource(R.string.reminder_day_before, rule.offsetDays, formatTime(rule.timeOfDay!!))
            Text("• $text (${rule.importance.label()})")
```

with

```kotlin
            val text = if (rule.offsetDays == 0) stringResource(R.string.reminder_same_day, formatTime(rule.timeOfDay!!))
            else plural(R.plurals.reminder_day_before, rule.offsetDays, rule.offsetDays, formatTime(rule.timeOfDay!!))
            Text(stringResource(R.string.onb_reminder_line, text, rule.importance.label()))
```

replace

```kotlin
        Text(stringResource(R.string.onb_calendar_connect) + " → " + stringResource(R.string.nav_settings), style = MaterialTheme.typography.bodyMedium)
```

with

```kotlin
        Text(stringResource(R.string.onb_calendar_hint), style = MaterialTheme.typography.bodyMedium)
```

and add `import com.nobby.ring.ui.components.plural`.

`widget/RingWidget.kt` — replace

```kotlin
            inDays = { context.getString(R.string.widget_in_days, it) },
            overdueDays = { context.getString(R.string.widget_overdue_days, it) },
            overdueHours = { context.getString(R.string.widget_overdue_hours, it) },
```

with

```kotlin
            inDays = { context.resources.getQuantityString(R.plurals.widget_in_days, it, it) },
            overdueDays = { context.resources.getQuantityString(R.plurals.widget_overdue_days, it, it) },
            overdueHours = { context.resources.getQuantityString(R.plurals.widget_overdue_hours, it, it) },
```

`notifications/Notifier.kt` — replace

```kotlin
        val text = buildString {
            append(context.getString(R.string.notif_overdue_body, formatDateTime(plannedAt)))
            if (safetyBreached) append("\n").append(context.getString(safety))
        }
```

with

```kotlin
        val body = context.getString(R.string.notif_overdue_body, formatDateTime(plannedAt))
        val text = if (safetyBreached) context.getString(R.string.notif_overdue_body_with_safety, body, context.getString(safety)) else body
```

- [ ] **Step 7: Check that no removed key is still referenced**

Run: `grep -rnE "R\.string\.(home_next_on|confirm_now|action_skip|reminder_disabled|widget_in_days|widget_overdue_days|widget_overdue_hours|duration_days|duration_hours|reminder_day_before|reminder_hours_before|cleanup_confirm_title|palette_rose)" app/src/main/java`
Expected: no output.

- [ ] **Step 8: Run the tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.i18n.*" --tests "com.nobby.ring.widget.*" --console=plain`
Expected: `BUILD SUCCESSFUL`; StringsParityTest 4, PluralsTest 2, WidgetModelTest unchanged and green (it uses its own string lambdas).

- [ ] **Step 9: Verify and commit**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-de/strings.xml app/src/main/java/com/nobby/ring/ui \
  app/src/main/java/com/nobby/ring/widget/RingWidget.kt app/src/main/java/com/nobby/ring/notifications/Notifier.kt app/src/test/java/com/nobby/ring/i18n
git commit -m "feat(copy): casual DE-first copy, plurals and EN/DE key parity test"
```

---

### Task 4: v1.1 fixes 1 (overdue phase chip) and 3 (phase after a very late removal)

Fix 2 (clipped time-picker header) is done in Task 1 (`displayLarge` 57 sp, `TypographyTest`) and checked on the emulator in Task 16.

**Fix 3 root cause.** `ConfirmAction.relogShiftedRemove` logs a shifted removal under the regenerated REMOVE id but keeps the original `plannedAt` so History shows planned vs actual. `CycleCalculator.positionAt` only treats a REMOVE as confirmed when `plannedAt >= config.anchor`; after a removal more than `wearDays` late the new anchor moves past the original `plannedAt`, so Home shows "Ring drin · Tag 23 von 21" instead of the pause. The fix also accepts a log whose id equals the current cycle's REMOVE id.

**Files:**
- Modify: `app/src/main/java/com/nobby/ring/domain/engine/CycleCalculator.kt`
- Create: `app/src/main/java/com/nobby/ring/ui/home/PhaseChipModel.kt`
- Modify: `app/src/main/java/com/nobby/ring/ui/home/HomeScreen.kt` (PhaseChip text only)
- Test: `app/src/test/java/com/nobby/ring/domain/engine/CycleCalculatorTest.kt` (add tests), `app/src/test/java/com/nobby/ring/ui/home/PhaseChipModelTest.kt`

**Interfaces:**
- Consumes: `plural()` (Task 3), plurals `phase_over_days`, `phase_over_hours`, string `phase_day`.
- Produces: `sealed interface PhaseChipModel { val phase: PhaseKind; data class InPhase(phase, day: Int, length: Int); data class OverDays(phase, days: Int); data class OverHours(phase, hours: Int); companion object { fun from(state: ScheduleState): PhaseChipModel } }` and `@Composable fun PhaseChipModel.text(): String` in `com.nobby.ring.ui.home`.

- [ ] **Step 1: Write the failing domain tests** — append to `CycleCalculatorTest` (inside the class), and add the imports `kotlinx.datetime.LocalDateTime` (already there), `com.nobby.ring.domain.model.EventKind` (already there):

```kotlin
    private fun confirmRemove(confirmedAt: LocalDateTime, policy: ShiftPolicy): ConfirmResult {
        val remove = ScheduleEngine.generate(config).first { it.kind == EventKind.REMOVE }
        return ConfirmAction.confirm(config, remove, emptyList(), confirmedAt, policy)
    }

    @Test
    fun `removal more than wearDays late with SHIFT reports the pause (v1_1 fix 3)`() {
        val result = confirmRemove(LocalDateTime(2026, 12, 15, 10, 0), ShiftPolicy.SHIFT) // planned Nov 22, 23 days late
        assertEquals(LocalDateTime(2026, 11, 23, 21, 0), result.config.anchor)
        val p = CycleCalculator.positionAt(result.config, result.logs, LocalDateTime(2026, 12, 15, 12, 0))
        assertEquals(PhaseKind.PAUSE, p.phase)
        assertEquals(2, p.dayInPhase)
        assertEquals(7, p.phaseLengthDays)
        assertFalse(p.ringIn)
        val next = OverdueCalculator.nextPending(ScheduleEngine.generate(result.config), result.logs)!!
        assertEquals(EventKind.INSERT, next.kind)
        assertEquals(LocalDateTime(2026, 12, 21, 21, 0), next.plannedAt)
    }

    @Test
    fun `removal more than wearDays late with KEEP also reports the pause`() {
        val result = confirmRemove(LocalDateTime(2026, 12, 15, 10, 0), ShiftPolicy.KEEP)
        val p = CycleCalculator.positionAt(result.config, result.logs, LocalDateTime(2026, 12, 15, 12, 0))
        assertEquals(PhaseKind.PAUSE, p.phase)
        assertFalse(p.ringIn)
    }

    @Test
    fun `a removal logged in the previous cycle does not count for the current one`() {
        val next = ScheduleConfig(config.method, LocalDateTime(2026, 11, 29, 21, 0)) // cycle 2 started Nov 29
        val oldRemove = EventLog("REMOVE@2026-11-22T21:00", EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), LocalDateTime(2026, 11, 22, 21, 0))
        val p = CycleCalculator.positionAt(next, listOf(oldRemove), LocalDateTime(2026, 12, 3, 12, 0))
        assertEquals(PhaseKind.WEAR, p.phase)
        assertEquals(5, p.dayInPhase)
    }
```

- [ ] **Step 2: Run them to verify the first two fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.domain.engine.CycleCalculatorTest" --console=plain`
Expected: FAIL — `removal more than wearDays late with SHIFT …` and `… KEEP …` with `expected:<PAUSE> but was:<WEAR>`; the third test passes.

- [ ] **Step 3: Fix `CycleCalculator.kt`**

Replace

```kotlin
        val removeConfirmed = logs.any { it.kind == EventKind.REMOVE && it.plannedAt >= config.anchor }
```

with

```kotlin
        // A late removal confirmed with a shift is logged under the regenerated REMOVE id but keeps its original
        // plannedAt, which lies before the new anchor when the removal was more than wearDays late (v1.1 fix 3).
        val currentRemoveId = eventId(
            EventKind.REMOVE,
            config.anchor.date.plus(method.wearDays, DateTimeUnit.DAY).atTime(method.timeOfDay),
        )
        val removeConfirmed = logs.any { it.kind == EventKind.REMOVE && (it.plannedAt >= config.anchor || it.eventId == currentRemoveId) }
```

and add the imports:

```kotlin
import com.nobby.ring.domain.model.eventId
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
```

- [ ] **Step 4: Write the failing chip tests** `app/src/test/java/com/nobby/ring/ui/home/PhaseChipModelTest.kt`:

```kotlin
package com.nobby.ring.ui.home

import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.PhaseKind
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class PhaseChipModelTest {
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0))
    private val removed = EventLog("REMOVE@2026-11-22T21:00", EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), LocalDateTime(2026, 11, 22, 21, 0))
    private fun at(now: LocalDateTime, logs: List<EventLog> = emptyList()) =
        PhaseChipModel.from(ScheduleRepository.buildState(config, logs, now))

    @Test
    fun `normal wear day`() = assertEquals(PhaseChipModel.InPhase(PhaseKind.WEAR, 12, 21), at(LocalDateTime(2026, 11, 12, 12, 0)))

    @Test
    fun `due day before the planned time is clamped to the phase length`() =
        assertEquals(PhaseChipModel.InPhase(PhaseKind.WEAR, 21, 21), at(LocalDateTime(2026, 11, 22, 10, 0)))

    @Test
    fun `removal two days overdue shows days over, not day 24 of 21`() =
        assertEquals(PhaseChipModel.OverDays(PhaseKind.WEAR, 2), at(LocalDateTime(2026, 11, 24, 22, 0)))

    @Test
    fun `overdue on the same day shows hours`() =
        assertEquals(PhaseChipModel.OverHours(PhaseKind.WEAR, 2), at(LocalDateTime(2026, 11, 22, 23, 30)))

    @Test
    fun `insertion two days overdue shows the pause over, not day 9 of 7 (v1_1 fix 1)`() =
        assertEquals(PhaseChipModel.OverDays(PhaseKind.PAUSE, 2), at(LocalDateTime(2026, 12, 1, 22, 0), listOf(removed)))
}
```

- [ ] **Step 5: Run to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.home.PhaseChipModelTest" --console=plain`
Expected: compilation FAILS (`Unresolved reference: PhaseChipModel`).

- [ ] **Step 6: Create `ui/home/PhaseChipModel.kt`**

```kotlin
package com.nobby.ring.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nobby.ring.R
import com.nobby.ring.domain.model.PhaseKind
import com.nobby.ring.domain.model.ScheduleState
import com.nobby.ring.ui.components.label
import com.nobby.ring.ui.components.plural

/** What the phase chip on Home says. While an event is overdue it shows the overdue amount instead of "day 9 of 7". */
sealed interface PhaseChipModel {
    val phase: PhaseKind

    data class InPhase(override val phase: PhaseKind, val day: Int, val length: Int) : PhaseChipModel
    data class OverDays(override val phase: PhaseKind, val days: Int) : PhaseChipModel
    data class OverHours(override val phase: PhaseKind, val hours: Int) : PhaseChipModel

    companion object {
        fun from(state: ScheduleState): PhaseChipModel {
            val p = state.position
            val overdue = state.overdue
                ?: return InPhase(p.phase, p.dayInPhase.coerceAtMost(p.phaseLengthDays.coerceAtLeast(1)), p.phaseLengthDays)
            val days = -(state.daysUntilNext ?: 0)
            return if (days >= 1) OverDays(p.phase, days) else OverHours(p.phase, overdue.delay.inWholeHours.toInt().coerceAtLeast(1))
        }
    }
}

@Composable
fun PhaseChipModel.text(): String = when (this) {
    is PhaseChipModel.InPhase -> stringResource(R.string.phase_day, phase.label(), day, length)
    is PhaseChipModel.OverDays -> plural(R.plurals.phase_over_days, days, phase.label(), days)
    is PhaseChipModel.OverHours -> plural(R.plurals.phase_over_hours, hours, phase.label(), hours)
}
```

- [ ] **Step 7: Use it on the current Home** — in `ui/home/HomeScreen.kt` `PhaseChip`, replace

```kotlin
    val p = state.position
    Text(
        stringResource(R.string.phase_day, p.phase.label(), p.dayInPhase, p.phaseLengthDays),
```

with

```kotlin
    Text(
        PhaseChipModel.from(state).text(),
```

- [ ] **Step 8: Run the tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.domain.engine.*" --tests "com.nobby.ring.ui.home.*" --console=plain`
Expected: `BUILD SUCCESSFUL`; CycleCalculatorTest 9 tests, PhaseChipModelTest 5 tests, all engine tests still green.

- [ ] **Step 9: Verify and commit**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/nobby/ring/domain/engine/CycleCalculator.kt app/src/main/java/com/nobby/ring/ui/home \
  app/src/test/java/com/nobby/ring/domain/engine/CycleCalculatorTest.kt app/src/test/java/com/nobby/ring/ui/home/PhaseChipModelTest.kt
git commit -m "fix: overdue phase chip and pause phase after a very late removal" -m "v1.1 fixes 1 and 3: the chip shows '2 Tage drüber' instead of 'day 9 of 7'; a removal confirmed more than wearDays late now reports the pause."
```

---
### Task 5: Shape, space and motion tokens; sticker buttons, chips, top bar, pickers

**Files:**
- Create: `app/src/main/java/com/nobby/ring/ui/theme/Shapes.kt`, `ui/theme/Space.kt`, `ui/theme/Motion.kt`
- Modify: `app/src/main/java/com/nobby/ring/ui/theme/Theme.kt` (shapes)
- Create: `app/src/main/java/com/nobby/ring/ui/components/Buttons.kt`, `ui/components/Chips.kt`, `ui/components/NobbyTopBar.kt`, `ui/components/Pickers.kt`
- Modify: `app/src/main/java/com/nobby/ring/ui/components/DatePickerField.kt`, `ui/components/TimePickerField.kt` (trigger only)
- Modify (replace): `app/src/main/java/com/nobby/ring/ui/components/NumberStepper.kt`
- Test: `app/src/test/java/com/nobby/ring/ui/components/ComponentsTest.kt`

**Interfaces:**
- Consumes: `NobbyTheme`, `NobbyTypography` (Tasks 1–2), strings `stepper_minus`, `stepper_plus` (Task 3).
- Produces:
  - `val NobbyShapes: Shapes`; `object NobbyRadius { xs = 8.dp; sm = 12.dp; md = 16.dp; lg = 24.dp; xl = 28.dp }`
  - `object NobbySpace { xs = 4.dp; s = 8.dp; m = 12.dp; l = 16.dp; xl = 24.dp; xxl = 32.dp; xxxl = 48.dp; gutter = 20.dp; card = 20.dp; touch = 48.dp; button = 52.dp }`
  - `object NobbyMotion { SHORT = 120; MEDIUM = 220; LONG = 360; CELEBRATE = 520; Standard; Decelerate; Squish: Easing; fun <T> squishSpring(): SpringSpec<T> }`, `@Composable fun reducedMotion(): Boolean`
  - `enum class ButtonTone { PRIMARY, TONAL, OUTLINED, TEXT, DESTRUCTIVE }`, `@Composable fun NobbyButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, tone: ButtonTone = ButtonTone.PRIMARY, icon: ImageVector? = null, contentColor: Color = Color.Unspecified, content: @Composable RowScope.() -> Unit)`, `fun Modifier.stickerEdge(color: Color, shape: Shape = CircleShape, edge: Dp = 3.dp): Modifier`
  - `enum class ChipTone { WEAR, PAUSE, OVERDUE }`, `@Composable fun PhaseChip(text: String, tone: ChipTone, modifier: Modifier = Modifier)`, `@Composable fun ChoiceChip(selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier)`, `@Composable fun <T> SegmentedChoice(options: List<T>, selected: T, onSelect: (T) -> Unit, label: @Composable (T) -> String, modifier: Modifier = Modifier)`, `@Composable fun nobbySegmentedColors(): SegmentedButtonColors`
  - `@Composable fun NobbyTopBar(title: @Composable () -> Unit, modifier: Modifier = Modifier, navigationIcon: @Composable () -> Unit = {}, actions: @Composable RowScope.() -> Unit = {})` (same named parameters as `TopAppBar`, so call sites swap by name)
  - `@Composable fun PickerField(label: String, value: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier)`
  - `NumberStepper(value, onValueChange, range, label, modifier, hint: String? = null)` (new optional `hint`)

- [ ] **Step 1: Write the failing test** `app/src/test/java/com/nobby/ring/ui/components/ComponentsTest.kt`:

```kotlin
package com.nobby.ring.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.nobby.ring.domain.model.ThemeMode
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.theme.NobbyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComponentsTest {
    @get:Rule val compose = createComposeRule()

    private fun show(content: @Composable () -> Unit) = compose.setContent { NobbyTheme(ThemeSpec()) { content() } }

    @Test
    fun `primary sticker button is at least 52 dp tall and clicks`() {
        var clicks = 0
        show { NobbyButton(onClick = { clicks++ }) { Text("Erledigt") } }
        compose.onNodeWithText("Erledigt").assertHeightIsAtLeast(52.dp).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `segmented choice exposes selection and switches`() {
        var picked by mutableStateOf(ThemeMode.LIGHT)
        show { SegmentedChoice(listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM), picked, { picked = it }, label = { it.name }) }
        compose.onNodeWithText("LIGHT").assertIsSelected()
        compose.onNodeWithText("DARK").assertIsNotSelected().performClick()
        compose.onNodeWithText("DARK").assertIsSelected()
        assertEquals(ThemeMode.DARK, picked)
    }

    @Test
    fun `picker field shows label and value as separate texts`() {
        var opened = false
        show { PickerField("Datum", "So., 8. Nov.", Icons.Rounded.CalendarMonth, onClick = { opened = true }) }
        compose.onNodeWithText("Datum", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("So., 8. Nov.", useUnmergedTree = true).assertExists()
        compose.onNode(hasClickAction()).performClick()
        assertTrue(opened)
    }

    @Test
    fun `number stepper respects its range`() {
        var value by mutableIntStateOf(1)
        show { NumberStepper(value, { value = it }, 1..3, "Pause") }
        compose.onNodeWithContentDescription("Less").assertIsNotEnabled()
        compose.onNodeWithContentDescription("More").performClick()
        assertEquals(2, value)
    }

    @Test
    fun `phase chip renders its text`() {
        show { PhaseChip("Ring drin · Tag 12 von 21", ChipTone.WEAR) }
        compose.onNodeWithText("Ring drin · Tag 12 von 21").assertExists()
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.components.ComponentsTest" --console=plain`
Expected: compilation FAILS (`Unresolved reference: NobbyButton`, `SegmentedChoice`, `PickerField`, `PhaseChip`, `ChipTone`).

- [ ] **Step 3: Create the theme tokens**

`ui/theme/Shapes.kt`:

```kotlin
package com.nobby.ring.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Material shape scale from spec §6 (small 12, medium 20, large 28, extraLarge 32). Buttons and chips are pills. */
val NobbyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Bundle radius tokens (tokens.json `radius`) for explicitly styled components: cards 24, sheets 28, tiles 16. */
object NobbyRadius {
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 28.dp
}
```

`ui/theme/Space.kt`:

```kotlin
package com.nobby.ring.ui.theme

import androidx.compose.ui.unit.dp

/** Spacing tokens (spec §6: 4/8/12/16/24/32/48) plus the bundle's gutter, card padding and touch sizes. */
object NobbySpace {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    val gutter = 20.dp
    val card = 20.dp
    val touch = 48.dp
    val button = 52.dp
}
```

`ui/theme/Motion.kt`:

```kotlin
package com.nobby.ring.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** tokens.json `motion`. */
object NobbyMotion {
    const val SHORT = 120
    const val MEDIUM = 220
    const val LONG = 360
    const val CELEBRATE = 520
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Decelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
    val Squish: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    fun <T> squishSpring(): SpringSpec<T> = spring(dampingRatio = 0.55f, stiffness = 420f)
}

/** True when the user removed animations (animator duration scale 0): animations become a 120 ms fade or a snap. */
@Composable
fun reducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
```

In `ui/theme/Theme.kt` replace

```kotlin
        MaterialTheme(colorScheme = scheme, typography = NobbyTypography, content = content)
```

with

```kotlin
        MaterialTheme(colorScheme = scheme, typography = NobbyTypography, shapes = NobbyShapes, content = content)
```

- [ ] **Step 4: Create `ui/components/Buttons.kt`**

```kotlin
package com.nobby.ring.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nobby.ring.ui.theme.NobbySpace

enum class ButtonTone { PRIMARY, TONAL, OUTLINED, TEXT, DESTRUCTIVE }

/**
 * The 3 dp darker bottom edge of a filled "sticker" button (bundle `.btn` inset shadow), drawn over the bottom of
 * the pill after its content so it sits on the fill like the CSS inset shadow.
 */
fun Modifier.stickerEdge(color: Color, shape: Shape = CircleShape, edge: Dp = 3.dp): Modifier = drawWithContent {
    drawContent()
    val clip = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }
    clipPath(clip) {
        drawRect(color, topLeft = Offset(0f, size.height - edge.toPx()), size = Size(size.width, edge.toPx()))
    }
}

private fun Modifier.edgeIf(enabled: Boolean, color: Color): Modifier = if (enabled) stickerEdge(color) else this

/** Pill button, min height 52 dp. Use one PRIMARY per screen; TEXT/OUTLINED take [contentColor] on coloured cards. */
@Composable
fun NobbyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: ButtonTone = ButtonTone.PRIMARY,
    icon: ImageVector? = null,
    contentColor: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val sized = modifier.heightIn(min = NobbySpace.button)
    val padding = PaddingValues(horizontal = if (tone == ButtonTone.TEXT) 12.dp else 24.dp)
    val quiet = if (contentColor.isSpecified) contentColor else c.primary
    val body: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        content()
    }
    when (tone) {
        ButtonTone.PRIMARY -> Button(
            onClick = onClick, modifier = sized.edgeIf(enabled, Color.Black.copy(alpha = 0.16f)), enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
            contentPadding = padding, content = body,
        )
        ButtonTone.TONAL -> Button(
            onClick = onClick, modifier = sized.edgeIf(enabled, c.onPrimaryContainer.copy(alpha = 0.10f)), enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = c.primaryContainer, contentColor = c.onPrimaryContainer),
            contentPadding = padding, content = body,
        )
        ButtonTone.DESTRUCTIVE -> Button(
            onClick = onClick, modifier = sized.edgeIf(enabled, Color.Black.copy(alpha = 0.18f)), enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = c.error, contentColor = c.onError),
            contentPadding = padding, content = body,
        )
        ButtonTone.OUTLINED -> OutlinedButton(
            onClick = onClick, modifier = sized, enabled = enabled,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = quiet),
            border = BorderStroke(1.5.dp, if (enabled) c.outline else c.onSurface.copy(alpha = 0.12f)),
            contentPadding = padding, content = body,
        )
        ButtonTone.TEXT -> TextButton(
            onClick = onClick, modifier = sized, enabled = enabled,
            colors = ButtonDefaults.textButtonColors(contentColor = quiet),
            contentPadding = padding, content = body,
        )
    }
}
```

- [ ] **Step 5: Create `ui/components/Chips.kt`**

```kotlin
package com.nobby.ring.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class ChipTone { WEAR, PAUSE, OVERDUE }

/** Non-interactive 34 dp phase chip with a dot; the tone carries the meaning (wear / pause / overdue). */
@Composable
fun PhaseChip(text: String, tone: ChipTone, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val (bg, fg, dot) = when (tone) {
        ChipTone.WEAR -> Triple(c.primaryContainer, c.onPrimaryContainer, c.primary)
        ChipTone.PAUSE -> Triple(c.secondaryContainer, c.onSecondaryContainer, c.secondary)
        ChipTone.OVERDUE -> Triple(c.errorContainer, c.onErrorContainer, c.error)
    }
    Row(
        modifier.heightIn(min = 34.dp).clip(CircleShape).background(bg).padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Text(text, style = MaterialTheme.typography.titleSmall, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Selectable pill: cocoa ink (inverseSurface) when selected, hairline outline otherwise. 48 dp touch target. */
@Composable
fun ChoiceChip(selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val fg = if (selected) c.inverseOnSurface else c.onSurface
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(if (selected) c.inverseSurface else Color.Transparent)
            .then(if (selected) Modifier else Modifier.border(1.dp, c.outlineVariant, CircleShape))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(18.dp), tint = fg)
        Text(label, style = MaterialTheme.typography.titleSmall, color = fg, maxLines = 1)
    }
}

/** A row of [ChoiceChip]s with single selection, e.g. Light / Dark / Auto. */
@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option -> ChoiceChip(option == selected, { onSelect(option) }, label(option)) }
    }
}

/** Material segmented buttons with the v2 selection colour (inverseSurface). */
@Composable
fun nobbySegmentedColors(): SegmentedButtonColors {
    val c = MaterialTheme.colorScheme
    return SegmentedButtonDefaults.colors(
        activeContainerColor = c.inverseSurface,
        activeContentColor = c.inverseOnSurface,
        activeBorderColor = c.inverseSurface,
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = c.onSurface,
        inactiveBorderColor = c.outlineVariant,
    )
}
```

- [ ] **Step 6: Create `ui/components/NobbyTopBar.kt`**

```kotlin
package com.nobby.ring.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Top bar on paper (background), Fredoka title (titleLarge). Same named parameters as TopAppBar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NobbyTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val bg = MaterialTheme.colorScheme.background
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = bg, scrolledContainerColor = bg),
    )
}
```

- [ ] **Step 7: Create `ui/components/Pickers.kt` and switch the picker triggers**

```kotlin
package com.nobby.ring.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nobby.ring.ui.theme.NobbyRadius

/** Tappable field: small label above the value, leading icon. Replaces the v1 "$label: $value" buttons. */
@Composable
fun PickerField(label: String, value: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(NobbyRadius.md))
            .background(c.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = c.primary)
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, color = c.onSurface)
        }
    }
}
```

In `ui/components/DatePickerField.kt` replace

```kotlin
    OutlinedButton(onClick = { open = true }, modifier) { Text("$label: ${formatDate(date)}") }
```

with

```kotlin
    PickerField(label, formatDate(date), Icons.Rounded.CalendarMonth, onClick = { open = true }, modifier = modifier)
```

remove `import androidx.compose.material3.OutlinedButton` and add `import androidx.compose.material.icons.Icons` and `import androidx.compose.material.icons.rounded.CalendarMonth`.

In `ui/components/TimePickerField.kt` replace

```kotlin
    OutlinedButton(onClick = { open = true }, modifier) { Text("$label: ${formatTime(time)}") }
```

with

```kotlin
    PickerField(label, formatTime(time), Icons.Rounded.Schedule, onClick = { open = true }, modifier = modifier)
```

remove `import androidx.compose.material3.OutlinedButton` and add `import androidx.compose.material.icons.Icons` and `import androidx.compose.material.icons.rounded.Schedule`.

- [ ] **Step 8: Replace `ui/components/NumberStepper.kt`**

```kotlin
package com.nobby.ring.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nobby.ring.R

@Composable
fun NumberStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StepButton(Icons.Rounded.Remove, stringResource(R.string.stepper_minus), value > range.first) { onValueChange((value - 1).coerceIn(range)) }
        Text(
            "$value",
            Modifier.widthIn(min = 44.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        )
        StepButton(Icons.Rounded.Add, stringResource(R.string.stepper_plus), value < range.last) { onValueChange((value + 1).coerceIn(range)) }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.primaryContainer, contentColor = c.onPrimaryContainer),
    ) { Icon(icon, contentDescription = description) }
}
```

- [ ] **Step 9: Run the tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.components.ComponentsTest" --console=plain`
Expected: `BUILD SUCCESSFUL`, 5 tests passed.

- [ ] **Step 10: Verify and commit**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/nobby/ring/ui/theme app/src/main/java/com/nobby/ring/ui/components app/src/test/java/com/nobby/ring/ui/components
git commit -m "feat(ui): shape, space and motion tokens; sticker buttons, chips, top bar and pickers"
```

---

### Task 6: NoBby mascot renderer (moods, props, flat face, Done burst)

**Decision:** draw the mascot in Compose `Canvas` from the exact `nobby.js` geometry rather than six multi-layer VectorDrawables. Per-preset recolouring is then just a `MascotColors` argument (VectorDrawables can only take one tint for the whole drawable), props and the burst share the same painter, and the same code renders the notification bitmaps through `CanvasDrawScope` (Task 15). This is the fallback the spec names in §15. The widget cannot run Compose drawing and uses tinted VectorDrawable masks instead (Task 14).

**Files:**
- Create: `app/src/main/java/com/nobby/ring/ui/mascot/MascotMood.kt`, `MascotColors.kt`, `MascotGeometry.kt`, `MascotPainter.kt`, `Nobby.kt`, `MascotBitmaps.kt`, `DoneBurst.kt`
- Test: `app/src/test/java/com/nobby/ring/ui/mascot/MascotGeometryTest.kt`, `MascotPainterTest.kt`, `NobbyTest.kt`

**Interfaces:**
- Consumes: `Palette.roles(dark)`, `PresetRoles`, `NobbyTheme.extra` (Task 2).
- Produces (package `com.nobby.ring.ui.mascot`):
  - `enum class MascotMood { HAPPY, NEUTRAL, SLEEPY, WORRIED, ALERT, YAY }`, `enum class MascotProp { WAVE, CALENDAR, BELL, PALETTE, NOTE }`
  - `val MascotMoodKey: SemanticsPropertyKey<MascotMood>`, `val MascotPropKey: SemanticsPropertyKey<MascotProp>` (+ `SemanticsPropertyReceiver.mascotMood/mascotProp`)
  - `data class MascotColors(body, cheek, ink, rim, drop, accent, primary, secondary, outline: Color)`, `fun PresetRoles.mascotColors(dark: Boolean)`, `fun Palette.mascotColors(dark: Boolean)`, `@Composable fun mascotColors(): MascotColors`
  - `@Composable fun Nobby(mood: MascotMood, size: Dp, modifier: Modifier = Modifier, prop: MascotProp? = null, flat: Boolean = false)` — `size` is the 136-unit base box; a prop widens/heightens it by its extents.
  - `@Composable fun DoneBurst(modifier: Modifier = Modifier)` (aspect 320:170)
  - `object MascotBitmaps { fun face(mood: MascotMood, colors: MascotColors, px: Int): android.graphics.Bitmap }`
  - internal: `class MascotPaths`, `DrawScope.drawNobby(...)`, `DrawScope.drawNobbyFace(...)`, `DrawScope.drawBurst(...)`, `object MascotGeometry`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/nobby/ring/ui/mascot/MascotGeometryTest.kt`:

```kotlin
package com.nobby.ring.ui.mascot

import androidx.compose.ui.graphics.vector.PathParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MascotGeometryTest {
    @Test
    fun `every nobby_js path string parses`() {
        MascotGeometry.allPaths().forEach { d -> assertTrue(d, PathParser().parsePathString(d).toNodes().isNotEmpty()) }
    }

    @Test
    fun `prop extents match nobby_js`() {
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 0f), MascotGeometry.extents(null), 0f)
        assertArrayEquals(floatArrayOf(4f, 0f, 14f, 0f), MascotGeometry.extents(MascotProp.WAVE), 0f)
        assertArrayEquals(floatArrayOf(4f, 4f, 14f, 4f), MascotGeometry.extents(MascotProp.CALENDAR), 0f)
        assertArrayEquals(floatArrayOf(4f, 6f, 16f, 0f), MascotGeometry.extents(MascotProp.BELL), 0f)
        assertArrayEquals(floatArrayOf(4f, 0f, 16f, 6f), MascotGeometry.extents(MascotProp.PALETTE), 0f)
        assertArrayEquals(floatArrayOf(4f, 4f, 14f, 6f), MascotGeometry.extents(MascotProp.NOTE), 0f)
    }

    @Test
    fun `line weight follows lineFor`() {
        assertEquals(2.5f, MascotGeometry.lineFor(120f))
        assertEquals(2f, MascotGeometry.lineFor(40f))
        assertEquals(1.5f, MascotGeometry.lineFor(24f))
    }
}
```

`app/src/test/java/com/nobby/ring/ui/mascot/MascotPainterTest.kt`:

```kotlin
package com.nobby.ring.ui.mascot

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.nobby.ring.domain.model.Palette
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MascotPainterTest {
    private val colors = Palette.BERRY.mascotColors(dark = false)

    private fun draw(block: DrawScope.() -> Unit) {
        val image = ImageBitmap(272, 272)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(272f, 272f), block)
    }

    @Test
    fun `every mood with and without every prop draws`() {
        val props = listOf<MascotProp?>(null) + MascotProp.entries
        for (mood in MascotMood.entries) for (prop in props) for (flat in listOf(false, true)) {
            draw { drawNobby(mood, prop, flat, renderDp = 120f, c = colors, paths = MascotPaths()) }
        }
    }

    @Test
    fun `flat faces and the done burst draw`() {
        MascotMood.entries.forEach { mood -> draw { drawNobbyFace(mood, colors, MascotPaths()) } }
        draw { drawBurst(colors, MascotPaths()) }
    }

    @Test
    fun `face bitmap has the requested size`() {
        val bmp = MascotBitmaps.face(MascotMood.WORRIED, Palette.MATCHA.mascotColors(dark = false), 96)
        assertEquals(96, bmp.width)
        assertEquals(96, bmp.height)
    }
}
```

`app/src/test/java/com/nobby/ring/ui/mascot/NobbyTest.kt`:

```kotlin
package com.nobby.ring.ui.mascot

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.theme.NobbyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NobbyTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `nobby exposes mood and prop and grows by the prop extents`() {
        compose.setContent {
            NobbyTheme(ThemeSpec()) {
                Column {
                    Nobby(MascotMood.WORRIED, 80.dp, Modifier.testTag("plain"))
                    Nobby(MascotMood.HAPPY, 80.dp, Modifier.testTag("wave"), prop = MascotProp.WAVE)
                }
            }
        }
        compose.onNode(SemanticsMatcher.expectValue(MascotMoodKey, MascotMood.WORRIED)).assertExists()
        compose.onNode(SemanticsMatcher.expectValue(MascotPropKey, MascotProp.WAVE)).assertExists()
        compose.onNodeWithTag("plain").assertWidthIsEqualTo(80.dp).assertHeightIsEqualTo(80.dp)
        compose.onNodeWithTag("wave").assertWidthIsAtLeast(90.dp) // 80 * (136 + 18) / 136 = 90.6
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.mascot.*" --console=plain`
Expected: compilation FAILS (`Unresolved reference: MascotGeometry`, `MascotMood`, …).

- [ ] **Step 3: Create `ui/mascot/MascotMood.kt`**

```kotlin
package com.nobby.ring.ui.mascot

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

/** The six moods of nobby.js (`celebrate` is called YAY here, as in the spec). */
enum class MascotMood { HAPPY, NEUTRAL, SLEEPY, WORRIED, ALERT, YAY }

/** Onboarding / empty-state props (nobby.js v2.1 PROPS). */
enum class MascotProp { WAVE, CALENDAR, BELL, PALETTE, NOTE }

/** Test-only semantics: TalkBack ignores custom keys, so the mascot stays decorative. */
val MascotMoodKey = SemanticsPropertyKey<MascotMood>("NobbyMood")
var SemanticsPropertyReceiver.mascotMood by MascotMoodKey

val MascotPropKey = SemanticsPropertyKey<MascotProp>("NobbyProp")
var SemanticsPropertyReceiver.mascotProp by MascotPropKey
```

- [ ] **Step 4: Create `ui/mascot/MascotColors.kt`**

```kotlin
package com.nobby.ring.ui.mascot

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.ui.theme.NobbyTheme
import com.nobby.ring.ui.theme.PresetRoles
import com.nobby.ring.ui.theme.roles

/** Everything the painter needs, so every preset (and mode) gets its own mascot colours. */
@Immutable
data class MascotColors(
    val body: Color,
    val cheek: Color,
    val ink: Color,
    val rim: Color,
    /** Hard sticker drop: 14 % cocoa in light, 35 % black in dark (tokens.json elevation.sticker). */
    val drop: Color,
    val accent: Color,
    val primary: Color,
    val secondary: Color,
    val outline: Color,
)

private fun dropFor(dark: Boolean, ink: Color): Color = if (dark) Color.Black.copy(alpha = 0.35f) else ink.copy(alpha = 0.14f)

fun PresetRoles.mascotColors(dark: Boolean): MascotColors = MascotColors(
    body = mascot, cheek = mascotCheek, ink = ink, rim = stickerRim, drop = dropFor(dark, ink),
    accent = accent, primary = primary, secondary = secondary, outline = outline,
)

fun Palette.mascotColors(dark: Boolean): MascotColors = roles(dark).mascotColors(dark)

@Composable
@ReadOnlyComposable
fun mascotColors(): MascotColors {
    val c = MaterialTheme.colorScheme
    val x = NobbyTheme.extra
    return MascotColors(
        body = x.mascotBody, cheek = x.mascotCheek, ink = x.ink, rim = x.mascotRim, drop = dropFor(x.dark, x.ink),
        accent = x.sticker, primary = c.primary, secondary = c.secondary, outline = c.outline,
    )
}
```

- [ ] **Step 5: Create `ui/mascot/MascotGeometry.kt`** (every string copied from `bundle/assets/nobby.js`)

```kotlin
package com.nobby.ring.ui.mascot

/** Path geometry of bundle/assets/nobby.js, in its 120-unit body grid (viewBox 136 with 8 units of rim padding). */
internal object MascotGeometry {
    const val VIEWBOX = 136f
    const val PAD = 8f

    const val BODY = "M60 20C89 20 110 39 110 65C110 91 89 108 60 108C31 108 10 91 10 65C10 39 31 20 60 20Z"
    const val HOLE = "M60 38C67.7 38 74 42.3 74 47.5C74 52.7 67.7 57 60 57C52.3 57 46 52.7 46 47.5C46 42.3 52.3 38 60 38Z"
    const val HIGHLIGHT = "M26 52Q29 39 41 33"

    const val EYES_HAPPY = "M39.5 78Q45 70.5 50.5 78M69.5 78Q75 70.5 80.5 78"
    const val EYES_CLOSED = "M39.5 75Q45 80.5 50.5 75M69.5 75Q75 80.5 80.5 75"
    const val MOUTH_SMILE = "M54.5 84Q60 89.5 65.5 84"
    const val MOUTH_CALM = "M55.5 85Q60 87.2 64.5 85"
    const val MOUTH_WAVY = "M53 87.5Q56.5 84.5 60 87.5Q63.5 90.5 67 87.5"
    const val MOUTH_OPEN = "M52.5 83H67.5Q67 93.5 60 93.5Q53 93.5 52.5 83Z"
    const val TONGUE = "M55.6 90Q60 86.8 64.4 90Q62.4 93 60 93Q57.6 93 55.6 90Z"
    const val BROWS_WORRIED = "M38 67.5L49 64M71 64L82 67.5"
    const val BROWS_ALERT = "M38.5 65Q44.5 61 50.5 63.5M69.5 63.5Q75.5 61 81.5 65"

    const val EXTRA_ZZ = "M94 16H104L94 27H104M110 2H116L110 9H116"
    const val EXTRA_DROP = "M103 26Q110.5 36.5 103 42Q95.5 36.5 103 26Z"
    const val EXTRA_BANG = "M106 4V18"

    const val WAVE_ARC_1 = "M120 41Q126 47 124 55"
    const val WAVE_ARC_2 = "M127 33Q136 45 132 59"
    const val CAL_HEADER = "M94 73V67Q94 60 101 60H123Q130 60 130 67V73Z"
    const val CAL_DIVIDER = "M94 73H130"
    const val CAL_RINGS = "M104 55V65M120 55V65"
    const val BELL = "M-13 9C-13 -5 -8 -13 0 -13C8 -13 13 -5 13 9L16 13H-16Z"
    const val BELL_ARCS = "M-21 -7Q-25.5 0 -21 7M21 -7Q25.5 0 21 7"
    const val PALETTE = "M-19 -2C-19 -14 -6 -20 6 -18C17 -16 22 -7 20 2C18 10 10 9 7 14C4 19 -2 20 -8 18C-15 16 -19 8 -19 -2Z"
    const val NOTE_LINES = "M103 75H123M103 83H123M103 91H116"

    /** Done-burst star, pre-transformed exactly like nobby.js burst() (x * 0.6 + 34, y * 0.6 + 94, toFixed(1)). */
    const val BURST_STAR = "M46 96.4L48.8 102.5L55.4 103.2L50.4 107.7L51.8 114.3L46 110.9L40.2 114.3L41.6 107.7L36.6 103.2L43.2 102.5Z"

    /** nobby.js SPARK(x, y, s). */
    fun spark(x: Float, y: Float, s: Float): String {
        val k = s / 40f
        fun p(a: Float, b: Float) = "${x + a * k} ${y + b * k}"
        return "M${p(20f, 3f)}Q${p(22.5f, 17.5f)} ${p(37f, 20f)}Q${p(22.5f, 22.5f)} ${p(20f, 37f)}" +
            "Q${p(17.5f, 22.5f)} ${p(3f, 20f)}Q${p(17.5f, 17.5f)} ${p(20f, 3f)}Z"
    }

    /** nobby.js HEART(x, y, s). */
    fun heart(x: Float, y: Float, s: Float): String {
        val k = s / 40f
        fun p(a: Float, b: Float) = "${x + a * k} ${y + b * k}"
        return "M${p(20f, 34f)}C${p(8f, 26f)} ${p(4f, 19f)} ${p(4f, 14f)}C${p(4f, 8.5f)} ${p(8.5f, 5f)} ${p(13f, 5f)}" +
            "C${p(16.5f, 5f)} ${p(19f, 7f)} ${p(20f, 10f)}C${p(21f, 7f)} ${p(23.5f, 5f)} ${p(27f, 5f)}" +
            "C${p(31.5f, 5f)} ${p(36f, 8.5f)} ${p(36f, 14f)}C${p(36f, 19f)} ${p(32f, 26f)} ${p(20f, 34f)}Z"
    }

    /** Extra room [left, top, right, bottom] a prop needs (nobby.js PROPS.*.ext), so layouts never clip. */
    fun extents(prop: MascotProp?): FloatArray = when (prop) {
        null -> floatArrayOf(0f, 0f, 0f, 0f)
        MascotProp.WAVE -> floatArrayOf(4f, 0f, 14f, 0f)
        MascotProp.CALENDAR -> floatArrayOf(4f, 4f, 14f, 4f)
        MascotProp.BELL -> floatArrayOf(4f, 6f, 16f, 0f)
        MascotProp.PALETTE -> floatArrayOf(4f, 0f, 16f, 6f)
        MascotProp.NOTE -> floatArrayOf(4f, 4f, 14f, 6f)
    }

    /** nobby.js lineFor: 2.5 rendered at 56 dp and up, 2 from 32 dp, 1.5 below. */
    fun lineFor(renderDp: Float): Float = when {
        renderDp >= 56f -> 2.5f
        renderDp >= 32f -> 2f
        else -> 1.5f
    }

    fun allPaths(): List<String> = listOf(
        BODY, HOLE, HIGHLIGHT, EYES_HAPPY, EYES_CLOSED, MOUTH_SMILE, MOUTH_CALM, MOUTH_WAVY, MOUTH_OPEN, TONGUE,
        BROWS_WORRIED, BROWS_ALERT, EXTRA_ZZ, EXTRA_DROP, EXTRA_BANG, WAVE_ARC_1, WAVE_ARC_2, CAL_HEADER, CAL_DIVIDER,
        CAL_RINGS, BELL, BELL_ARCS, PALETTE, NOTE_LINES, BURST_STAR,
        spark(92f, -2f, 30f), heart(-4f, 14f, 22f), spark(232f, 34f, 34f), spark(52f, 52f, 22f), heart(256f, 104f, 26f),
    )
}

internal enum class Eyes { DOT, BIG, HAPPY, CLOSED }
internal enum class Mouth { SMILE, CALM, O, WAVY, OPEN, GASP }
internal data class Expression(val eyes: Eyes, val mouth: Mouth, val cheeks: Boolean, val brows: String? = null)

/** nobby.js EXPR. */
internal fun MascotMood.expression(): Expression = when (this) {
    MascotMood.HAPPY -> Expression(Eyes.HAPPY, Mouth.SMILE, cheeks = true)
    MascotMood.NEUTRAL -> Expression(Eyes.DOT, Mouth.CALM, cheeks = true)
    MascotMood.SLEEPY -> Expression(Eyes.CLOSED, Mouth.O, cheeks = true)
    MascotMood.WORRIED -> Expression(Eyes.DOT, Mouth.WAVY, cheeks = false, brows = MascotGeometry.BROWS_WORRIED)
    MascotMood.YAY -> Expression(Eyes.HAPPY, Mouth.OPEN, cheeks = true)
    MascotMood.ALERT -> Expression(Eyes.BIG, Mouth.GASP, cheeks = false, brows = MascotGeometry.BROWS_ALERT)
}
```

- [ ] **Step 6: Create `ui/mascot/MascotPainter.kt`**

```kotlin
package com.nobby.ring.ui.mascot

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import com.nobby.ring.ui.mascot.MascotGeometry.PAD
import com.nobby.ring.ui.mascot.MascotGeometry.VIEWBOX

/** Parsed-path cache of one renderer. Not shared between threads: create one per composable / bitmap. */
internal class MascotPaths {
    private val cache = HashMap<String, Path>()
    fun of(d: String): Path = cache.getOrPut(d) { PathParser().parsePathString(d).toPath() }
    val bodyRing: Path by lazy {
        Path().apply {
            addPath(of(MascotGeometry.BODY))
            addPath(of(MascotGeometry.HOLE))
            fillType = PathFillType.EvenOdd
        }
    }
}

/** Flat face (nobby.js notifIcon): viewBox 6 12 108 108, line 2.5 * 108 / 96 * 1.1. */
internal const val FACE_VIEWBOX = 108f
internal const val FACE_STROKE = 2.5f * 108f / 96f * 1.1f

private fun round(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float) = Path().apply { addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry)) }
private fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    Path().apply { addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(r))) }

private fun DrawScope.oval(color: Color, cx: Float, cy: Float, rx: Float, ry: Float) =
    drawOval(color, Offset(cx - rx, cy - ry), Size(2 * rx, 2 * ry))

/** nobby.js rimmed(): sticker-rim halo, optional fill, ink outline. */
private fun DrawScope.rimmed(path: Path, sw: Float, fill: Color?, c: MascotColors) {
    drawPath(path, c.rim)
    drawPath(path, c.rim, style = round(sw + 6f))
    if (fill != null) drawPath(path, fill)
    drawPath(path, c.ink, style = round(sw))
}

private fun DrawScope.nub(cx: Float, cy: Float, sw: Float, c: MascotColors) = rimmed(ellipse(cx, cy, 8f, 6.4f), sw, c.body, c)

private fun DrawScope.dot(cx: Float, cy: Float, r: Float, fill: Color, sw: Float, c: MascotColors) {
    drawCircle(fill, r, Offset(cx, cy))
    drawCircle(c.ink, r, Offset(cx, cy), style = Stroke(sw * 0.8f))
}

/**
 * Draws NoBby (nobby.js mascot()) scaled so the 136-unit viewBox plus the prop's extents fills [DrawScope.size].
 * [renderDp] is the base size in dp; it picks the line weight like lineFor.
 */
internal fun DrawScope.drawNobby(mood: MascotMood, prop: MascotProp?, flat: Boolean, renderDp: Float, c: MascotColors, paths: MascotPaths) {
    val ext = MascotGeometry.extents(prop)
    val k = size.width / (VIEWBOX + ext[0] + ext[2])
    val sw = MascotGeometry.lineFor(renderDp) * VIEWBOX / renderDp
    withTransform({
        scale(k, k, pivot = Offset.Zero)
        translate(PAD + ext[0], PAD + ext[1])
    }) {
        if (prop != null) nub(11f, 90f, sw, c) // the resting nub sits behind the body
        if (!flat) {
            val rimW = sw + 9f
            translate(0f, 3f) {
                drawPath(paths.of(MascotGeometry.BODY), c.drop)
                drawPath(paths.of(MascotGeometry.BODY), c.drop, style = Stroke(rimW, join = StrokeJoin.Round))
            }
            drawPath(paths.bodyRing, c.rim)
            drawPath(paths.bodyRing, c.rim, style = Stroke(rimW, join = StrokeJoin.Round))
        }
        drawBodyAndFace(mood, sw, c, paths, cheekRx = 6.5f, cheekRy = 3.8f, highlight = true)
        drawExtra(mood, sw, c, paths)
        if (prop != null) drawProp(prop, sw, c, paths)
    }
}

/** Flat, rim-less face for the notification large icon (nobby.js notifIcon). */
internal fun DrawScope.drawNobbyFace(mood: MascotMood, c: MascotColors, paths: MascotPaths) {
    val k = size.width / FACE_VIEWBOX
    withTransform({
        scale(k, k, pivot = Offset.Zero)
        translate(-6f, -12f)
    }) {
        drawBodyAndFace(mood, FACE_STROKE, c, paths, cheekRx = 7f, cheekRy = 4.2f, highlight = false)
    }
}

private fun DrawScope.drawBodyAndFace(
    mood: MascotMood, sw: Float, c: MascotColors, paths: MascotPaths, cheekRx: Float, cheekRy: Float, highlight: Boolean,
) {
    val x = mood.expression()
    drawPath(paths.bodyRing, c.body)
    clipPath(paths.of(MascotGeometry.HOLE)) { oval(c.cheek.copy(alpha = 0.55f), 60f, 41f, 17f, 8f) }
    drawPath(paths.bodyRing, c.ink, style = Stroke(sw, join = StrokeJoin.Round))
    if (highlight) drawPath(paths.of(MascotGeometry.HIGHLIGHT), Color.White.copy(alpha = 0.8f), style = Stroke(sw * 1.15f, cap = StrokeCap.Round))
    if (x.cheeks) {
        oval(c.cheek, 33.5f, 85f, cheekRx, cheekRy)
        oval(c.cheek, 86.5f, 85f, cheekRx, cheekRy)
    }
    x.brows?.let { drawPath(paths.of(it), c.ink, style = round(sw)) }
    drawEyes(x.eyes, sw, c, paths)
    drawMouth(x.mouth, sw, c, paths)
}

private fun DrawScope.drawEyes(eyes: Eyes, sw: Float, c: MascotColors, paths: MascotPaths) {
    when (eyes) {
        Eyes.DOT -> listOf(45f, 75f).forEach { ex ->
            drawCircle(c.ink, 4.3f, Offset(ex, 76f))
            drawCircle(Color.White, 1.35f, Offset(ex + 1.5f, 74.4f))
        }
        Eyes.BIG -> listOf(45f, 75f).forEach { ex ->
            drawCircle(c.ink, 5f, Offset(ex, 76f))
            drawCircle(Color.White, 1.6f, Offset(ex + 1.7f, 74.2f))
        }
        Eyes.HAPPY -> drawPath(paths.of(MascotGeometry.EYES_HAPPY), c.ink, style = round(sw))
        Eyes.CLOSED -> drawPath(paths.of(MascotGeometry.EYES_CLOSED), c.ink, style = round(sw))
    }
}

private fun DrawScope.drawMouth(mouth: Mouth, sw: Float, c: MascotColors, paths: MascotPaths) {
    when (mouth) {
        Mouth.SMILE -> drawPath(paths.of(MascotGeometry.MOUTH_SMILE), c.ink, style = round(sw))
        Mouth.CALM -> drawPath(paths.of(MascotGeometry.MOUTH_CALM), c.ink, style = round(sw))
        Mouth.O -> oval(c.ink, 60f, 87f, 2.7f, 2.3f)
        Mouth.WAVY -> drawPath(paths.of(MascotGeometry.MOUTH_WAVY), c.ink, style = round(sw))
        Mouth.OPEN -> {
            drawPath(paths.of(MascotGeometry.MOUTH_OPEN), c.ink)
            drawPath(paths.of(MascotGeometry.TONGUE), c.cheek)
        }
        Mouth.GASP -> oval(c.ink, 60f, 87.5f, 3.6f, 4.4f)
    }
}

private fun DrawScope.drawExtra(mood: MascotMood, sw: Float, c: MascotColors, paths: MascotPaths) {
    when (mood) {
        MascotMood.SLEEPY -> rimmed(paths.of(MascotGeometry.EXTRA_ZZ), sw, null, c)
        MascotMood.WORRIED -> rimmed(paths.of(MascotGeometry.EXTRA_DROP), sw, Color.White, c)
        MascotMood.YAY -> {
            rimmed(paths.of(MascotGeometry.spark(92f, -2f, 30f)), sw, c.accent, c)
            rimmed(paths.of(MascotGeometry.heart(-4f, 14f, 22f)), sw, c.body, c)
        }
        MascotMood.ALERT -> {
            rimmed(paths.of(MascotGeometry.EXTRA_BANG), sw, null, c)
            drawCircle(c.rim, sw * 1.1f + 3f, Offset(106f, 26f))
            drawCircle(c.ink, sw * 1.1f, Offset(106f, 26f))
        }
        MascotMood.HAPPY, MascotMood.NEUTRAL -> Unit
    }
}

private fun DrawScope.drawProp(prop: MascotProp, sw: Float, c: MascotColors, paths: MascotPaths) {
    when (prop) {
        MascotProp.WAVE -> {
            rotate(-38f, Offset(109f, 57f)) { nub(109f, 57f, sw, c) }
            rimmed(paths.of(MascotGeometry.WAVE_ARC_1), sw, null, c)
            rimmed(paths.of(MascotGeometry.WAVE_ARC_2), sw, null, c)
        }
        MascotProp.CALENDAR -> {
            rotate(8f, Offset(112f, 80f)) {
                val frame = roundRect(94f, 60f, 36f, 38f, 7f)
                rimmed(frame, sw, Color.White, c)
                drawPath(paths.of(MascotGeometry.CAL_HEADER), c.primary)
                drawPath(frame, c.ink, style = Stroke(sw, join = StrokeJoin.Round))
                drawPath(paths.of(MascotGeometry.CAL_DIVIDER), c.ink, style = Stroke(sw, join = StrokeJoin.Round))
                rimmed(paths.of(MascotGeometry.CAL_RINGS), sw, null, c)
                for (x in listOf(103f, 112f, 121f)) for (y in listOf(81f, 89f)) {
                    if (!(x == 121f && y == 89f)) drawCircle(c.ink, 1.9f, Offset(x, y))
                }
                dot(121f, 89f, 4f, c.cheek, sw, c)
            }
            nub(96f, 92f, sw, c)
        }
        MascotProp.BELL -> {
            withTransform({
                translate(115f, 37f)
                rotate(16f, Offset.Zero)
            }) {
                rimmed(paths.of(MascotGeometry.BELL), sw, c.accent, c)
                drawCircle(c.accent, 3f, Offset(0f, -15.5f))
                drawCircle(c.ink, 3f, Offset(0f, -15.5f), style = Stroke(sw))
                oval(c.ink, 0f, 16.5f, 4.2f, 3.4f)
                rimmed(paths.of(MascotGeometry.BELL_ARCS), sw, null, c)
            }
            rotate(-30f, Offset(106f, 62f)) { nub(106f, 62f, sw, c) }
        }
        MascotProp.PALETTE -> {
            withTransform({
                translate(115f, 84f)
                rotate(-12f, Offset.Zero)
            }) {
                rimmed(paths.of(MascotGeometry.PALETTE), sw, Color.White, c)
                oval(c.rim, 10f, 4f, 3.6f, 3.1f)
                drawPath(ellipse(10f, 4f, 3.6f, 3.1f), c.ink, style = Stroke(sw * 0.8f))
                dot(-11f, -4f, 3.8f, c.primary, sw, c)
                dot(-3f, -11.5f, 3.8f, c.secondary, sw, c)
                dot(7f, -10f, 3.8f, c.accent, sw, c)
                dot(-7f, 8f, 3.8f, c.cheek, sw, c)
            }
            nub(97f, 95f, sw, c)
        }
        MascotProp.NOTE -> {
            rotate(7f, Offset(113f, 82f)) {
                rimmed(roundRect(97f, 60f, 32f, 42f, 5f), sw, Color.White, c)
                drawPath(paths.of(MascotGeometry.NOTE_LINES), c.outline, style = Stroke(sw * 0.8f, cap = StrokeCap.Round))
                rotate(-6f, Offset(113f, 58f)) { drawPath(roundRect(103f, 54f, 20f, 9f, 2f), c.accent) }
            }
            nub(97f, 92f, sw, c)
        }
    }
}

/** The Done burst (nobby.js burst()): strips, dots, two sparkles, a heart and a star. Counts as one doodle. */
internal fun DrawScope.drawBurst(c: MascotColors, paths: MascotPaths) {
    val k = size.width / 320f
    withTransform({ scale(k, k, pivot = Offset.Zero) }) {
        fun strip(x: Float, y: Float, deg: Float, fill: Color) = rotate(deg, Offset(x, y)) {
            drawRoundRect(fill, Offset(x - 7f, y - 2.6f), Size(14f, 5.2f), CornerRadius(2.6f))
        }
        strip(46f, 38f, -30f, c.primary); strip(274f, 30f, 24f, c.secondary); strip(30f, 120f, 18f, c.accent)
        strip(292f, 112f, -40f, c.cheek); strip(96f, 14f, 60f, c.accent); strip(232f, 150f, -12f, c.primary)
        strip(80f, 156f, 40f, c.secondary)
        drawCircle(c.primary, 3.6f, Offset(70f, 86f)); drawCircle(c.accent, 3.6f, Offset(252f, 78f))
        drawCircle(c.cheek, 3f, Offset(214f, 18f)); drawCircle(c.accent, 3f, Offset(118f, 150f))
        drawCircle(c.primary, 2.6f, Offset(300f, 62f)); drawCircle(c.secondary, 2.6f, Offset(16f, 70f))
        val sw = 2f
        fun sticker(p: Path, fill: Color) {
            drawPath(p, c.rim)
            drawPath(p, c.rim, style = Stroke(sw + 5f, join = StrokeJoin.Round))
            drawPath(p, fill)
            drawPath(p, c.ink, style = Stroke(sw, join = StrokeJoin.Round))
        }
        sticker(paths.of(MascotGeometry.spark(232f, 34f, 34f)), c.accent)
        sticker(paths.of(MascotGeometry.spark(52f, 52f, 22f)), c.accent)
        sticker(paths.of(MascotGeometry.heart(256f, 104f, 26f)), c.body)
        sticker(paths.of(MascotGeometry.BURST_STAR), c.accent)
    }
}
```

- [ ] **Step 7: Create `ui/mascot/Nobby.kt`, `DoneBurst.kt`, `MascotBitmaps.kt`**

`Nobby.kt`:

```kotlin
package com.nobby.ring.ui.mascot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.nobby.ring.ui.mascot.MascotGeometry.VIEWBOX

/**
 * NoBby with a sticker rim (or [flat]). [size] is the base box (body + rim padding); a [prop] adds its extents.
 * Decorative: no content description. Colours follow the current preset and mode.
 */
@Composable
fun Nobby(mood: MascotMood, size: Dp, modifier: Modifier = Modifier, prop: MascotProp? = null, flat: Boolean = false) {
    val colors = mascotColors()
    val paths = remember { MascotPaths() }
    val ext = MascotGeometry.extents(prop)
    val width = size * ((VIEWBOX + ext[0] + ext[2]) / VIEWBOX)
    val height = size * ((VIEWBOX + ext[1] + ext[3]) / VIEWBOX)
    Canvas(
        modifier
            .size(width, height)
            .semantics {
                mascotMood = mood
                if (prop != null) mascotProp = prop
            },
    ) { drawNobby(mood, prop, flat, size.value, colors, paths) }
}
```

`DoneBurst.kt`:

```kotlin
package com.nobby.ring.ui.mascot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/** Confetti for the Done sheet only (one doodle). Decorative. */
@Composable
fun DoneBurst(modifier: Modifier = Modifier) {
    val colors = mascotColors()
    val paths = remember { MascotPaths() }
    Canvas(modifier.aspectRatio(320f / 170f)) { drawBurst(colors, paths) }
}
```

`MascotBitmaps.kt`:

```kotlin
package com.nobby.ring.ui.mascot

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Off-screen renders of the mascot for places without Compose (notification large icon). */
object MascotBitmaps {
    /** The flat, rim-less, circle-safe face of nobby.js notifIcon() as a [px] x [px] bitmap. */
    fun face(mood: MascotMood, colors: MascotColors, px: Int): Bitmap {
        val image = ImageBitmap(px, px)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(px.toFloat(), px.toFloat())) {
            drawNobbyFace(mood, colors, MascotPaths())
        }
        return image.asAndroidBitmap()
    }
}
```

- [ ] **Step 8: Run the tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.mascot.*" --console=plain`
Expected: `BUILD SUCCESSFUL`; MascotGeometryTest 3, MascotPainterTest 3, NobbyTest 1.

- [ ] **Step 9: Verify and commit**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/nobby/ring/ui/mascot app/src/test/java/com/nobby/ring/ui/mascot
git commit -m "feat(ui): NoBby mascot renderer with moods, props, flat face and Done burst" -m "Drawn in Compose Canvas from the nobby.js geometry so each preset recolours it at runtime (spec §15 fallback)."
```

---
### Task 7: Bead-bracelet countdown ring and the ring model

**Files:**
- Create: `app/src/main/java/com/nobby/ring/ui/components/BeadRing.kt`
- Create: `app/src/main/java/com/nobby/ring/ui/ring/RingModel.kt`
- Test: `app/src/test/java/com/nobby/ring/ui/components/BeadRingTest.kt`, `app/src/test/java/com/nobby/ring/ui/ring/RingModelTest.kt`

**Interfaces:**
- Consumes: `reducedMotion()`, `NobbyMotion` (Task 5); `MascotMood` (Task 6); `ScheduleState`, `PhaseKind`, `EventKind`, `ScheduledEvent` (domain).
- Produces:
  - `enum class RingTone { WEAR, DUE, OVERDUE, SAFETY, PAUSE }`, `enum class BeadKind { PAST, CURRENT, FUTURE }`, `const val MAX_BEADS = 35`, `fun beadKinds(beads: Int, done: Int): List<BeadKind>`, `val RingToneKey: SemanticsPropertyKey<RingTone>` (package `com.nobby.ring.ui.components`)
  - `@Composable fun BeadRing(tone: RingTone, beads: Int, done: Int, modifier: Modifier = Modifier, sticker: (@Composable (Dp) -> Unit)? = null, center: @Composable (Dp) -> Unit)` — `center` and `sticker` receive the ring size / sticker size.
  - `enum class RingLabel { UNTIL_REMOVE, UNTIL_INSERT, UNTIL_SWAP, TODAY, OVERDUE_DAYS, OVERDUE_HOURS, NONE }`, `data class RingModel(val tone: RingTone, val mood: MascotMood, val beads: Int, val done: Int, val number: Int?, val label: RingLabel, val event: ScheduledEvent?, val safety: Boolean) { companion object { fun from(state: ScheduleState): RingModel } }` (package `com.nobby.ring.ui.ring`)

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/nobby/ring/ui/ring/RingModelTest.kt`:

```kotlin
package com.nobby.ring.ui.ring

import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.ui.components.RingTone
import com.nobby.ring.ui.mascot.MascotMood
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RingModelTest {
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0))
    private val removed = EventLog("REMOVE@2026-11-22T21:00", EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), LocalDateTime(2026, 11, 22, 21, 0))
    private fun at(now: LocalDateTime, logs: List<EventLog> = emptyList()) = RingModel.from(ScheduleRepository.buildState(config, logs, now))

    @Test
    fun `wear day 12 of 21 is happy with 11 beads done and 10 days to removal`() {
        val m = at(LocalDateTime(2026, 11, 12, 12, 0))
        assertEquals(RingTone.WEAR, m.tone)
        assertEquals(MascotMood.HAPPY, m.mood)
        assertEquals(21, m.beads)
        assertEquals(11, m.done)
        assertEquals(10, m.number)
        assertEquals(RingLabel.UNTIL_REMOVE, m.label)
    }

    @Test
    fun `due today is neutral and shows the word instead of a number`() {
        val m = at(LocalDateTime(2026, 11, 22, 10, 0))
        assertEquals(RingTone.DUE, m.tone)
        assertEquals(MascotMood.NEUTRAL, m.mood)
        assertEquals(RingLabel.TODAY, m.label)
        assertNull(m.number)
        assertEquals(21, m.done)
    }

    @Test
    fun `overdue two days is worried`() {
        val m = at(LocalDateTime(2026, 11, 24, 22, 0))
        assertEquals(RingTone.OVERDUE, m.tone)
        assertEquals(MascotMood.WORRIED, m.mood)
        assertEquals(RingLabel.OVERDUE_DAYS, m.label)
        assertEquals(2, m.number)
    }

    @Test
    fun `overdue the same evening counts hours`() {
        val m = at(LocalDateTime(2026, 11, 22, 23, 30))
        assertEquals(RingLabel.OVERDUE_HOURS, m.label)
        assertEquals(2, m.number)
    }

    @Test
    fun `beyond the safety threshold is alert`() {
        val m = at(LocalDateTime(2026, 11, 30, 22, 0))
        assertEquals(RingTone.SAFETY, m.tone)
        assertEquals(MascotMood.ALERT, m.mood)
        assertTrue(m.safety)
        assertEquals(8, m.number)
    }

    @Test
    fun `pause day 4 of 7 is sleepy with 4 days to insertion`() {
        val m = at(LocalDateTime(2026, 11, 25, 12, 0), listOf(removed))
        assertEquals(RingTone.PAUSE, m.tone)
        assertEquals(MascotMood.SLEEPY, m.mood)
        assertEquals(7, m.beads)
        assertEquals(3, m.done)
        assertEquals(4, m.number)
        assertEquals(RingLabel.UNTIL_INSERT, m.label)
    }
}
```

`app/src/test/java/com/nobby/ring/ui/components/BeadRingTest.kt`:

```kotlin
package com.nobby.ring.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.theme.NobbyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BeadRingTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `bead kinds follow nobby_js ring`() {
        val k = beadKinds(21, 11)
        assertEquals(List(11) { BeadKind.PAST }, k.take(11))
        assertEquals(BeadKind.CURRENT, k[11])
        assertEquals(List(9) { BeadKind.FUTURE }, k.drop(12))
        val all = beadKinds(21, 21)
        assertEquals(List(20) { BeadKind.PAST }, all.take(20))
        assertEquals(BeadKind.CURRENT, all.last())
    }

    @Test
    fun `ring exposes its tone and renders centre content`() {
        compose.setContent {
            NobbyTheme(ThemeSpec()) {
                BeadRing(RingTone.PAUSE, beads = 7, done = 3, modifier = Modifier.size(200.dp)) { Text("4") }
            }
        }
        compose.onNodeWithText("4").assertExists()
        compose.onNode(SemanticsMatcher.expectValue(RingToneKey, RingTone.PAUSE)).assertExists()
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.ring.*" --tests "com.nobby.ring.ui.components.BeadRingTest" --console=plain`
Expected: compilation FAILS (`Unresolved reference: RingModel`, `beadKinds`, `BeadRing`).

- [ ] **Step 3: Create `ui/components/BeadRing.kt`**

```kotlin
package com.nobby.ring.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.nobby.ring.ui.theme.NobbyMotion
import com.nobby.ring.ui.theme.reducedMotion
import kotlin.math.cos
import kotlin.math.sin

enum class RingTone { WEAR, DUE, OVERDUE, SAFETY, PAUSE }
enum class BeadKind { PAST, CURRENT, FUTURE }

/** Above this many days the bracelet becomes a plain progress arc (bundle: "arc fallback above 35 beads"). */
const val MAX_BEADS = 35

val RingToneKey = SemanticsPropertyKey<RingTone>("RingTone")
var SemanticsPropertyReceiver.ringTone by RingToneKey

/** One bead per day: [done] beads filled, the bead at index [done] (or the last one when all are done) is today. */
fun beadKinds(beads: Int, done: Int): List<BeadKind> = List(beads) { i ->
    when {
        i == done || (done >= beads && i == beads - 1) -> BeadKind.CURRENT
        i < done -> BeadKind.PAST
        else -> BeadKind.FUTURE
    }
}

private data class Tones(val track: Color, val bead: Color)

private fun ColorScheme.tones(tone: RingTone): Tones = when (tone) {
    RingTone.WEAR -> Tones(surfaceContainerHigh, primary)
    RingTone.DUE -> Tones(primaryContainer, primary)
    RingTone.OVERDUE, RingTone.SAFETY -> Tones(errorContainer, error)
    RingTone.PAUSE -> Tones(secondaryContainer, secondary)
}

/**
 * The countdown bracelet (nobby.js ring()): a track circle plus one bead per day of the phase, today's bead
 * highlighted. [center] is placed in the middle 66 %; [sticker] sits on the rim top-right at 30 % size, rotated 8°.
 */
@Composable
fun BeadRing(
    tone: RingTone,
    beads: Int,
    done: Int,
    modifier: Modifier = Modifier,
    sticker: (@Composable (Dp) -> Unit)? = null,
    center: @Composable (Dp) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val reduced = reducedMotion()
    val fill by animateFloatAsState(
        targetValue = done.toFloat(),
        animationSpec = if (reduced) snap() else tween(NobbyMotion.LONG, easing = NobbyMotion.Decelerate),
        label = "beads",
    )
    BoxWithConstraints(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        val ringSize = min(maxWidth, maxHeight)
        Canvas(Modifier.size(ringSize).semantics { ringTone = tone }) { drawRing(scheme, tone, beads.coerceAtLeast(1), fill) }
        Box(Modifier.size(ringSize * 0.66f), contentAlignment = Alignment.Center) { center(ringSize) }
        if (sticker != null) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = ringSize * 0.06f, y = -ringSize * 0.05f).rotate(8f)) {
                sticker(ringSize * 0.3f)
            }
        }
    }
}

private fun DrawScope.drawRing(scheme: ColorScheme, tone: RingTone, beads: Int, fill: Float) {
    val t = scheme.tones(tone)
    val s = size.minDimension
    val tw = s * 0.085f
    val r = s / 2f - tw / 2f - 4.dp.toPx()
    val c = Offset(size.width / 2f, size.height / 2f)
    if (tone == RingTone.SAFETY) drawCircle(scheme.errorContainer.copy(alpha = 0.55f), r - tw / 2f - 10.dp.toPx(), c)
    drawCircle(t.track, r, c, style = Stroke(tw))
    if (beads > MAX_BEADS) {
        val sweep = 360f * (fill / beads).coerceIn(0f, 1f)
        drawArc(t.bead, -90f, sweep, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(tw * 0.58f, cap = StrokeCap.Round))
        return
    }
    val step = 360f / beads
    beadKinds(beads, fill.toInt()).forEachIndexed { i, kind ->
        val a = Math.toRadians((-90f + step * (i + 0.5f)).toDouble())
        val p = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
        when (kind) {
            BeadKind.CURRENT -> {
                drawCircle(scheme.surfaceContainerLowest, tw * 0.62f, p)
                drawCircle(t.bead, tw * 0.44f, p)
            }
            BeadKind.PAST -> drawCircle(t.bead, tw * 0.29f, p)
            BeadKind.FUTURE -> drawCircle(scheme.outline.copy(alpha = 0.7f), tw * 0.14f, p)
        }
    }
}
```

The `center` and `sticker` slots receive a `Dp` (ring size / sticker size); callers that don't need it write `{ Text("4") }` and leave the implicit `it` unused.

- [ ] **Step 4: Create `ui/ring/RingModel.kt`**

```kotlin
package com.nobby.ring.ui.ring

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.PhaseKind
import com.nobby.ring.domain.model.ScheduleState
import com.nobby.ring.domain.model.ScheduledEvent
import com.nobby.ring.ui.components.RingTone
import com.nobby.ring.ui.mascot.MascotMood

enum class RingLabel { UNTIL_REMOVE, UNTIL_INSERT, UNTIL_SWAP, TODAY, OVERDUE_DAYS, OVERDUE_HOURS, NONE }

/** What the Home ring (and the widget) shows for a schedule state. Pure, so it is unit-tested. */
data class RingModel(
    val tone: RingTone,
    val mood: MascotMood,
    val beads: Int,
    val done: Int,
    /** Days (or hours when overdue for less than a day); null when the word "Heute" is shown or nothing is pending. */
    val number: Int?,
    val label: RingLabel,
    val event: ScheduledEvent?,
    val safety: Boolean,
) {
    companion object {
        fun from(state: ScheduleState): RingModel {
            val p = state.position
            val beads = p.phaseLengthDays.coerceAtLeast(1)
            val doneInPhase = (p.dayInPhase - 1).coerceIn(0, beads)
            val pause = p.phase == PhaseKind.PAUSE
            val calm = if (pause) RingTone.PAUSE else RingTone.WEAR
            val calmMood = if (pause) MascotMood.SLEEPY else MascotMood.HAPPY
            val next = state.nextEvent
            val days = state.daysUntilNext
            val overdue = state.overdue
            return when {
                overdue != null -> {
                    val overDays = -(days ?: 0)
                    val byDays = overDays >= 1
                    RingModel(
                        tone = if (overdue.safetyBreached) RingTone.SAFETY else RingTone.OVERDUE,
                        mood = if (overdue.safetyBreached) MascotMood.ALERT else MascotMood.WORRIED,
                        beads = beads,
                        done = beads,
                        number = if (byDays) overDays else overdue.delay.inWholeHours.toInt().coerceAtLeast(1),
                        label = if (byDays) RingLabel.OVERDUE_DAYS else RingLabel.OVERDUE_HOURS,
                        event = overdue.event,
                        safety = overdue.safetyBreached,
                    )
                }
                next == null || days == null -> RingModel(calm, calmMood, beads, doneInPhase, null, RingLabel.NONE, null, false)
                days <= 0 -> RingModel(RingTone.DUE, MascotMood.NEUTRAL, beads, beads, null, RingLabel.TODAY, next, false)
                else -> RingModel(calm, calmMood, beads, doneInPhase, days, labelFor(next.kind), next, false)
            }
        }

        private fun labelFor(kind: EventKind) = when (kind) {
            EventKind.REMOVE -> RingLabel.UNTIL_REMOVE
            EventKind.INSERT -> RingLabel.UNTIL_INSERT
            EventKind.SWAP -> RingLabel.UNTIL_SWAP
        }
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.ring.*" --tests "com.nobby.ring.ui.components.BeadRingTest" --console=plain`
Expected: `BUILD SUCCESSFUL`; RingModelTest 6, BeadRingTest 2.

- [ ] **Step 6: Verify and commit**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/nobby/ring/ui/components/BeadRing.kt app/src/main/java/com/nobby/ring/ui/ring \
  app/src/test/java/com/nobby/ring/ui/components/BeadRingTest.kt app/src/test/java/com/nobby/ring/ui/ring
git commit -m "feat(ui): bead-bracelet countdown ring and ring model"
```

---

### Task 8: Cards, banners, settings rows and date formats

**Files:**
- Create: `app/src/main/java/com/nobby/ring/ui/components/Cards.kt`, `ui/components/EventCards.kt`, `ui/components/Banners.kt`, `ui/components/SettingsRows.kt`
- Modify: `app/src/main/java/com/nobby/ring/ui/components/Formatting.kt` (short/long dates, short event labels, mode labels)
- Test: `app/src/test/java/com/nobby/ring/ui/components/CardsTest.kt`

**Interfaces:**
- Consumes: `NobbyButton`, `ButtonTone` (Task 5), `NobbyRadius`, `NobbySpace`, `NobbyTheme.extra` (Tasks 2, 5), `plural()` and keys `home_next_in_days`, `home_due_today`, `home_overdue_body`, `home_mark_done`, `home_snooze`, `event_*_short`, `mode_*` (Task 3).
- Produces:
  - `enum class CardTone { PLAIN, TONAL, DANGER, FLAT }`, `@Composable fun NobbyCard(modifier: Modifier = Modifier, tone: CardTone = CardTone.PLAIN, content: @Composable ColumnScope.() -> Unit)`
  - `enum class TileTone { WEAR, PAUSE, NEUTRAL, ERROR }`, `@Composable fun IconTile(icon: ImageVector, tone: TileTone, modifier: Modifier = Modifier, size: Dp = 48.dp)`
  - `@Composable fun NoteCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)` (washi tape)
  - `@Composable fun NextEventCard(kind: EventKind, plannedAt: LocalDateTime, daysUntil: Int, modifier: Modifier = Modifier, actions: (@Composable RowScope.() -> Unit)? = null)`
  - `@Composable fun OverdueCard(kind: EventKind, plannedAt: LocalDateTime, overdueText: String, safetyText: String?, onDone: () -> Unit, onSnooze: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier)`
  - `@Composable fun PermissionBanner(text: String, icon: ImageVector, actionLabel: String, onClick: () -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun SettingsGroup(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)`, `@Composable fun SettingsRow(icon: ImageVector, title: String, modifier: Modifier = Modifier, summary: String? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null)`, `@Composable fun SettingsDivider()`
  - Formatting: `fun formatShortDate(date: LocalDate): String` ("So., 8. Nov."), `fun formatLongDate(date: LocalDate): String` ("Sonntag, 8. November"), `formatDateTime()` now `"<short date> · <time>"`, `@Composable fun EventKind.shortLabel(): String`, `@Composable fun ThemeMode.label(): String`

- [ ] **Step 1: Write the failing test** `app/src/test/java/com/nobby/ring/ui/components/CardsTest.kt`:

```kotlin
package com.nobby.ring.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.theme.NobbyTheme
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CardsTest {
    @get:Rule val compose = createComposeRule()
    private val planned = LocalDateTime(2026, 11, 22, 21, 0)

    private fun show(content: @Composable () -> Unit) = compose.setContent { NobbyTheme(ThemeSpec()) { content() } }

    @Test
    fun `next event card counts down in days`() {
        show { NextEventCard(EventKind.REMOVE, planned, daysUntil = 10) }
        compose.onNodeWithText("Remove ring").assertExists()
        compose.onNodeWithText("in 10 days", substring = true).assertExists()
    }

    @Test
    fun `due card says today and shows its actions`() {
        show { NextEventCard(EventKind.REMOVE, planned, daysUntil = 0, actions = { Text("Done") }) }
        compose.onNodeWithText("Today: Ring out at", substring = true).assertExists()
        compose.onNodeWithText("Done").assertExists()
    }

    @Test
    fun `overdue card offers done and snooze`() {
        var done = 0
        var snoozed = 0
        show {
            OverdueCard(EventKind.REMOVE, planned, "2 days overdue", "Protection may be reduced", { done++ }, { snoozed++ }, enabled = true)
        }
        compose.onNodeWithText("2 days overdue").assertExists()
        compose.onNodeWithText("Protection may be reduced").assertExists()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Snooze").performClick()
        assertEquals(1, done)
        assertEquals(1, snoozed)
    }

    @Test
    fun `settings row is clickable and shows its summary`() {
        var opened = 0
        show { SettingsGroup("Ring") { SettingsRow(Icons.Rounded.Schedule, "Cycle & schedule", summary = "21 + 7 days · 21:00", onClick = { opened++ }) } }
        compose.onNodeWithText("21 + 7 days · 21:00", substring = true).assertExists()
        compose.onNodeWithText("Cycle & schedule", substring = true).performClick()
        assertEquals(1, opened)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.components.CardsTest" --console=plain`
Expected: compilation FAILS (`Unresolved reference: NextEventCard`, `OverdueCard`, `SettingsGroup`, `SettingsRow`).

- [ ] **Step 3: Extend `ui/components/Formatting.kt`**

Add imports: `android.text.format.DateFormat`, `com.nobby.ring.domain.model.ThemeMode`.

Replace

```kotlin
fun formatDateTime(dt: LocalDateTime): String = "${formatDate(dt.date)} ${formatTime(dt.time)}"
```

with

```kotlin
/** Locale-aware pattern from a skeleton, e.g. "EEEdMMM" -> "EEE, d. MMM" (de) / "EEE, MMM d" (en). */
private fun bestPattern(skeleton: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton), Locale.getDefault())

/** "So., 8. Nov." / "Sun, Nov 8". */
fun formatShortDate(date: LocalDate): String = date.toJavaLocalDate().format(bestPattern("EEEdMMM"))

/** "Sonntag, 8. November" / "Sunday, November 8". */
fun formatLongDate(date: LocalDate): String = date.toJavaLocalDate().format(bestPattern("EEEEdMMMM"))

/** "So., 8. Nov. · 21:00". The middle dot separates two values; it is not translated text. */
fun formatDateTime(dt: LocalDateTime): String = "${formatShortDate(dt.date)} · ${formatTime(dt.time)}"
```

Add after `EventKind.label()`:

```kotlin
@Composable fun EventKind.shortLabel(): String = stringResource(
    when (this) {
        EventKind.REMOVE -> R.string.event_remove_short
        EventKind.INSERT -> R.string.event_insert_short
        EventKind.SWAP -> R.string.event_swap_short
    },
)

@Composable fun ThemeMode.label(): String = stringResource(
    when (this) {
        ThemeMode.SYSTEM -> R.string.mode_system
        ThemeMode.LIGHT -> R.string.mode_light
        ThemeMode.DARK -> R.string.mode_dark
    },
)
```

- [ ] **Step 4: Create `ui/components/Cards.kt`**

```kotlin
package com.nobby.ring.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nobby.ring.ui.theme.NobbyRadius
import com.nobby.ring.ui.theme.NobbySpace
import com.nobby.ring.ui.theme.NobbyTheme

enum class CardTone { PLAIN, TONAL, DANGER, FLAT }

/**
 * 24 dp card, 20 dp padding. PLAIN = white on paper with a hairline (dark: surfaceContainer + hairline); colour-filled
 * tones are for state only: TONAL = coming up / due, DANGER = overdue, FLAT = quiet grouping.
 */
@Composable
fun NobbyCard(modifier: Modifier = Modifier, tone: CardTone = CardTone.PLAIN, content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    val dark = NobbyTheme.extra.dark
    val (bg, fg) = when (tone) {
        CardTone.PLAIN -> (if (dark) c.surfaceContainer else c.surfaceContainerLowest) to c.onSurface
        CardTone.TONAL -> c.primaryContainer to c.onPrimaryContainer
        CardTone.DANGER -> c.errorContainer to c.onErrorContainer
        CardTone.FLAT -> c.surfaceContainer to c.onSurface
    }
    val shape = RoundedCornerShape(NobbyRadius.lg)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .then(if (tone == CardTone.PLAIN) Modifier.border(1.dp, c.outlineVariant, shape) else Modifier)
            .padding(NobbySpace.card),
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) { this@Column.content() }
    }
}

enum class TileTone { WEAR, PAUSE, NEUTRAL, ERROR }

/** Rounded icon tile (16 dp radius) used on cards and rows. Decorative: the row text carries the meaning. */
@Composable
fun IconTile(icon: ImageVector, tone: TileTone, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val c = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        TileTone.WEAR -> c.primaryContainer to c.onPrimaryContainer
        TileTone.PAUSE -> c.secondaryContainer to c.onSecondaryContainer
        TileTone.NEUTRAL -> c.surfaceContainerHigh to c.onSurface
        TileTone.ERROR -> c.error to c.onError
    }
    Box(modifier.size(size).clip(RoundedCornerShape(NobbyRadius.md)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, Modifier.size(24.dp), tint = fg)
    }
}

/** Plain card with a strip of washi tape (accent, 85 %, rotated -4°) on its top edge. */
@Composable
fun NoteCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val tape = NobbyTheme.extra.sticker
    Box(modifier.fillMaxWidth().padding(top = 10.dp)) {
        NobbyCard(content = content)
        Box(
            Modifier
                .offset(x = 28.dp, y = (-9).dp)
                .size(width = 64.dp, height = 20.dp)
                .rotate(-4f)
                .clip(RoundedCornerShape(4.dp))
                .background(tape.copy(alpha = 0.85f)),
        )
    }
}
```

- [ ] **Step 5: Create `ui/components/EventCards.kt`**

```kotlin
package com.nobby.ring.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobby.ring.R
import com.nobby.ring.domain.model.EventKind
import kotlinx.datetime.LocalDateTime

/** Next-event card (bundle cards-banners "Next event" / "due today"). [actions] go in a row below. */
@Composable
fun NextEventCard(
    kind: EventKind,
    plannedAt: LocalDateTime,
    daysUntil: Int,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val due = daysUntil <= 0
    NobbyCard(modifier, tone = if (due) CardTone.TONAL else CardTone.PLAIN) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(
                if (due) Icons.Rounded.Alarm else Icons.Rounded.Event,
                if (kind == EventKind.INSERT) TileTone.PAUSE else TileTone.WEAR,
            )
            Column(Modifier.weight(1f)) {
                Text(kind.label(), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (due) stringResource(R.string.home_due_today, kind.shortLabel(), formatTime(plannedAt.time))
                    else plural(R.plurals.home_next_in_days, daysUntil, formatDateTime(plannedAt), daysUntil),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (due) LocalContentColor.current else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (actions != null) {
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/** Overdue card (tomato container) with Done + Snooze and, beyond the safety threshold, the safety line. */
@Composable
fun OverdueCard(
    kind: EventKind,
    plannedAt: LocalDateTime,
    overdueText: String,
    safetyText: String?,
    onDone: () -> Unit,
    onSnooze: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    NobbyCard(modifier, tone = CardTone.DANGER) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(Icons.Rounded.Alarm, TileTone.ERROR)
            Column(Modifier.weight(1f)) {
                Text(kind.label(), style = MaterialTheme.typography.titleMedium)
                Text(overdueText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            stringResource(R.string.home_overdue_body, formatDateTime(plannedAt)),
            Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (safetyText != null) {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(safetyText, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NobbyButton(onDone, enabled = enabled, icon = Icons.Rounded.Check) { Text(stringResource(R.string.home_mark_done)) }
            NobbyButton(onSnooze, enabled = enabled, tone = ButtonTone.TEXT, icon = Icons.Rounded.Snooze, contentColor = LocalContentColor.current) {
                Text(stringResource(R.string.home_snooze))
            }
        }
    }
}
```

- [ ] **Step 6: Create `ui/components/Banners.kt`**

```kotlin
package com.nobby.ring.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobby.ring.ui.theme.NobbyRadius
import com.nobby.ring.ui.theme.NobbyTheme

/** Compact, non-alarming hint (butter tertiaryContainer; dark: surfaceContainerHigh + tertiary hairline). */
@Composable
fun PermissionBanner(text: String, icon: ImageVector, actionLabel: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val dark = NobbyTheme.extra.dark
    val shape = RoundedCornerShape(NobbyRadius.md)
    val fg = if (dark) c.onSurface else c.onTertiaryContainer
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (dark) c.surfaceContainerHigh else c.tertiaryContainer)
            .then(if (dark) Modifier.border(1.dp, c.tertiary.copy(alpha = 0.45f), shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, Modifier.size(20.dp), tint = c.tertiary)
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            actionLabel,
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(textDecoration = TextDecoration.Underline),
            color = if (dark) c.tertiary else c.onTertiaryContainer,
        )
    }
}
```

- [ ] **Step 7: Create `ui/components/SettingsRows.kt`**

```kotlin
package com.nobby.ring.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nobby.ring.ui.theme.NobbyRadius
import com.nobby.ring.ui.theme.NobbyTheme

/** A titled group of rows in one 24 dp list card (bundle `.list`). */
@Composable
fun SettingsGroup(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(NobbyRadius.lg)
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(title, Modifier.padding(start = 4.dp, bottom = 8.dp), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (NobbyTheme.extra.dark) c.surfaceContainer else c.surfaceContainerLowest)
                .border(1.dp, c.outlineVariant, shape),
            content = content,
        )
    }
}

/** 68 dp row: neutral icon tile, title, optional summary, chevron when clickable (or a custom [trailing]). */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon, TileTone.NEUTRAL, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.onSurface)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.onSurfaceVariant)
        }
    }
}

/** Hairline between rows, inset past the icon tile. */
@Composable
fun SettingsDivider() = HorizontalDivider(
    Modifier.padding(start = 70.dp, end = 16.dp),
    thickness = 1.dp,
    color = MaterialTheme.colorScheme.outlineVariant,
)
```

- [ ] **Step 8: Run the tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.components.*" --console=plain`
Expected: `BUILD SUCCESSFUL`; CardsTest 4 plus the earlier component tests.

- [ ] **Step 9: Verify and commit**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/nobby/ring/ui/components app/src/test/java/com/nobby/ring/ui/components/CardsTest.kt
git commit -m "feat(ui): v2 cards, event cards, banners and settings rows"
```

---
### Task 9: Home, Done celebration sheet, bottom bar

**Files:**
- Modify (replace): `app/src/main/java/com/nobby/ring/ui/home/HomeViewModel.kt`, `app/src/main/java/com/nobby/ring/ui/home/HomeScreen.kt`
- Create: `app/src/main/java/com/nobby/ring/ui/home/DoneSheet.kt`
- Modify: `app/src/main/java/com/nobby/ring/ui/navigation/NobbyNavHost.kt` (bottom bar)
- Delete: `app/src/main/java/com/nobby/ring/ui/components/RingProgress.kt`, `app/src/main/java/com/nobby/ring/ui/components/EventCard.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-de/strings.xml` (remove legacy Home keys)
- Modify: `app/src/test/java/com/nobby/ring/ui/home/HomeViewModelTest.kt`
- Test: `app/src/test/java/com/nobby/ring/ui/home/HomeContentTest.kt`

**Interfaces:**
- Consumes: `RingModel`, `RingLabel` (Task 7); `BeadRing`, `RingTone` (Task 7); `Nobby`, `MascotMood`, `DoneBurst` (Task 6); `NobbyCard`, `NextEventCard`, `OverdueCard`, `PermissionBanner`, `IconTile`, `TileTone`, `formatLongDate`, `shortLabel` (Task 8); `NobbyButton`, `ButtonTone`, `PhaseChip`, `ChipTone`, `NobbyTopBar`, `reducedMotion`, `NobbyMotion`, `NobbyRadius`, `NobbySpace` (Task 5); `PhaseChipModel` (Task 4); `NobbyType` (Task 1); `ConfirmDialog` (unchanged).
- Produces:
  - `data class Celebration(val kind: EventKind, val nextKind: EventKind?, val nextAt: LocalDateTime?)`
  - `data class HomeUiState(val state: ScheduleState? = null, val pendingConfirm: PendingConfirm? = null, val busy: Boolean = false, val celebration: Celebration? = null)`
  - `HomeViewModel(context, repo, clock, notifier: ScheduleChangeNotifier, scheduler: ReminderScheduler, alerts: Notifier)` with new `fun dismissCelebration()` and `fun snooze()`
  - `data class HomeActions(...)` and `@Composable fun HomeContent(ui: HomeUiState, exactAlarmsAllowed: Boolean, notificationsEnabled: Boolean, actions: HomeActions)`
  - `@Composable fun DoneSheet(celebration: Celebration, onDismiss: () -> Unit, modifier: Modifier = Modifier)`, `const val DONE_SHEET_TAG = "done_sheet"`, `const val AUTO_DISMISS_MS = 4_000L`

- [ ] **Step 1: Write the failing tests**

In `app/src/test/java/com/nobby/ring/ui/home/HomeViewModelTest.kt` replace the `vm()` factory

```kotlin
    private fun vm() = HomeViewModel(context, repo, clock, ScheduleChangeNotifier(setOf(object : ScheduleChangeListener {
        override suspend fun onScheduleChanged() { changes++ }
    })), ReminderScheduler(context, repo, ReminderRepository(db.reminderRuleDao()), OverdueConfigStore(ds), clock, ds, Notifier(context)))
```

with

```kotlin
    private fun vm() = HomeViewModel(
        context, repo, clock,
        ScheduleChangeNotifier(setOf(object : ScheduleChangeListener { override suspend fun onScheduleChanged() { changes++ } })),
        ReminderScheduler(context, repo, ReminderRepository(db.reminderRuleDao()), OverdueConfigStore(ds), clock, ds, Notifier(context)),
        Notifier(context),
    )
```

and add this test inside the class:

```kotlin
    @Test
    fun `an in-app done shows the celebration with the next event and dismiss clears it`() = runTest(dispatcher) {
        nowValue = LocalDateTime(2026, 11, 22, 21, 0) // on time: no dialog
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        vm.uiState.first { it.state != null }
        vm.onDoneTapped()
        val shown = vm.uiState.first { it.celebration != null }.celebration!!
        assertEquals(EventKind.REMOVE, shown.kind)
        assertEquals(EventKind.INSERT, shown.nextKind)
        assertEquals(LocalDateTime(2026, 11, 29, 21, 0), shown.nextAt)
        vm.dismissCelebration()
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.celebration)
    }
```

Create `app/src/test/java/com/nobby/ring/ui/home/HomeContentTest.kt`:

```kotlin
package com.nobby.ring.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.mascot.MascotMoodKey
import com.nobby.ring.ui.theme.NobbyTheme
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeContentTest {
    @get:Rule val compose = createComposeRule()

    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0))
    private val removed = EventLog("REMOVE@2026-11-22T21:00", EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), LocalDateTime(2026, 11, 22, 21, 0))
    private val celebration = Celebration(EventKind.REMOVE, EventKind.INSERT, LocalDateTime(2026, 11, 29, 21, 0))

    private fun state(now: LocalDateTime, logs: List<EventLog> = emptyList()) = ScheduleRepository.buildState(config, logs, now)
    private fun mood(m: MascotMood) = SemanticsMatcher.expectValue(MascotMoodKey, m)

    private fun showRing(now: LocalDateTime, logs: List<EventLog> = emptyList()) = compose.setContent {
        NobbyTheme(ThemeSpec()) { HomeContent(HomeUiState(state(now, logs)), exactAlarmsAllowed = true, notificationsEnabled = true, actions = HomeActions()) }
    }

    @Test fun `wear shows a happy sticker`() { showRing(LocalDateTime(2026, 11, 12, 12, 0)); compose.onNode(mood(MascotMood.HAPPY)).assertExists() }

    @Test fun `due today shows a neutral sticker`() { showRing(LocalDateTime(2026, 11, 22, 10, 0)); compose.onNode(mood(MascotMood.NEUTRAL)).assertExists() }

    @Test fun `overdue shows a worried sticker`() { showRing(LocalDateTime(2026, 11, 24, 22, 0)); compose.onNode(mood(MascotMood.WORRIED)).assertExists() }

    @Test fun `safety threshold shows an alert sticker`() { showRing(LocalDateTime(2026, 11, 30, 22, 0)); compose.onNode(mood(MascotMood.ALERT)).assertExists() }

    @Test fun `pause shows a sleepy sticker`() { showRing(LocalDateTime(2026, 11, 25, 12, 0), listOf(removed)); compose.onNode(mood(MascotMood.SLEEPY)).assertExists() }

    @Test
    fun `done sheet replaces the ring sticker and closes on tap`() {
        var shown by mutableStateOf<Celebration?>(celebration)
        compose.setContent {
            NobbyTheme(ThemeSpec()) {
                HomeContent(
                    HomeUiState(state(LocalDateTime(2026, 11, 22, 21, 30), listOf(removed)), celebration = shown),
                    exactAlarmsAllowed = true, notificationsEnabled = true,
                    actions = HomeActions(onDismissCelebration = { shown = null }),
                )
            }
        }
        compose.onNodeWithTag(DONE_SHEET_TAG).assertExists()
        compose.onNode(mood(MascotMood.YAY)).assertExists()
        compose.onNode(mood(MascotMood.SLEEPY)).assertDoesNotExist() // one mascot per screen
        compose.onNodeWithText("Yay, ring's out!").performClick()
        compose.onNodeWithTag(DONE_SHEET_TAG).assertDoesNotExist()
        compose.onNode(mood(MascotMood.SLEEPY)).assertExists()
    }

    @Test
    fun `done sheet closes by itself after four seconds`() {
        compose.mainClock.autoAdvance = false
        var shown by mutableStateOf<Celebration?>(celebration)
        compose.setContent {
            NobbyTheme(ThemeSpec()) {
                HomeContent(
                    HomeUiState(state(LocalDateTime(2026, 11, 22, 21, 30), listOf(removed)), celebration = shown),
                    exactAlarmsAllowed = true, notificationsEnabled = true,
                    actions = HomeActions(onDismissCelebration = { shown = null }),
                )
            }
        }
        compose.onNodeWithTag(DONE_SHEET_TAG).assertExists()
        compose.mainClock.advanceTimeBy(AUTO_DISMISS_MS + 1_000)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(DONE_SHEET_TAG).assertDoesNotExist()
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.home.*" --console=plain`
Expected: compilation FAILS (`Unresolved reference: HomeContent`, `Celebration`, `DONE_SHEET_TAG`; too many arguments for `HomeViewModel`).

- [ ] **Step 3: Replace `ui/home/HomeViewModel.kt`**

```kotlin
package com.nobby.ring.ui.home

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.engine.ConfirmAction
import com.nobby.ring.domain.engine.ShiftPolicy
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.ScheduleState
import com.nobby.ring.domain.model.ScheduledEvent
import com.nobby.ring.notifications.Notifier
import com.nobby.ring.notifications.ReminderScheduler
import com.nobby.ring.notifications.ScheduleChangeNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import javax.inject.Inject
import kotlin.time.Duration

data class PendingConfirm(val event: ScheduledEvent, val confirmedAt: LocalDateTime, val delay: Duration)

/** Shown on the Done sheet after a confirmation made in the app (never for notification or widget confirmations). */
data class Celebration(val kind: EventKind, val nextKind: EventKind?, val nextAt: LocalDateTime?)

/** True when the next event is due today or overdue, i.e. it may be confirmed. */
val ScheduleState.isDue: Boolean get() = daysUntilNext?.let { it <= 0 } == true

data class HomeUiState(
    val state: ScheduleState? = null,
    val pendingConfirm: PendingConfirm? = null,
    val busy: Boolean = false,
    val celebration: Celebration? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: ScheduleRepository,
    private val clock: NobbyClock,
    private val notifier: ScheduleChangeNotifier,
    private val scheduler: ReminderScheduler,
    private val alerts: Notifier,
) : ViewModel() {
    private val _exactAlarmsAllowed = MutableStateFlow(scheduler.canScheduleExact())
    /** Refreshed by [refreshPermissions] on every resume: the user may grant the permission in system settings. */
    val exactAlarmsAllowed: StateFlow<Boolean> = _exactAlarmsAllowed

    private val _notificationsEnabled = MutableStateFlow(notificationsOn())
    /** False when POST_NOTIFICATIONS is denied (API 33+) or notifications are switched off for the app. */
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled

    private val pending = MutableStateFlow<PendingConfirm?>(null)
    private val committing = MutableStateFlow(false)
    private val celebration = MutableStateFlow<Celebration?>(null)

    val uiState: StateFlow<HomeUiState> = combine(repo.state, pending, committing, celebration) { s, p, c, cel -> HomeUiState(s, p, c, cel) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun refreshPermissions() {
        _exactAlarmsAllowed.value = scheduler.canScheduleExact()
        _notificationsEnabled.value = notificationsOn()
    }

    fun onDoneTapped() {
        if (committing.value) return
        val state = uiState.value.state ?: return
        // Only the event that is due today (or overdue) can be confirmed; future events are not "done" yet.
        if (!state.isDue) return
        val event = state.nextEvent ?: return
        val now = clock.now()
        if (ConfirmAction.needsPolicyChoice(event, now)) {
            pending.value = PendingConfirm(event, now, delayBetween(event.plannedAt, now))
        } else {
            commit(event, now, ShiftPolicy.KEEP)
        }
    }

    fun onConfirmTime(time: LocalDateTime) = pending.update { p -> p?.copy(confirmedAt = time, delay = delayBetween(p.event.plannedAt, time)) }

    fun onPolicyChosen(policy: ShiftPolicy) {
        if (committing.value) return
        val p = pending.value ?: return
        pending.value = null
        commit(p.event, p.confirmedAt, policy)
    }

    fun dismissConfirm() { pending.value = null }

    fun dismissCelebration() { celebration.value = null }

    fun toggleSkipPause() = runExclusive("toggle skip pause") {
        val config = repo.currentState()?.config ?: return@runExclusive
        if (config.skipPauseOnce) repo.cancelSkipPause() else repo.skipPause()
    }

    /** In-app Snooze on the overdue card: exactly the notification's Snooze action (cancel the nag, re-arm later). */
    fun snooze() {
        val eventId = uiState.value.state?.overdue?.event?.id ?: return
        viewModelScope.launch {
            try {
                alerts.cancelOverdue()
                scheduler.armOverdueIn(scheduler.snoozeHours(), eventId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("NoBby", "snooze failed", e)
            }
        }
    }

    private fun commit(event: ScheduledEvent, at: LocalDateTime, policy: ShiftPolicy) =
        runExclusive("confirm event", onSuccess = { celebrate(event) }) { repo.confirm(event.id, at, policy) }

    private suspend fun celebrate(event: ScheduledEvent) {
        val next = repo.currentState()?.nextEvent
        celebration.value = Celebration(event.kind, next?.kind, next?.plannedAt)
    }

    private fun runExclusive(label: String, onSuccess: suspend () -> Unit = {}, block: suspend () -> Unit) {
        if (!committing.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                runCatching {
                    block()
                    notifier.notifyChanged()
                    onSuccess()
                }.onFailure { e ->
                    if (e is CancellationException) throw e
                    Log.e("NoBby", "$label failed", e)
                }
            } finally {
                committing.value = false
            }
        }
    }

    private fun notificationsOn(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun delayBetween(from: LocalDateTime, to: LocalDateTime): Duration =
        to.toInstant(TimeZone.UTC) - from.toInstant(TimeZone.UTC)
}
```

- [ ] **Step 4: Create `ui/home/DoneSheet.kt`**

```kotlin
package com.nobby.ring.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nobby.ring.R
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.ui.components.IconTile
import com.nobby.ring.ui.components.TileTone
import com.nobby.ring.ui.components.formatDateTime
import com.nobby.ring.ui.components.label
import com.nobby.ring.ui.mascot.DoneBurst
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.mascot.Nobby
import com.nobby.ring.ui.theme.NobbyMotion
import com.nobby.ring.ui.theme.NobbyRadius
import com.nobby.ring.ui.theme.NobbyTheme
import com.nobby.ring.ui.theme.reducedMotion
import kotlinx.datetime.LocalDateTime
import kotlin.math.roundToInt

const val DONE_SHEET_TAG = "done_sheet"
const val AUTO_DISMISS_MS = 4_000L

private fun EventKind.doneTitle(): Int = when (this) {
    EventKind.REMOVE -> R.string.done_title_remove
    EventKind.INSERT -> R.string.done_title_insert
    EventKind.SWAP -> R.string.done_title_swap
}

/**
 * Done celebration (bundle screens/done-sheet.html, without the "Rückgängig" button by owner decision): scrim + bottom
 * sheet with the yay mascot in the burst. Closes after 4 s, on tap anywhere or on a downward swipe.
 */
@Composable
fun DoneSheet(celebration: Celebration, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val dark = NobbyTheme.extra.dark
    val reduced = reducedMotion()
    val dismiss by rememberUpdatedState(onDismiss)
    val countdown = remember(celebration) { Animatable(1f) }
    val enter = remember(celebration) { Animatable(0f) }
    val pop = remember(celebration) { Animatable(if (reduced) 1f else 0.6f) }
    var dragY by remember(celebration) { mutableFloatStateOf(0f) }
    val dismissPx = with(LocalDensity.current) { 80.dp.toPx() }

    LaunchedEffect(celebration) {
        enter.animateTo(1f, tween(if (reduced) NobbyMotion.SHORT else NobbyMotion.LONG, easing = NobbyMotion.Decelerate))
    }
    LaunchedEffect(celebration) { if (!reduced) pop.animateTo(1f, NobbyMotion.squishSpring()) }
    LaunchedEffect(celebration) {
        countdown.animateTo(0f, tween(AUTO_DISMISS_MS.toInt(), easing = LinearEasing))
        dismiss()
    }

    Box(modifier.fillMaxSize().testTag(DONE_SHEET_TAG)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = enter.value }
                .background(c.scrim.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { dismiss() }),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .offset { IntOffset(0, dragY.roundToInt()) }
                .graphicsLayer {
                    alpha = if (reduced) enter.value else 1f
                    translationY = if (reduced) 0f else (1f - enter.value) * size.height
                }
                .clip(RoundedCornerShape(topStart = NobbyRadius.xl, topEnd = NobbyRadius.xl))
                .background(if (dark) c.surfaceContainerHigh else c.surfaceContainerLow)
                .pointerInput(celebration) {
                    detectVerticalDragGestures(onDragEnd = { if (dragY > dismissPx) dismiss() else dragY = 0f }) { _, dy ->
                        dragY = (dragY + dy).coerceAtLeast(0f)
                    }
                }
                .clickable(onClick = { dismiss() })
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(if (dark) c.outline else c.outlineVariant))
            Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                DoneBurst(Modifier.fillMaxWidth().widthIn(max = 320.dp))
                Nobby(MascotMood.YAY, 112.dp, Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; rotationZ = -4f })
            }
            Text(
                stringResource(celebration.kind.doneTitle()),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.done_lead),
                Modifier.padding(top = 6.dp, bottom = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = c.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (celebration.nextKind != null && celebration.nextAt != null) NextRow(celebration.nextKind, celebration.nextAt)
            LinearProgressIndicator(
                progress = { countdown.value },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(4.dp),
                color = c.outline,
                trackColor = c.surfaceContainerHighest,
            )
            Text(
                stringResource(R.string.done_autoclose),
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NextRow(kind: EventKind, at: LocalDateTime) {
    val icon = when (kind) {
        EventKind.REMOVE -> Icons.AutoMirrored.Rounded.Logout
        EventKind.INSERT -> Icons.AutoMirrored.Rounded.Login
        EventKind.SWAP -> Icons.Rounded.SwapHoriz
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(icon, if (kind == EventKind.INSERT) TileTone.PAUSE else TileTone.WEAR, size = 40.dp)
        Text(stringResource(R.string.done_next, kind.label(), formatDateTime(at)), style = MaterialTheme.typography.bodyMedium)
    }
}
```

- [ ] **Step 5: Replace `ui/home/HomeScreen.kt`**

```kotlin
package com.nobby.ring.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.domain.engine.ShiftPolicy
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.PhaseKind
import com.nobby.ring.domain.model.ScheduleState
import com.nobby.ring.ui.components.BeadRing
import com.nobby.ring.ui.components.ButtonTone
import com.nobby.ring.ui.components.ChipTone
import com.nobby.ring.ui.components.NextEventCard
import com.nobby.ring.ui.components.NobbyButton
import com.nobby.ring.ui.components.NobbyTopBar
import com.nobby.ring.ui.components.OverdueCard
import com.nobby.ring.ui.components.PermissionBanner
import com.nobby.ring.ui.components.PhaseChip
import com.nobby.ring.ui.components.RingTone
import com.nobby.ring.ui.components.formatDateTime
import com.nobby.ring.ui.components.formatLongDate
import com.nobby.ring.ui.components.formatTime
import com.nobby.ring.ui.components.plural
import com.nobby.ring.ui.components.shortLabel
import com.nobby.ring.ui.mascot.Nobby
import com.nobby.ring.ui.ring.RingLabel
import com.nobby.ring.ui.ring.RingModel
import com.nobby.ring.ui.theme.NobbySpace
import com.nobby.ring.ui.theme.NobbyType
import kotlinx.datetime.LocalDateTime

/** Callbacks of the Home screen, so [HomeContent] stays stateless and testable. */
data class HomeActions(
    val onDone: () -> Unit = {},
    val onSnooze: () -> Unit = {},
    val onToggleSkipPause: () -> Unit = {},
    val onConfirmTime: (LocalDateTime) -> Unit = {},
    val onPolicy: (ShiftPolicy) -> Unit = {},
    val onDismissConfirm: () -> Unit = {},
    val onDismissCelebration: () -> Unit = {},
    val onPermissionResult: () -> Unit = {},
)

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val exactAlarmsAllowed by viewModel.exactAlarmsAllowed.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose {}
    }
    HomeContent(
        ui, exactAlarmsAllowed, notificationsEnabled,
        HomeActions(
            onDone = viewModel::onDoneTapped,
            onSnooze = viewModel::snooze,
            onToggleSkipPause = viewModel::toggleSkipPause,
            onConfirmTime = viewModel::onConfirmTime,
            onPolicy = viewModel::onPolicyChosen,
            onDismissConfirm = viewModel::dismissConfirm,
            onDismissCelebration = viewModel::dismissCelebration,
            onPermissionResult = viewModel::refreshPermissions,
        ),
    )
}

@Composable
fun HomeContent(ui: HomeUiState, exactAlarmsAllowed: Boolean, notificationsEnabled: Boolean, actions: HomeActions) {
    val state = ui.state ?: return
    ui.pendingConfirm?.let { ConfirmDialog(it, actions.onConfirmTime, actions.onPolicy, actions.onDismissConfirm) }
    val model = RingModel.from(state)
    val compact = !exactAlarmsAllowed || !notificationsEnabled
    Box(Modifier.fillMaxSize()) {
        Scaffold(topBar = { HomeTopBar(state) }) { padding ->
            // Layout budget: the Done button stays above the fold on a Pixel 3a (393 x 807 dp) with both banners.
            Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = NobbySpace.gutter, vertical = NobbySpace.l),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(NobbySpace.l),
            ) {
                if (compact) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NobbySpace.s)) {
                        if (!notificationsEnabled) NotificationsBanner(actions.onPermissionResult)
                        if (!exactAlarmsAllowed) ExactAlarmBanner()
                    }
                }
                PhaseChip(PhaseChipModel.from(state).text(), chipTone(state))
                BeadRing(
                    model.tone, model.beads, model.done,
                    Modifier.fillMaxWidth(if (compact) 0.6f else 0.72f).widthIn(max = if (compact) 220.dp else 264.dp),
                    // One mascot per screen: the sticker steps aside while the Done sheet shows the yay mascot.
                    sticker = if (ui.celebration == null) { s -> Nobby(model.mood, s) } else null,
                ) { ringSize -> RingCenter(model, ringSize) }
                if (model.safety) SafetyNotice()
                NextSection(state, model, ui.busy, actions)
            }
        }
        ui.celebration?.let { DoneSheet(it, actions.onDismissCelebration) }
    }
}

@Composable
private fun HomeTopBar(state: ScheduleState) {
    NobbyTopBar(title = {
        Column {
            Text(stringResource(R.string.app_name))
            Text(
                formatLongDate(state.now.date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    })
}

private fun chipTone(state: ScheduleState): ChipTone = when {
    state.overdue != null -> ChipTone.OVERDUE
    state.position.phase == PhaseKind.PAUSE -> ChipTone.PAUSE
    else -> ChipTone.WEAR
}

/** Number + label (+ sub line / safety pill) inside the ring; the number keeps its size at font scale 1.3. */
@Composable
private fun RingCenter(model: RingModel, ringSize: Dp) {
    val c = MaterialTheme.colorScheme
    val k = ringSize / 264.dp
    val numberSp = with(LocalDensity.current) { (96.dp * k).toSp() }
    val wordSp = with(LocalDensity.current) { (45.dp * k).toSp() }
    val urgent = model.tone == RingTone.OVERDUE || model.tone == RingTone.SAFETY
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (model.label == RingLabel.TODAY) {
            Text(stringResource(R.string.ring_today), style = NobbyType.countdownWord.copy(fontSize = wordSp, lineHeight = wordSp), color = c.onSurface)
        } else {
            model.number?.let {
                Text("$it", style = NobbyType.countdown.copy(fontSize = numberSp, lineHeight = numberSp), color = if (urgent) c.error else c.onSurface, maxLines = 1)
            }
        }
        Text(model.labelText(), style = MaterialTheme.typography.titleMedium, color = c.onSurface, textAlign = TextAlign.Center)
        model.subText()?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center) }
        if (model.safety) {
            Row(
                Modifier.padding(top = 8.dp).clip(CircleShape).background(c.error).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Rounded.Warning, contentDescription = null, Modifier.size(14.dp), tint = c.onError)
                Text(stringResource(R.string.ring_check_protection), style = MaterialTheme.typography.labelMedium, color = c.onError, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RingModel.labelText(): String {
    val n = number ?: 0
    return when (label) {
        RingLabel.UNTIL_REMOVE -> plural(R.plurals.ring_until_remove, n)
        RingLabel.UNTIL_INSERT -> plural(R.plurals.ring_until_insert, n)
        RingLabel.UNTIL_SWAP -> plural(R.plurals.ring_until_swap, n)
        RingLabel.OVERDUE_DAYS -> plural(R.plurals.ring_overdue_days, n)
        RingLabel.OVERDUE_HOURS -> plural(R.plurals.ring_overdue_hours, n)
        RingLabel.TODAY -> event?.kind?.shortLabel() ?: ""
        RingLabel.NONE -> stringResource(R.string.widget_none)
    }
}

@Composable
private fun RingModel.subText(): String? {
    val e = event ?: return null
    return when (label) {
        RingLabel.TODAY -> stringResource(R.string.ring_at_time, formatTime(e.plannedAt.time))
        RingLabel.OVERDUE_DAYS, RingLabel.OVERDUE_HOURS -> if (safety) null else stringResource(R.string.ring_since, formatDateTime(e.plannedAt))
        RingLabel.NONE -> null
        else -> formatDateTime(e.plannedAt)
    }
}

@Composable
private fun NextSection(state: ScheduleState, model: RingModel, busy: Boolean, actions: HomeActions) {
    val overdue = state.overdue
    if (overdue != null) {
        val n = model.number ?: 0
        val overText = if (model.label == RingLabel.OVERDUE_DAYS) plural(R.plurals.card_overdue_days, n, n) else plural(R.plurals.card_overdue_hours, n, n)
        val safety = if (!overdue.safetyBreached) null
        else stringResource(if (overdue.event.kind == EventKind.INSERT) R.string.home_overdue_safety_pause else R.string.home_overdue_safety_wear)
        OverdueCard(overdue.event.kind, overdue.event.plannedAt, overText, safety, actions.onDone, actions.onSnooze, enabled = !busy)
        return
    }
    val next = state.nextEvent ?: return
    val days = state.daysUntilNext ?: return
    NextEventCard(next.kind, next.plannedAt, days, actions = {
        if (state.isDue) {
            NobbyButton(actions.onDone, enabled = !busy, icon = Icons.Rounded.Check) { Text(stringResource(R.string.home_mark_done)) }
        }
        NobbyButton(actions.onToggleSkipPause, enabled = !busy, tone = ButtonTone.TEXT) {
            Text(stringResource(if (state.config.skipPauseOnce) R.string.home_skip_pause_cancel else R.string.home_skip_pause))
        }
    })
}

/** One-line notice above the cards when a safety threshold is crossed, so it is visible without scrolling. */
@Composable
private fun SafetyNotice() {
    val color = MaterialTheme.colorScheme.error
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Rounded.Warning, contentDescription = null, Modifier.size(18.dp), tint = color)
        Text(
            stringResource(R.string.notif_overdue_safety_short),
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ExactAlarmBanner() {
    val ctx = LocalContext.current
    PermissionBanner(stringResource(R.string.exact_alarm_warning), Icons.Rounded.AlarmOff, stringResource(R.string.action_allow), onClick = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.startSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
        }
    })
}

/**
 * The first tap on API 33+ asks for POST_NOTIFICATIONS. After the system dialog has been answered (or below API 33,
 * or when notifications were switched off in settings) taps open the app's notification settings instead.
 */
@Composable
private fun NotificationsBanner(onResult: () -> Unit) {
    val ctx = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        asked = true
        onResult()
    }
    PermissionBanner(stringResource(R.string.notifications_disabled_warning), Icons.Rounded.NotificationsOff, stringResource(R.string.action_allow), onClick = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !asked) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ctx.startSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }
    })
}

private fun Context.startSettings(intent: Intent) {
    runCatching { startActivity(intent) }.onFailure { Log.e("NoBby", "Cannot open settings: ${intent.action}", it) }
}
```

- [ ] **Step 6: Restyle the bottom bar** — in `ui/navigation/NobbyNavHost.kt` replace the whole `bottomBar = { … },` argument

```kotlin
        bottomBar = {
            if (showBar) NavigationBar {
                listOf(
                    Triple(Routes.HOME, R.string.nav_home, Icons.Default.Home),
                    Triple(Routes.CALENDAR, R.string.nav_calendar, Icons.Default.CalendarMonth),
                    Triple(Routes.HISTORY, R.string.nav_history, Icons.Default.History),
                    Triple(Routes.SETTINGS, R.string.nav_settings, Icons.Default.Settings),
                ).forEach { (dest, label, icon) ->
                    NavigationBarItem(
                        selected = route == dest,
                        onClick = { navController.navigate(dest) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(stringResource(label), maxLines = 1, softWrap = false, fontSize = 11.sp) },
                    )
                }
            }
        },
```

with

```kotlin
        bottomBar = {
            if (showBar) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 0.dp) {
                listOf(
                    NavItem(Routes.HOME, R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home),
                    NavItem(Routes.CALENDAR, R.string.nav_calendar, Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth),
                    NavItem(Routes.HISTORY, R.string.nav_history, Icons.Rounded.History, Icons.Outlined.History),
                    NavItem(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings),
                ).forEach { item ->
                    val selected = route == item.route
                    val c = MaterialTheme.colorScheme
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigate(item.route) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.label), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.onPrimaryContainer,
                            selectedTextColor = c.onSurface,
                            indicatorColor = c.primaryContainer,
                            unselectedIconColor = c.onSurfaceVariant,
                            unselectedTextColor = c.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
```

append at the end of the file

```kotlin
/** Filled icon when selected, outlined otherwise (bundle nav bar). */
private data class NavItem(val route: String, @StringRes val label: Int, val selectedIcon: ImageVector, val icon: ImageVector)
```

and replace the imports

```kotlin
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
```

with

```kotlin
import androidx.annotation.StringRes
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
```

and remove `import androidx.compose.ui.unit.sp` (no longer used).

- [ ] **Step 7: Delete replaced v1 components and legacy Home strings**

```bash
git rm app/src/main/java/com/nobby/ring/ui/components/RingProgress.kt app/src/main/java/com/nobby/ring/ui/components/EventCard.kt
for f in app/src/main/res/values/strings.xml app/src/main/res/values-de/strings.xml; do
  sed -i -e '/legacy v1 Home/d' -e '/name="home_days_remaining"/d' -e '/name="home_day_remaining"/d' \
         -e '/name="home_today"/d' -e '/name="home_overdue_days"/d' -e '/name="home_overdue_title"/d' "$f"
done
grep -rnE "RingProgress|EventCard\(|home_days_remaining|home_day_remaining|R\.string\.home_today|home_overdue_days|home_overdue_title" app/src/main
```

Expected: the final `grep` prints nothing.

- [ ] **Step 8: Run the tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.home.*" --tests "com.nobby.ring.i18n.*" --console=plain`
Expected: `BUILD SUCCESSFUL`; HomeViewModelTest 6, HomeContentTest 7, PhaseChipModelTest 5, parity tests green.

- [ ] **Step 9: Verify and commit**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

```bash
git add -A app/src/main/java/com/nobby/ring/ui app/src/main/res/values app/src/main/res/values-de app/src/test/java/com/nobby/ring/ui/home
git commit -m "feat(home): sticker-diary Home with bead ring, mascot sticker and Done celebration sheet" -m "No undo on the Done sheet (owner decision). In-app Snooze reuses the notification snooze path."
```

---

### Task 10: Calendar with highlighter bands, legend and selected-day card

**Files:**
- Modify (replace): `app/src/main/java/com/nobby/ring/ui/calendar/DayInfoBuilder.kt`, `ui/calendar/MonthGrid.kt`, `ui/calendar/CalendarScreen.kt`
- Modify: `app/src/main/java/com/nobby/ring/ui/calendar/CalendarViewModel.kt` (add `goToToday`)
- Modify: strings (remove legacy `calendar_notes_title`)
- Test: `app/src/test/java/com/nobby/ring/ui/calendar/DayInfoTest.kt` (add test), `app/src/test/java/com/nobby/ring/ui/calendar/MonthGridTest.kt`, `app/src/test/java/com/nobby/ring/ui/calendar/CalendarViewModelTest.kt`

**Interfaces:**
- Consumes: `NoteCard`, `NobbyTopBar`, `NobbyButton`, `ButtonTone`, `formatLongDate`, `formatDate` (Tasks 5, 8); `Nobby` (Task 6); keys `calendar_*`, `legend_*`, `phase_day` (Task 3).
- Produces: `data class DayInfo(date, phase, event, hasNote, isToday, dayInPhase: Int = 0, phaseLength: Int = 0)`, `data class BandEdges(val start: Boolean, val end: Boolean)`, `fun bandEdges(cells: List<DayInfo?>, index: Int): BandEdges`, `@Composable fun DayPhase.label(): String`, `CalendarViewModel.goToToday()`.

- [ ] **Step 1: Write the failing tests**

Append inside `DayInfoTest`:

```kotlin
    @Test
    fun `day in phase counts wear and pause separately`() {
        val days = DayInfoBuilder.build(state, 2026, 11, emptySet())
        assertEquals(3, days[2].dayInPhase)    // Nov 3: wear day 3
        assertEquals(21, days[2].phaseLength)
        assertEquals(1, days[21].dayInPhase)   // Nov 22: pause day 1
        assertEquals(7, days[21].phaseLength)
        assertEquals(1, days[28].dayInPhase)   // Nov 29: new ring, wear day 1
    }
```

`app/src/test/java/com/nobby/ring/ui/calendar/MonthGridTest.kt`:

```kotlin
package com.nobby.ring.ui.calendar

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthGridTest {
    private fun day(d: Int) = DayInfo(LocalDate(2026, 11, d), if (d in 22..28) DayPhase.PAUSE else DayPhase.WEAR, null, false, false)

    // 1 Nov 2026 is a Sunday: six leading blanks, Nov d sits at index d + 5.
    private val cells: List<DayInfo?> = List(6) { null } + (1..30).map(::day)

    @Test
    fun `bands round off where the phase changes or the week row ends`() {
        assertEquals(BandEdges(start = true, end = true), bandEdges(cells, 6))     // Sun Nov 1, alone in its row
        assertEquals(BandEdges(start = true, end = false), bandEdges(cells, 7))    // Mon Nov 2 starts a row
        assertEquals(BandEdges(start = false, end = true), bandEdges(cells, 26))   // Sat Nov 21, last wear day
        assertEquals(BandEdges(start = true, end = true), bandEdges(cells, 27))    // Sun Nov 22, first pause day, row end
        assertEquals(BandEdges(start = true, end = false), bandEdges(cells, 28))   // Mon Nov 23
        assertEquals(BandEdges(start = false, end = false), bandEdges(cells, 0))   // blank cell
    }
}
```

`app/src/test/java/com/nobby/ring/ui/calendar/CalendarViewModelTest.kt`:

```kotlin
package com.nobby.ring.ui.calendar

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.data.db.NobbyDatabase
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.data.repo.NotesRepository
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CalendarViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: NobbyDatabase
    private lateinit var vm: CalendarViewModel
    private val clock = object : NobbyClock {
        override fun now() = LocalDateTime(2026, 11, 10, 12, 0)
        override val zone = TimeZone.UTC
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NobbyDatabase::class.java).allowMainThreadQueries().build()
        val ds = PreferenceDataStoreFactory.create { File(context.cacheDir, "cal-${System.nanoTime()}.preferences_pb") }
        vm = CalendarViewModel(ScheduleRepository(MethodConfigStore(ds), db.eventLogDao(), clock), NotesRepository(db.noteDao(), clock), clock)
    }

    @After fun tearDown() { Dispatchers.resetMain(); db.close() }

    @Test
    fun `go to today returns to the current month and selects today`() = runTest(dispatcher) {
        backgroundScope.launch { vm.uiState.collect {} }
        vm.nextMonth()
        vm.nextMonth()
        vm.select(LocalDate(2027, 1, 5))
        dispatcher.scheduler.advanceUntilIdle()
        vm.goToToday()
        val ui = vm.uiState.first { it.month == 11 && it.selected == LocalDate(2026, 11, 10) }
        assertEquals(2026, ui.year)
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.calendar.*" --console=plain`
Expected: compilation FAILS (`Unresolved reference: dayInPhase`, `BandEdges`, `bandEdges`, `goToToday`).

- [ ] **Step 3: Replace `ui/calendar/DayInfoBuilder.kt`**

```kotlin
package com.nobby.ring.ui.calendar

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.ScheduleState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

enum class DayPhase { WEAR, PAUSE, NONE }

data class DayInfo(
    val date: LocalDate,
    val phase: DayPhase,
    val event: EventKind?,
    val hasNote: Boolean,
    val isToday: Boolean,
    /** 1-based day within the wear or pause phase; 0 when [phase] is NONE. */
    val dayInPhase: Int = 0,
    val phaseLength: Int = 0,
)

object DayInfoBuilder {
    fun build(state: ScheduleState, year: Int, month: Int, notes: Set<LocalDate>): List<DayInfo> {
        val first = LocalDate(year, month, 1)
        val length = first.plus(1, DateTimeUnit.MONTH).toEpochDays() - first.toEpochDays()
        val method = state.config.method
        val eventsByDate = state.events.associateBy({ it.plannedAt.date }, { it.kind })
        // pause windows: [REMOVE date, INSERT date)
        val pauseWindows = state.events.filter { it.kind == EventKind.REMOVE }.map { remove ->
            val insert = state.events.firstOrNull { it.kind == EventKind.INSERT && it.plannedAt > remove.plannedAt }
            remove.plannedAt.date to (insert?.plannedAt?.date ?: remove.plannedAt.date.plus(method.pauseDays, DateTimeUnit.DAY))
        }
        val anchorDate = state.config.anchor.date
        val horizonEnd = state.events.lastOrNull()?.plannedAt?.date ?: anchorDate
        val cycleStarts = (listOf(anchorDate) + state.events.filter { it.startsCycle }.map { it.plannedAt.date }).sorted()
        return (0 until length).map { i ->
            val date = first.plus(i, DateTimeUnit.DAY)
            val pauseStart = pauseWindows.firstOrNull { (from, to) -> date >= from && date < to }?.first
            val phase = when {
                date < anchorDate || date > horizonEnd -> DayPhase.NONE
                pauseStart != null -> DayPhase.PAUSE
                else -> DayPhase.WEAR
            }
            val dayInPhase = when (phase) {
                DayPhase.WEAR -> date.toEpochDays() - cycleStarts.last { it <= date }.toEpochDays() + 1
                DayPhase.PAUSE -> date.toEpochDays() - requireNotNull(pauseStart).toEpochDays() + 1
                DayPhase.NONE -> 0
            }
            val phaseLength = when (phase) {
                DayPhase.WEAR -> method.wearDays
                DayPhase.PAUSE -> method.pauseDays
                DayPhase.NONE -> 0
            }
            DayInfo(date, phase, eventsByDate[date], date in notes, date == state.now.date, dayInPhase, phaseLength)
        }
    }
}
```

- [ ] **Step 4: Replace `ui/calendar/MonthGrid.kt`**

```kotlin
package com.nobby.ring.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nobby.ring.R
import com.nobby.ring.ui.components.formatDate
import kotlinx.datetime.LocalDate
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private const val DAYS_PER_WEEK = 7

data class BandEdges(val start: Boolean, val end: Boolean)

/** Rounded band ends where the phase changes, or where the week row or the month starts or ends. */
fun bandEdges(cells: List<DayInfo?>, index: Int): BandEdges {
    val day = cells.getOrNull(index) ?: return BandEdges(start = false, end = false)
    val col = index % DAYS_PER_WEEK
    val prev = if (col > 0) cells.getOrNull(index - 1) else null
    val next = if (col < DAYS_PER_WEEK - 1) cells.getOrNull(index + 1) else null
    return BandEdges(start = prev?.phase != day.phase, end = next?.phase != day.phase)
}

@Composable
fun DayPhase.label(): String = when (this) {
    DayPhase.WEAR -> stringResource(R.string.phase_wear)
    DayPhase.PAUSE -> stringResource(R.string.phase_pause)
    DayPhase.NONE -> ""
}

/** Monday-first month grid with continuous highlighter bands per phase (bundle components/calendar.html). */
@Composable
fun MonthGrid(days: List<DayInfo>, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val leading = days.firstOrNull()?.date?.dayOfWeek?.let { (it.ordinal - DayOfWeek.MONDAY.ordinal + DAYS_PER_WEEK) % DAYS_PER_WEEK } ?: 0
    val cells: List<DayInfo?> = List(leading) { null } + days
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            DayOfWeek.entries.forEach { dow ->
                Text(
                    dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        cells.chunked(DAYS_PER_WEEK).forEachIndexed { w, week ->
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until DAYS_PER_WEEK) {
                    val index = w * DAYS_PER_WEEK + i
                    val day = week.getOrNull(i)
                    Box(Modifier.weight(1f).height(52.dp)) {
                        if (day != null) DayCell(day, day.date == selected, bandEdges(cells, index)) { onSelect(day.date) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: DayInfo, selected: Boolean, edges: BandEdges, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val band = when (day.phase) {
        DayPhase.WEAR -> c.primaryContainer
        DayPhase.PAUSE -> c.secondaryContainer
        DayPhase.NONE -> Color.Transparent
    }
    val description = if (day.phase == DayPhase.NONE) formatDate(day.date)
    else stringResource(R.string.calendar_cell_a11y, formatDate(day.date), day.phase.label())
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                if (day.phase == DayPhase.NONE) return@drawBehind
                val inset = 4.dp.toPx()
                val r = CornerRadius(14.dp.toPx())
                val zero = CornerRadius.Zero
                val shape = RoundRect(
                    left = if (edges.start) inset else 0f,
                    top = 7.dp.toPx(),
                    right = size.width - if (edges.end) inset else 0f,
                    bottom = size.height - 9.dp.toPx(),
                    topLeftCornerRadius = if (edges.start) r else zero,
                    topRightCornerRadius = if (edges.end) r else zero,
                    bottomRightCornerRadius = if (edges.end) r else zero,
                    bottomLeftCornerRadius = if (edges.start) r else zero,
                )
                drawPath(Path().apply { addRoundRect(shape) }, band)
            }
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (selected) c.inverseSurface else Color.Transparent)
                .then(if (day.isToday && !selected) Modifier.border(2.dp, c.onSurface, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${day.date.dayOfMonth}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (day.event != null || day.isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    selected -> c.inverseOnSurface
                    day.phase == DayPhase.NONE -> c.outline
                    else -> c.onSurface
                },
            )
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp).height(7.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (day.event != null) Box(Modifier.size(7.dp).border(1.75.dp, c.onSurface, CircleShape))
            if (day.hasNote) Box(Modifier.size(6.dp).clip(CircleShape).background(c.tertiary))
        }
    }
}
```

- [ ] **Step 5: Add `goToToday` to `CalendarViewModel.kt`** — insert after `fun nextMonth() = …`:

```kotlin
    fun goToToday() {
        month.value = LocalDate(today.year, today.monthNumber, 1)
        select(today)
    }
```

- [ ] **Step 6: Replace `ui/calendar/CalendarScreen.kt`**

```kotlin
package com.nobby.ring.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.ui.components.ButtonTone
import com.nobby.ring.ui.components.NobbyButton
import com.nobby.ring.ui.components.NobbyTopBar
import com.nobby.ring.ui.components.NoteCard
import com.nobby.ring.ui.components.formatLongDate
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.mascot.Nobby
import com.nobby.ring.ui.theme.NobbyRadius
import com.nobby.ring.ui.theme.NobbySpace
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        NobbyTopBar(
            title = { Text(stringResource(R.string.nav_calendar)) },
            actions = { NobbyButton(viewModel::goToToday, tone = ButtonTone.TEXT) { Text(stringResource(R.string.calendar_today)) } },
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = NobbySpace.gutter, vertical = NobbySpace.m),
            verticalArrangement = Arrangement.spacedBy(NobbySpace.l),
        ) {
            MonthHeader(ui.year, ui.month, viewModel::prevMonth, viewModel::nextMonth)
            MonthGrid(ui.days, ui.selected, viewModel::select)
            Legend()
            SelectedDayCard(ui, viewModel::setNote)
        }
    }
}

@Composable
private fun MonthHeader(year: Int, month: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.Rounded.ChevronLeft, stringResource(R.string.calendar_prev_month)) }
        Text(
            stringResource(R.string.calendar_month_title, Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()), year),
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onNext) { Icon(Icons.Rounded.ChevronRight, stringResource(R.string.calendar_next_month)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    val c = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LegendItem(stringResource(R.string.legend_wear)) { Box(Modifier.size(18.dp, 10.dp).clip(RoundedCornerShape(5.dp)).background(c.primaryContainer)) }
        LegendItem(stringResource(R.string.legend_pause)) { Box(Modifier.size(18.dp, 10.dp).clip(RoundedCornerShape(5.dp)).background(c.secondaryContainer)) }
        LegendItem(stringResource(R.string.legend_event)) { Box(Modifier.size(8.dp).border(1.75.dp, c.onSurface, CircleShape)) }
        LegendItem(stringResource(R.string.legend_note)) { Box(Modifier.size(7.dp).clip(CircleShape).background(c.tertiary)) }
    }
}

@Composable
private fun LegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        swatch()
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Selected-day note card; a small flat NoBby keeps an empty day company (the only mascot on this screen). */
@Composable
private fun SelectedDayCard(ui: CalendarUiState, onNote: (String) -> Unit) {
    val day = ui.days.firstOrNull { it.date == ui.selected }
    NoteCard {
        Text(formatLongDate(ui.selected), style = MaterialTheme.typography.titleMedium)
        if (day != null && day.phase != DayPhase.NONE) {
            Text(
                stringResource(R.string.phase_day, day.phase.label(), day.dayInPhase, day.phaseLength),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (ui.noteText.isBlank()) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Nobby(MascotMood.NEUTRAL, 40.dp, flat = true)
                Text(stringResource(R.string.calendar_note_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(
            value = ui.noteText,
            onValueChange = onNote,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            label = { Text(stringResource(R.string.calendar_note_label)) },
            placeholder = { Text(stringResource(R.string.calendar_notes_hint)) },
            minLines = 3,
            shape = RoundedCornerShape(NobbyRadius.sm),
        )
    }
}
```

- [ ] **Step 7: Remove the legacy calendar key**

```bash
for f in app/src/main/res/values/strings.xml app/src/main/res/values-de/strings.xml; do
  sed -i -e '/legacy v1 calendar/d' -e '/legacy v1 Kalender/d' -e '/name="calendar_notes_title"/d' "$f"
done
grep -rn "calendar_notes_title" app/src/main
```

Expected: no output.

- [ ] **Step 8: Run the tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.calendar.*" --tests "com.nobby.ring.i18n.*" --console=plain`
Expected: `BUILD SUCCESSFUL`; DayInfoTest 3, MonthGridTest 1, CalendarViewModelTest 1.

- [ ] **Step 9: Verify and commit**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/nobby/ring/ui/calendar app/src/main/res/values app/src/main/res/values-de app/src/test/java/com/nobby/ring/ui/calendar
git commit -m "feat(calendar): highlighter phase bands, legend, today jump and selected-day card"
```

---

### Task 11: History rows and the NoBby empty state

**Files:**
- Modify (replace): `app/src/main/java/com/nobby/ring/ui/history/HistoryScreen.kt`
- Modify: strings (remove legacy `history_empty`)
- Test: `app/src/test/java/com/nobby/ring/ui/history/HistoryTest.kt`

**Interfaces:**
- Consumes: `HistoryEntry`, `HistoryViewModel` (unchanged); `IconTile`, `TileTone`, `NobbyTopBar`, `formatDateTime`, `formatTime`, `formatDuration` (Tasks 5, 8); `Nobby`, `MascotProp` (Task 6); `ConfirmAction.CONFIRM_TOLERANCE_MINUTES` (domain).
- Produces: `enum class HistoryTiming { ON_TIME, LATE, EARLY }`, `fun historyTiming(delay: Duration): HistoryTiming`, `@Composable fun HistoryContent(entries: List<HistoryEntry>)`.

- [ ] **Step 1: Write the failing test** `app/src/test/java/com/nobby/ring/ui/history/HistoryTest.kt`:

```kotlin
package com.nobby.ring.ui.history

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.mascot.MascotProp
import com.nobby.ring.ui.mascot.MascotPropKey
import com.nobby.ring.ui.theme.NobbyTheme
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
class HistoryTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `timing uses the 60 minute confirmation tolerance`() {
        assertEquals(HistoryTiming.ON_TIME, historyTiming(59.minutes))
        assertEquals(HistoryTiming.ON_TIME, historyTiming((-59).minutes))
        assertEquals(HistoryTiming.LATE, historyTiming(60.minutes))
        assertEquals(HistoryTiming.EARLY, historyTiming((-2).hours))
    }

    @Test
    fun `empty history shows NoBby with the note`() {
        compose.setContent { NobbyTheme(ThemeSpec()) { HistoryContent(emptyList()) } }
        compose.onNode(SemanticsMatcher.expectValue(MascotPropKey, MascotProp.NOTE)).assertExists()
        compose.onNodeWithText("Nothing here yet").assertExists()
    }

    @Test
    fun `the first entry replaces the empty state`() {
        val e = HistoryEntry(EventKind.REMOVE, LocalDateTime(2026, 10, 11, 21, 0), LocalDateTime(2026, 10, 11, 21, 40), 40.minutes)
        compose.setContent { NobbyTheme(ThemeSpec()) { HistoryContent(listOf(e)) } }
        compose.onNodeWithText("Ring removed").assertExists()
        compose.onNodeWithText("40 min late").assertExists()
        compose.onNode(SemanticsMatcher.expectValue(MascotPropKey, MascotProp.NOTE)).assertDoesNotExist()
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.history.*" --console=plain`
Expected: compilation FAILS (`Unresolved reference: HistoryTiming`, `HistoryContent`).

- [ ] **Step 3: Replace `ui/history/HistoryScreen.kt`**

```kotlin
package com.nobby.ring.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.domain.engine.ConfirmAction
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.ui.components.IconTile
import com.nobby.ring.ui.components.NobbyTopBar
import com.nobby.ring.ui.components.TileTone
import com.nobby.ring.ui.components.formatDateTime
import com.nobby.ring.ui.components.formatDuration
import com.nobby.ring.ui.components.formatTime
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.mascot.MascotProp
import com.nobby.ring.ui.mascot.Nobby
import com.nobby.ring.ui.theme.NobbyRadius
import com.nobby.ring.ui.theme.NobbySpace
import com.nobby.ring.ui.theme.NobbyTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

enum class HistoryTiming { ON_TIME, LATE, EARLY }

/** Same 60-minute tolerance as the confirmation dialog. */
fun historyTiming(delay: Duration): HistoryTiming = when {
    delay.absoluteValue < ConfirmAction.CONFIRM_TOLERANCE_MINUTES.minutes -> HistoryTiming.ON_TIME
    delay.isPositive() -> HistoryTiming.LATE
    else -> HistoryTiming.EARLY
}

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    HistoryContent(entries)
}

@Composable
fun HistoryContent(entries: List<HistoryEntry>) {
    Scaffold(topBar = { NobbyTopBar(title = { Text(stringResource(R.string.nav_history)) }) }) { padding ->
        if (entries.isEmpty()) {
            EmptyHistory(Modifier.padding(padding))
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = NobbySpace.gutter, vertical = NobbySpace.m),
                verticalArrangement = Arrangement.spacedBy(NobbySpace.s),
            ) { items(entries) { HistoryRow(it) } }
        }
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Nobby(MascotMood.NEUTRAL, 120.dp, prop = MascotProp.NOTE)
        Text(stringResource(R.string.history_empty_title), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.history_empty_body),
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HistoryRow(e: HistoryEntry) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(NobbyRadius.lg)
    val (icon, tone, title) = when (e.kind) {
        EventKind.REMOVE -> Triple(Icons.AutoMirrored.Rounded.Logout, TileTone.PAUSE, R.string.history_removed)
        EventKind.INSERT -> Triple(Icons.AutoMirrored.Rounded.Login, TileTone.WEAR, R.string.history_inserted)
        EventKind.SWAP -> Triple(Icons.Rounded.SwapHoriz, TileTone.WEAR, R.string.history_swapped)
    }
    val timing = historyTiming(e.delay)
    val timingText = when (timing) {
        HistoryTiming.ON_TIME -> stringResource(R.string.history_on_time)
        HistoryTiming.LATE -> stringResource(R.string.history_late, formatDuration(e.delay))
        HistoryTiming.EARLY -> stringResource(R.string.history_early, formatDuration(-e.delay))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (NobbyTheme.extra.dark) c.surfaceContainer else c.surfaceContainerLowest)
            .border(1.dp, c.outlineVariant, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon, tone, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.history_row_meta, formatDateTime(e.confirmedAt), formatTime(e.plannedAt.time)),
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
            )
        }
        Text(
            timingText,
            style = MaterialTheme.typography.labelMedium,
            color = when (timing) {
                HistoryTiming.ON_TIME -> c.secondary
                HistoryTiming.LATE -> c.error
                HistoryTiming.EARLY -> c.onSurfaceVariant
            },
        )
    }
}
```

- [ ] **Step 4: Remove the legacy history key**

```bash
for f in app/src/main/res/values/strings.xml app/src/main/res/values-de/strings.xml; do
  sed -i -e '/legacy v1 history/d' -e '/legacy v1 Verlauf/d' -e '/name="history_empty"/d' "$f"
done
grep -rn "R.string.history_empty\b" app/src/main
```

Expected: no output (`history_empty_title` / `history_empty_body` remain).

- [ ] **Step 5: Run the tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.history.*" --tests "com.nobby.ring.i18n.*" --console=plain`
Expected: `BUILD SUCCESSFUL`; HistoryTest 3.

- [ ] **Step 6: Verify and commit**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/nobby/ring/ui/history app/src/main/res/values app/src/main/res/values-de app/src/test/java/com/nobby/ring/ui/history
git commit -m "feat(history): restyled rows with timing and a NoBby empty state"
```

---
### Task 12: Settings hub, About + licences, Appearance with preset picker, restyled sub-screens

**Files:**
- Create: `app/src/main/java/com/nobby/ring/ui/components/PresetPicker.kt`
- Modify (replace): `app/src/main/java/com/nobby/ring/ui/settings/SettingsScreen.kt`, `ui/settings/AppearanceScreen.kt`
- Modify (mechanical): `ui/settings/ScheduleSettingsScreen.kt`, `RemindersScreen.kt`, `WidgetSettingsScreen.kt`, `CalendarSettingsScreen.kt`, `CalendarCleanupScreen.kt`, `ReminderEditDialog.kt`, and the confirm dialog `ui/home/ConfirmDialog.kt`
- Test: `app/src/test/java/com/nobby/ring/ui/settings/AppearanceTest.kt`

**Interfaces:**
- Consumes: `SettingsViewModel` flows `schedule`, `rules`, `overdue`, `theme`, `widget`, `setTheme` (unchanged); `SettingsGroup`, `SettingsRow`, `SettingsDivider`, `NobbyCard`, `CardTone` (Task 8); `NobbyButton`, `ButtonTone`, `NobbyTopBar`, `SegmentedChoice`, `PhaseChip`, `ChipTone`, `nobbySegmentedColors` (Task 5); `BeadRing`, `RingTone` (Task 7); `Nobby`, `MascotProp` (Task 6); `Palette.roles`, `NobbyTheme.extra` (Task 2); `Palette.label()`, `ThemeMode.label()`, `formatTime`, `plural` (Tasks 2, 3, 8); `R.raw.ofl_fredoka`, `R.raw.ofl_nunito_sans` (Task 1).
- Produces: `@Composable fun PresetPicker(selected: Palette, onSelect: (Palette) -> Unit, modifier: Modifier = Modifier)` (test tags `preset_<NAME>`), `@Composable fun AppearanceContent(theme: ThemeSpec, onChange: (ThemeSpec) -> Unit, modifier: Modifier = Modifier)`, `val MODE_ORDER: List<ThemeMode>` (Light, Dark, Auto).

- [ ] **Step 1: Write the failing test** `app/src/test/java/com/nobby/ring/ui/settings/AppearanceTest.kt`:

```kotlin
package com.nobby.ring.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.ThemeMode
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.theme.NobbyTheme
import com.nobby.ring.ui.theme.roles
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppearanceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `tapping a preset switches the live theme`() {
        var spec by mutableStateOf(ThemeSpec())
        var primary = Color.Unspecified
        compose.setContent {
            NobbyTheme(spec) {
                primary = MaterialTheme.colorScheme.primary
                AppearanceContent(spec, onChange = { spec = it })
            }
        }
        compose.onNodeWithTag("preset_BERRY").assertIsSelected()
        compose.onNodeWithTag("preset_LILAC").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(Palette.LILAC, spec.palette)
        assertEquals(Palette.LILAC.roles(false).primary, primary)
        compose.onNodeWithTag("preset_LILAC").assertIsSelected()
        compose.onNodeWithTag("preset_BERRY").assertIsNotSelected()
    }

    @Test
    fun `mode chips switch between light, dark and auto`() {
        var spec by mutableStateOf(ThemeSpec())
        compose.setContent { NobbyTheme(spec) { AppearanceContent(spec, onChange = { spec = it }) } }
        compose.onNodeWithText("Auto").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Dark").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(ThemeMode.DARK, spec.mode)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.settings.AppearanceTest" --console=plain`
Expected: compilation FAILS (`Unresolved reference: AppearanceContent`).

- [ ] **Step 3: Create `ui/components/PresetPicker.kt`**

```kotlin
package com.nobby.ring.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.ui.theme.NobbyTheme
import com.nobby.ring.ui.theme.roles

/**
 * The five colour presets (bundle `.preset-pick`): split swatch primary / primaryContainer with an accent dot, in the
 * current mode; selection = cocoa ring + check badge. Radio semantics, test tags `preset_<NAME>`.
 */
@Composable
fun PresetPicker(selected: Palette, onSelect: (Palette) -> Unit, modifier: Modifier = Modifier) {
    val dark = NobbyTheme.extra.dark
    val c = MaterialTheme.colorScheme
    val gap = if (dark) c.surfaceContainer else c.surfaceContainerLowest
    Row(modifier.fillMaxWidth().selectableGroup()) {
        Palette.entries.forEach { p ->
            val on = p == selected
            val roles = p.roles(dark)
            Column(
                Modifier
                    .weight(1f)
                    .testTag("preset_${p.name}")
                    .selectable(selected = on, onClick = { onSelect(p) }, role = Role.RadioButton)
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(46.dp)) {
                        val r = size.minDimension / 2f
                        if (on) {
                            drawCircle(gap, r + 3.dp.toPx())
                            drawCircle(c.inverseSurface, r + 4.25.dp.toPx(), style = Stroke(2.5.dp.toPx()))
                        }
                        drawCircle(roles.primaryContainer, r)
                        drawArc(roles.primary, startAngle = 90f, sweepAngle = 180f, useCenter = true)
                        drawCircle(c.outline.copy(alpha = 0.35f), r, style = Stroke(1.dp.toPx()))
                        val dotR = 7.5.dp.toPx()
                        val dot = Offset(size.width - dotR + 1.dp.toPx(), size.height - dotR + 1.dp.toPx())
                        drawCircle(gap, dotR + 2.5.dp.toPx(), dot)
                        drawCircle(roles.accent, dotR, dot)
                    }
                    if (on) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(c.inverseSurface), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(16.dp), tint = c.inverseOnSurface)
                        }
                    }
                }
                Text(
                    p.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) c.onSurface else c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
```

- [ ] **Step 4: Replace `ui/settings/AppearanceScreen.kt`**

```kotlin
package com.nobby.ring.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.domain.model.ThemeMode
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.components.BeadRing
import com.nobby.ring.ui.components.CardTone
import com.nobby.ring.ui.components.ChipTone
import com.nobby.ring.ui.components.NobbyButton
import com.nobby.ring.ui.components.NobbyCard
import com.nobby.ring.ui.components.NobbyTopBar
import com.nobby.ring.ui.components.PhaseChip
import com.nobby.ring.ui.components.PresetPicker
import com.nobby.ring.ui.components.RingTone
import com.nobby.ring.ui.components.SegmentedChoice
import com.nobby.ring.ui.components.label
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.mascot.Nobby
import com.nobby.ring.ui.theme.NobbySpace
import com.nobby.ring.ui.theme.NobbyType

/** Bundle order for the mode chips: Hell, Dunkel, Auto. */
val MODE_ORDER: List<ThemeMode> = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)

@Composable
fun AppearanceScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        NobbyTopBar(
            title = { Text(stringResource(R.string.settings_appearance)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) } },
        )
    }) { padding ->
        AppearanceContent(theme, viewModel::setTheme, Modifier.padding(padding))
    }
}

/** Live preview (the whole app re-themes as soon as a preset is saved), five presets, Light / Dark / Auto. */
@Composable
fun AppearanceContent(theme: ThemeSpec, onChange: (ThemeSpec) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = NobbySpace.gutter, vertical = NobbySpace.m),
        verticalArrangement = Arrangement.spacedBy(NobbySpace.l),
    ) {
        MiniHomePreview()
        Text(stringResource(R.string.appearance_palette), style = MaterialTheme.typography.titleMedium)
        PresetPicker(theme.palette, { onChange(theme.copy(palette = it)) })
        Text(stringResource(R.string.appearance_mode), style = MaterialTheme.typography.titleMedium)
        SegmentedChoice(MODE_ORDER, theme.mode, { onChange(theme.copy(mode = it)) }, label = { it.label() })
    }
}

/** A tiny Home: ring with sticker, phase chips and the Done button. Decorative, hidden from accessibility services. */
@Composable
private fun MiniHomePreview() {
    val previewLabel = stringResource(R.string.appearance_preview)
    NobbyCard(Modifier.clearAndSetSemantics { }, tone = CardTone.FLAT) {
        Text(previewLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BeadRing(RingTone.WEAR, beads = 21, done = 11, modifier = Modifier.size(120.dp), sticker = { s -> Nobby(MascotMood.HAPPY, s) }) {
                Text("10", style = NobbyType.countdown.copy(fontSize = 40.sp, lineHeight = 40.sp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PhaseChip(stringResource(R.string.phase_day, stringResource(R.string.phase_wear), 12, 21), ChipTone.WEAR)
                PhaseChip(stringResource(R.string.phase_pause), ChipTone.PAUSE)
                NobbyButton(onClick = {}, icon = Icons.Rounded.Check) { Text(stringResource(R.string.action_done)) }
            }
        }
    }
}
```

- [ ] **Step 5: Replace `ui/settings/SettingsScreen.kt`**

```kotlin
package com.nobby.ring.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.BuildConfig
import com.nobby.ring.R
import com.nobby.ring.domain.model.WidgetMode
import com.nobby.ring.ui.components.ButtonTone
import com.nobby.ring.ui.components.CardTone
import com.nobby.ring.ui.components.NobbyButton
import com.nobby.ring.ui.components.NobbyCard
import com.nobby.ring.ui.components.NobbyTopBar
import com.nobby.ring.ui.components.SettingsDivider
import com.nobby.ring.ui.components.SettingsGroup
import com.nobby.ring.ui.components.SettingsRow
import com.nobby.ring.ui.components.formatTime
import com.nobby.ring.ui.components.label
import com.nobby.ring.ui.components.plural
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.mascot.MascotProp
import com.nobby.ring.ui.mascot.Nobby
import com.nobby.ring.ui.navigation.Routes
import com.nobby.ring.ui.theme.NobbySpace

@Composable
fun SettingsScreen(onNavigate: (String) -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val overdue by viewModel.overdue.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val widget by viewModel.widget.collectAsStateWithLifecycle()
    var showLicences by rememberSaveable { mutableStateOf(false) }
    if (showLicences) LicenceDialog(onDismiss = { showLicences = false })

    Scaffold(topBar = { NobbyTopBar(title = { Text(stringResource(R.string.nav_settings)) }) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = NobbySpace.gutter, vertical = NobbySpace.m),
            verticalArrangement = Arrangement.spacedBy(NobbySpace.xl),
        ) {
            SettingsGroup(stringResource(R.string.settings_group_ring)) {
                val method = schedule?.config?.method
                SettingsRow(
                    Icons.Rounded.Schedule, stringResource(R.string.settings_schedule),
                    summary = method?.let { stringResource(R.string.settings_schedule_summary, it.wearDays, it.pauseDays, formatTime(it.timeOfDay)) },
                    onClick = { onNavigate(Routes.SETTINGS_SCHEDULE) },
                )
                SettingsDivider()
                val active = rules.count { it.enabled }
                SettingsRow(
                    Icons.Rounded.Notifications, stringResource(R.string.settings_reminders),
                    summary = plural(R.plurals.settings_reminders_summary, active, active),
                    onClick = { onNavigate(Routes.SETTINGS_REMINDERS) },
                )
                SettingsDivider()
                SettingsRow(
                    Icons.Rounded.Alarm, stringResource(R.string.settings_overdue),
                    summary = stringResource(R.string.settings_overdue_summary, overdue.repeatEveryHours, overdue.snoozeHours),
                    onClick = { onNavigate(Routes.SETTINGS_REMINDERS) },
                )
            }
            SettingsGroup(stringResource(R.string.settings_group_look)) {
                SettingsRow(
                    Icons.Rounded.Palette, stringResource(R.string.settings_appearance),
                    summary = stringResource(R.string.settings_appearance_summary, theme.palette.label(), theme.mode.label()),
                    onClick = { onNavigate(Routes.SETTINGS_APPEARANCE) },
                )
            }
            SettingsGroup(stringResource(R.string.settings_group_more)) {
                SettingsRow(Icons.Rounded.CalendarMonth, stringResource(R.string.settings_calendar), onClick = { onNavigate(Routes.SETTINGS_CALENDAR) })
                SettingsDivider()
                SettingsRow(
                    Icons.Rounded.Widgets, stringResource(R.string.settings_widget),
                    summary = stringResource(if (widget.mode == WidgetMode.SINGLE) R.string.widget_mode_single else R.string.widget_mode_multi),
                    onClick = { onNavigate(Routes.SETTINGS_WIDGET) },
                )
            }
            AboutCard(onLicences = { showLicences = true })
        }
    }
}

/** Settings > Über NoBby: the only mascot on this screen (happy + wave), safety line right below. */
@Composable
private fun AboutCard(onLicences: () -> Unit) {
    NobbyCard(tone = CardTone.TONAL) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Nobby(MascotMood.HAPPY, 96.dp, prop = MascotProp.WAVE)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(stringResource(R.string.settings_about_body), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
        NobbyButton(onLicences, Modifier.padding(top = 4.dp), tone = ButtonTone.TEXT, contentColor = LocalContentColor.current) {
            Text(stringResource(R.string.settings_licenses))
        }
    }
}

/** The two OFL licence texts from res/raw, shown offline. */
@Composable
private fun LicenceDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = remember {
        listOf(R.raw.ofl_fredoka, R.raw.ofl_nunito_sans).joinToString("\n\n") { id ->
            context.resources.openRawResource(id).bufferedReader().use { it.readText() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { NobbyButton(onDismiss, tone = ButtonTone.TEXT) { Text(stringResource(R.string.action_close)) } },
        title = { Text(stringResource(R.string.settings_licenses)) },
        text = {
            Text(
                text,
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall,
            )
        },
    )
}
```

- [ ] **Step 6: Restyle the sub-screens mechanically**

The swaps are name-compatible (`NobbyTopBar` has `TopAppBar`'s `title` / `navigationIcon` / `actions`; `NobbyButton` has `Button`'s `onClick` / `modifier` / `enabled` / trailing content). `\bButton(` does not match `IconButton(`, `TextButton(`, `OutlinedButton(`, `RadioButton(`, `SegmentedButton(` or `FloatingActionButton(`.

```bash
S=app/src/main/java/com/nobby/ring/ui/settings
for f in ScheduleSettingsScreen RemindersScreen WidgetSettingsScreen CalendarSettingsScreen CalendarCleanupScreen; do
  sed -i -e '/^import androidx\.compose\.material3\.TopAppBar$/d' -e 's/\bTopAppBar(/NobbyTopBar(/g' \
         -e '/^package /a import com.nobby.ring.ui.components.NobbyTopBar' "$S/$f.kt"
done
for f in $S/ScheduleSettingsScreen.kt $S/CalendarSettingsScreen.kt $S/CalendarCleanupScreen.kt $S/ReminderEditDialog.kt \
         app/src/main/java/com/nobby/ring/ui/home/ConfirmDialog.kt; do
  sed -i -e '/^import androidx\.compose\.material3\.Button$/d' -e 's/\bButton(/NobbyButton(/g' \
         -e '/^package /a import com.nobby.ring.ui.components.NobbyButton' "$f"
done
sed -i -e 's/shape = SegmentedButtonDefaults\.itemShape(\([^)]*\))) {/shape = SegmentedButtonDefaults.itemShape(\1), colors = nobbySegmentedColors()) {/' \
       -e '/^package /a import com.nobby.ring.ui.components.nobbySegmentedColors' "$S/ReminderEditDialog.kt"
grep -nE "\bTopAppBar\(|\bButton\(" $S/*.kt app/src/main/java/com/nobby/ring/ui/home/ConfirmDialog.kt
grep -c "nobbySegmentedColors()" $S/ReminderEditDialog.kt
```

Expected: the first `grep` prints nothing; the second prints `1`.

- [ ] **Step 7: Run the tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.settings.*" --console=plain`
Expected: `BUILD SUCCESSFUL`; AppearanceTest 2, SettingsViewModelTest unchanged and green.

- [ ] **Step 8: Verify and commit**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/nobby/ring/ui/components/PresetPicker.kt app/src/main/java/com/nobby/ring/ui/settings \
  app/src/main/java/com/nobby/ring/ui/home/ConfirmDialog.kt app/src/test/java/com/nobby/ring/ui/settings/AppearanceTest.kt
git commit -m "feat(settings): grouped settings, About with licences, preset picker and restyled sub-screens"
```

---

### Task 13: Onboarding in four illustrated steps with preset and mode

**Files:**
- Modify: `app/src/main/java/com/nobby/ring/ui/onboarding/OnboardingViewModel.kt` (4 steps, mode)
- Modify (replace): `app/src/main/java/com/nobby/ring/ui/onboarding/OnboardingScreen.kt`
- Modify: strings (remove legacy onboarding keys)
- Modify: `app/src/test/java/com/nobby/ring/ui/onboarding/OnboardingViewModelTest.kt`
- Test: `app/src/test/java/com/nobby/ring/ui/onboarding/OnboardingContentTest.kt`

**Interfaces:**
- Consumes: `PresetPicker`, `MODE_ORDER` (Task 12); `NobbyCard`, `PermissionBanner` (Task 8); `NobbyButton`, `ButtonTone`, `SegmentedChoice`, `NumberStepper`, `DatePickerField`, `TimePickerField` (Task 5); `Nobby`, `MascotMood`, `MascotProp` (Task 6); `NobbyTheme(ThemeSpec)` (Task 2).
- Produces: `OnboardingState.mode: ThemeMode`, `OnboardingState.STEPS == 4`, `OnboardingViewModel.setMode(ThemeMode)`, `data class OnboardingActions(...)`, `@Composable fun OnboardingContent(state: OnboardingState, actions: OnboardingActions)`.

- [ ] **Step 1: Write the failing tests**

In `OnboardingViewModelTest.kt` add the import `com.nobby.ring.domain.model.ThemeMode`, and in the test `finish saves config, default reminders, theme and marks onboarding done` replace

```kotlin
        vm.setPalette(Palette.MATCHA)
```

with

```kotlin
        vm.setPalette(Palette.MATCHA)
        vm.setMode(ThemeMode.DARK)
```

and after `assertEquals(Palette.MATCHA, themeStore.theme.first().palette)` add

```kotlin
        assertEquals(ThemeMode.DARK, themeStore.theme.first().mode)
```

Add this test to the class:

```kotlin
    @Test
    fun `onboarding has four steps`() {
        val vm = OnboardingViewModel(configStore, reminders, themeStore, notifier, CoroutineScope(dispatcher), clock)
        repeat(6) { vm.next() }
        assertEquals(3, vm.state.value.step)
        assertEquals(4, OnboardingState.STEPS)
    }
```

Create `app/src/test/java/com/nobby/ring/ui/onboarding/OnboardingContentTest.kt`:

```kotlin
package com.nobby.ring.ui.onboarding

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.mascot.MascotMoodKey
import com.nobby.ring.ui.mascot.MascotProp
import com.nobby.ring.ui.mascot.MascotPropKey
import com.nobby.ring.ui.theme.NobbyTheme
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnboardingContentTest {
    @get:Rule val compose = createComposeRule()
    private val base = OnboardingState(anchorDate = LocalDate(2026, 11, 1))

    private fun show(state: OnboardingState, actions: OnboardingActions = OnboardingActions()) =
        compose.setContent { NobbyTheme(ThemeSpec()) { OnboardingContent(state, actions) } }

    private fun prop(p: MascotProp) = SemanticsMatcher.expectValue(MascotPropKey, p)

    @Test
    fun `step 1 waves hello`() {
        show(base)
        compose.onNode(prop(MascotProp.WAVE)).assertExists()
        compose.onNode(SemanticsMatcher.expectValue(MascotMoodKey, MascotMood.HAPPY)).assertExists()
        compose.onNodeWithText("Hi! · Step 1 of 4").assertExists()
    }

    @Test
    fun `step 2 holds the calendar`() {
        show(base.copy(step = 1))
        compose.onNode(prop(MascotProp.CALENDAR)).assertExists()
        compose.onNode(SemanticsMatcher.expectValue(MascotMoodKey, MascotMood.NEUTRAL)).assertExists()
    }

    @Test
    fun `step 4 shows the palette and picks a preset`() {
        var picked: Palette? = null
        show(base.copy(step = 3), OnboardingActions(onPalette = { picked = it }))
        compose.onNode(prop(MascotProp.PALETTE)).assertExists()
        compose.onNodeWithTag("preset_PEACH").performScrollTo().performClick()
        assertEquals(Palette.PEACH, picked)
        compose.onNodeWithText("Let's go").assertExists()
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.onboarding.*" --console=plain`
Expected: compilation FAILS (`Unresolved reference: setMode`, `OnboardingContent`, `OnboardingActions`).

- [ ] **Step 3: Update `OnboardingViewModel.kt`**

Add `import com.nobby.ring.domain.model.ThemeMode`.

Replace

```kotlin
    val palette: Palette = Palette.BERRY,
```

with

```kotlin
    val palette: Palette = Palette.BERRY,
    val mode: ThemeMode = ThemeMode.SYSTEM,
```

replace `    companion object { const val STEPS = 5 }` with `    companion object { const val STEPS = 4 }`

after `    fun setPalette(palette: Palette) = _state.update { it.copy(palette = palette) }` add

```kotlin
    fun setMode(mode: ThemeMode) = _state.update { it.copy(mode = mode) }
```

and replace `                themeStore.save(ThemeSpec(palette = s.palette))` with `                themeStore.save(ThemeSpec(palette = s.palette, mode = s.mode))`.

- [ ] **Step 4: Replace `OnboardingScreen.kt`**

```kotlin
package com.nobby.ring.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.domain.model.OverdueConfig
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.ReminderRule
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.ThemeMode
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.components.ButtonTone
import com.nobby.ring.ui.components.DatePickerField
import com.nobby.ring.ui.components.NobbyButton
import com.nobby.ring.ui.components.NobbyCard
import com.nobby.ring.ui.components.NumberStepper
import com.nobby.ring.ui.components.PermissionBanner
import com.nobby.ring.ui.components.PresetPicker
import com.nobby.ring.ui.components.SegmentedChoice
import com.nobby.ring.ui.components.TimePickerField
import com.nobby.ring.ui.components.formatTime
import com.nobby.ring.ui.components.label
import com.nobby.ring.ui.components.plural
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.mascot.MascotProp
import com.nobby.ring.ui.mascot.Nobby
import com.nobby.ring.ui.settings.MODE_ORDER
import com.nobby.ring.ui.theme.NobbyRadius
import com.nobby.ring.ui.theme.NobbySpace
import com.nobby.ring.ui.theme.NobbyTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

data class OnboardingActions(
    val onBack: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onFinish: () -> Unit = {},
    val onWear: (Int) -> Unit = {},
    val onPause: (Int) -> Unit = {},
    val onTime: (LocalTime) -> Unit = {},
    val onAnchorDate: (LocalDate) -> Unit = {},
    val onAnchorTime: (LocalTime) -> Unit = {},
    val onPalette: (Palette) -> Unit = {},
    val onMode: (ThemeMode) -> Unit = {},
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }
    // Preview the picked preset and mode live while onboarding; they are saved on finish.
    NobbyTheme(ThemeSpec(state.palette, state.mode)) {
        OnboardingContent(
            state,
            OnboardingActions(
                onBack = viewModel::back, onNext = viewModel::next, onFinish = viewModel::finish,
                onWear = viewModel::setWear, onPause = viewModel::setPause, onTime = viewModel::setTime,
                onAnchorDate = viewModel::setAnchorDate, onAnchorTime = viewModel::setAnchorTime,
                onPalette = viewModel::setPalette, onMode = viewModel::setMode,
            ),
        )
    }
}

@Composable
fun OnboardingContent(state: OnboardingState, actions: OnboardingActions) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = NobbySpace.gutter, vertical = NobbySpace.l)) {
            StepBars(state.step)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = NobbySpace.xl, bottom = NobbySpace.l),
                verticalArrangement = Arrangement.spacedBy(NobbySpace.l),
            ) {
                StepIntro(state.step)
                when (state.step) {
                    0 -> RhythmStep(state, actions)
                    1 -> WhereStep(state, actions)
                    2 -> RemindersStep(state)
                    else -> LookStep(state, actions)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (state.step > 0) NobbyButton(actions.onBack, tone = ButtonTone.TEXT) { Text(stringResource(R.string.action_back)) } else Spacer(Modifier)
                if (state.step < OnboardingState.STEPS - 1) NobbyButton(actions.onNext) { Text(stringResource(R.string.action_next)) }
                else NobbyButton(actions.onFinish, enabled = !state.saving) { Text(stringResource(R.string.action_finish)) }
            }
        }
    }
}

@Composable
private fun StepBars(step: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(OnboardingState.STEPS) { i ->
            Box(
                Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
    }
}

private data class Intro(val title: Int, val body: Int, val mood: MascotMood, val prop: MascotProp, val size: Dp)

private fun introFor(step: Int): Intro = when (step) {
    0 -> Intro(R.string.onb_rhythm_title, R.string.onb_rhythm_body, MascotMood.HAPPY, MascotProp.WAVE, 84.dp)
    1 -> Intro(R.string.onb_where_title, R.string.onb_where_body, MascotMood.NEUTRAL, MascotProp.CALENDAR, 80.dp)
    2 -> Intro(R.string.onb_reminders_title, R.string.onb_reminders_body, MascotMood.HAPPY, MascotProp.BELL, 80.dp)
    else -> Intro(R.string.onb_look_title, R.string.onb_look_body, MascotMood.HAPPY, MascotProp.PALETTE, 80.dp)
}

/** Eyebrow, title and body with this step's NoBby top-right, rotated 6° (the only mascot on the screen). */
@Composable
private fun StepIntro(step: Int) {
    val intro = introFor(step)
    val eyebrow = if (step == 0) stringResource(R.string.onb_step_first, step + 1, OnboardingState.STEPS)
    else stringResource(R.string.onb_step, step + 1, OnboardingState.STEPS)
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(end = 100.dp)) {
            Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(intro.title), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(intro.body),
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Nobby(intro.mood, intro.size, Modifier.align(Alignment.TopEnd).offset(x = 8.dp).rotate(6f), prop = intro.prop)
    }
}

@Composable
private fun RhythmStep(state: OnboardingState, actions: OnboardingActions) {
    NobbyCard {
        NumberStepper(state.wearDays, actions.onWear, ScheduleConfig.MIN_WEAR..ScheduleConfig.MAX_WEAR, stringResource(R.string.onb_wear_days), hint = stringResource(R.string.onb_wear_hint))
        if (state.wearWarning) Text(stringResource(R.string.onb_safety_wear), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        NumberStepper(state.pauseDays, actions.onPause, ScheduleConfig.MIN_PAUSE..ScheduleConfig.MAX_PAUSE, stringResource(R.string.onb_pause_days), hint = stringResource(R.string.onb_pause_hint))
        if (state.pauseWarning) Text(stringResource(R.string.onb_safety_pause), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        TimePickerField(state.timeOfDay, actions.onTime, stringResource(R.string.onb_time_of_day))
    }
    InfoNote(stringResource(R.string.onb_safety_hint))
}

@Composable
private fun WhereStep(state: OnboardingState, actions: OnboardingActions) {
    NobbyCard {
        DatePickerField(state.anchorDate, actions.onAnchorDate, stringResource(R.string.onb_anchor_date_label), Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        TimePickerField(state.anchorTime, actions.onAnchorTime, stringResource(R.string.onb_anchor_time), Modifier.fillMaxWidth())
    }
}

@Composable
private fun RemindersStep(state: OnboardingState) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    // Ask once when the step is entered (API 33+); the banner below stays for a second try.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    NobbyCard {
        ReminderRule.defaults(state.timeOfDay).forEach { rule ->
            val text = if (rule.offsetDays == 0) stringResource(R.string.reminder_same_day, formatTime(rule.timeOfDay!!))
            else plural(R.plurals.reminder_day_before, rule.offsetDays, rule.offsetDays, formatTime(rule.timeOfDay!!))
            Text(stringResource(R.string.onb_reminder_line, text, rule.importance.label()), Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.bodyLarge)
        }
        val every = OverdueConfig().repeatEveryHours
        Text(plural(R.plurals.onb_overdue_line, every, every), Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.bodyLarge)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        PermissionBanner(
            stringResource(R.string.onb_permission_body), Icons.Rounded.NotificationsActive, stringResource(R.string.action_allow),
            onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        )
    }
}

@Composable
private fun LookStep(state: OnboardingState, actions: OnboardingActions) {
    PresetPicker(state.palette, actions.onPalette)
    Text(stringResource(R.string.appearance_mode), style = MaterialTheme.typography.titleMedium)
    SegmentedChoice(MODE_ORDER, state.mode, actions.onMode, label = { it.label() })
    InfoNote(stringResource(R.string.onb_calendar_hint))
}

/** Small butter hint (tertiaryContainer; dark: surfaceContainerHigh). */
@Composable
private fun InfoNote(text: String) {
    val c = MaterialTheme.colorScheme
    val dark = NobbyTheme.extra.dark
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(NobbyRadius.md))
            .background(if (dark) c.surfaceContainerHigh else c.tertiaryContainer).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Info, contentDescription = null, Modifier.size(20.dp), tint = c.tertiary)
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (dark) c.onSurface else c.onTertiaryContainer)
    }
}
```

- [ ] **Step 5: Remove the legacy onboarding keys**

```bash
for f in app/src/main/res/values/strings.xml app/src/main/res/values-de/strings.xml; do
  sed -i -e '/legacy v1 onboarding/d' -e '/legacy v1 Onboarding/d' -e '/name="onb_welcome_title"/d' -e '/name="onb_welcome_body"/d' \
         -e '/name="onb_schedule_title"/d' -e '/name="onb_notifications_permission"/d' "$f"
done
grep -rnE "onb_welcome_title|onb_welcome_body|onb_schedule_title|onb_notifications_permission" app/src/main
```

Expected: no output.

- [ ] **Step 6: Run the tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.onboarding.*" --tests "com.nobby.ring.i18n.*" --console=plain`
Expected: `BUILD SUCCESSFUL`; OnboardingViewModelTest 3, OnboardingContentTest 3.

- [ ] **Step 7: Verify and commit**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/nobby/ring/ui/onboarding app/src/main/res/values app/src/main/res/values-de app/src/test/java/com/nobby/ring/ui/onboarding
git commit -m "feat(onboarding): four illustrated steps with live preset and mode picker"
```

---
### Task 14: Widget restyle per preset (solid day/night colours, progress bar, flat NoBby face)

**Files:**
- Create: `app/src/main/java/com/nobby/ring/widget/WidgetColors.kt`
- Modify (replace): `app/src/main/java/com/nobby/ring/widget/WidgetModel.kt`, `app/src/main/java/com/nobby/ring/widget/RingWidget.kt`
- Create: `app/src/main/res/drawable/widget_nobby_body.xml`, `widget_nobby_cheeks.xml`, `widget_nobby_face_happy.xml`, `widget_nobby_face_neutral.xml`, `widget_nobby_face_sleepy.xml`, `widget_nobby_face_worried.xml`, `widget_nobby_face_alert.xml`
- Test: `app/src/test/java/com/nobby/ring/widget/WidgetModelTest.kt` (add tests), `app/src/test/java/com/nobby/ring/widget/WidgetColorsTest.kt`

**Interfaces:**
- Consumes: `RingModel`, `RingLabel`, `RingTone` (Task 7); `MascotMood` (Task 6); `Palette.roles` (Task 2); plurals/strings from Task 3.
- Produces:
  - `data class WidgetColors(bg, fg, sub, hi, onHi, hiContainer, onHiContainer, err, onErr, errContainer, onErrContainer, track, pause, pauseContainer, mascotBody, mascotCheek, ink: Color)`, `fun Palette.widgetColors(dark: Boolean): WidgetColors` (spec §4 replaces `Palette.accent()`)
  - `class WidgetPalette(light: WidgetColors, dark: WidgetColors, mode: ThemeMode) { fun of(pick: (WidgetColors) -> Color): ColorProvider }`
  - `WidgetStrings` gains `ringLabel: (RingLabel, Int) -> String`, `today: String`, `dayOf: (Int, Int) -> String` (all with defaults, so existing tests compile)
  - `data class WidgetHeadline(val number: String, val label: String)`; `WidgetData` gains `headline: WidgetHeadline?`, `progress: Float`, `mood: MascotMood`, `tone: RingTone`, `dayLabel: String` (defaults)

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/nobby/ring/widget/WidgetColorsTest.kt` (expected hex values are the bundle's `components/widget.html` Glance table for Berry):

```kotlin
package com.nobby.ring.widget

import androidx.compose.ui.graphics.Color
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.ui.theme.roles
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetColorsTest {
    @Test
    fun `berry matches the bundle widget table`() {
        val l = Palette.BERRY.widgetColors(false)
        val d = Palette.BERRY.widgetColors(true)
        assertEquals(Color(0xFFFFFBF7), l.bg)
        assertEquals(Color(0xFFF2E6DC), l.track)
        assertEquals(Color(0xFFB03A62), l.hi)
        assertEquals(Color(0xFF292022), d.bg)
        assertEquals(Color(0xFF3E3336), d.track)
        assertEquals(Color(0xFFF5A6BE), d.hi)
        assertEquals(Color(0xFF5A1431), d.onHi)
    }

    @Test
    fun `every preset maps from its own table`() {
        for (p in Palette.entries) for (dark in listOf(false, true)) {
            val r = p.roles(dark)
            val w = p.widgetColors(dark)
            assertEquals(r.primary, w.hi)
            assertEquals(r.secondary, w.pause)
            assertEquals(r.error, w.err)
            assertEquals(r.mascot, w.mascotBody)
            assertEquals(r.ink, w.ink)
        }
    }
}
```

Append to `WidgetModelTest` (inside the class) and add the imports `com.nobby.ring.ui.components.RingTone`, `com.nobby.ring.ui.mascot.MascotMood`:

```kotlin
    private val v2 = strings.copy(ringLabel = { label, n -> "$label $n" }, today = "Today", dayOf = { d, n -> "$d/$n" })

    @Test
    fun `headline, progress and mood come from the ring model`() {
        val state = ScheduleRepository.buildState(config, emptyList(), LocalDateTime(2026, 11, 12, 12, 0))
        val data = WidgetModel.build(state, WidgetConfig(WidgetMode.SINGLE, emptyList()), v2)
        assertEquals(WidgetHeadline("10", "UNTIL_REMOVE 10"), data.headline)
        assertEquals(12f / 21f, data.progress, 0.0001f)
        assertEquals(MascotMood.HAPPY, data.mood)
        assertEquals(RingTone.WEAR, data.tone)
        assertEquals("12/21", data.dayLabel)
    }

    @Test
    fun `due today shows the word, the event and the done target`() {
        val state = ScheduleRepository.buildState(config, emptyList(), LocalDateTime(2026, 11, 22, 10, 0))
        val data = WidgetModel.build(state, WidgetConfig(WidgetMode.SINGLE, emptyList()), v2)
        assertEquals(WidgetHeadline("Today", "remove"), data.headline)
        assertEquals(RingTone.DUE, data.tone)
        assertEquals(MascotMood.NEUTRAL, data.mood)
        assertEquals("REMOVE@2026-11-22T21:00", data.dueEventId)
        assertEquals("21/21", data.dayLabel)
    }
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.widget.*" --console=plain`
Expected: compilation FAILS (`Unresolved reference: widgetColors`, `WidgetHeadline`, no parameter `ringLabel`).

- [ ] **Step 3: Create `widget/WidgetColors.kt`**

```kotlin
package com.nobby.ring.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.ThemeMode
import com.nobby.ring.ui.theme.roles
import androidx.glance.color.ColorProvider as DayNightColorProvider

/** Solid widget colours of one preset in one mode (bundle components/widget.html, generalised to every preset). */
data class WidgetColors(
    val bg: Color,
    val fg: Color,
    val sub: Color,
    val hi: Color,
    val onHi: Color,
    val hiContainer: Color,
    val onHiContainer: Color,
    val err: Color,
    val onErr: Color,
    val errContainer: Color,
    val onErrContainer: Color,
    val track: Color,
    val pause: Color,
    val pauseContainer: Color,
    val mascotBody: Color,
    val mascotCheek: Color,
    val ink: Color,
)

/** Background = surface (light) / surfaceContainer (dark); track = surfaceContainerHigh / Highest. */
fun Palette.widgetColors(dark: Boolean): WidgetColors {
    val r = roles(dark)
    return WidgetColors(
        bg = if (dark) r.surfaceContainer else r.surface,
        fg = r.onSurface,
        sub = r.onSurfaceVariant,
        hi = r.primary,
        onHi = r.onPrimary,
        hiContainer = r.primaryContainer,
        onHiContainer = r.onPrimaryContainer,
        err = r.error,
        onErr = r.onError,
        errContainer = r.errorContainer,
        onErrContainer = r.onErrorContainer,
        track = if (dark) r.surfaceContainerHighest else r.surfaceContainerHigh,
        pause = r.secondary,
        pauseContainer = r.secondaryContainer,
        mascotBody = r.mascot,
        mascotCheek = r.mascotCheek,
        ink = r.ink,
    )
}

/** Day/night pairs follow the system for ThemeMode.SYSTEM; a forced Light/Dark mode pins one colour. */
class WidgetPalette(private val light: WidgetColors, private val dark: WidgetColors, private val mode: ThemeMode) {
    fun of(pick: (WidgetColors) -> Color): ColorProvider = when (mode) {
        ThemeMode.SYSTEM -> DayNightColorProvider(day = pick(light), night = pick(dark))
        ThemeMode.LIGHT -> ColorProvider(pick(light))
        ThemeMode.DARK -> ColorProvider(pick(dark))
    }
}
```

- [ ] **Step 4: Replace `widget/WidgetModel.kt`**

```kotlin
package com.nobby.ring.widget

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.OverdueInfo
import com.nobby.ring.domain.model.ScheduleState
import com.nobby.ring.domain.model.ScheduledEvent
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.domain.model.WidgetMode
import com.nobby.ring.domain.model.WidgetRow
import com.nobby.ring.ui.components.RingTone
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.ring.RingLabel
import com.nobby.ring.ui.ring.RingModel
import kotlin.time.Duration.Companion.hours

data class WidgetStrings(
    val nextAny: String, val nextRemove: String, val nextInsert: String, val overdue: String, val cycleDay: String, val none: String,
    val inDays: (Int) -> String, val overdueDays: (Int) -> String, val overdueHours: (Int) -> String,
    val cycleDayFmt: (Int, Int) -> String, val kindLabel: (EventKind) -> String,
    val ringLabel: (RingLabel, Int) -> String = { _, _ -> "" },
    val today: String = "",
    val dayOf: (Int, Int) -> String = { _, _ -> "" },
)

data class WidgetRowData(val row: WidgetRow, val label: String, val value: String, val urgent: Boolean)
data class WidgetHeadline(val number: String, val label: String)
data class WidgetData(
    val rows: List<WidgetRowData>,
    val dueEventId: String?,
    val headline: WidgetHeadline? = null,
    val progress: Float = 0f,
    val mood: MascotMood = MascotMood.HAPPY,
    val tone: RingTone = RingTone.WEAR,
    val dayLabel: String = "",
)

object WidgetModel {
    fun build(state: ScheduleState?, config: WidgetConfig, s: WidgetStrings): WidgetData {
        if (state == null) return WidgetData(listOf(WidgetRowData(WidgetRow.NEXT_ANY, s.nextAny, s.none, false)), null)
        val due = state.nextEvent?.takeIf { state.daysUntilNext != null && state.daysUntilNext!! <= 0 }?.id
        val rows = when (config.mode) {
            WidgetMode.SINGLE -> listOf(WidgetRow.NEXT_ANY)
            WidgetMode.MULTI -> config.rows.take(WidgetConfig.MAX_ROWS).ifEmpty { listOf(WidgetRow.NEXT_ANY) }
        }
        val ring = RingModel.from(state)
        val p = state.position
        val length = p.phaseLengthDays.coerceAtLeast(1)
        return WidgetData(
            rows = rows.map { row(it, state, s) },
            dueEventId = due,
            headline = headline(ring, s),
            progress = (p.dayInPhase.toFloat() / length).coerceIn(0f, 1f),
            mood = ring.mood,
            tone = ring.tone,
            dayLabel = s.dayOf(p.dayInPhase.coerceAtMost(length), p.phaseLengthDays),
        )
    }

    private fun headline(ring: RingModel, s: WidgetStrings): WidgetHeadline = when (ring.label) {
        RingLabel.TODAY -> WidgetHeadline(s.today, ring.event?.let { s.kindLabel(it.kind) } ?: s.none)
        RingLabel.NONE -> WidgetHeadline("–", s.none)
        else -> WidgetHeadline("${ring.number ?: 0}", s.ringLabel(ring.label, ring.number ?: 0))
    }

    private fun row(row: WidgetRow, state: ScheduleState, s: WidgetStrings): WidgetRowData = when (row) {
        WidgetRow.NEXT_ANY -> countdown(row, state.nextEvent, state, s, labelOverride = null)
        WidgetRow.NEXT_REMOVE -> countdown(row, state.pendingEvents.firstOrNull { it.kind != EventKind.INSERT }, state, s, s.nextRemove)
        WidgetRow.NEXT_INSERT -> countdown(row, state.pendingEvents.firstOrNull { it.kind != EventKind.REMOVE }, state, s, s.nextInsert)
        WidgetRow.OVERDUE -> {
            val o = state.overdue
            if (o == null) WidgetRowData(row, s.overdue, "–", false)
            else WidgetRowData(row, s.overdue, overdueText(o, state, s), true)
        }
        WidgetRow.CYCLE_DAY -> WidgetRowData(row, s.cycleDay, s.cycleDayFmt(state.position.dayInCycle, state.position.cycleLengthDays), false)
    }

    private fun countdown(row: WidgetRow, event: ScheduledEvent?, state: ScheduleState, s: WidgetStrings, labelOverride: String?): WidgetRowData {
        if (event == null) return WidgetRowData(row, labelOverride ?: s.nextAny, s.none, false)
        val days = daysUntil(event, state)
        val label = labelOverride ?: s.kindLabel(event.kind)
        val overdue = state.overdue?.takeIf { it.event.id == event.id }
        if (overdue != null) return WidgetRowData(row, label, overdueText(overdue, state, s), true)
        return if (days < 0) WidgetRowData(row, label, s.overdueDays(-days), true) else WidgetRowData(row, label, s.inDays(days), false)
    }

    /** Under one day the overdue time is shown in hours (at least 1), otherwise in calendar days. */
    private fun overdueText(o: OverdueInfo, state: ScheduleState, s: WidgetStrings): String =
        if (o.delay < 24.hours) s.overdueHours(o.delay.inWholeHours.toInt().coerceAtLeast(1))
        else s.overdueDays(-daysUntil(o.event, state))

    private fun daysUntil(event: ScheduledEvent, state: ScheduleState): Int =
        event.plannedAt.date.toEpochDays() - state.now.date.toEpochDays()
}
```

- [ ] **Step 5: Create the widget face masks** (white shapes, tinted per preset at runtime; geometry = `nobby.js` `notifIcon()`, viewBox 6 12 108 108)

`app/src/main/res/drawable/widget_nobby_body.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="40dp" android:height="40dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:translateX="-6" android:translateY="-12">
        <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd"
            android:pathData="M60 20C89 20 110 39 110 65C110 91 89 108 60 108C31 108 10 91 10 65C10 39 31 20 60 20ZM60 38C67.7 38 74 42.3 74 47.5C74 52.7 67.7 57 60 57C52.3 57 46 52.7 46 47.5C46 42.3 52.3 38 60 38Z" />
    </group>
</vector>
```

`app/src/main/res/drawable/widget_nobby_cheeks.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="40dp" android:height="40dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:translateX="-6" android:translateY="-12">
        <path android:fillColor="#FFFFFFFF"
            android:pathData="M26.5 85a7 4.2 0 1 0 14 0a7 4.2 0 1 0 -14 0ZM79.5 85a7 4.2 0 1 0 14 0a7 4.2 0 1 0 -14 0Z" />
    </group>
</vector>
```

`app/src/main/res/drawable/widget_nobby_face_happy.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="40dp" android:height="40dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:translateX="-6" android:translateY="-12">
        <path android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="3.4" android:strokeLineJoin="round"
            android:pathData="M60 20C89 20 110 39 110 65C110 91 89 108 60 108C31 108 10 91 10 65C10 39 31 20 60 20ZM60 38C67.7 38 74 42.3 74 47.5C74 52.7 67.7 57 60 57C52.3 57 46 52.7 46 47.5C46 42.3 52.3 38 60 38Z" />
        <path android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="3.4"
            android:strokeLineCap="round" android:strokeLineJoin="round"
            android:pathData="M39.5 78Q45 70.5 50.5 78M69.5 78Q75 70.5 80.5 78M54.5 84Q60 89.5 65.5 84" />
    </group>
</vector>
```

`app/src/main/res/drawable/widget_nobby_face_neutral.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="40dp" android:height="40dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:translateX="-6" android:translateY="-12">
        <path android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="3.4" android:strokeLineJoin="round"
            android:pathData="M60 20C89 20 110 39 110 65C110 91 89 108 60 108C31 108 10 91 10 65C10 39 31 20 60 20ZM60 38C67.7 38 74 42.3 74 47.5C74 52.7 67.7 57 60 57C52.3 57 46 52.7 46 47.5C46 42.3 52.3 38 60 38Z" />
        <path android:fillColor="#FFFFFFFF"
            android:pathData="M40.7 76a4.3 4.3 0 1 0 8.6 0a4.3 4.3 0 1 0 -8.6 0ZM70.7 76a4.3 4.3 0 1 0 8.6 0a4.3 4.3 0 1 0 -8.6 0Z" />
        <path android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="3.4"
            android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M55.5 85Q60 87.2 64.5 85" />
    </group>
</vector>
```

`app/src/main/res/drawable/widget_nobby_face_sleepy.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="40dp" android:height="40dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:translateX="-6" android:translateY="-12">
        <path android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="3.4" android:strokeLineJoin="round"
            android:pathData="M60 20C89 20 110 39 110 65C110 91 89 108 60 108C31 108 10 91 10 65C10 39 31 20 60 20ZM60 38C67.7 38 74 42.3 74 47.5C74 52.7 67.7 57 60 57C52.3 57 46 52.7 46 47.5C46 42.3 52.3 38 60 38Z" />
        <path android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="3.4"
            android:strokeLineCap="round" android:strokeLineJoin="round"
            android:pathData="M39.5 75Q45 80.5 50.5 75M69.5 75Q75 80.5 80.5 75" />
        <path android:fillColor="#FFFFFFFF" android:pathData="M57.3 87a2.7 2.3 0 1 0 5.4 0a2.7 2.3 0 1 0 -5.4 0Z" />
    </group>
</vector>
```

`app/src/main/res/drawable/widget_nobby_face_worried.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="40dp" android:height="40dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:translateX="-6" android:translateY="-12">
        <path android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="3.4" android:strokeLineJoin="round"
            android:pathData="M60 20C89 20 110 39 110 65C110 91 89 108 60 108C31 108 10 91 10 65C10 39 31 20 60 20ZM60 38C67.7 38 74 42.3 74 47.5C74 52.7 67.7 57 60 57C52.3 57 46 52.7 46 47.5C46 42.3 52.3 38 60 38Z" />
        <path android:fillColor="#FFFFFFFF"
            android:pathData="M40.7 76a4.3 4.3 0 1 0 8.6 0a4.3 4.3 0 1 0 -8.6 0ZM70.7 76a4.3 4.3 0 1 0 8.6 0a4.3 4.3 0 1 0 -8.6 0Z" />
        <path android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="3.4"
            android:strokeLineCap="round" android:strokeLineJoin="round"
            android:pathData="M38 67.5L49 64M71 64L82 67.5M53 87.5Q56.5 84.5 60 87.5Q63.5 90.5 67 87.5" />
    </group>
</vector>
```

`app/src/main/res/drawable/widget_nobby_face_alert.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="40dp" android:height="40dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:translateX="-6" android:translateY="-12">
        <path android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="3.4" android:strokeLineJoin="round"
            android:pathData="M60 20C89 20 110 39 110 65C110 91 89 108 60 108C31 108 10 91 10 65C10 39 31 20 60 20ZM60 38C67.7 38 74 42.3 74 47.5C74 52.7 67.7 57 60 57C52.3 57 46 52.7 46 47.5C46 42.3 52.3 38 60 38Z" />
        <path android:fillColor="#FFFFFFFF"
            android:pathData="M40 76a5 5 0 1 0 10 0a5 5 0 1 0 -10 0ZM70 76a5 5 0 1 0 10 0a5 5 0 1 0 -10 0ZM56.4 87.5a3.6 4.4 0 1 0 7.2 0a3.6 4.4 0 1 0 -7.2 0Z" />
        <path android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="3.4"
            android:strokeLineCap="round" android:strokeLineJoin="round"
            android:pathData="M38.5 65Q44.5 61 50.5 63.5M69.5 63.5Q75.5 61 81.5 65" />
    </group>
</vector>
```

- [ ] **Step 6: Replace `widget/RingWidget.kt`**

```kotlin
package com.nobby.ring.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.nobby.ring.MainActivity
import com.nobby.ring.R
import com.nobby.ring.data.prefs.ThemeStore
import com.nobby.ring.data.prefs.WidgetConfigStore
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.engine.ConfirmAction
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.domain.model.WidgetMode
import com.nobby.ring.notifications.ScheduleChangeNotifier
import com.nobby.ring.ui.components.RingTone
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.ring.RingLabel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun schedule(): ScheduleRepository
    fun widgetConfig(): WidgetConfigStore
    fun theme(): ThemeStore
    fun clock(): NobbyClock
    fun changeNotifier(): ScheduleChangeNotifier
}

private data class WidgetLabels(val done: String, val appName: String)

class RingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val initialState = ep.schedule().currentState()
        val initialConfig = ep.widgetConfig().config.first()
        val initialTheme = ep.theme().theme.first()
        val strings = widgetStrings(context)
        val labels = WidgetLabels(done = context.getString(R.string.home_mark_done), appName = context.getString(R.string.app_name))
        // Collect the stores inside the composition: an existing Glance session does not re-run
        // provideGlance on updateAll(), so values captured above would go stale (config/theme changes lagged by one update).
        provideContent {
            val state by ep.schedule().state.collectAsState(initialState)
            val config by ep.widgetConfig().config.collectAsState(initialConfig)
            val theme by ep.theme().theme.collectAsState(initialTheme)
            val data = WidgetModel.build(state, config, strings)
            val palette = WidgetPalette(theme.palette.widgetColors(false), theme.palette.widgetColors(true), theme.mode)
            WidgetContent(data, config.mode, palette, labels)
        }
    }

    @Composable
    private fun WidgetContent(data: WidgetData, mode: WidgetMode, p: WidgetPalette, labels: WidgetLabels) {
        val size = LocalSize.current
        val small = size.height < MEDIUM.height
        Box(
            GlanceModifier.fillMaxSize().background(p.of { it.bg }).cornerRadius(24.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            when {
                mode == WidgetMode.MULTI -> MultiLayout(data, p, labels, small)
                small -> SmallLayout(data, p)
                size.width < WIDE.width -> SquareLayout(data, p, labels)
                else -> WideLayout(data, p, labels)
            }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 50.dp)
        val MEDIUM = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
    }
}

private fun text(color: ColorProvider, size: Int, weight: FontWeight = FontWeight.Medium) =
    TextStyle(color = color, fontSize = size.sp, fontWeight = weight, fontFamily = FontFamily.SansSerif)

private fun WidgetData.urgent(): Boolean = tone == RingTone.OVERDUE || tone == RingTone.SAFETY
private fun WidgetData.head(): WidgetHeadline = headline ?: WidgetHeadline("–", "")
private fun WidgetData.numberSize(): Int = if (head().number.length > 3) 30 else 44
private fun WidgetData.barPick(): (WidgetColors) -> Color = when {
    urgent() -> { c -> c.err }
    tone == RingTone.PAUSE -> { c -> c.pause }
    else -> { c -> c.hi }
}

/** 2x1: number + label side by side. */
@Composable
private fun SmallLayout(d: WidgetData, p: WidgetPalette) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Text(d.head().number, style = text(p.of { if (d.urgent()) it.err else it.fg }, if (d.numberSize() == 44) 36 else 26, FontWeight.Bold), maxLines = 1)
        Spacer(GlanceModifier.width(10.dp))
        Text(d.head().label, style = text(p.of { it.sub }, 13), maxLines = 2)
    }
}

/** 2x2: day label + face, big number, label + phase bar, or the Done pill when due/overdue. */
@Composable
private fun SquareLayout(d: WidgetData, p: WidgetPalette, labels: WidgetLabels) {
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(d.dayLabel, GlanceModifier.defaultWeight(), style = text(p.of { it.sub }, 13), maxLines = 1)
            WidgetFace(d.mood, p, 40.dp)
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(d.head().number, style = text(p.of { if (d.urgent()) it.err else it.fg }, d.numberSize(), FontWeight.Bold), maxLines = 1)
        if (d.dueEventId != null) {
            DonePill(labels.done, d.urgent(), p)
        } else {
            Text(d.head().label, style = text(p.of { it.sub }, 13), maxLines = 1)
            Spacer(GlanceModifier.height(6.dp))
            PhaseBar(d, p)
        }
    }
}

/** 4x2: number block on the left, face + bar (or Done) on the right. */
@Composable
private fun WideLayout(d: WidgetData, p: WidgetPalette, labels: WidgetLabels) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            Text(d.dayLabel, style = text(p.of { it.sub }, 13), maxLines = 1)
            Text(d.head().number, style = text(p.of { if (d.urgent()) it.err else it.fg }, d.numberSize(), FontWeight.Bold), maxLines = 1)
            Text(d.head().label, style = text(p.of { it.sub }, 13), maxLines = 2)
        }
        Spacer(GlanceModifier.width(16.dp))
        Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
            Row(GlanceModifier.fillMaxWidth()) {
                Spacer(GlanceModifier.defaultWeight())
                WidgetFace(d.mood, p, 40.dp)
            }
            Spacer(GlanceModifier.defaultWeight())
            if (d.dueEventId != null) DonePill(labels.done, d.urgent(), p) else PhaseBar(d, p)
        }
    }
}

/** Multi: up to three rows with a colour dot; header with the face from 2x2 up; Done when due/overdue. */
@Composable
private fun MultiLayout(d: WidgetData, p: WidgetPalette, labels: WidgetLabels, small: Boolean) {
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (!small) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(labels.appName, GlanceModifier.defaultWeight(), style = text(p.of { it.fg }, 14, FontWeight.Bold), maxLines = 1)
                WidgetFace(d.mood, p, 28.dp)
            }
        }
        d.rows.take(if (small) 2 else WidgetConfig.MAX_ROWS).forEach { row ->
            Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.size(10.dp).cornerRadius(5.dp).background(p.of { if (row.urgent) it.err else it.hi })) {}
                Spacer(GlanceModifier.width(8.dp))
                Text(row.label, GlanceModifier.defaultWeight(), style = text(p.of { it.fg }, 14), maxLines = 1)
                Text(row.value, style = text(p.of { if (row.urgent) it.err else it.fg }, 15, FontWeight.Bold), maxLines = 1)
            }
        }
        if (!small && d.dueEventId != null) {
            Spacer(GlanceModifier.height(6.dp))
            DonePill(labels.done, d.urgent(), p)
        }
    }
}

@Composable
private fun PhaseBar(d: WidgetData, p: WidgetPalette) {
    LinearProgressIndicator(
        progress = d.progress,
        modifier = GlanceModifier.fillMaxWidth().height(8.dp),
        color = p.of(d.barPick()),
        backgroundColor = p.of { it.track },
    )
}

@Composable
private fun DonePill(label: String, urgent: Boolean, p: WidgetPalette) {
    Box(
        GlanceModifier.fillMaxWidth().height(36.dp).cornerRadius(18.dp)
            .background(p.of { if (urgent) it.err else it.hi })
            .clickable(actionRunCallback<DoneAction>()),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = text(p.of { if (urgent) it.onErr else it.onHi }, 14, FontWeight.Bold), maxLines = 1)
    }
}

/** Flat NoBby face: white VectorDrawable masks tinted with the preset's mascot, cheek and ink colours. */
@Composable
private fun WidgetFace(mood: MascotMood, p: WidgetPalette, size: Dp) {
    Box(GlanceModifier.size(size)) {
        Image(ImageProvider(R.drawable.widget_nobby_body), null, GlanceModifier.fillMaxSize(), colorFilter = ColorFilter.tint(p.of { it.mascotBody }))
        if (mood != MascotMood.WORRIED && mood != MascotMood.ALERT) {
            Image(ImageProvider(R.drawable.widget_nobby_cheeks), null, GlanceModifier.fillMaxSize(), colorFilter = ColorFilter.tint(p.of { it.mascotCheek }))
        }
        Image(ImageProvider(faceRes(mood)), null, GlanceModifier.fillMaxSize(), colorFilter = ColorFilter.tint(p.of { it.ink }))
    }
}

private fun faceRes(mood: MascotMood): Int = when (mood) {
    MascotMood.HAPPY, MascotMood.YAY -> R.drawable.widget_nobby_face_happy
    MascotMood.NEUTRAL -> R.drawable.widget_nobby_face_neutral
    MascotMood.SLEEPY -> R.drawable.widget_nobby_face_sleepy
    MascotMood.WORRIED -> R.drawable.widget_nobby_face_worried
    MascotMood.ALERT -> R.drawable.widget_nobby_face_alert
}

private fun widgetStrings(context: Context): WidgetStrings {
    val res = context.resources
    fun q(id: Int, n: Int): String = res.getQuantityString(id, n)
    fun qn(id: Int, n: Int): String = res.getQuantityString(id, n, n)
    return WidgetStrings(
        nextAny = context.getString(R.string.widget_row_next_any),
        nextRemove = context.getString(R.string.widget_row_next_remove),
        nextInsert = context.getString(R.string.widget_row_next_insert),
        overdue = context.getString(R.string.widget_row_overdue),
        cycleDay = context.getString(R.string.widget_row_cycle_day),
        none = context.getString(R.string.widget_none),
        inDays = { qn(R.plurals.widget_in_days, it) },
        overdueDays = { qn(R.plurals.widget_overdue_days, it) },
        overdueHours = { qn(R.plurals.widget_overdue_hours, it) },
        cycleDayFmt = { d, n -> context.getString(R.string.cycle_day, d, n) },
        kindLabel = { k ->
            context.getString(
                when (k) {
                    EventKind.REMOVE -> R.string.event_remove
                    EventKind.INSERT -> R.string.event_insert
                    EventKind.SWAP -> R.string.event_swap
                },
            )
        },
        ringLabel = { label, n ->
            when (label) {
                RingLabel.UNTIL_REMOVE -> q(R.plurals.ring_until_remove, n)
                RingLabel.UNTIL_INSERT -> q(R.plurals.ring_until_insert, n)
                RingLabel.UNTIL_SWAP -> q(R.plurals.ring_until_swap, n)
                RingLabel.OVERDUE_DAYS -> q(R.plurals.ring_overdue_days, n)
                RingLabel.OVERDUE_HOURS -> q(R.plurals.ring_overdue_hours, n)
                RingLabel.TODAY, RingLabel.NONE -> ""
            }
        },
        today = context.getString(R.string.ring_today),
        dayOf = { d, n -> context.getString(R.string.widget_day_of, d, n) },
    )
}

class DoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        runCatching {
            val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            val state = ep.schedule().currentState() ?: return
            // The widget may be stale: re-check that the event is still due today (or overdue) before confirming.
            val days = state.daysUntilNext ?: return
            if (days > 0) return
            val next = state.nextEvent ?: return
            val now = ep.clock().now()
            // A late removal keeps the usual full break; a late insertion keeps the planned removal.
            ep.schedule().confirm(next.id, now, ConfirmAction.quickConfirmPolicy(next, now))
            ep.changeNotifier().notifyChanged()
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Log.e("NoBby", "widget done action failed", e)
        }
    }
}
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.widget.*" --console=plain`
Expected: `BUILD SUCCESSFUL`; WidgetModelTest 7 (5 old + 2 new), WidgetColorsTest 2.

- [ ] **Step 8: Verify and commit**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/nobby/ring/widget app/src/main/res/drawable/widget_nobby_*.xml app/src/test/java/com/nobby/ring/widget
git commit -m "feat(widget): preset colours with day/night pairs, phase bar and flat NoBby face"
```

---

### Task 15: Notification icons per preset and the new launcher icon

**Files:**
- Create: `app/src/main/res/drawable/ic_stat_nobby.xml`, `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Modify (replace): `app/src/main/res/drawable/ic_launcher_foreground.xml`, `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `app/src/main/res/values/ic_launcher_background.xml`
- Create: `app/src/main/java/com/nobby/ring/notifications/NotificationIcons.kt`
- Modify (replace): `app/src/main/java/com/nobby/ring/notifications/Notifier.kt`
- Modify: `app/src/test/java/com/nobby/ring/notifications/ReminderSchedulerTest.kt`, `app/src/test/java/com/nobby/ring/ui/home/HomeViewModelTest.kt` (Notifier constructor)
- Test: `app/src/test/java/com/nobby/ring/notifications/NotifierTest.kt`

**Interfaces:**
- Consumes: `MascotBitmaps.face`, `Palette.mascotColors`, `MascotMood` (Task 6); `Palette.roles` (Task 2); `ThemeStore` (unchanged); `notif_overdue_body_with_safety` (Task 3).
- Produces: `Notifier(context: Context, themeStore: ThemeStore)` with `suspend fun showReminder(...)` and `suspend fun showOverdue(...)` (same parameters as before; all callers already run in coroutines), `object NotificationIcons { fun large(context: Context, palette: Palette, mood: MascotMood): Bitmap }`, drawable `ic_stat_nobby`.

Decision: the spec's "96×96 PNGs, one per preset × mood (10 bitmaps)" are rendered at runtime from the same `notifIcon()` geometry and cached (exactly 10 combinations), at the platform's large-icon size (`android.R.dimen.notification_large_icon_width`, 64 dp, i.e. 192 px on xxhdpi) instead of 96 px files, so they stay sharp and can never drift from the presets.

- [ ] **Step 1: Write the failing test** `app/src/test/java/com/nobby/ring/notifications/NotifierTest.kt`:

```kotlin
package com.nobby.ring.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.R
import com.nobby.ring.data.prefs.ThemeStore
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Importance
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.mascot.MascotMood
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

@RunWith(RobolectricTestRunner::class)
class NotifierTest {
    private lateinit var context: Context
    private lateinit var themeStore: ThemeStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Channels.create(context)
        themeStore = ThemeStore(PreferenceDataStoreFactory.create { File(context.cacheDir, "notif-${System.nanoTime()}.preferences_pb") })
    }

    @Test
    fun `large icons exist for every preset and mood and are cached`() {
        val px = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
        for (p in Palette.entries) for (m in listOf(MascotMood.HAPPY, MascotMood.WORRIED)) {
            val bmp = NotificationIcons.large(context, p, m)
            assertEquals(px, bmp.width)
            assertSame(bmp, NotificationIcons.large(context, p, m))
        }
    }

    @Test
    fun `reminder uses the ring glyph and a preset face`() = runTest {
        themeStore.save(ThemeSpec(Palette.MATCHA))
        Notifier(context, themeStore).showReminder(EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), Importance.NORMAL, "REMOVE@2026-11-22T21:00")
        val n = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals(R.drawable.ic_stat_nobby, n.smallIcon.resId)
        assertNotNull(n.getLargeIcon())
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.notifications.NotifierTest" --console=plain`
Expected: compilation FAILS (`Unresolved reference: NotificationIcons`, `ic_stat_nobby`; too many arguments for `Notifier`).

- [ ] **Step 3: Create the icons**

`app/src/main/res/drawable/ic_stat_nobby.xml` (monochrome ring glyph for the status bar; the body ring of the mascot):

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <group android:scaleX="0.2" android:scaleY="0.2" android:translateY="-0.8">
        <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd"
            android:pathData="M60 20C89 20 110 39 110 65C110 91 89 108 60 108C31 108 10 91 10 65C10 39 31 20 60 20ZM60 38C67.7 38 74 42.3 74 47.5C74 52.7 67.7 57 60 57C52.3 57 46 52.7 46 47.5C46 42.3 52.3 38 60 38Z" />
    </group>
</vector>
```

`app/src/main/res/drawable/ic_launcher_foreground.xml` (flat NoBby face, Berry light colours from tokens.json, inside the 66 dp safe circle):

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:scaleX="0.6" android:scaleY="0.6" android:translateX="18" android:translateY="15.6">
        <path android:fillColor="#F6B4C7" android:fillType="evenOdd"
            android:pathData="M60 20C89 20 110 39 110 65C110 91 89 108 60 108C31 108 10 91 10 65C10 39 31 20 60 20ZM60 38C67.7 38 74 42.3 74 47.5C74 52.7 67.7 57 60 57C52.3 57 46 52.7 46 47.5C46 42.3 52.3 38 60 38Z" />
        <group>
            <clip-path android:pathData="M60 38C67.7 38 74 42.3 74 47.5C74 52.7 67.7 57 60 57C52.3 57 46 52.7 46 47.5C46 42.3 52.3 38 60 38Z" />
            <path android:fillColor="#EC8AA6" android:fillAlpha="0.55" android:pathData="M43 41a17 8 0 1 0 34 0a17 8 0 1 0 -34 0Z" />
        </group>
        <path android:fillColor="#00000000" android:strokeColor="#3A2B2B" android:strokeWidth="3.4" android:strokeLineJoin="round"
            android:pathData="M60 20C89 20 110 39 110 65C110 91 89 108 60 108C31 108 10 91 10 65C10 39 31 20 60 20ZM60 38C67.7 38 74 42.3 74 47.5C74 52.7 67.7 57 60 57C52.3 57 46 52.7 46 47.5C46 42.3 52.3 38 60 38Z" />
        <path android:fillColor="#00000000" android:strokeColor="#FFFFFF" android:strokeAlpha="0.8" android:strokeWidth="3.6"
            android:strokeLineCap="round" android:pathData="M26 52Q29 39 41 33" />
        <path android:fillColor="#EC8AA6"
            android:pathData="M26.5 85a7 4.2 0 1 0 14 0a7 4.2 0 1 0 -14 0ZM79.5 85a7 4.2 0 1 0 14 0a7 4.2 0 1 0 -14 0Z" />
        <path android:fillColor="#00000000" android:strokeColor="#3A2B2B" android:strokeWidth="3.4"
            android:strokeLineCap="round" android:strokeLineJoin="round"
            android:pathData="M39.5 78Q45 70.5 50.5 78M69.5 78Q75 70.5 80.5 78M54.5 84Q60 89.5 65.5 84" />
    </group>
</vector>
```

`app/src/main/res/drawable/ic_launcher_monochrome.xml` (themed icons, API 33+; only alpha is used):

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:scaleX="0.6" android:scaleY="0.6" android:translateX="18" android:translateY="15.6">
        <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd"
            android:pathData="M60 20C89 20 110 39 110 65C110 91 89 108 60 108C31 108 10 91 10 65C10 39 31 20 60 20ZM60 38C67.7 38 74 42.3 74 47.5C74 52.7 67.7 57 60 57C52.3 57 46 52.7 46 47.5C46 42.3 52.3 38 60 38Z" />
    </group>
</vector>
```

`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
```

`app/src/main/res/values/ic_launcher_background.xml` (fixed Berry, spec §13):

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- tokens.json presets.berry.light.primary; the launcher icon stays Berry for every preset. -->
    <color name="ic_launcher_background">#B03A62</color>
</resources>
```

- [ ] **Step 4: Create `notifications/NotificationIcons.kt`**

```kotlin
package com.nobby.ring.notifications

import android.content.Context
import android.graphics.Bitmap
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.ui.mascot.MascotBitmaps
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.mascot.mascotColors
import java.util.concurrent.ConcurrentHashMap

/** Flat NoBby faces for the notification large icon: per preset (light colours) x mood, rendered once and cached. */
object NotificationIcons {
    private val cache = ConcurrentHashMap<String, Bitmap>()

    fun large(context: Context, palette: Palette, mood: MascotMood): Bitmap {
        val px = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
        return cache.getOrPut("${palette.name}-${mood.name}-$px") { MascotBitmaps.face(mood, palette.mascotColors(dark = false), px) }
    }
}
```

- [ ] **Step 5: Replace `notifications/Notifier.kt`**

```kotlin
package com.nobby.ring.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nobby.ring.MainActivity
import com.nobby.ring.R
import com.nobby.ring.data.prefs.ThemeStore
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Importance
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.ui.components.formatDateTime
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.theme.roles
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDateTime
import javax.inject.Inject

class Notifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val themeStore: ThemeStore,
) {

    suspend fun showReminder(kind: EventKind, plannedAt: LocalDateTime, importance: Importance, eventId: String) {
        val palette = palette()
        val n = NotificationCompat.Builder(context, Channels.idFor(importance))
            .setSmallIcon(R.drawable.ic_stat_nobby)
            .setLargeIcon(NotificationIcons.large(context, palette, MascotMood.HAPPY))
            .setColor(palette.roles(dark = false).primary.toArgb())
            .setContentTitle(context.getString(R.string.notif_reminder_title, kindLabel(kind)))
            .setContentText(context.getString(R.string.notif_reminder_body, formatDateTime(plannedAt)))
            .setPriority(priorityFor(importance))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        notify(eventId.hashCode(), n)
    }

    suspend fun showOverdue(kind: EventKind, plannedAt: LocalDateTime, eventId: String, safetyBreached: Boolean) {
        val palette = palette()
        // REMOVE/SWAP overdue means the ring stays in too long; INSERT overdue means the break runs too long.
        val safety = if (kind == EventKind.INSERT) R.string.notif_overdue_safety_pause else R.string.notif_overdue_safety_wear
        val body = context.getString(R.string.notif_overdue_body, formatDateTime(plannedAt))
        val text = if (safetyBreached) context.getString(R.string.notif_overdue_body_with_safety, body, context.getString(safety)) else body
        val builder = NotificationCompat.Builder(context, Channels.URGENT)
            .setSmallIcon(R.drawable.ic_stat_nobby)
            .setLargeIcon(NotificationIcons.large(context, palette, MascotMood.WORRIED))
            .setColor(palette.roles(dark = false).primary.toArgb())
            .setContentTitle(context.getString(R.string.notif_overdue_title, kindLabel(kind)))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(openApp())
            .addAction(0, context.getString(R.string.action_done), action(ActionReceiver.ACTION_DONE, eventId))
            .addAction(0, context.getString(R.string.home_snooze), action(ActionReceiver.ACTION_SNOOZE, eventId))
        // subText stays visible while the notification is collapsed, unlike the second line of the big text.
        if (safetyBreached) builder.setSubText(context.getString(R.string.notif_overdue_safety_short))
        notify(OVERDUE_ID, builder.build())
    }

    fun cancelOverdue() = NotificationManagerCompat.from(context).cancel(OVERDUE_ID)

    /** The active preset; a broken preference file must never stop an alarm, so reading falls back to Berry. */
    private suspend fun palette(): Palette = try {
        themeStore.theme.first().palette
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("NoBby", "reading the theme for a notification failed", e)
        Palette.BERRY
    }

    private fun notify(id: Int, n: Notification) {
        // notify() throws SecurityException when POST_NOTIFICATIONS is denied; that must not crash a receiver.
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(action: String, eventId: String): PendingIntent = PendingIntent.getBroadcast(
        context, action.hashCode(),
        Intent(context, ActionReceiver::class.java).setAction(action).putExtra(ActionReceiver.EXTRA_EVENT_ID, eventId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun kindLabel(kind: EventKind): String = context.getString(
        when (kind) {
            EventKind.REMOVE -> R.string.event_remove
            EventKind.INSERT -> R.string.event_insert
            EventKind.SWAP -> R.string.event_swap
        },
    )

    private fun priorityFor(i: Importance) = when (i) {
        Importance.LOW -> NotificationCompat.PRIORITY_LOW
        Importance.NORMAL -> NotificationCompat.PRIORITY_DEFAULT
        Importance.HIGH -> NotificationCompat.PRIORITY_HIGH
        Importance.URGENT -> NotificationCompat.PRIORITY_MAX
    }

    companion object { const val OVERDUE_ID = 1 }
}
```

`AlarmReceiver` calls `showReminder` / `showOverdue` inside `CoroutineScope(Dispatchers.IO).launch { … }` and needs no change; `ReminderScheduler` and `ActionReceiver` only call `cancelOverdue()`.

- [ ] **Step 6: Update the tests that build a `Notifier`**

```bash
for f in app/src/test/java/com/nobby/ring/notifications/ReminderSchedulerTest.kt app/src/test/java/com/nobby/ring/ui/home/HomeViewModelTest.kt; do
  sed -i 's/Notifier(context)/Notifier(context, ThemeStore(ds))/g' "$f"
  grep -q "^import com.nobby.ring.data.prefs.ThemeStore$" "$f" || sed -i '/^package /a import com.nobby.ring.data.prefs.ThemeStore' "$f"
done
grep -rn "Notifier(context)" app/src/test
```

Expected: the final `grep` prints nothing.

- [ ] **Step 7: Run the tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.notifications.*" --tests "com.nobby.ring.ui.home.*" --console=plain`
Expected: `BUILD SUCCESSFUL`; NotifierTest 2, ReminderSchedulerTest and HomeViewModelTest green.

- [ ] **Step 8: Verify and commit**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

```bash
git add app/src/main/res/drawable/ic_stat_nobby.xml app/src/main/res/drawable/ic_launcher_foreground.xml app/src/main/res/drawable/ic_launcher_monochrome.xml \
  app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml app/src/main/res/values/ic_launcher_background.xml \
  app/src/main/java/com/nobby/ring/notifications app/src/test/java/com/nobby/ring/notifications app/src/test/java/com/nobby/ring/ui/home/HomeViewModelTest.kt
git commit -m "feat(notifications): ring glyph small icon, preset NoBby large icons, new adaptive launcher icon"
```

---

### Task 16: Emulator verification (Pixel 3a API 34)

No new code unless a check fails; fixes go in separate `fix(...)` commits with a test where one is possible. Screenshots go to `notes/shots-v2/` (git-ignored, not committed). Write the results table to `notes/v2-verify-report.md` (git-ignored) and add one line to `notes/progress.md`.

**Files:** none in the repo (unless a fix is needed).

- [ ] **Step 1: Start the emulator and install a fresh build**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"; export PATH="${PATH//\"/}"
SDK="${ANDROID_HOME:?set ANDROID_HOME}"; ADB="$SDK/platform-tools/adb"
"$SDK/emulator/emulator" -avd Pixel_3a_API_34_extension_level_7_x86_64 -no-snapshot-save >/dev/null 2>&1 &
"$ADB" wait-for-device; until [ "$("$ADB" shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain
"$ADB" uninstall com.nobby.ring; "$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
mkdir -p notes/shots-v2
shot() { "$ADB" exec-out screencap -p > "notes/shots-v2/$1.png"; }
```

Expected: `BUILD SUCCESSFUL`, `Success` from install. Use `shot <name>` after each check below. Clear logcat first (`"$ADB" logcat -c`) and keep `"$ADB" logcat -s AndroidRuntime:E NoBby:E` clean throughout.

- [ ] **Step 2: Matrix of states to capture**

Language: `"$ADB" shell cmd locale set-app-locales com.nobby.ring --user current --locales de-DE` (and `en-US`); if the command is rejected, use `"$ADB" root` then `"$ADB" shell "setprop persist.sys.locale de-DE; setprop ctl.restart zygote"`. Dark mode: `"$ADB" shell cmd uimode night yes|no` with the app on "Auto", plus the in-app Hell/Dunkel switch. Preset: Settings > Farbe & Modus.

For **DE light Berry, DE dark Berry, EN light Lilac, EN dark Lilac** capture: onboarding steps 1–4 (`onb1..onb4`), Home wear (`home_wear`), Calendar with a selected empty day and a day with a note (`cal_empty`, `cal_note`), History empty and with entries (`hist_empty`, `hist_rows`), Settings hub (`settings`), Appearance (`appearance`), Cycle & schedule (`schedule`), Reminders + edit dialog (`reminders`, `reminder_edit`), Calendar sync + clean-up (`calsync`, `cleanup`), Widget settings (`widget_settings`), the late-confirm dialog (`confirm_late`) and the early-confirm dialog (`confirm_early`). Name files `<state>_<lang>_<mode>_<preset>.png`.

Check on each: paper background (no white flash on start), Fredoka titles and countdown, pill buttons with the darker bottom edge, cocoa selection (chips, calendar day), at most one mascot per screen, German bottom-bar labels on one line, no clipped text.

- [ ] **Step 3: Home ring states (Berry, DE light + one dark shot each)**

Drive the states by setting Cycle & schedule (anchor date/time) so that today is: wear day 12 (happy sticker, beads 11 + today), due today before the time (neutral, word "Heute"), 2 days overdue (worried, tomato beads, chip "Ring drin · 2 Tage drüber", overdue card with Erledigt + Später), more than 28 days of wear (alert, "Schutz prüfen" pill, safety notice line, safety text in the card), pause day 4 (sleepy, sage beads). Capture `ring_wear`, `ring_due`, `ring_overdue`, `ring_safety`, `ring_pause`. With an insertion 2 days overdue the chip must read "Pause · 2 Tage drüber" (v1.1 fix 1, `chip_pause_over`).

- [ ] **Step 4: Done sheet and v1.1 fix 3**

From "due today" tap Erledigt: the sheet slides up with the yay mascot and confetti, the ring sticker is gone, "Yay, Ring ist raus!", next-event row, auto-close bar; it closes after 4 s (`done_sheet`). Repeat and close by tap, then by swipe down. With `"$ADB" shell settings put global animator_duration_scale 0` repeat once: no slide, quick fade (`done_sheet_reduced`), then restore with `… animator_duration_scale 1`. Confirm from the notification's Erledigt: no sheet appears in the app.
Fix 3: set the anchor so the REMOVE is 23 days late, tap Erledigt and choose "Termine verschieben": Home shows the pause (chip "Pause · Tag 1 von 7" or 2), sleepy sticker, next event "Ring einsetzen" (`fix3_pause`).

- [ ] **Step 5: Time picker (v1.1 fix 2)**

Open any time field (onboarding step 1, Cycle & schedule, confirm dialog): the header digits "21 : 00" are fully visible, not clipped (`timepicker_de_light`, `timepicker_en_dark`).

- [ ] **Step 6: Widget**

Add the widget to the home screen; resize to 2×1, 2×2 and 4×2 where the launcher allows it (v1 could only produce widths, record what is possible). For Single and Multi, light and dark (system night mode with the app on Auto), Berry and Matcha: solid background, system sans number, phase bar in primary (pause: secondary, overdue: error), flat NoBby face at 2×2 and up only, Done pill only when due/overdue and it confirms (`widget_<size>_<mode>_<theme>_<preset>`).

- [ ] **Step 7: Notifications and launcher icon**

`"$ADB" root`, then trigger a reminder and an overdue nag directly:

```bash
"$ADB" shell am broadcast -n com.nobby.ring/.notifications.AlarmReceiver -a com.nobby.ring.REMINDER \
  --es eventId "REMOVE@2026-11-22T21:00" --es kind REMOVE --es plannedAt 2026-11-22T21:00 --es importance NORMAL
"$ADB" shell am broadcast -n com.nobby.ring/.notifications.AlarmReceiver -a com.nobby.ring.OVERDUE
```

(the overdue broadcast only posts when the current schedule is overdue; set the anchor accordingly first). Expected: small icon is the monochrome ring glyph (not a blob), large icon is the flat happy face (reminder) / worried face (overdue) in the active preset's mascot colours; switch the preset to Peach and trigger again (`notif_reminder_berry`, `notif_overdue_berry`, `notif_reminder_peach`). Launcher: NoBby face on berry, and with Wallpaper & style > Themed icons on, the monochrome ring (`launcher`, `launcher_themed`).

- [ ] **Step 8: Landscape and font scale 1.3**

`"$ADB" shell settings put system accelerometer_rotation 0; "$ADB" shell settings put system user_rotation 1` → Home, Calendar, Settings, Onboarding step 1 scroll and nothing overlaps (`land_*`); restore `user_rotation 0`. `"$ADB" shell settings put system font_scale 1.3` → Home (Done reachable, ring number inside the ring), preset picker labels, bottom bar, Done sheet (`fs13_*`); restore `font_scale 1.0`.

- [ ] **Step 9: Report**

Write `notes/v2-verify-report.md` with one row per step (PASS / FAIL / PARTIAL + screenshot names), append `v2 emulator verification: <n>/8 pass; shots in notes/shots-v2/` to `notes/progress.md`, and run the verify command once more. If any fix was committed, the final state must again be `BUILD SUCCESSFUL`.

---

## Spec coverage map

| Spec section | Task(s) |
|---|---|
| §4 Theme system (Palette enum, migration, 41-role tables, NobbyExtraColors, semantics, contrast test, widget tables) | 2, 14 |
| §5 Typography (bundled fonts, OFL in res/raw + About, type scale, countdown, widget system sans) | 1, 12, 14 |
| §6 Shape, space, motion (Shapes, NobbySpace, sticker edge, durations/easings, reduced motion) | 5, 7, 9 |
| §7 Mascot (moods, props, runtime recolour, Nobby composable, moments, notification + widget faces, decorative a11y) | 6, 9, 10, 11, 12, 13, 14, 15 |
| §8 Components (BeadRing, buttons, chips, cards/banners, MonthGrid bands, stepper/pickers/switches/rows, top/nav bars) | 5, 7, 8, 9, 10, 12 |
| §9 Screens (Home, Calendar, Onboarding 4 steps, Settings, Appearance, History, sub-screens, dialogs; portrait/landscape/1.3) | 9–13, 16 |
| §10 Copy (DE-first casual du, EN mirror, plurals, no concatenation, key parity) | 3, (key trims in 9, 10, 11, 13) |
| §11 v1.1 fixes (chip, time picker, phase after very late removal) | 4, 1 |
| §12 Widget | 14 |
| §13 Launcher icon | 15 |
| §14 Testing (unit, Robolectric/Compose, existing tests green, emulator) | every task, 16 |
| §15 Risks (generated tables + tests, Canvas mascot fallback, APK size) | 2, 6, 1 |
