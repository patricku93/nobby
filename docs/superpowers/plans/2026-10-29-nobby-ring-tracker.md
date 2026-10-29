# NoBby Ring Tracker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build v1 of NoBby, a native Android app that schedules, reminds about, confirms and calendar-syncs contraceptive-ring removal/insertion, with a Glance home-screen widget and curated themes.

**Architecture:** Single Gradle module `app`, package `com.nobby.ring`. A pure-Kotlin `domain` package (schedule engine, overdue/safety logic, reminder planning, calendar diff) is unit-tested on the JVM. `data` (Room + Preferences DataStore + CalendarContract) feeds a `ScheduleRepository` that exposes `Flow<ScheduleState>`; UI (Compose/Material 3), the Glance widget, the AlarmManager-based reminder scheduler and the calendar sync all consume that flow. Hilt wires everything.

**Tech Stack:** Kotlin 2.1, AGP 8.7, Gradle 8.14.3 (JBR 17 bundled with Android Studio), Jetpack Compose BOM 2024.12 + Material 3, Navigation Compose, Hilt 2.53, Room 2.6, Preferences DataStore 1.1, Glance 1.1, WorkManager 2.10, kotlinx-datetime 0.6, JUnit 4, Robolectric 4.14.

## Global Constraints

- `minSdk 26`, `targetSdk 35`, `compileSdk 35`.
- Package / applicationId: `com.nobby.ring`. App display name: `NoBby`.
- All user-visible strings live in `res/values/strings.xml` (English) and `res/values-de/strings.xml` (German). Never hard-code UI text in Kotlin.
- `domain/` must not import anything from `android.*` or `androidx.*`.
- Immutability: domain and UI state are `data class`es; update with `copy()`, never mutate.
- No `println`/`Log` left in domain code; Android layers use `android.util.Log` with tag `"NoBby"` only for errors.
- Safety thresholds (from spec): wear > 28 days or pause > 7 days = `safetyBreached`. Validation ranges: wear 1–35, pause 0–14, horizon 1–24 months.
- Overdue repeat default 2 h, snooze default 1 h.
- Palettes: Rose (default), Lavender, Mint, Ocean, Graphite. Modes: light / dark / system.
- Event ids are deterministic: `"${kind}@${plannedAt}"` with `plannedAt` in ISO-8601 `LocalDateTime` form (e.g. `REMOVE@2026-11-10T21:00`).
- Commit after every task with a conventional-commit message (`feat:`, `test:`, `chore:` …). Commit only when the user has asked for commits; otherwise leave the steps unchecked and tell the user.
- Build/test command from the project root (Git Bash):
  `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"; ./gradlew.bat <task> --console=plain -q`

---

## File map

```
settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml, gradle/wrapper/*
app/build.gradle.kts, app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/res/values/strings.xml, values-de/strings.xml, values/themes.xml, xml/ring_widget_info.xml, drawable/ic_launcher_foreground.xml, mipmap-anydpi-v26/ic_launcher.xml
app/src/main/java/com/nobby/ring/
  NobbyApp.kt                      Hilt Application + WorkManager config + notification channels
  MainActivity.kt                  single Activity, sets theme + NavHost
  domain/model/Method.kt           Phase, PhaseKind, Method
  domain/model/ScheduledEvent.kt   EventKind, ScheduledEvent, eventId()
  domain/model/EventLog.kt         EventLog
  domain/model/ReminderRule.kt     Importance, ReminderTarget, ReminderRule, OverdueConfig
  domain/model/ScheduleConfig.kt   ScheduleConfig (method, anchor, horizon, skipPauseOnce)
  domain/model/ScheduleState.kt    ScheduleState, CyclePosition, OverdueInfo
  domain/engine/ScheduleEngine.kt  generate(config): List<ScheduledEvent>
  domain/engine/CycleCalculator.kt positionAt(config, now): CyclePosition
  domain/engine/OverdueCalculator.kt overdue(events, logs, config, now): OverdueInfo?
  domain/engine/ConfirmAction.kt   ShiftPolicy, confirm(config, event, logs, confirmedAt, policy): ConfirmResult
  domain/engine/ReminderPlanner.kt plan(rules, events, logs, now): List<PlannedReminder>
  domain/engine/CalendarDiff.kt    CalendarEntry, diff(desired, links): CalendarDiffResult
  data/prefs/MethodConfigStore.kt  DataStore: wear/pause/time/anchor/horizon/skipPause/onboardingDone
  data/prefs/ThemeStore.kt         palette + mode
  data/prefs/WidgetConfigStore.kt  widget mode + rows
  data/prefs/OverdueConfigStore.kt repeat/snooze hours
  data/db/NobbyDatabase.kt         Room db + entities + DAOs (EventLogEntity, NoteEntity, ReminderRuleEntity, CalendarLinkEntity)
  data/repo/ScheduleRepository.kt  Flow<ScheduleState>, confirm(), skipPause(), updateConfig()
  data/repo/NotesRepository.kt
  data/repo/ReminderRepository.kt
  data/calendar/DeviceCalendarSource.kt  CalendarContract access (list, insert, update, delete, search)
  data/calendar/CalendarSyncer.kt        applies CalendarDiff
  data/calendar/CalendarSyncWorker.kt
  notifications/Channels.kt
  notifications/ReminderScheduler.kt     AlarmManager + rescheduleAll()
  notifications/AlarmReceiver.kt, BootReceiver.kt, ActionReceiver.kt
  notifications/Notifier.kt              builds reminder/overdue notifications
  widget/RingWidget.kt, RingWidgetReceiver.kt, WidgetUpdater.kt, WidgetConfigActivity.kt, WidgetWorker.kt
  ui/theme/Palettes.kt, Theme.kt, Type.kt
  ui/navigation/NobbyNavHost.kt, Routes.kt
  ui/components/RingProgress.kt, EventCard.kt, NumberStepper.kt, TimePickerField.kt, DatePickerField.kt
  ui/onboarding/OnboardingScreen.kt, OnboardingViewModel.kt
  ui/home/HomeScreen.kt, HomeViewModel.kt, ConfirmDialog.kt
  ui/calendar/CalendarScreen.kt, CalendarViewModel.kt, MonthGrid.kt
  ui/history/HistoryScreen.kt, HistoryViewModel.kt
  ui/settings/SettingsScreen.kt, ScheduleSettingsScreen.kt, RemindersScreen.kt, ReminderEditDialog.kt, AppearanceScreen.kt, CalendarSettingsScreen.kt, CalendarCleanupScreen.kt, WidgetSettingsScreen.kt, SettingsViewModel.kt
  di/AppModule.kt
app/src/test/java/com/nobby/ring/domain/...   JVM unit tests
app/src/test/java/com/nobby/ring/data/...     Robolectric tests
```

---

### Task 1: Project scaffold that builds

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `.gitignore`
- Create: `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/nobby/ring/NobbyApp.kt`, `app/src/main/java/com/nobby/ring/MainActivity.kt`
- Create: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-de/strings.xml`, `app/src/main/res/values/themes.xml`, `app/src/main/res/drawable/ic_launcher_foreground.xml`, `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `app/src/main/res/values/ic_launcher_background.xml`
- Create: `app/src/test/java/com/nobby/ring/SmokeTest.kt`

**Interfaces:**
- Produces: a Hilt-enabled app with `MainActivity` showing the text "NoBby"; `./gradlew.bat :app:testDebugUnitTest` and `:app:assembleDebug` pass.

- [ ] **Step 1: Generate the Gradle wrapper from the cached 8.14.3 distribution**

```bash
cd "/c/Users/Patrick/Documents/Claude/NoBby - Pill"
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
GRADLE=$(ls -d ~/.gradle/wrapper/dists/gradle-8.14.3-all/*/gradle-8.14.3/bin/gradle)
"$GRADLE" wrapper --gradle-version 8.14.3 --distribution-type all
ls gradle/wrapper   # expect gradle-wrapper.jar gradle-wrapper.properties
```

- [ ] **Step 2: Write `.gitignore`**

```
.gradle/
build/
local.properties
.idea/
*.iml
.kotlin/
captures/
.cxx/
.superpowers/
```

- [ ] **Step 3: Write `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "NoBby"
include(":app")
```

- [ ] **Step 4: Write `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.7.3"
kotlin = "2.1.0"
ksp = "2.1.0-1.0.29"
hilt = "2.53.1"
hiltAndroidX = "1.2.0"
composeBom = "2024.12.01"
activityCompose = "1.9.3"
navigation = "2.8.5"
lifecycle = "2.8.7"
coreKtx = "1.15.0"
room = "2.6.1"
datastore = "1.1.1"
glance = "1.1.1"
work = "2.10.0"
kotlinxDatetime = "0.6.1"
coroutines = "1.9.0"
junit = "4.13.2"
robolectric = "4.14.1"
androidxTestCore = "1.6.1"
androidxJunit = "1.2.1"

[libraries]
core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltAndroidX" }
hilt-work = { group = "androidx.hilt", name = "hilt-work", version.ref = "hiltAndroidX" }
hilt-androidx-compiler = { group = "androidx.hilt", name = "hilt-compiler", version.ref = "hiltAndroidX" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
glance-appwidget = { group = "androidx.glance", name = "glance-appwidget", version.ref = "glance" }
glance-material3 = { group = "androidx.glance", name = "glance-material3", version.ref = "glance" }
work-runtime = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
kotlinx-datetime = { group = "org.jetbrains.kotlinx", name = "kotlinx-datetime", version.ref = "kotlinxDatetime" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core = { group = "androidx.test", name = "core-ktx", version.ref = "androidxTestCore" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxJunit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

- [ ] **Step 5: Write root `build.gradle.kts` and `gradle.properties`**

`build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
```

`gradle.properties`:
```
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
org.gradle.caching=true
org.gradle.configuration-cache=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

- [ ] **Step 6: Write `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.nobby.ring"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nobby.ring"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.work.runtime)
    implementation(libs.kotlinx.datetime)
    implementation(libs.coroutines.android)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.room.ktx)
}
```

`app/proguard-rules.pro`:
```
-keep class com.nobby.ring.** { *; }
```

- [ ] **Step 7: Write the manifest, Application and Activity**

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.READ_CALENDAR" />
    <uses-permission android:name="android.permission.WRITE_CALENDAR" />

    <application
        android:name=".NobbyApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.NoBby">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:theme="@style/Theme.NoBby">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge"
            xmlns:tools="http://schemas.android.com/tools">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>
    </application>
</manifest>
```

`app/src/main/java/com/nobby/ring/NobbyApp.kt`:
```kotlin
package com.nobby.ring

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NobbyApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
```

`app/src/main/java/com/nobby/ring/MainActivity.kt`:
```kotlin
package com.nobby.ring

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface { Text(stringResource(R.string.app_name)) }
            }
        }
    }
}
```

- [ ] **Step 8: Write resources**

`app/src/main/res/values/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">NoBby</string>
</resources>
```

`app/src/main/res/values-de/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">NoBby</string>
</resources>
```

`app/src/main/res/values/themes.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.NoBby" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:windowLightStatusBar">true</item>
    </style>
</resources>
```

`app/src/main/res/values/ic_launcher_background.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#F8BBD0</color>
</resources>
```

`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

`app/src/main/res/drawable/ic_launcher_foreground.xml` (a ring):
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path
        android:fillColor="#00000000"
        android:strokeColor="#AD1457"
        android:strokeWidth="9"
        android:pathData="M54,54m-24,0a24,24 0 1,1 48,0a24,24 0 1,1 -48,0" />
</vector>
```

- [ ] **Step 9: Write a smoke test**

`app/src/test/java/com/nobby/ring/SmokeTest.kt`:
```kotlin
package com.nobby.ring

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test fun `arithmetic works`() = assertEquals(4, 2 + 2)
}
```

- [ ] **Step 10: Build and test**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain -q
```
Expected: `BUILD SUCCESSFUL`, `app/build/outputs/apk/debug/app-debug.apk` exists. If the first run fails on a missing SDK path, create `local.properties` with `sdk.dir=C\:\\Users\\Patrick\\AppData\\Local\\Android\\Sdk`.

- [ ] **Step 11: Commit**

```bash
git add -A && git commit -m "chore: scaffold NoBby Android project with Compose, Hilt, Room, Glance"
```

---

### Task 2: Domain models and ScheduleEngine

**Files:**
- Create: `app/src/main/java/com/nobby/ring/domain/model/Method.kt`
- Create: `app/src/main/java/com/nobby/ring/domain/model/ScheduledEvent.kt`
- Create: `app/src/main/java/com/nobby/ring/domain/model/ScheduleConfig.kt`
- Create: `app/src/main/java/com/nobby/ring/domain/engine/ScheduleEngine.kt`
- Test: `app/src/test/java/com/nobby/ring/domain/engine/ScheduleEngineTest.kt`

**Interfaces:**
- Produces:
  - `enum class PhaseKind { WEAR, PAUSE }`, `data class Phase(kind, days: Int)`, `data class Method(name: String, phases: List<Phase>, timeOfDay: LocalTime)` with `cycleLengthDays: Int`, `wearDays: Int`, `pauseDays: Int`, `companion fun ring(wearDays, pauseDays, timeOfDay)`.
  - `enum class EventKind { REMOVE, INSERT, SWAP }`, `data class ScheduledEvent(id: String, kind: EventKind, plannedAt: LocalDateTime, cycleIndex: Int, cycleStart: LocalDateTime)`, `fun eventId(kind, plannedAt): String`.
  - `data class ScheduleConfig(method: Method, anchor: LocalDateTime, horizonMonths: Int = 12, skipPauseOnce: Boolean = false)`.
  - `object ScheduleEngine { fun generate(config: ScheduleConfig): List<ScheduledEvent> }` — sorted by time, starting at cycle 0 = `anchor`, until `anchor + horizonMonths`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/nobby/ring/domain/engine/ScheduleEngineTest.kt`:
```kotlin
package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleEngineTest {
    private val nine = LocalTime(21, 0)
    private val anchor = LocalDateTime(2026, 11, 1, 21, 0)

    @Test
    fun `standard 21-7 cycle produces remove then insert per cycle`() {
        val config = ScheduleConfig(Method.ring(21, 7, nine), anchor, horizonMonths = 2)
        val events = ScheduleEngine.generate(config)

        assertEquals(EventKind.REMOVE, events[0].kind)
        assertEquals(LocalDateTime(2026, 11, 22, 21, 0), events[0].plannedAt)
        assertEquals(EventKind.INSERT, events[1].kind)
        assertEquals(LocalDateTime(2026, 11, 29, 21, 0), events[1].plannedAt)
        assertEquals(0, events[0].cycleIndex)
        assertEquals(1, events[2].cycleIndex)
        assertEquals(LocalDateTime(2026, 12, 20, 21, 0), events[2].plannedAt)
    }

    @Test
    fun `continuous use with zero pause produces only swaps`() {
        val config = ScheduleConfig(Method.ring(28, 0, nine), anchor, horizonMonths = 3)
        val events = ScheduleEngine.generate(config)

        assertTrue(events.all { it.kind == EventKind.SWAP })
        assertEquals(LocalDateTime(2026, 11, 29, 21, 0), events[0].plannedAt)
        assertEquals(LocalDateTime(2026, 12, 27, 21, 0), events[1].plannedAt)
    }

    @Test
    fun `skipPauseOnce turns only the first remove into a swap`() {
        val config = ScheduleConfig(Method.ring(21, 7, nine), anchor, horizonMonths = 2, skipPauseOnce = true)
        val events = ScheduleEngine.generate(config)

        assertEquals(EventKind.SWAP, events[0].kind)
        assertEquals(LocalDateTime(2026, 11, 22, 21, 0), events[0].plannedAt)
        // cycle 1 starts at the swap, no insert in cycle 0
        assertEquals(EventKind.REMOVE, events[1].kind)
        assertEquals(LocalDateTime(2026, 12, 13, 21, 0), events[1].plannedAt)
        assertEquals(1, events[1].cycleIndex)
    }

    @Test
    fun `events stop at the horizon and are sorted`() {
        val config = ScheduleConfig(Method.ring(21, 7, nine), anchor, horizonMonths = 12)
        val events = ScheduleEngine.generate(config)
        val last = events.last().plannedAt
        assertTrue(last < LocalDateTime(2027, 11, 1, 21, 0))
        assertEquals(events, events.sortedBy { it.plannedAt })
    }

    @Test
    fun `event ids are deterministic`() {
        val config = ScheduleConfig(Method.ring(21, 7, nine), anchor, horizonMonths = 1)
        val events = ScheduleEngine.generate(config)
        assertEquals("REMOVE@2026-11-22T21:00", events[0].id)
    }

    @Test
    fun `time of day applies to every event`() {
        val config = ScheduleConfig(Method.ring(21, 7, LocalTime(8, 30)), LocalDateTime(2026, 11, 1, 8, 30), 1)
        val events = ScheduleEngine.generate(config)
        assertTrue(events.all { it.plannedAt.hour == 8 && it.plannedAt.minute == 30 })
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.domain.engine.ScheduleEngineTest" --console=plain -q
```
Expected: compilation failure (`Unresolved reference: ScheduleEngine`).

- [ ] **Step 3: Write the models**

`domain/model/Method.kt`:
```kotlin
package com.nobby.ring.domain.model

import kotlinx.datetime.LocalTime

enum class PhaseKind { WEAR, PAUSE }

data class Phase(val kind: PhaseKind, val days: Int)

data class Method(
    val name: String,
    val phases: List<Phase>,
    val timeOfDay: LocalTime,
) {
    val cycleLengthDays: Int get() = phases.sumOf { it.days }
    val wearDays: Int get() = phases.filter { it.kind == PhaseKind.WEAR }.sumOf { it.days }
    val pauseDays: Int get() = phases.filter { it.kind == PhaseKind.PAUSE }.sumOf { it.days }

    companion object {
        const val RING = "ring"
        fun ring(wearDays: Int, pauseDays: Int, timeOfDay: LocalTime): Method = Method(
            name = RING,
            phases = listOf(Phase(PhaseKind.WEAR, wearDays), Phase(PhaseKind.PAUSE, pauseDays)),
            timeOfDay = timeOfDay,
        )
    }
}
```

`domain/model/ScheduledEvent.kt`:
```kotlin
package com.nobby.ring.domain.model

import kotlinx.datetime.LocalDateTime

enum class EventKind { REMOVE, INSERT, SWAP }

data class ScheduledEvent(
    val id: String,
    val kind: EventKind,
    val plannedAt: LocalDateTime,
    val cycleIndex: Int,
    val cycleStart: LocalDateTime,
) {
    /** True when confirming this event starts a new cycle (ring goes in). */
    val startsCycle: Boolean get() = kind != EventKind.REMOVE
}

fun eventId(kind: EventKind, plannedAt: LocalDateTime): String = "${kind.name}@$plannedAt"
```

`domain/model/ScheduleConfig.kt`:
```kotlin
package com.nobby.ring.domain.model

import kotlinx.datetime.LocalDateTime

data class ScheduleConfig(
    val method: Method,
    /** Start of the current cycle (cycle index 0): the moment the current ring went in. */
    val anchor: LocalDateTime,
    val horizonMonths: Int = 12,
    /** One-off: the next REMOVE becomes a SWAP and the following cycle starts there. */
    val skipPauseOnce: Boolean = false,
) {
    companion object {
        const val MIN_WEAR = 1
        const val MAX_WEAR = 35
        const val MIN_PAUSE = 0
        const val MAX_PAUSE = 14
        const val MIN_HORIZON = 1
        const val MAX_HORIZON = 24
        const val SAFE_MAX_WEAR = 28
        const val SAFE_MAX_PAUSE = 7
    }
}
```

- [ ] **Step 4: Write the engine**

`domain/engine/ScheduleEngine.kt`:
```kotlin
package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.ScheduledEvent
import com.nobby.ring.domain.model.eventId
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus

object ScheduleEngine {

    fun generate(config: ScheduleConfig): List<ScheduledEvent> {
        val method = config.method
        val horizonEnd = config.anchor.date.plus(config.horizonMonths, DateTimeUnit.MONTH).atTime(config.anchor.time)
        val events = mutableListOf<ScheduledEvent>()
        var cycleStart = config.anchor
        var cycleIndex = 0
        var skipPause = config.skipPauseOnce

        while (true) {
            val removeAt = cycleStart.date.plus(method.wearDays, DateTimeUnit.DAY).atTime(method.timeOfDay)
            if (removeAt >= horizonEnd) break

            val swap = method.pauseDays == 0 || skipPause
            if (swap) {
                events += ScheduledEvent(eventId(EventKind.SWAP, removeAt), EventKind.SWAP, removeAt, cycleIndex, cycleStart)
                skipPause = false
                cycleStart = removeAt
            } else {
                events += ScheduledEvent(eventId(EventKind.REMOVE, removeAt), EventKind.REMOVE, removeAt, cycleIndex, cycleStart)
                val insertAt = removeAt.date.plus(method.pauseDays, DateTimeUnit.DAY).atTime(method.timeOfDay)
                if (insertAt >= horizonEnd) break
                events += ScheduledEvent(eventId(EventKind.INSERT, insertAt), EventKind.INSERT, insertAt, cycleIndex, cycleStart)
                cycleStart = insertAt
            }
            cycleIndex += 1
        }
        return events.toList()
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Same command as Step 2. Expected: `BUILD SUCCESSFUL`, 6 tests pass.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(domain): schedule engine with ring/continuous/skip-pause generation"
```

---

### Task 3: CycleCalculator, OverdueCalculator and safety

**Files:**
- Create: `domain/model/EventLog.kt`, `domain/model/ScheduleState.kt`
- Create: `domain/engine/CycleCalculator.kt`, `domain/engine/OverdueCalculator.kt`
- Test: `app/src/test/java/com/nobby/ring/domain/engine/CycleCalculatorTest.kt`, `OverdueCalculatorTest.kt`

**Interfaces:**
- Consumes: `ScheduleConfig`, `ScheduledEvent`, `ScheduleEngine.generate`.
- Produces:
  - `data class EventLog(eventId: String, kind: EventKind, plannedAt: LocalDateTime, confirmedAt: LocalDateTime)`.
  - `data class CyclePosition(phase: PhaseKind, dayInCycle: Int, dayInPhase: Int, cycleLengthDays: Int, phaseLengthDays: Int, ringIn: Boolean)`.
  - `data class OverdueInfo(event: ScheduledEvent, delay: Duration, safetyBreached: Boolean)`.
  - `data class ScheduleState(config, events, logs, now, position: CyclePosition, nextEvent: ScheduledEvent?, overdue: OverdueInfo?)` with `val pendingEvents` (events without log) and `val daysUntilNext: Int?`.
  - `object CycleCalculator { fun positionAt(config: ScheduleConfig, logs: List<EventLog>, now: LocalDateTime): CyclePosition }`.
  - `object OverdueCalculator { fun overdue(config, events, logs, now): OverdueInfo?; fun nextPending(events, logs): ScheduledEvent? }`.

- [ ] **Step 1: Write the failing tests**

`CycleCalculatorTest.kt`:
```kotlin
package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.PhaseKind
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleCalculatorTest {
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0))

    @Test
    fun `day 1 of wear right after anchor`() {
        val p = CycleCalculator.positionAt(config, emptyList(), LocalDateTime(2026, 11, 1, 22, 0))
        assertEquals(PhaseKind.WEAR, p.phase)
        assertEquals(1, p.dayInCycle)
        assertEquals(1, p.dayInPhase)
        assertEquals(28, p.cycleLengthDays)
        assertEquals(21, p.phaseLengthDays)
        assertTrue(p.ringIn)
    }

    @Test
    fun `day 12 of wear`() {
        val p = CycleCalculator.positionAt(config, emptyList(), LocalDateTime(2026, 11, 12, 23, 0))
        assertEquals(12, p.dayInCycle)
        assertEquals(PhaseKind.WEAR, p.phase)
    }

    @Test
    fun `pause day 3 when remove was confirmed`() {
        val removeLog = EventLog("REMOVE@2026-11-22T21:00", EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), LocalDateTime(2026, 11, 22, 21, 5))
        val p = CycleCalculator.positionAt(config, listOf(removeLog), LocalDateTime(2026, 11, 24, 23, 0))
        assertEquals(PhaseKind.PAUSE, p.phase)
        assertEquals(24, p.dayInCycle)
        assertEquals(3, p.dayInPhase)
        assertEquals(7, p.phaseLengthDays)
        assertFalse(p.ringIn)
    }

    @Test
    fun `ring still counted as in while remove is unconfirmed`() {
        val p = CycleCalculator.positionAt(config, emptyList(), LocalDateTime(2026, 11, 24, 23, 0))
        assertTrue(p.ringIn)
        assertEquals(PhaseKind.WEAR, p.phase)
    }
}
```

`OverdueCalculatorTest.kt`:
```kotlin
package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours

class OverdueCalculatorTest {
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0), 3)
    private val events = ScheduleEngine.generate(config)

    @Test
    fun `nothing overdue before first event`() {
        assertNull(OverdueCalculator.overdue(config, events, emptyList(), LocalDateTime(2026, 11, 20, 12, 0)))
    }

    @Test
    fun `remove overdue by two hours`() {
        val info = OverdueCalculator.overdue(config, events, emptyList(), LocalDateTime(2026, 11, 22, 23, 0))
        assertNotNull(info)
        assertEquals(EventKind.REMOVE, info!!.event.kind)
        assertEquals(2.hours, info.delay)
        assertFalse(info.safetyBreached)
    }

    @Test
    fun `remove overdue beyond 28 days of wear breaches safety`() {
        val info = OverdueCalculator.overdue(config, events, emptyList(), LocalDateTime(2026, 11, 29, 22, 0))
        assertTrue(info!!.safetyBreached)
    }

    @Test
    fun `insert overdue beyond 7 days of pause breaches safety`() {
        val removeLog = EventLog("REMOVE@2026-11-22T21:00", EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), LocalDateTime(2026, 11, 22, 21, 0))
        val ok = OverdueCalculator.overdue(config, events, listOf(removeLog), LocalDateTime(2026, 11, 29, 23, 0))
        assertEquals(EventKind.INSERT, ok!!.event.kind)
        assertFalse(ok.safetyBreached)
        val bad = OverdueCalculator.overdue(config, events, listOf(removeLog), LocalDateTime(2026, 11, 30, 22, 0))
        assertTrue(bad!!.safetyBreached)
    }

    @Test
    fun `confirmed event is not overdue`() {
        val log = EventLog("REMOVE@2026-11-22T21:00", EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), LocalDateTime(2026, 11, 23, 9, 0))
        assertNull(OverdueCalculator.overdue(config, events, listOf(log), LocalDateTime(2026, 11, 23, 12, 0)))
    }

    @Test
    fun `nextPending skips confirmed events`() {
        val log = EventLog("REMOVE@2026-11-22T21:00", EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), LocalDateTime(2026, 11, 22, 21, 0))
        assertEquals(EventKind.INSERT, OverdueCalculator.nextPending(events, listOf(log))!!.kind)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.domain.engine.*" --console=plain -q
```
Expected: compilation failure (unresolved `CycleCalculator`, `OverdueCalculator`, `EventLog`).

- [ ] **Step 3: Write models**

`domain/model/EventLog.kt`:
```kotlin
package com.nobby.ring.domain.model

import kotlinx.datetime.LocalDateTime

data class EventLog(
    val eventId: String,
    val kind: EventKind,
    val plannedAt: LocalDateTime,
    val confirmedAt: LocalDateTime,
)
```

`domain/model/ScheduleState.kt`:
```kotlin
package com.nobby.ring.domain.model

import kotlinx.datetime.LocalDateTime
import kotlin.time.Duration

data class CyclePosition(
    val phase: PhaseKind,
    val dayInCycle: Int,
    val dayInPhase: Int,
    val cycleLengthDays: Int,
    val phaseLengthDays: Int,
    val ringIn: Boolean,
)

data class OverdueInfo(
    val event: ScheduledEvent,
    val delay: Duration,
    val safetyBreached: Boolean,
)

data class ScheduleState(
    val config: ScheduleConfig,
    val events: List<ScheduledEvent>,
    val logs: List<EventLog>,
    val now: LocalDateTime,
    val position: CyclePosition,
    val nextEvent: ScheduledEvent?,
    val overdue: OverdueInfo?,
) {
    val loggedIds: Set<String> get() = logs.map { it.eventId }.toSet()
    val pendingEvents: List<ScheduledEvent> get() = events.filter { it.id !in loggedIds }
    /** Whole days from today's date to the next event's date (negative when overdue). */
    val daysUntilNext: Int? get() = nextEvent?.let { it.plannedAt.date.toEpochDays() - now.date.toEpochDays() }
}
```

- [ ] **Step 4: Write the calculators**

`domain/engine/CycleCalculator.kt`:
```kotlin
package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.CyclePosition
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.PhaseKind
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.datetime.LocalDateTime

object CycleCalculator {

    fun positionAt(config: ScheduleConfig, logs: List<EventLog>, now: LocalDateTime): CyclePosition {
        val method = config.method
        val dayInCycle = (now.date.toEpochDays() - config.anchor.date.toEpochDays()) + 1
        val removeConfirmed = logs.any { it.kind == EventKind.REMOVE && it.plannedAt >= config.anchor }
        val ringIn = !removeConfirmed
        return if (ringIn) {
            CyclePosition(PhaseKind.WEAR, dayInCycle, dayInCycle, method.cycleLengthDays, method.wearDays, ringIn = true)
        } else {
            val dayInPause = dayInCycle - method.wearDays
            CyclePosition(PhaseKind.PAUSE, dayInCycle, dayInPause, method.cycleLengthDays, method.pauseDays, ringIn = false)
        }
    }
}
```

`domain/engine/OverdueCalculator.kt`:
```kotlin
package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.OverdueInfo
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.ScheduledEvent
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

object OverdueCalculator {

    fun nextPending(events: List<ScheduledEvent>, logs: List<EventLog>): ScheduledEvent? {
        val logged = logs.map { it.eventId }.toSet()
        return events.firstOrNull { it.id !in logged }
    }

    fun overdue(config: ScheduleConfig, events: List<ScheduledEvent>, logs: List<EventLog>, now: LocalDateTime): OverdueInfo? {
        val pending = nextPending(events, logs) ?: return null
        if (pending.plannedAt >= now) return null
        val delay = between(pending.plannedAt, now)
        val breached = when (pending.kind) {
            EventKind.REMOVE, EventKind.SWAP -> between(pending.cycleStart, now) > ScheduleConfig.SAFE_MAX_WEAR.days
            EventKind.INSERT -> {
                val removedAt = logs.firstOrNull { it.kind == EventKind.REMOVE && it.plannedAt >= pending.cycleStart }?.confirmedAt
                    ?: pending.cycleStart.let { config.removeTimeFor(it) }
                between(removedAt, now) > ScheduleConfig.SAFE_MAX_PAUSE.days
            }
        }
        return OverdueInfo(pending, delay, breached)
    }

    private fun between(from: LocalDateTime, to: LocalDateTime): Duration {
        val tz = TimeZone.UTC
        return to.toInstant(tz) - from.toInstant(tz)
    }

    private fun ScheduleConfig.removeTimeFor(cycleStart: LocalDateTime): LocalDateTime =
        cycleStart.date.plus(method.wearDays, DateTimeUnit.DAY).atTime(method.timeOfDay)
}
```

(Add `import kotlinx.datetime.DateTimeUnit`, `import kotlinx.datetime.atTime` and `import kotlinx.datetime.plus` to the imports of `OverdueCalculator.kt`.)

Note: durations are computed in UTC on purpose — the domain works in wall-clock `LocalDateTime`, so a DST shift of one hour is intentionally ignored here (reminders use real zones in Task 12).

- [ ] **Step 5: Run tests to verify they pass**

Same command as Step 2. Expected: all `ScheduleEngineTest`, `CycleCalculatorTest`, `OverdueCalculatorTest` pass.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(domain): cycle position, overdue detection and safety thresholds"
```

---

### Task 4: ConfirmAction (Done with SHIFT / KEEP, skip pause, re-anchoring)

**Files:**
- Create: `domain/engine/ConfirmAction.kt`
- Test: `app/src/test/java/com/nobby/ring/domain/engine/ConfirmActionTest.kt`

**Interfaces:**
- Consumes: `ScheduleConfig`, `ScheduledEvent`, `EventLog`, `ScheduleEngine`.
- Produces:
  - `enum class ShiftPolicy { SHIFT, KEEP }`
  - `data class ConfirmResult(config: ScheduleConfig, logs: List<EventLog>)`
  - `object ConfirmAction { fun confirm(config, event, logs, confirmedAt, policy): ConfirmResult; fun needsPolicyChoice(event, confirmedAt): Boolean }` — `needsPolicyChoice` is true when `|confirmedAt - plannedAt| >= 1 hour`.

Rules:
- Always appends `EventLog(event.id, event.kind, event.plannedAt, confirmedAt)`.
- If `event.startsCycle` (INSERT/SWAP): new `anchor` = `confirmedAt` (SHIFT) or `event.plannedAt` (KEEP); `skipPauseOnce` cleared; logs for events *before* the new anchor are kept (history).
- If REMOVE and SHIFT: anchor moves by `confirmedAt - plannedAt` so the following INSERT moves by the same delay. The REMOVE log keeps the *original* `plannedAt`.
- If REMOVE and KEEP: config unchanged.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmActionTest {
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0), 3)
    private val events = ScheduleEngine.generate(config)
    private val remove = events[0]
    private val insert = events[1]

    @Test
    fun `on-time confirmation needs no policy choice`() {
        assertFalse(ConfirmAction.needsPolicyChoice(remove, LocalDateTime(2026, 11, 22, 21, 30)))
        assertTrue(ConfirmAction.needsPolicyChoice(remove, LocalDateTime(2026, 11, 22, 23, 0)))
    }

    @Test
    fun `remove with KEEP leaves schedule unchanged and logs event`() {
        val r = ConfirmAction.confirm(config, remove, emptyList(), LocalDateTime(2026, 11, 24, 21, 0), ShiftPolicy.KEEP)
        assertEquals(config, r.config)
        assertEquals(1, r.logs.size)
        assertEquals(remove.id, r.logs[0].eventId)
        assertEquals(LocalDateTime(2026, 11, 24, 21, 0), r.logs[0].confirmedAt)
    }

    @Test
    fun `remove with SHIFT moves following insert by the delay`() {
        val r = ConfirmAction.confirm(config, remove, emptyList(), LocalDateTime(2026, 11, 24, 21, 0), ShiftPolicy.SHIFT)
        val newEvents = ScheduleEngine.generate(r.config)
        val nextInsert = OverdueCalculator.nextPending(newEvents, r.logs)!!
        assertEquals(EventKind.INSERT, nextInsert.kind)
        assertEquals(LocalDateTime(2026, 12, 1, 21, 0), nextInsert.plannedAt)
    }

    @Test
    fun `remove with SHIFT keeps the remove confirmed`() {
        val r = ConfirmAction.confirm(config, remove, emptyList(), LocalDateTime(2026, 11, 24, 21, 0), ShiftPolicy.SHIFT)
        val newEvents = ScheduleEngine.generate(r.config)
        assertTrue(newEvents.none { it.kind == EventKind.REMOVE && it.id !in r.logs.map { l -> l.eventId }.toSet() && it.plannedAt < LocalDateTime(2026, 12, 1, 21, 0) })
    }

    @Test
    fun `insert re-anchors at planned time with KEEP`() {
        val r = ConfirmAction.confirm(config, insert, emptyList(), LocalDateTime(2026, 11, 30, 9, 0), ShiftPolicy.KEEP)
        assertEquals(insert.plannedAt, r.config.anchor)
    }

    @Test
    fun `insert re-anchors at actual time with SHIFT`() {
        val at = LocalDateTime(2026, 11, 30, 9, 0)
        val r = ConfirmAction.confirm(config, insert, emptyList(), at, ShiftPolicy.SHIFT)
        assertEquals(at, r.config.anchor)
        val next = ScheduleEngine.generate(r.config)[0]
        assertEquals(LocalDateTime(2026, 12, 21, 21, 0), next.plannedAt)
    }

    @Test
    fun `swap clears skipPauseOnce and re-anchors`() {
        val skipConfig = config.copy(skipPauseOnce = true)
        val swap = ScheduleEngine.generate(skipConfig)[0]
        assertEquals(EventKind.SWAP, swap.kind)
        val r = ConfirmAction.confirm(skipConfig, swap, emptyList(), swap.plannedAt, ShiftPolicy.KEEP)
        assertFalse(r.config.skipPauseOnce)
        assertEquals(swap.plannedAt, r.config.anchor)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.domain.engine.ConfirmActionTest" --console=plain -q
```
Expected: compilation failure.

- [ ] **Step 3: Implement**

`domain/engine/ConfirmAction.kt`:
```kotlin
package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.ScheduledEvent
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.time.Duration.Companion.hours

enum class ShiftPolicy { SHIFT, KEEP }

data class ConfirmResult(val config: ScheduleConfig, val logs: List<EventLog>)

object ConfirmAction {
    private val tz = TimeZone.UTC

    fun needsPolicyChoice(event: ScheduledEvent, confirmedAt: LocalDateTime): Boolean {
        val diff = confirmedAt.toInstant(tz) - event.plannedAt.toInstant(tz)
        return abs(diff.inWholeMinutes) >= 1.hours.inWholeMinutes
    }

    fun confirm(
        config: ScheduleConfig,
        event: ScheduledEvent,
        logs: List<EventLog>,
        confirmedAt: LocalDateTime,
        policy: ShiftPolicy,
    ): ConfirmResult {
        val newLogs = logs + EventLog(event.id, event.kind, event.plannedAt, confirmedAt)
        val newConfig = when {
            event.startsCycle -> config.copy(
                anchor = if (policy == ShiftPolicy.SHIFT) confirmedAt else event.plannedAt,
                skipPauseOnce = false,
            )
            policy == ShiftPolicy.SHIFT -> {
                val delay = confirmedAt.toInstant(tz) - event.plannedAt.toInstant(tz)
                config.copy(anchor = (config.anchor.toInstant(tz) + delay).toLocalDateTime(tz))
            }
            else -> config
        }
        return ConfirmResult(newConfig, newLogs)
    }
}
```

Note on the SHIFT-after-REMOVE test "keeps the remove confirmed": after shifting the anchor, the generated REMOVE has a new id (`REMOVE@2026-11-24T21:00`) that is not logged. To keep the confirmed state, `confirm` must also log the *shifted* remove. Add this to the REMOVE+SHIFT branch:

```kotlin
            policy == ShiftPolicy.SHIFT -> {
                val delay = confirmedAt.toInstant(tz) - event.plannedAt.toInstant(tz)
                val shifted = config.copy(anchor = (config.anchor.toInstant(tz) + delay).toLocalDateTime(tz))
                val shiftedRemove = ScheduleEngine.generate(shifted).first()
                return ConfirmResult(
                    shifted,
                    newLogs + EventLog(shiftedRemove.id, shiftedRemove.kind, shiftedRemove.plannedAt, confirmedAt),
                )
            }
```

(Keep the `when` structure; this branch returns early.)

- [ ] **Step 4: Run tests to verify they pass**

Same command. Expected: 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(domain): confirm action with shift/keep policies and re-anchoring"
```

---

### Task 5: ReminderPlanner

**Files:**
- Create: `domain/model/ReminderRule.kt`, `domain/engine/ReminderPlanner.kt`
- Test: `app/src/test/java/com/nobby/ring/domain/engine/ReminderPlannerTest.kt`

**Interfaces:**
- Produces:
  - `enum class Importance { LOW, NORMAL, HIGH, URGENT }`, `enum class ReminderTarget { REMOVE, INSERT, ANY }`
  - `data class ReminderRule(id: Long, target, offsetDays: Int, timeOfDay: LocalTime?, offsetHours: Int?, importance, enabled: Boolean)` — `offsetDays + timeOfDay` means "N days before at HH:MM"; `offsetHours` means "N hours before the event time" (takes precedence when non-null).
  - `data class OverdueConfig(repeatEveryHours: Int = 2, snoozeHours: Int = 1)`
  - `data class PlannedReminder(ruleId: Long, eventId: String, fireAt: LocalDateTime, importance: Importance, kind: EventKind)`
  - `object ReminderPlanner { fun plan(rules, events, logs, now, limit: Int = 50): List<PlannedReminder> }` — future-only, sorted, pending events only; SWAP matches both REMOVE and INSERT targets.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.Importance
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ReminderRule
import com.nobby.ring.domain.model.ReminderTarget
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPlannerTest {
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0), 2)
    private val events = ScheduleEngine.generate(config)
    private val now = LocalDateTime(2026, 11, 2, 12, 0)
    private val dayBefore = ReminderRule(1, ReminderTarget.ANY, 1, LocalTime(20, 0), null, Importance.NORMAL, true)
    private val twoHours = ReminderRule(2, ReminderTarget.REMOVE, 0, null, 2, Importance.HIGH, true)

    @Test
    fun `day-before rule fires the evening before each event`() {
        val plan = ReminderPlanner.plan(listOf(dayBefore), events, emptyList(), now)
        assertEquals(LocalDateTime(2026, 11, 21, 20, 0), plan[0].fireAt)
        assertEquals("REMOVE@2026-11-22T21:00", plan[0].eventId)
        assertEquals(LocalDateTime(2026, 11, 28, 20, 0), plan[1].fireAt)
    }

    @Test
    fun `hours-before rule only targets matching kind`() {
        val plan = ReminderPlanner.plan(listOf(twoHours), events, emptyList(), now)
        assertTrue(plan.all { it.kind == EventKind.REMOVE })
        assertEquals(LocalDateTime(2026, 11, 22, 19, 0), plan[0].fireAt)
        assertEquals(Importance.HIGH, plan[0].importance)
    }

    @Test
    fun `disabled rules and past reminders are skipped`() {
        val plan = ReminderPlanner.plan(listOf(dayBefore.copy(enabled = false)), events, emptyList(), now)
        assertTrue(plan.isEmpty())
        val late = ReminderPlanner.plan(listOf(dayBefore), events, emptyList(), LocalDateTime(2026, 11, 21, 21, 0))
        assertEquals(LocalDateTime(2026, 11, 28, 20, 0), late[0].fireAt)
    }

    @Test
    fun `confirmed events get no reminders`() {
        val log = EventLog("REMOVE@2026-11-22T21:00", EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), now)
        val plan = ReminderPlanner.plan(listOf(dayBefore), events, listOf(log), now)
        assertEquals("INSERT@2026-11-29T21:00", plan[0].eventId)
    }

    @Test
    fun `swap matches remove and insert targets`() {
        val cont = ScheduleConfig(Method.ring(28, 0, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0), 2)
        val plan = ReminderPlanner.plan(listOf(twoHours), ScheduleEngine.generate(cont), emptyList(), now)
        assertEquals(EventKind.SWAP, plan[0].kind)
    }

    @Test
    fun `result is sorted and limited`() {
        val plan = ReminderPlanner.plan(listOf(dayBefore, twoHours), events, emptyList(), now, limit = 3)
        assertEquals(3, plan.size)
        assertEquals(plan, plan.sortedBy { it.fireAt })
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.domain.engine.ReminderPlannerTest" --console=plain -q
```
Expected: compilation failure.

- [ ] **Step 3: Implement**

`domain/model/ReminderRule.kt`:
```kotlin
package com.nobby.ring.domain.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

enum class Importance { LOW, NORMAL, HIGH, URGENT }

enum class ReminderTarget { REMOVE, INSERT, ANY }

data class ReminderRule(
    val id: Long,
    val target: ReminderTarget,
    val offsetDays: Int,
    val timeOfDay: LocalTime?,
    val offsetHours: Int?,
    val importance: Importance,
    val enabled: Boolean,
) {
    fun matches(kind: EventKind): Boolean = when (target) {
        ReminderTarget.ANY -> true
        ReminderTarget.REMOVE -> kind == EventKind.REMOVE || kind == EventKind.SWAP
        ReminderTarget.INSERT -> kind == EventKind.INSERT || kind == EventKind.SWAP
    }

    companion object {
        fun defaults(timeOfDay: LocalTime): List<ReminderRule> = listOf(
            ReminderRule(0, ReminderTarget.ANY, 1, LocalTime(20, 0), null, Importance.NORMAL, true),
            ReminderRule(0, ReminderTarget.ANY, 0, timeOfDay, null, Importance.HIGH, true),
        )
    }
}

data class OverdueConfig(val repeatEveryHours: Int = 2, val snoozeHours: Int = 1)

data class PlannedReminder(
    val ruleId: Long,
    val eventId: String,
    val fireAt: LocalDateTime,
    val importance: Importance,
    val kind: EventKind,
)
```

`domain/engine/ReminderPlanner.kt`:
```kotlin
package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.PlannedReminder
import com.nobby.ring.domain.model.ReminderRule
import com.nobby.ring.domain.model.ScheduledEvent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours

object ReminderPlanner {

    fun plan(
        rules: List<ReminderRule>,
        events: List<ScheduledEvent>,
        logs: List<EventLog>,
        now: LocalDateTime,
        limit: Int = 50,
    ): List<PlannedReminder> {
        val logged = logs.map { it.eventId }.toSet()
        val pending = events.filter { it.id !in logged }
        return rules.asSequence()
            .filter { it.enabled }
            .flatMap { rule ->
                pending.asSequence()
                    .filter { rule.matches(it.kind) }
                    .map { event -> PlannedReminder(rule.id, event.id, fireAt(rule, event.plannedAt), rule.importance, event.kind) }
            }
            .filter { it.fireAt > now }
            .sortedBy { it.fireAt }
            .take(limit)
            .toList()
    }

    private fun fireAt(rule: ReminderRule, plannedAt: LocalDateTime): LocalDateTime {
        val hours = rule.offsetHours
        if (hours != null) {
            val tz = TimeZone.UTC
            return (plannedAt.toInstant(tz) - hours.hours).toLocalDateTime(tz)
        }
        val date = plannedAt.date.minus(rule.offsetDays, DateTimeUnit.DAY)
        return date.atTime(rule.timeOfDay ?: plannedAt.time)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Same command. Expected: 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(domain): reminder rules and planner"
```

---

### Task 6: CalendarDiff

**Files:**
- Create: `domain/engine/CalendarDiff.kt`
- Test: `app/src/test/java/com/nobby/ring/domain/engine/CalendarDiffTest.kt`

**Interfaces:**
- Produces:
  - `data class CalendarEntry(eventId: String, title: String, startAt: LocalDateTime, durationMinutes: Int = 30, description: String)` with `companion fun marker(eventId) = "nobby:$eventId"`.
  - `data class CalendarLink(eventId: String, providerEventId: Long, startAt: LocalDateTime, title: String)`
  - `data class CalendarDiffResult(create: List<CalendarEntry>, update: List<Pair<CalendarLink, CalendarEntry>>, delete: List<CalendarLink>)`
  - `object CalendarDiff { fun compute(desired: List<CalendarEntry>, links: List<CalendarLink>): CalendarDiffResult }`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.nobby.ring.domain.engine

import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarDiffTest {
    private val t1 = LocalDateTime(2026, 11, 22, 21, 0)
    private val t2 = LocalDateTime(2026, 11, 29, 21, 0)
    private val e1 = CalendarEntry("REMOVE@$t1", "Remove ring", t1, 30, CalendarEntry.marker("REMOVE@$t1"))
    private val e2 = CalendarEntry("INSERT@$t2", "Insert ring", t2, 30, CalendarEntry.marker("INSERT@$t2"))

    @Test
    fun `everything is created when no links exist`() {
        val r = CalendarDiff.compute(listOf(e1, e2), emptyList())
        assertEquals(listOf(e1, e2), r.create)
        assertTrue(r.update.isEmpty() && r.delete.isEmpty())
    }

    @Test
    fun `unchanged linked entries are left alone`() {
        val links = listOf(CalendarLink(e1.eventId, 10, t1, "Remove ring"))
        val r = CalendarDiff.compute(listOf(e1), links)
        assertTrue(r.create.isEmpty() && r.update.isEmpty() && r.delete.isEmpty())
    }

    @Test
    fun `changed title triggers update`() {
        val links = listOf(CalendarLink(e1.eventId, 10, t1, "Ring entfernen"))
        val r = CalendarDiff.compute(listOf(e1), links)
        assertEquals(1, r.update.size)
        assertEquals(10L, r.update[0].first.providerEventId)
    }

    @Test
    fun `stale links are deleted`() {
        val links = listOf(CalendarLink("REMOVE@2026-10-01T21:00", 7, LocalDateTime(2026, 10, 1, 21, 0), "Remove ring"))
        val r = CalendarDiff.compute(listOf(e1), links)
        assertEquals(listOf(links[0]), r.delete)
        assertEquals(listOf(e1), r.create)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.domain.engine.CalendarDiffTest" --console=plain -q
```

- [ ] **Step 3: Implement**

`domain/engine/CalendarDiff.kt`:
```kotlin
package com.nobby.ring.domain.engine

import kotlinx.datetime.LocalDateTime

data class CalendarEntry(
    val eventId: String,
    val title: String,
    val startAt: LocalDateTime,
    val durationMinutes: Int = 30,
    val description: String,
) {
    companion object {
        const val MARKER_PREFIX = "nobby:"
        fun marker(eventId: String): String = MARKER_PREFIX + eventId
    }
}

data class CalendarLink(
    val eventId: String,
    val providerEventId: Long,
    val startAt: LocalDateTime,
    val title: String,
)

data class CalendarDiffResult(
    val create: List<CalendarEntry>,
    val update: List<Pair<CalendarLink, CalendarEntry>>,
    val delete: List<CalendarLink>,
)

object CalendarDiff {
    fun compute(desired: List<CalendarEntry>, links: List<CalendarLink>): CalendarDiffResult {
        val byId = links.associateBy { it.eventId }
        val desiredIds = desired.map { it.eventId }.toSet()
        val create = desired.filter { it.eventId !in byId }
        val update = desired.mapNotNull { entry ->
            val link = byId[entry.eventId] ?: return@mapNotNull null
            if (link.startAt != entry.startAt || link.title != entry.title) link to entry else null
        }
        val delete = links.filter { it.eventId !in desiredIds }
        return CalendarDiffResult(create, update, delete)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Same command. Expected: 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(domain): calendar diff for idempotent sync"
```

---

### Task 7: Persistence (DataStore + Room) and ScheduleRepository

**Files:**
- Create: `data/prefs/MethodConfigStore.kt`, `data/prefs/ThemeStore.kt`, `data/prefs/WidgetConfigStore.kt`, `data/prefs/OverdueConfigStore.kt`
- Create: `data/db/NobbyDatabase.kt`
- Create: `data/repo/ScheduleRepository.kt`, `data/repo/NotesRepository.kt`, `data/repo/ReminderRepository.kt`
- Create: `di/AppModule.kt`, `domain/model/ThemeSpec.kt`, `domain/model/WidgetConfig.kt`, `domain/Clock.kt`
- Test: `app/src/test/java/com/nobby/ring/data/ScheduleRepositoryTest.kt` (Robolectric)

**Interfaces:**
- Consumes: everything from Tasks 2–5.
- Produces:
  - `interface NobbyClock { fun now(): LocalDateTime; val zone: TimeZone }` + `class SystemClock : NobbyClock`.
  - `data class ThemeSpec(palette: Palette, mode: ThemeMode)`, `enum class Palette { ROSE, LAVENDER, MINT, OCEAN, GRAPHITE }`, `enum class ThemeMode { SYSTEM, LIGHT, DARK }`.
  - `data class WidgetConfig(mode: WidgetMode, rows: List<WidgetRow>)`, `enum class WidgetMode { SINGLE, MULTI }`, `enum class WidgetRow { NEXT_ANY, NEXT_REMOVE, NEXT_INSERT, OVERDUE, CYCLE_DAY }`.
  - `class MethodConfigStore { val config: Flow<ScheduleConfig?>; val onboardingDone: Flow<Boolean>; suspend fun save(config); suspend fun setOnboardingDone() }` (null until onboarding saved).
  - `class ThemeStore { val theme: Flow<ThemeSpec>; suspend fun save(theme) }`, `class WidgetConfigStore { val config: Flow<WidgetConfig>; suspend fun save(config) }`, `class OverdueConfigStore { val config: Flow<OverdueConfig>; suspend fun save(config) }`.
  - Room entities `EventLogEntity(eventId PK, kind, plannedAt, confirmedAt)`, `NoteEntity(date PK (ISO), text, updatedAt)`, `ReminderRuleEntity(id auto PK, target, offsetDays, timeHour?, timeMinute?, offsetHours?, importance, enabled)`, `CalendarLinkEntity(eventId PK, providerEventId, startAt, title)` with DAOs exposing `Flow<List<…>>`, `upsert`, `delete`.
  - `class ScheduleRepository { val state: Flow<ScheduleState?>; suspend fun confirm(eventId: String, confirmedAt: LocalDateTime, policy: ShiftPolicy); suspend fun skipPause(); suspend fun updateConfig(config: ScheduleConfig); suspend fun currentState(): ScheduleState? }`. Emits `null` until onboarding.
  - `class NotesRepository { fun note(date: LocalDate): Flow<String?>; fun datesWithNotes(): Flow<Set<LocalDate>>; suspend fun save(date, text) }`.
  - `class ReminderRepository { val rules: Flow<List<ReminderRule>>; suspend fun upsert(rule); suspend fun delete(id); suspend fun replaceAll(rules) }`.
  - `AppModule` provides Room db, DAOs, DataStore, `NobbyClock`.

- [ ] **Step 1: Write the failing repository test**

`app/src/test/java/com/nobby/ring/data/ScheduleRepositoryTest.kt`:
```kotlin
package com.nobby.ring.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.data.db.NobbyDatabase
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.engine.ShiftPolicy
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ScheduleRepositoryTest {
    private lateinit var db: NobbyDatabase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: ScheduleRepository
    private val fixedNow = LocalDateTime(2026, 11, 22, 23, 0)
    private val clock = object : NobbyClock {
        override fun now() = fixedNow
        override val zone = TimeZone.UTC
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NobbyDatabase::class.java).allowMainThreadQueries().build()
        dataStore = PreferenceDataStoreFactory.create { File(context.cacheDir, "test-${System.nanoTime()}.preferences_pb") }
        repo = ScheduleRepository(MethodConfigStore(dataStore), db.eventLogDao(), clock)
    }

    @After fun tearDown() = db.close()

    @Test
    fun `state is null before onboarding`() = runTest {
        assertNull(repo.state.first())
    }

    @Test
    fun `saving config produces a state with overdue remove`() = runTest {
        repo.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0)))
        val state = repo.state.filterNotNull().first()
        assertEquals(EventKind.REMOVE, state.overdue!!.event.kind)
        assertEquals(0, state.daysUntilNext)
    }

    @Test
    fun `confirm clears overdue and persists the log`() = runTest {
        repo.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0)))
        val before = repo.state.filterNotNull().first()
        repo.confirm(before.nextEvent!!.id, fixedNow, ShiftPolicy.KEEP)
        val after = repo.state.filterNotNull().first()
        assertNull(after.overdue)
        assertEquals(EventKind.INSERT, after.nextEvent!!.kind)
        assertEquals(1, db.eventLogDao().all().first().size)
    }

    @Test
    fun `skipPause turns next remove into swap`() = runTest {
        repo.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0)))
        repo.skipPause()
        assertEquals(EventKind.SWAP, repo.state.filterNotNull().first().nextEvent!!.kind)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.data.ScheduleRepositoryTest" --console=plain -q
```
Expected: compilation failure.

- [ ] **Step 3: Write the clock and small domain models**

`domain/Clock.kt`:
```kotlin
package com.nobby.ring.domain

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

interface NobbyClock {
    fun now(): LocalDateTime
    val zone: TimeZone
}

class SystemClock : NobbyClock {
    override val zone: TimeZone get() = TimeZone.currentSystemDefault()
    override fun now(): LocalDateTime = Clock.System.now().toLocalDateTime(zone)
}
```

`domain/model/ThemeSpec.kt`:
```kotlin
package com.nobby.ring.domain.model

enum class Palette { ROSE, LAVENDER, MINT, OCEAN, GRAPHITE }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
data class ThemeSpec(val palette: Palette = Palette.ROSE, val mode: ThemeMode = ThemeMode.SYSTEM)
```

`domain/model/WidgetConfig.kt`:
```kotlin
package com.nobby.ring.domain.model

enum class WidgetMode { SINGLE, MULTI }
enum class WidgetRow { NEXT_ANY, NEXT_REMOVE, NEXT_INSERT, OVERDUE, CYCLE_DAY }
data class WidgetConfig(
    val mode: WidgetMode = WidgetMode.SINGLE,
    val rows: List<WidgetRow> = listOf(WidgetRow.NEXT_ANY, WidgetRow.CYCLE_DAY, WidgetRow.OVERDUE),
) {
    companion object { const val MAX_ROWS = 3 }
}
```

- [ ] **Step 4: Write the DataStore stores**

`data/prefs/MethodConfigStore.kt`:
```kotlin
package com.nobby.ring.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import javax.inject.Inject

class MethodConfigStore @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private object Keys {
        val wear = intPreferencesKey("wear_days")
        val pause = intPreferencesKey("pause_days")
        val hour = intPreferencesKey("time_hour")
        val minute = intPreferencesKey("time_minute")
        val anchor = stringPreferencesKey("anchor")
        val horizon = intPreferencesKey("horizon_months")
        val skipPause = booleanPreferencesKey("skip_pause_once")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
    }

    val config: Flow<ScheduleConfig?> = dataStore.data.map { p ->
        val anchor = p[Keys.anchor] ?: return@map null
        ScheduleConfig(
            method = Method.ring(p[Keys.wear] ?: 21, p[Keys.pause] ?: 7, LocalTime(p[Keys.hour] ?: 21, p[Keys.minute] ?: 0)),
            anchor = LocalDateTime.parse(anchor),
            horizonMonths = p[Keys.horizon] ?: 12,
            skipPauseOnce = p[Keys.skipPause] ?: false,
        )
    }

    val onboardingDone: Flow<Boolean> = dataStore.data.map { it[Keys.onboardingDone] ?: false }

    suspend fun save(config: ScheduleConfig) {
        dataStore.edit { p ->
            p[Keys.wear] = config.method.wearDays
            p[Keys.pause] = config.method.pauseDays
            p[Keys.hour] = config.method.timeOfDay.hour
            p[Keys.minute] = config.method.timeOfDay.minute
            p[Keys.anchor] = config.anchor.toString()
            p[Keys.horizon] = config.horizonMonths
            p[Keys.skipPause] = config.skipPauseOnce
        }
    }

    suspend fun setOnboardingDone() {
        dataStore.edit { it[Keys.onboardingDone] = true }
    }
}
```

`data/prefs/ThemeStore.kt`:
```kotlin
package com.nobby.ring.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.ThemeMode
import com.nobby.ring.domain.model.ThemeSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ThemeStore @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private val paletteKey = stringPreferencesKey("theme_palette")
    private val modeKey = stringPreferencesKey("theme_mode")

    val theme: Flow<ThemeSpec> = dataStore.data.map { p ->
        ThemeSpec(
            palette = p[paletteKey]?.let { runCatching { Palette.valueOf(it) }.getOrNull() } ?: Palette.ROSE,
            mode = p[modeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        )
    }

    suspend fun save(theme: ThemeSpec) {
        dataStore.edit { p ->
            p[paletteKey] = theme.palette.name
            p[modeKey] = theme.mode.name
        }
    }
}
```

`data/prefs/WidgetConfigStore.kt`:
```kotlin
package com.nobby.ring.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.domain.model.WidgetMode
import com.nobby.ring.domain.model.WidgetRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class WidgetConfigStore @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private val modeKey = stringPreferencesKey("widget_mode")
    private val rowsKey = stringPreferencesKey("widget_rows")

    val config: Flow<WidgetConfig> = dataStore.data.map { p ->
        val defaults = WidgetConfig()
        WidgetConfig(
            mode = p[modeKey]?.let { runCatching { WidgetMode.valueOf(it) }.getOrNull() } ?: defaults.mode,
            rows = p[rowsKey]?.split(',')?.mapNotNull { runCatching { WidgetRow.valueOf(it) }.getOrNull() }?.ifEmpty { null }
                ?: defaults.rows,
        )
    }

    suspend fun save(config: WidgetConfig) {
        dataStore.edit { p ->
            p[modeKey] = config.mode.name
            p[rowsKey] = config.rows.take(WidgetConfig.MAX_ROWS).joinToString(",") { it.name }
        }
    }
}
```

`data/prefs/OverdueConfigStore.kt`:
```kotlin
package com.nobby.ring.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.nobby.ring.domain.model.OverdueConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OverdueConfigStore @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private val repeatKey = intPreferencesKey("overdue_repeat_hours")
    private val snoozeKey = intPreferencesKey("overdue_snooze_hours")

    val config: Flow<OverdueConfig> = dataStore.data.map { p ->
        OverdueConfig(p[repeatKey] ?: 2, p[snoozeKey] ?: 1)
    }

    suspend fun save(config: OverdueConfig) {
        dataStore.edit { p ->
            p[repeatKey] = config.repeatEveryHours
            p[snoozeKey] = config.snoozeHours
        }
    }
}
```

- [ ] **Step 5: Write the Room database**

`data/db/NobbyDatabase.kt`:
```kotlin
package com.nobby.ring.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "event_log")
data class EventLogEntity(
    @PrimaryKey val eventId: String,
    val kind: String,
    val plannedAt: String,
    val confirmedAt: String,
)

@Entity(tableName = "note")
data class NoteEntity(
    @PrimaryKey val date: String,
    val text: String,
    val updatedAt: String,
)

@Entity(tableName = "reminder_rule")
data class ReminderRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val target: String,
    val offsetDays: Int,
    val timeHour: Int?,
    val timeMinute: Int?,
    val offsetHours: Int?,
    val importance: String,
    val enabled: Boolean,
)

@Entity(tableName = "calendar_link")
data class CalendarLinkEntity(
    @PrimaryKey val eventId: String,
    val providerEventId: Long,
    val startAt: String,
    val title: String,
)

@Dao
interface EventLogDao {
    @Query("SELECT * FROM event_log ORDER BY plannedAt") fun all(): Flow<List<EventLogEntity>>
    @Query("SELECT * FROM event_log ORDER BY plannedAt") suspend fun allOnce(): List<EventLogEntity>
    @Upsert suspend fun upsert(entities: List<EventLogEntity>)
    @Query("DELETE FROM event_log") suspend fun clear()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM note WHERE date = :date") fun byDate(date: String): Flow<NoteEntity?>
    @Query("SELECT date FROM note WHERE text != ''") fun datesWithNotes(): Flow<List<String>>
    @Upsert suspend fun upsert(note: NoteEntity)
    @Query("DELETE FROM note WHERE date = :date") suspend fun delete(date: String)
}

@Dao
interface ReminderRuleDao {
    @Query("SELECT * FROM reminder_rule ORDER BY id") fun all(): Flow<List<ReminderRuleEntity>>
    @Query("SELECT * FROM reminder_rule ORDER BY id") suspend fun allOnce(): List<ReminderRuleEntity>
    @Upsert suspend fun upsert(rule: ReminderRuleEntity): Long
    @Query("DELETE FROM reminder_rule WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM reminder_rule") suspend fun clear()
}

@Dao
interface CalendarLinkDao {
    @Query("SELECT * FROM calendar_link") suspend fun allOnce(): List<CalendarLinkEntity>
    @Upsert suspend fun upsert(links: List<CalendarLinkEntity>)
    @Query("DELETE FROM calendar_link WHERE eventId IN (:ids)") suspend fun delete(ids: List<String>)
    @Query("DELETE FROM calendar_link") suspend fun clear()
}

@Database(
    entities = [EventLogEntity::class, NoteEntity::class, ReminderRuleEntity::class, CalendarLinkEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class NobbyDatabase : RoomDatabase() {
    abstract fun eventLogDao(): EventLogDao
    abstract fun noteDao(): NoteDao
    abstract fun reminderRuleDao(): ReminderRuleDao
    abstract fun calendarLinkDao(): CalendarLinkDao
}
```

- [ ] **Step 6: Write the repositories**

`data/repo/ScheduleRepository.kt`:
```kotlin
package com.nobby.ring.data.repo

import com.nobby.ring.data.db.EventLogDao
import com.nobby.ring.data.db.EventLogEntity
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.engine.ConfirmAction
import com.nobby.ring.domain.engine.CycleCalculator
import com.nobby.ring.domain.engine.OverdueCalculator
import com.nobby.ring.domain.engine.ScheduleEngine
import com.nobby.ring.domain.engine.ShiftPolicy
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.ScheduleState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepository @Inject constructor(
    private val configStore: MethodConfigStore,
    private val eventLogDao: EventLogDao,
    private val clock: NobbyClock,
) {
    val state: Flow<ScheduleState?> = combine(configStore.config, eventLogDao.all()) { config, logEntities ->
        config?.let { build(it, logEntities.map(::toDomain), clock.now()) }
    }

    suspend fun currentState(): ScheduleState? = state.first()

    suspend fun updateConfig(config: ScheduleConfig) = configStore.save(config)

    suspend fun skipPause() {
        val config = configStore.config.first() ?: return
        configStore.save(config.copy(skipPauseOnce = true))
    }

    suspend fun cancelSkipPause() {
        val config = configStore.config.first() ?: return
        configStore.save(config.copy(skipPauseOnce = false))
    }

    suspend fun confirm(eventId: String, confirmedAt: LocalDateTime, policy: ShiftPolicy) {
        val config = configStore.config.first() ?: return
        val logs = eventLogDao.allOnce().map(::toDomain)
        val event = ScheduleEngine.generate(config).firstOrNull { it.id == eventId } ?: return
        val result = ConfirmAction.confirm(config, event, logs, confirmedAt, policy)
        eventLogDao.upsert(result.logs.map(::toEntity))
        configStore.save(result.config)
    }

    fun build(config: ScheduleConfig, logs: List<EventLog>, now: LocalDateTime): ScheduleState {
        val events = ScheduleEngine.generate(config)
        return ScheduleState(
            config = config,
            events = events,
            logs = logs,
            now = now,
            position = CycleCalculator.positionAt(config, logs, now),
            nextEvent = OverdueCalculator.nextPending(events, logs),
            overdue = OverdueCalculator.overdue(config, events, logs, now),
        )
    }

    private fun toDomain(e: EventLogEntity) = EventLog(
        e.eventId, EventKind.valueOf(e.kind), LocalDateTime.parse(e.plannedAt), LocalDateTime.parse(e.confirmedAt),
    )

    private fun toEntity(l: EventLog) = EventLogEntity(l.eventId, l.kind.name, l.plannedAt.toString(), l.confirmedAt.toString())
}
```

`data/repo/NotesRepository.kt`:
```kotlin
package com.nobby.ring.data.repo

import com.nobby.ring.data.db.NoteDao
import com.nobby.ring.data.db.NoteEntity
import com.nobby.ring.domain.NobbyClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import javax.inject.Inject

class NotesRepository @Inject constructor(private val dao: NoteDao, private val clock: NobbyClock) {
    fun note(date: LocalDate): Flow<String?> = dao.byDate(date.toString()).map { it?.text }
    fun datesWithNotes(): Flow<Set<LocalDate>> = dao.datesWithNotes().map { list -> list.map(LocalDate::parse).toSet() }
    suspend fun save(date: LocalDate, text: String) {
        if (text.isBlank()) dao.delete(date.toString())
        else dao.upsert(NoteEntity(date.toString(), text.trim(), clock.now().toString()))
    }
}
```

`data/repo/ReminderRepository.kt`:
```kotlin
package com.nobby.ring.data.repo

import com.nobby.ring.data.db.ReminderRuleDao
import com.nobby.ring.data.db.ReminderRuleEntity
import com.nobby.ring.domain.model.Importance
import com.nobby.ring.domain.model.ReminderRule
import com.nobby.ring.domain.model.ReminderTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalTime
import javax.inject.Inject

class ReminderRepository @Inject constructor(private val dao: ReminderRuleDao) {
    val rules: Flow<List<ReminderRule>> = dao.all().map { it.map(::toDomain) }
    suspend fun rulesOnce(): List<ReminderRule> = dao.allOnce().map(::toDomain)
    suspend fun upsert(rule: ReminderRule): Long = dao.upsert(toEntity(rule))
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun replaceAll(rules: List<ReminderRule>) {
        dao.clear()
        rules.forEach { dao.upsert(toEntity(it.copy(id = 0))) }
    }

    private fun toDomain(e: ReminderRuleEntity) = ReminderRule(
        id = e.id,
        target = ReminderTarget.valueOf(e.target),
        offsetDays = e.offsetDays,
        timeOfDay = if (e.timeHour != null && e.timeMinute != null) LocalTime(e.timeHour, e.timeMinute) else null,
        offsetHours = e.offsetHours,
        importance = Importance.valueOf(e.importance),
        enabled = e.enabled,
    )

    private fun toEntity(r: ReminderRule) = ReminderRuleEntity(
        id = r.id,
        target = r.target.name,
        offsetDays = r.offsetDays,
        timeHour = r.timeOfDay?.hour,
        timeMinute = r.timeOfDay?.minute,
        offsetHours = r.offsetHours,
        importance = r.importance.name,
        enabled = r.enabled,
    )
}
```

- [ ] **Step 7: Write the Hilt module**

`di/AppModule.kt`:
```kotlin
package com.nobby.ring.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.nobby.ring.data.db.CalendarLinkDao
import com.nobby.ring.data.db.EventLogDao
import com.nobby.ring.data.db.NobbyDatabase
import com.nobby.ring.data.db.NoteDao
import com.nobby.ring.data.db.ReminderRuleDao
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("nobby_prefs") }

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): NobbyDatabase =
        Room.databaseBuilder(context, NobbyDatabase::class.java, "nobby.db").build()

    @Provides fun eventLogDao(db: NobbyDatabase): EventLogDao = db.eventLogDao()
    @Provides fun noteDao(db: NobbyDatabase): NoteDao = db.noteDao()
    @Provides fun reminderRuleDao(db: NobbyDatabase): ReminderRuleDao = db.reminderRuleDao()
    @Provides fun calendarLinkDao(db: NobbyDatabase): CalendarLinkDao = db.calendarLinkDao()

    @Provides @Singleton fun clock(): NobbyClock = SystemClock()
}
```

- [ ] **Step 8: Run tests to verify they pass**

```bash
./gradlew.bat :app:testDebugUnitTest --console=plain -q
```
Expected: all tests pass (the Robolectric test downloads the android-all jar on first run; allow a few minutes).

- [ ] **Step 9: Commit**

```bash
git add -A && git commit -m "feat(data): DataStore prefs, Room db and schedule repository"
```

---

### Task 8: Theme, strings and navigation shell

**Files:**
- Create: `ui/theme/Palettes.kt`, `ui/theme/Theme.kt`, `ui/theme/Type.kt`
- Create: `ui/navigation/Routes.kt`, `ui/navigation/NobbyNavHost.kt`
- Create: `ui/components/RingProgress.kt`, `ui/components/NumberStepper.kt`, `ui/components/TimePickerField.kt`, `ui/components/DatePickerField.kt`, `ui/components/EventCard.kt`
- Create: `ui/MainViewModel.kt`
- Modify: `MainActivity.kt`, `res/values/strings.xml`, `res/values-de/strings.xml`

**Interfaces:**
- Produces:
  - `fun paletteSchemes(palette: Palette): Pair<ColorScheme, ColorScheme>` (light, dark) and `fun Palette.accent(dark: Boolean): Color` (used by the widget).
  - `@Composable fun NobbyTheme(theme: ThemeSpec, content)`.
  - `object Routes { ONBOARDING, HOME, CALENDAR, HISTORY, SETTINGS, SETTINGS_SCHEDULE, SETTINGS_REMINDERS, SETTINGS_APPEARANCE, SETTINGS_CALENDAR, SETTINGS_CALENDAR_CLEANUP, SETTINGS_WIDGET }` string constants.
  - `@Composable fun NobbyNavHost(startDestination: String)` with a bottom bar (Home / Calendar / History / Settings) shown on top-level routes.
  - `@Composable fun RingProgress(progress: Float, modifier, trackColor, progressColor, strokeWidth, content)`.
  - `@Composable fun NumberStepper(value: Int, onValueChange, range: IntRange, label: String)`.
  - `@Composable fun TimePickerField(time: LocalTime, onTimeChange, label)`, `@Composable fun DatePickerField(date: LocalDate, onDateChange, label)`.
  - `@Composable fun EventCard(kind: EventKind, plannedAt: LocalDateTime, overdue: Boolean, onDone: (() -> Unit)?)`.
  - `@Composable fun EventKind.label(): String` + `fun Importance.label()`, `fun formatDate(LocalDate)`, `fun formatDateTime(LocalDateTime)` in `ui/components/Formatting.kt`.
  - `MainViewModel` exposing `theme: StateFlow<ThemeSpec>` and `startDestination: StateFlow<String?>` (null while loading).

- [ ] **Step 1: Add all strings (EN) — this file is extended again in later tasks only where noted**

`res/values/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">NoBby</string>

    <!-- navigation -->
    <string name="nav_home">Status</string>
    <string name="nav_calendar">Calendar</string>
    <string name="nav_history">History</string>
    <string name="nav_settings">Settings</string>
    <string name="action_back">Back</string>
    <string name="action_next">Next</string>
    <string name="action_skip">Skip</string>
    <string name="action_done">Done</string>
    <string name="action_save">Save</string>
    <string name="action_cancel">Cancel</string>
    <string name="action_delete">Delete</string>
    <string name="action_finish">Finish</string>

    <!-- events -->
    <string name="event_remove">Remove ring</string>
    <string name="event_insert">Insert new ring</string>
    <string name="event_swap">Swap ring</string>
    <string name="phase_wear">Ring in</string>
    <string name="phase_pause">Ring-free break</string>
    <string name="cycle_day">Day %1$d of %2$d</string>
    <string name="phase_day">%1$s · day %2$d of %3$d</string>

    <!-- home -->
    <string name="home_days_remaining">days remaining</string>
    <string name="home_day_remaining">day remaining</string>
    <string name="home_today">today</string>
    <string name="home_overdue_days">days overdue</string>
    <string name="home_next_on">%1$s on %2$s</string>
    <string name="home_mark_done">Done ✓</string>
    <string name="home_skip_pause">Skip break</string>
    <string name="home_skip_pause_cancel">Undo skip break</string>
    <string name="home_overdue_title">Overdue: %1$s</string>
    <string name="home_overdue_body">Planned for %1$s. Confirm as soon as you have done it.</string>
    <string name="home_overdue_safety">More than the safe limit has passed. Use backup contraception and ask your doctor.</string>
    <string name="home_snooze">Snooze</string>
    <string name="confirm_title">You were late</string>
    <string name="confirm_body">This was planned for %1$s. Should the following dates move by %2$s, or stay as planned?</string>
    <string name="confirm_shift">Shift dates</string>
    <string name="confirm_keep">Keep schedule</string>
    <string name="confirm_when">When did you do it?</string>
    <string name="confirm_now">Now</string>

    <!-- onboarding -->
    <string name="onb_welcome_title">Hi, I\'m NoBby</string>
    <string name="onb_welcome_body">I remind you when to remove and insert your ring, keep your calendar in sync and nag you if you forget.</string>
    <string name="onb_schedule_title">Your cycle</string>
    <string name="onb_wear_days">Ring in for (days)</string>
    <string name="onb_pause_days">Break (days)</string>
    <string name="onb_time_of_day">Time of day</string>
    <string name="onb_safety_wear">More than 28 days of wear may reduce protection.</string>
    <string name="onb_safety_pause">A break longer than 7 days may reduce protection.</string>
    <string name="onb_anchor_title">When did the current ring go in?</string>
    <string name="onb_anchor_date">Date</string>
    <string name="onb_anchor_time">Time</string>
    <string name="onb_reminders_title">Reminders</string>
    <string name="onb_reminders_body">You can change these any time in Settings.</string>
    <string name="onb_notifications_permission">Allow notifications</string>
    <string name="onb_look_title">Make it yours</string>
    <string name="onb_calendar_connect">Connect a calendar</string>

    <!-- reminders -->
    <string name="reminder_day_before">%1$d day(s) before at %2$s</string>
    <string name="reminder_same_day">Same day at %1$s</string>
    <string name="reminder_hours_before">%1$d hour(s) before</string>
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
    <string name="reminder_enabled">Enabled</string>
    <string name="overdue_repeat">Repeat overdue alert every (hours)</string>
    <string name="overdue_snooze">Snooze length (hours)</string>
    <string name="exact_alarm_warning">Exact alarms are disabled. Reminders may be delayed. Tap to allow.</string>

    <!-- notifications -->
    <string name="channel_low">Quiet reminders</string>
    <string name="channel_normal">Reminders</string>
    <string name="channel_high">Important reminders</string>
    <string name="channel_urgent">Overdue alerts</string>
    <string name="notif_reminder_title">%1$s</string>
    <string name="notif_reminder_body">Planned for %1$s</string>
    <string name="notif_overdue_title">Overdue: %1$s</string>
    <string name="notif_overdue_body">Planned for %1$s. Tap Done once you have done it.</string>
    <string name="notif_overdue_safety">Safe limit exceeded — use backup contraception.</string>

    <!-- calendar -->
    <string name="calendar_notes_title">Notes – %1$s</string>
    <string name="calendar_notes_hint">Write here</string>

    <!-- history -->
    <string name="history_empty">No confirmed events yet.</string>
    <string name="history_on_time">on time</string>
    <string name="history_late">%1$s late</string>
    <string name="history_early">%1$s early</string>

    <!-- settings -->
    <string name="settings_schedule">Cycle & schedule</string>
    <string name="settings_reminders">Reminders</string>
    <string name="settings_appearance">Appearance</string>
    <string name="settings_calendar">Calendar sync</string>
    <string name="settings_widget">Widget</string>
    <string name="settings_about">About</string>
    <string name="settings_about_body">NoBby %1$s · This app is a reminder tool, not medical advice. Follow your product leaflet and your doctor.</string>
    <string name="schedule_preview_title">New schedule preview</string>
    <string name="schedule_preview_body">The next dates will be:</string>
    <string name="schedule_apply">Apply and update calendar</string>
    <string name="schedule_horizon">Plan ahead (months)</string>
    <string name="appearance_palette">Colour</string>
    <string name="appearance_mode">Mode</string>
    <string name="mode_system">System</string>
    <string name="mode_light">Light</string>
    <string name="mode_dark">Dark</string>
    <string name="palette_rose">Rose</string>
    <string name="palette_lavender">Lavender</string>
    <string name="palette_mint">Mint</string>
    <string name="palette_ocean">Ocean</string>
    <string name="palette_graphite">Graphite</string>
    <string name="calendar_sync_enable">Sync to a calendar</string>
    <string name="calendar_sync_choose">Choose calendar</string>
    <string name="calendar_sync_permission">Calendar permission is needed to sync.</string>
    <string name="calendar_sync_now">Sync now</string>
    <string name="calendar_sync_last">Last sync: %1$s</string>
    <string name="calendar_cleanup">Clean up old entries</string>
    <string name="cleanup_query">Title contains / regex</string>
    <string name="cleanup_from">From</string>
    <string name="cleanup_to">To</string>
    <string name="cleanup_search">Search</string>
    <string name="cleanup_delete_selected">Delete selected (%1$d)</string>
    <string name="cleanup_none">No matching entries.</string>
    <string name="widget_mode">Layout</string>
    <string name="widget_mode_single">One big countdown</string>
    <string name="widget_mode_multi">Up to three rows</string>
    <string name="widget_rows">Rows</string>
    <string name="widget_row_next_any">Next action</string>
    <string name="widget_row_next_remove">Next removal</string>
    <string name="widget_row_next_insert">Next insertion</string>
    <string name="widget_row_overdue">Overdue</string>
    <string name="widget_row_cycle_day">Cycle day</string>
    <string name="widget_description">Countdown to your next ring action</string>
    <string name="widget_none">All done</string>
    <string name="widget_in_days">in %1$d d</string>
    <string name="widget_overdue_days">%1$d d overdue</string>
</resources>
```

`res/values-de/strings.xml` — the same keys, German:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">NoBby</string>
    <string name="nav_home">Status</string>
    <string name="nav_calendar">Kalender</string>
    <string name="nav_history">Verlauf</string>
    <string name="nav_settings">Einstellungen</string>
    <string name="action_back">Zurück</string>
    <string name="action_next">Weiter</string>
    <string name="action_skip">Überspringen</string>
    <string name="action_done">Erledigt</string>
    <string name="action_save">Speichern</string>
    <string name="action_cancel">Abbrechen</string>
    <string name="action_delete">Löschen</string>
    <string name="action_finish">Fertig</string>
    <string name="event_remove">Ring entfernen</string>
    <string name="event_insert">Neuen Ring einsetzen</string>
    <string name="event_swap">Ring wechseln</string>
    <string name="phase_wear">Ring drin</string>
    <string name="phase_pause">Ringfreie Pause</string>
    <string name="cycle_day">Tag %1$d von %2$d</string>
    <string name="phase_day">%1$s · Tag %2$d von %3$d</string>
    <string name="home_days_remaining">verbleibende Tage</string>
    <string name="home_day_remaining">verbleibender Tag</string>
    <string name="home_today">heute</string>
    <string name="home_overdue_days">Tage überfällig</string>
    <string name="home_next_on">%1$s am %2$s</string>
    <string name="home_mark_done">Erledigt ✓</string>
    <string name="home_skip_pause">Pause auslassen</string>
    <string name="home_skip_pause_cancel">Pause doch einlegen</string>
    <string name="home_overdue_title">Überfällig: %1$s</string>
    <string name="home_overdue_body">Geplant für %1$s. Bitte bestätigen, sobald du es gemacht hast.</string>
    <string name="home_overdue_safety">Die sichere Frist ist überschritten. Nutze zusätzlich Verhütung und frag deine Ärztin.</string>
    <string name="home_snooze">Später</string>
    <string name="confirm_title">Du warst spät dran</string>
    <string name="confirm_body">Geplant war %1$s. Sollen die folgenden Termine um %2$s verschoben werden oder bleiben?</string>
    <string name="confirm_shift">Termine verschieben</string>
    <string name="confirm_keep">Plan beibehalten</string>
    <string name="confirm_when">Wann hast du es gemacht?</string>
    <string name="confirm_now">Jetzt</string>
    <string name="onb_welcome_title">Hi, ich bin NoBby</string>
    <string name="onb_welcome_body">Ich erinnere dich ans Entfernen und Einsetzen deines Rings, halte deinen Kalender aktuell und nerve, wenn du es vergisst.</string>
    <string name="onb_schedule_title">Dein Zyklus</string>
    <string name="onb_wear_days">Ring drin für (Tage)</string>
    <string name="onb_pause_days">Pause (Tage)</string>
    <string name="onb_time_of_day">Uhrzeit</string>
    <string name="onb_safety_wear">Mehr als 28 Tage Tragezeit können den Schutz verringern.</string>
    <string name="onb_safety_pause">Eine Pause über 7 Tage kann den Schutz verringern.</string>
    <string name="onb_anchor_title">Wann wurde der aktuelle Ring eingesetzt?</string>
    <string name="onb_anchor_date">Datum</string>
    <string name="onb_anchor_time">Uhrzeit</string>
    <string name="onb_reminders_title">Erinnerungen</string>
    <string name="onb_reminders_body">Das kannst du jederzeit in den Einstellungen ändern.</string>
    <string name="onb_notifications_permission">Benachrichtigungen erlauben</string>
    <string name="onb_look_title">Mach es zu deinem</string>
    <string name="onb_calendar_connect">Kalender verbinden</string>
    <string name="reminder_day_before">%1$d Tag(e) vorher um %2$s</string>
    <string name="reminder_same_day">Am selben Tag um %1$s</string>
    <string name="reminder_hours_before">%1$d Stunde(n) vorher</string>
    <string name="reminder_target_any">Alle Termine</string>
    <string name="reminder_target_remove">Nur Entfernen</string>
    <string name="reminder_target_insert">Nur Einsetzen</string>
    <string name="importance_low">Leise</string>
    <string name="importance_normal">Normal</string>
    <string name="importance_high">Laut</string>
    <string name="importance_urgent">Dringend</string>
    <string name="reminder_add">Erinnerung hinzufügen</string>
    <string name="reminder_edit">Erinnerung bearbeiten</string>
    <string name="reminder_mode_days">Tage vorher</string>
    <string name="reminder_mode_hours">Stunden vorher</string>
    <string name="reminder_enabled">Aktiv</string>
    <string name="overdue_repeat">Überfällig-Alarm wiederholen alle (Stunden)</string>
    <string name="overdue_snooze">Später-Dauer (Stunden)</string>
    <string name="exact_alarm_warning">Exakte Alarme sind deaktiviert. Erinnerungen können sich verzögern. Tippen zum Erlauben.</string>
    <string name="channel_low">Leise Erinnerungen</string>
    <string name="channel_normal">Erinnerungen</string>
    <string name="channel_high">Wichtige Erinnerungen</string>
    <string name="channel_urgent">Überfällig-Alarme</string>
    <string name="notif_reminder_title">%1$s</string>
    <string name="notif_reminder_body">Geplant für %1$s</string>
    <string name="notif_overdue_title">Überfällig: %1$s</string>
    <string name="notif_overdue_body">Geplant für %1$s. Tippe auf Erledigt, sobald du es gemacht hast.</string>
    <string name="notif_overdue_safety">Sichere Frist überschritten – nutze zusätzlich Verhütung.</string>
    <string name="calendar_notes_title">Hinweise – %1$s</string>
    <string name="calendar_notes_hint">Hier schreiben</string>
    <string name="history_empty">Noch keine bestätigten Termine.</string>
    <string name="history_on_time">pünktlich</string>
    <string name="history_late">%1$s zu spät</string>
    <string name="history_early">%1$s zu früh</string>
    <string name="settings_schedule">Zyklus &amp; Zeitplan</string>
    <string name="settings_reminders">Erinnerungen</string>
    <string name="settings_appearance">Aussehen</string>
    <string name="settings_calendar">Kalender-Sync</string>
    <string name="settings_widget">Widget</string>
    <string name="settings_about">Über</string>
    <string name="settings_about_body">NoBby %1$s · Diese App ist eine Erinnerungshilfe, keine medizinische Beratung. Halte dich an den Beipackzettel und deine Ärztin.</string>
    <string name="schedule_preview_title">Vorschau neuer Zeitplan</string>
    <string name="schedule_preview_body">Die nächsten Termine wären:</string>
    <string name="schedule_apply">Übernehmen und Kalender aktualisieren</string>
    <string name="schedule_horizon">Vorausplanen (Monate)</string>
    <string name="appearance_palette">Farbe</string>
    <string name="appearance_mode">Modus</string>
    <string name="mode_system">System</string>
    <string name="mode_light">Hell</string>
    <string name="mode_dark">Dunkel</string>
    <string name="palette_rose">Rosé</string>
    <string name="palette_lavender">Lavendel</string>
    <string name="palette_mint">Minze</string>
    <string name="palette_ocean">Ozean</string>
    <string name="palette_graphite">Graphit</string>
    <string name="calendar_sync_enable">Mit Kalender synchronisieren</string>
    <string name="calendar_sync_choose">Kalender wählen</string>
    <string name="calendar_sync_permission">Für den Sync wird die Kalender-Berechtigung benötigt.</string>
    <string name="calendar_sync_now">Jetzt synchronisieren</string>
    <string name="calendar_sync_last">Letzter Sync: %1$s</string>
    <string name="calendar_cleanup">Alte Einträge aufräumen</string>
    <string name="cleanup_query">Titel enthält / Regex</string>
    <string name="cleanup_from">Von</string>
    <string name="cleanup_to">Bis</string>
    <string name="cleanup_search">Suchen</string>
    <string name="cleanup_delete_selected">Ausgewählte löschen (%1$d)</string>
    <string name="cleanup_none">Keine passenden Einträge.</string>
    <string name="widget_mode">Layout</string>
    <string name="widget_mode_single">Ein großer Countdown</string>
    <string name="widget_mode_multi">Bis zu drei Zeilen</string>
    <string name="widget_rows">Zeilen</string>
    <string name="widget_row_next_any">Nächste Aktion</string>
    <string name="widget_row_next_remove">Nächstes Entfernen</string>
    <string name="widget_row_next_insert">Nächstes Einsetzen</string>
    <string name="widget_row_overdue">Überfällig</string>
    <string name="widget_row_cycle_day">Zyklustag</string>
    <string name="widget_description">Countdown bis zur nächsten Ring-Aktion</string>
    <string name="widget_none">Alles erledigt</string>
    <string name="widget_in_days">in %1$d T</string>
    <string name="widget_overdue_days">%1$d T überfällig</string>
</resources>
```

- [ ] **Step 2: Write palettes and theme**

`ui/theme/Palettes.kt`:
```kotlin
package com.nobby.ring.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.nobby.ring.domain.model.Palette

private data class Seed(val primary: Color, val primaryDark: Color, val secondary: Color, val tertiary: Color, val bgLight: Color, val bgDark: Color)

private val seeds = mapOf(
    Palette.ROSE to Seed(Color(0xFFD81B60), Color(0xFFF48FB1), Color(0xFFAD1457), Color(0xFFFF8A80), Color(0xFFFFF1F5), Color(0xFF1C1114)),
    Palette.LAVENDER to Seed(Color(0xFF7E57C2), Color(0xFFB39DDB), Color(0xFF5E35B1), Color(0xFF9FA8DA), Color(0xFFF5F2FB), Color(0xFF15121C)),
    Palette.MINT to Seed(Color(0xFF26A69A), Color(0xFF80CBC4), Color(0xFF00897B), Color(0xFFA5D6A7), Color(0xFFEFFAF7), Color(0xFF0F1A18)),
    Palette.OCEAN to Seed(Color(0xFF1E88E5), Color(0xFF90CAF9), Color(0xFF1565C0), Color(0xFF80DEEA), Color(0xFFEEF5FC), Color(0xFF0F151C)),
    Palette.GRAPHITE to Seed(Color(0xFF455A64), Color(0xFFB0BEC5), Color(0xFF263238), Color(0xFF90A4AE), Color(0xFFF4F6F7), Color(0xFF121415)),
)

fun Palette.accent(dark: Boolean): Color = seeds.getValue(this).let { if (dark) it.primaryDark else it.primary }

fun paletteSchemes(palette: Palette): Pair<ColorScheme, ColorScheme> {
    val s = seeds.getValue(palette)
    val light = lightColorScheme(
        primary = s.primary,
        onPrimary = Color.White,
        primaryContainer = s.primaryDark.copy(alpha = 0.35f),
        onPrimaryContainer = s.secondary,
        secondary = s.secondary,
        tertiary = s.tertiary,
        background = s.bgLight,
        surface = s.bgLight,
        surfaceVariant = s.primaryDark.copy(alpha = 0.18f),
        error = Color(0xFFB3261E),
    )
    val dark = darkColorScheme(
        primary = s.primaryDark,
        onPrimary = s.bgDark,
        primaryContainer = s.secondary.copy(alpha = 0.45f),
        onPrimaryContainer = s.primaryDark,
        secondary = s.primaryDark,
        tertiary = s.tertiary,
        background = s.bgDark,
        surface = s.bgDark,
        surfaceVariant = s.primaryDark.copy(alpha = 0.14f),
        error = Color(0xFFF2B8B5),
    )
    return light to dark
}
```

`ui/theme/Type.kt`:
```kotlin
package com.nobby.ring.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val NobbyTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontSize = 96.sp, fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}
```

`ui/theme/Theme.kt`:
```kotlin
package com.nobby.ring.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
    val (light, dark) = paletteSchemes(theme.palette)
    MaterialTheme(
        colorScheme = if (theme.isDark()) dark else light,
        typography = NobbyTypography,
        content = content,
    )
}
```

- [ ] **Step 3: Write shared components**

`ui/components/Formatting.kt`:
```kotlin
package com.nobby.ring.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nobby.ring.R
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Importance
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.PhaseKind
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaLocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Duration

@Composable fun EventKind.label(): String = stringResource(
    when (this) {
        EventKind.REMOVE -> R.string.event_remove
        EventKind.INSERT -> R.string.event_insert
        EventKind.SWAP -> R.string.event_swap
    },
)

@Composable fun PhaseKind.label(): String = stringResource(if (this == PhaseKind.WEAR) R.string.phase_wear else R.string.phase_pause)

@Composable fun Importance.label(): String = stringResource(
    when (this) {
        Importance.LOW -> R.string.importance_low
        Importance.NORMAL -> R.string.importance_normal
        Importance.HIGH -> R.string.importance_high
        Importance.URGENT -> R.string.importance_urgent
    },
)

@Composable fun Palette.label(): String = stringResource(
    when (this) {
        Palette.ROSE -> R.string.palette_rose
        Palette.LAVENDER -> R.string.palette_lavender
        Palette.MINT -> R.string.palette_mint
        Palette.OCEAN -> R.string.palette_ocean
        Palette.GRAPHITE -> R.string.palette_graphite
    },
)

private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

fun formatDate(date: LocalDate): String = date.toJavaLocalDate().format(dateFmt)
fun formatTime(time: LocalTime): String = time.toJavaLocalTime().format(timeFmt)
fun formatDateTime(dt: LocalDateTime): String = "${formatDate(dt.date)} ${formatTime(dt.time)}"

fun formatDuration(d: Duration): String {
    val days = d.inWholeDays
    val hours = d.inWholeHours % 24
    val minutes = d.inWholeMinutes % 60
    return buildList {
        if (days > 0) add("${days}d")
        if (hours > 0) add("${hours}h")
        if (days == 0L && minutes > 0) add("${minutes}min")
    }.joinToString(" ").ifEmpty { "0min" }
}
```

`ui/components/RingProgress.kt`:
```kotlin
package com.nobby.ring.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun RingProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 14.dp,
    content: @Composable () -> Unit = {},
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2
            val arcSize = Size(size.width - strokeWidth.toPx(), size.height - strokeWidth.toPx())
            drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), arcSize, style = stroke)
            drawArc(progressColor, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = stroke)
        }
        content()
    }
}
```

`ui/components/NumberStepper.kt`:
```kotlin
package com.nobby.ring.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun NumberStepper(value: Int, onValueChange: (Int) -> Unit, range: IntRange, label: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FilledTonalIconButton(onClick = { onValueChange((value - 1).coerceIn(range)) }, enabled = value > range.first) {
            Icon(Icons.Default.Remove, contentDescription = null)
        }
        Text("$value", Modifier.width(48.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
        FilledTonalIconButton(onClick = { onValueChange((value + 1).coerceIn(range)) }, enabled = value < range.last) {
            Icon(Icons.Default.Add, contentDescription = null)
        }
    }
}
```

`ui/components/TimePickerField.kt`:
```kotlin
package com.nobby.ring.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nobby.ring.R
import kotlinx.datetime.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerField(time: LocalTime, onTimeChange: (LocalTime) -> Unit, label: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier) { Text("$label: ${formatTime(time)}") }
    if (open) {
        val state = rememberTimePickerState(time.hour, time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = { onTimeChange(LocalTime(state.hour, state.minute)); open = false }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
            text = { TimePicker(state) },
        )
    }
}
```

`ui/components/DatePickerField.kt`:
```kotlin
package com.nobby.ring.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nobby.ring.R
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(date: LocalDate, onDateChange: (LocalDate) -> Unit, label: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier) { Text("$label: ${formatDate(date)}") }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateChange(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date) }
                    open = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state) }
    }
}
```

`ui/components/EventCard.kt`:
```kotlin
package com.nobby.ring.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nobby.ring.R
import com.nobby.ring.domain.model.EventKind
import kotlinx.datetime.LocalDateTime

@Composable
fun EventCard(kind: EventKind, plannedAt: LocalDateTime, overdue: Boolean, onDone: (() -> Unit)?, modifier: Modifier = Modifier) {
    val container = if (overdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(kind.label(), style = MaterialTheme.typography.titleMedium)
                Text(formatDateTime(plannedAt), style = MaterialTheme.typography.bodyMedium)
            }
            if (onDone != null) Button(onClick = onDone) { Text(stringResource(R.string.action_done)) }
        }
    }
}
```

- [ ] **Step 4: Write routes, nav host, MainViewModel and wire MainActivity**

`ui/navigation/Routes.kt`:
```kotlin
package com.nobby.ring.ui.navigation

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CALENDAR = "calendar"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val SETTINGS_SCHEDULE = "settings/schedule"
    const val SETTINGS_REMINDERS = "settings/reminders"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_CALENDAR = "settings/calendar"
    const val SETTINGS_CALENDAR_CLEANUP = "settings/calendar/cleanup"
    const val SETTINGS_WIDGET = "settings/widget"
    val topLevel = listOf(HOME, CALENDAR, HISTORY, SETTINGS)
}
```

`ui/navigation/NobbyNavHost.kt` — screens are placeholders here and replaced by the real composables in Tasks 9–11 and 15:
```kotlin
package com.nobby.ring.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nobby.ring.R

@Composable
fun NobbyNavHost(startDestination: String) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route in Routes.topLevel

    Scaffold(
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
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination, Modifier.padding(padding)) {
            composable(Routes.ONBOARDING) { Text("onboarding") }
            composable(Routes.HOME) { Text("home") }
            composable(Routes.CALENDAR) { Text("calendar") }
            composable(Routes.HISTORY) { Text("history") }
            composable(Routes.SETTINGS) { Text("settings") }
        }
    }
}
```

`ui/MainViewModel.kt`:
```kotlin
package com.nobby.ring.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.data.prefs.ThemeStore
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(themeStore: ThemeStore, configStore: MethodConfigStore) : ViewModel() {
    val theme: StateFlow<ThemeSpec> = themeStore.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeSpec())
    val startDestination: StateFlow<String?> = configStore.onboardingDone
        .map { if (it) Routes.HOME else Routes.ONBOARDING }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
```

Replace `MainActivity.kt`:
```kotlin
package com.nobby.ring

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.ui.MainViewModel
import com.nobby.ring.ui.navigation.NobbyNavHost
import com.nobby.ring.ui.theme.NobbyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val start by viewModel.startDestination.collectAsStateWithLifecycle()
            NobbyTheme(theme) {
                start?.let { NobbyNavHost(it) }
            }
        }
    }
}
```

- [ ] **Step 5: Build**

```bash
./gradlew.bat :app:assembleDebug --console=plain -q
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(ui): theme palettes, localized strings, navigation shell and shared components"
```

---

### Task 9: Onboarding

**Files:**
- Create: `ui/onboarding/OnboardingViewModel.kt`, `ui/onboarding/OnboardingScreen.kt`
- Modify: `ui/navigation/NobbyNavHost.kt` (replace the ONBOARDING placeholder)
- Test: `app/src/test/java/com/nobby/ring/ui/onboarding/OnboardingViewModelTest.kt`

**Interfaces:**
- Consumes: `MethodConfigStore`, `ReminderRepository`, `ThemeStore`, `NobbyClock`, `ScheduleConfig`, `ReminderRule.defaults`.
- Produces:
  - `data class OnboardingState(step: Int = 0, wearDays: Int = 21, pauseDays: Int = 7, timeOfDay: LocalTime = 21:00, anchorDate: LocalDate, anchorTime: LocalTime, palette: Palette = ROSE, finished: Boolean = false)` with `val wearWarning`, `val pauseWarning`.
  - `OnboardingViewModel { state: StateFlow<OnboardingState>; fun next(); fun back(); fun setWear(Int); fun setPause(Int); fun setTime(LocalTime); fun setAnchorDate(LocalDate); fun setAnchorTime(LocalTime); fun setPalette(Palette); fun finish() }`.
  - `@Composable fun OnboardingScreen(onFinished: () -> Unit)`.

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
package com.nobby.ring.ui.onboarding

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.data.db.NobbyDatabase
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.data.prefs.ThemeStore
import com.nobby.ring.data.repo.ReminderRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.model.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var configStore: MethodConfigStore
    private lateinit var themeStore: ThemeStore
    private lateinit var reminders: ReminderRepository
    private lateinit var db: NobbyDatabase
    private val clock = object : NobbyClock {
        override fun now() = LocalDateTime(2026, 11, 5, 10, 0)
        override val zone = TimeZone.UTC
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ds = PreferenceDataStoreFactory.create { File(context.cacheDir, "onb-${System.nanoTime()}.preferences_pb") }
        configStore = MethodConfigStore(ds)
        themeStore = ThemeStore(ds)
        db = Room.inMemoryDatabaseBuilder(context, NobbyDatabase::class.java).allowMainThreadQueries().build()
        reminders = ReminderRepository(db.reminderRuleDao())
    }

    @After fun tearDown() { Dispatchers.resetMain(); db.close() }

    @Test
    fun `finish saves config, default reminders, theme and marks onboarding done`() = runTest(dispatcher) {
        val vm = OnboardingViewModel(configStore, reminders, themeStore, clock)
        vm.setWear(21); vm.setPause(7); vm.setTime(LocalTime(21, 0))
        vm.setAnchorDate(LocalDate(2026, 11, 1)); vm.setAnchorTime(LocalTime(21, 0))
        vm.setPalette(Palette.MINT)
        vm.finish()
        dispatcher.scheduler.advanceUntilIdle()

        val config = configStore.config.first()!!
        assertEquals(LocalDateTime(2026, 11, 1, 21, 0), config.anchor)
        assertEquals(21, config.method.wearDays)
        assertTrue(configStore.onboardingDone.first())
        assertEquals(2, reminders.rules.first().size)
        assertEquals(Palette.MINT, themeStore.theme.first().palette)
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun `warnings appear above safety thresholds`() {
        val vm = OnboardingViewModel(configStore, reminders, themeStore, clock)
        vm.setWear(30); vm.setPause(9)
        assertTrue(vm.state.value.wearWarning)
        assertTrue(vm.state.value.pauseWarning)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.onboarding.OnboardingViewModelTest" --console=plain -q
```

- [ ] **Step 3: Write the ViewModel**

`ui/onboarding/OnboardingViewModel.kt`:
```kotlin
package com.nobby.ring.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.data.prefs.ThemeStore
import com.nobby.ring.data.repo.ReminderRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.ReminderRule
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.ThemeSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import javax.inject.Inject

data class OnboardingState(
    val step: Int = 0,
    val wearDays: Int = 21,
    val pauseDays: Int = 7,
    val timeOfDay: LocalTime = LocalTime(21, 0),
    val anchorDate: LocalDate,
    val anchorTime: LocalTime = LocalTime(21, 0),
    val palette: Palette = Palette.ROSE,
    val finished: Boolean = false,
) {
    val wearWarning: Boolean get() = wearDays > ScheduleConfig.SAFE_MAX_WEAR
    val pauseWarning: Boolean get() = pauseDays > ScheduleConfig.SAFE_MAX_PAUSE
    companion object { const val STEPS = 5 }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val configStore: MethodConfigStore,
    private val reminders: ReminderRepository,
    private val themeStore: ThemeStore,
    clock: NobbyClock,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState(anchorDate = clock.now().date))
    val state: StateFlow<OnboardingState> = _state

    fun next() = _state.update { it.copy(step = (it.step + 1).coerceAtMost(OnboardingState.STEPS - 1)) }
    fun back() = _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }
    fun setWear(days: Int) = _state.update { it.copy(wearDays = days.coerceIn(ScheduleConfig.MIN_WEAR, ScheduleConfig.MAX_WEAR)) }
    fun setPause(days: Int) = _state.update { it.copy(pauseDays = days.coerceIn(ScheduleConfig.MIN_PAUSE, ScheduleConfig.MAX_PAUSE)) }
    fun setTime(time: LocalTime) = _state.update { it.copy(timeOfDay = time, anchorTime = time) }
    fun setAnchorDate(date: LocalDate) = _state.update { it.copy(anchorDate = date) }
    fun setAnchorTime(time: LocalTime) = _state.update { it.copy(anchorTime = time) }
    fun setPalette(palette: Palette) = _state.update { it.copy(palette = palette) }

    fun finish() {
        val s = _state.value
        viewModelScope.launch {
            configStore.save(ScheduleConfig(Method.ring(s.wearDays, s.pauseDays, s.timeOfDay), LocalDateTime(s.anchorDate, s.anchorTime)))
            reminders.replaceAll(ReminderRule.defaults(s.timeOfDay))
            themeStore.save(ThemeSpec(palette = s.palette))
            configStore.setOnboardingDone()
            _state.update { it.copy(finished = true) }
        }
    }
}
```

- [ ] **Step 4: Write the screen**

`ui/onboarding/OnboardingScreen.kt`:
```kotlin
package com.nobby.ring.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.ReminderRule
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.ui.components.DatePickerField
import com.nobby.ring.ui.components.NumberStepper
import com.nobby.ring.ui.components.TimePickerField
import com.nobby.ring.ui.components.formatTime
import com.nobby.ring.ui.components.label
import com.nobby.ring.ui.theme.accent

@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        LinearProgressIndicator(progress = { (state.step + 1f) / OnboardingState.STEPS }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        AnimatedContent(state.step, Modifier.weight(1f), label = "step") { step ->
            when (step) {
                0 -> WelcomeStep()
                1 -> ScheduleStep(state, viewModel)
                2 -> AnchorStep(state, viewModel)
                3 -> RemindersStep(state)
                else -> LookStep(state, viewModel)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (state.step > 0) TextButton(onClick = viewModel::back) { Text(stringResource(R.string.action_back)) } else Spacer(Modifier)
            if (state.step < OnboardingState.STEPS - 1) Button(onClick = viewModel::next) { Text(stringResource(R.string.action_next)) }
            else Button(onClick = viewModel::finish) { Text(stringResource(R.string.action_finish)) }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.onb_welcome_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.onb_welcome_body), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ScheduleStep(state: OnboardingState, vm: OnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.onb_schedule_title), style = MaterialTheme.typography.headlineMedium)
        NumberStepper(state.wearDays, vm::setWear, ScheduleConfig.MIN_WEAR..ScheduleConfig.MAX_WEAR, stringResource(R.string.onb_wear_days))
        if (state.wearWarning) Text(stringResource(R.string.onb_safety_wear), color = MaterialTheme.colorScheme.error)
        NumberStepper(state.pauseDays, vm::setPause, ScheduleConfig.MIN_PAUSE..ScheduleConfig.MAX_PAUSE, stringResource(R.string.onb_pause_days))
        if (state.pauseWarning) Text(stringResource(R.string.onb_safety_pause), color = MaterialTheme.colorScheme.error)
        TimePickerField(state.timeOfDay, vm::setTime, stringResource(R.string.onb_time_of_day))
    }
}

@Composable
private fun AnchorStep(state: OnboardingState, vm: OnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.onb_anchor_title), style = MaterialTheme.typography.headlineMedium)
        DatePickerField(state.anchorDate, vm::setAnchorDate, stringResource(R.string.onb_anchor_date))
        TimePickerField(state.anchorTime, vm::setAnchorTime, stringResource(R.string.onb_anchor_time))
    }
}

@Composable
private fun RemindersStep(state: OnboardingState) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.onb_reminders_title), style = MaterialTheme.typography.headlineMedium)
        ReminderRule.defaults(state.timeOfDay).forEach { rule ->
            val text = if (rule.offsetDays == 0) stringResource(R.string.reminder_same_day, formatTime(rule.timeOfDay!!))
            else stringResource(R.string.reminder_day_before, rule.offsetDays, formatTime(rule.timeOfDay!!))
            Text("• $text (${rule.importance.label()})")
        }
        Text(stringResource(R.string.onb_reminders_body), style = MaterialTheme.typography.bodyMedium)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OutlinedButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text(stringResource(R.string.onb_notifications_permission))
            }
        }
    }
}

@Composable
private fun LookStep(state: OnboardingState, vm: OnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.onb_look_title), style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Palette.entries.forEach { p ->
                FilterChip(
                    selected = state.palette == p,
                    onClick = { vm.setPalette(p) },
                    label = { Text(p.label()) },
                    leadingIcon = { Box(Modifier.size(16.dp).clip(CircleShape).background(p.accent(false))) },
                )
            }
        }
        Text(stringResource(R.string.onb_calendar_connect) + " → " + stringResource(R.string.nav_settings), style = MaterialTheme.typography.bodyMedium)
    }
}
```

Note: the palette chip row overflows on narrow screens; wrap it in `androidx.compose.foundation.layout.FlowRow` (`@OptIn(ExperimentalLayoutApi::class)`) instead of `Row`.

- [ ] **Step 5: Wire into the nav host**

In `NobbyNavHost.kt`, replace `composable(Routes.ONBOARDING) { Text("onboarding") }` with:
```kotlin
            composable(Routes.ONBOARDING) {
                com.nobby.ring.ui.onboarding.OnboardingScreen(onFinished = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                })
            }
```

- [ ] **Step 6: Run tests and build**

```bash
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain -q
```
Expected: all pass, build succeeds.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat(ui): onboarding flow"
```

---

### Task 10: Home screen with Done / late-confirmation dialog / skip pause

**Files:**
- Create: `ui/home/HomeViewModel.kt`, `ui/home/HomeScreen.kt`, `ui/home/ConfirmDialog.kt`
- Create: `notifications/ScheduleChangeNotifier.kt` (fan-out hook; its implementations arrive in Tasks 12–14)
- Modify: `ui/navigation/NobbyNavHost.kt`
- Test: `app/src/test/java/com/nobby/ring/ui/home/HomeViewModelTest.kt`

**Interfaces:**
- Produces:
  - `interface ScheduleChangeListener { suspend fun onScheduleChanged() }` and `@Singleton class ScheduleChangeNotifier @Inject constructor(listeners: Set<@JvmSuppressWildcards ScheduleChangeListener>) { suspend fun notifyChanged() }` (Hilt multibinding; empty set allowed via `@Multibinds`).
  - `data class HomeUiState(state: ScheduleState?, pendingConfirm: PendingConfirm?)`, `data class PendingConfirm(event: ScheduledEvent, confirmedAt: LocalDateTime, delay: Duration)`.
  - `HomeViewModel { uiState: StateFlow<HomeUiState>; fun onDoneTapped(); fun onConfirmTime(LocalDateTime); fun onPolicyChosen(ShiftPolicy); fun dismissConfirm(); fun toggleSkipPause() }`.
  - `@Composable fun HomeScreen()`.

Flow: Done → if `ConfirmAction.needsPolicyChoice(event, now)` show `ConfirmDialog` (lets the user pick the actual time, default now, then SHIFT/KEEP); else confirm immediately with KEEP. After confirming, call `ScheduleChangeNotifier.notifyChanged()`.

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
package com.nobby.ring.ui.home

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.data.db.NobbyDatabase
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.engine.ShiftPolicy
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.notifications.ScheduleChangeListener
import com.nobby.ring.notifications.ScheduleChangeNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: NobbyDatabase
    private lateinit var repo: ScheduleRepository
    private var changes = 0
    private val clock = object : NobbyClock {
        override fun now() = LocalDateTime(2026, 11, 24, 21, 0)
        override val zone = TimeZone.UTC
    }

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NobbyDatabase::class.java).allowMainThreadQueries().build()
        val ds = PreferenceDataStoreFactory.create { File(context.cacheDir, "home-${System.nanoTime()}.preferences_pb") }
        repo = ScheduleRepository(MethodConfigStore(ds), db.eventLogDao(), clock)
        repo.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0)))
    }

    @After fun tearDown() { Dispatchers.resetMain(); db.close() }

    private fun vm() = HomeViewModel(repo, clock, ScheduleChangeNotifier(setOf(object : ScheduleChangeListener {
        override suspend fun onScheduleChanged() { changes++ }
    })))

    @Test
    fun `done on a late event opens the confirm dialog, SHIFT moves next insert`() = runTest(dispatcher) {
        val vm = vm()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onDoneTapped()
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(vm.uiState.value.pendingConfirm)
        vm.onPolicyChosen(ShiftPolicy.SHIFT)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.pendingConfirm)
        val next = vm.uiState.value.state!!.nextEvent!!
        assertEquals(EventKind.INSERT, next.kind)
        assertEquals(LocalDateTime(2026, 12, 1, 21, 0), next.plannedAt)
        assertEquals(1, changes)
    }

    @Test
    fun `toggle skip pause flips next event to swap and back`() = runTest(dispatcher) {
        val vm = vm()
        dispatcher.scheduler.advanceUntilIdle()
        vm.toggleSkipPause(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(EventKind.SWAP, vm.uiState.value.state!!.nextEvent!!.kind)
        vm.toggleSkipPause(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(EventKind.REMOVE, vm.uiState.value.state!!.nextEvent!!.kind)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.home.HomeViewModelTest" --console=plain -q
```

- [ ] **Step 3: Write the notifier and ViewModel**

`notifications/ScheduleChangeNotifier.kt`:
```kotlin
package com.nobby.ring.notifications

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Inject
import javax.inject.Singleton

interface ScheduleChangeListener {
    suspend fun onScheduleChanged()
}

@Singleton
class ScheduleChangeNotifier @Inject constructor(
    private val listeners: Set<@JvmSuppressWildcards ScheduleChangeListener>,
) {
    suspend fun notifyChanged() = listeners.forEach { it.onScheduleChanged() }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ScheduleChangeModule {
    @Multibinds abstract fun listeners(): Set<ScheduleChangeListener>
}
```

`ui/home/HomeViewModel.kt`:
```kotlin
package com.nobby.ring.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.engine.ConfirmAction
import com.nobby.ring.domain.engine.ShiftPolicy
import com.nobby.ring.domain.model.ScheduleState
import com.nobby.ring.domain.model.ScheduledEvent
import com.nobby.ring.notifications.ScheduleChangeNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
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

data class HomeUiState(val state: ScheduleState? = null, val pendingConfirm: PendingConfirm? = null)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: ScheduleRepository,
    private val clock: NobbyClock,
    private val notifier: ScheduleChangeNotifier,
) : ViewModel() {
    private val pending = MutableStateFlow<PendingConfirm?>(null)

    val uiState: StateFlow<HomeUiState> = combine(repo.state, pending) { s, p -> HomeUiState(s, p) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onDoneTapped() {
        val event = uiState.value.state?.nextEvent ?: return
        val now = clock.now()
        if (ConfirmAction.needsPolicyChoice(event, now)) {
            pending.value = PendingConfirm(event, now, delayBetween(event.plannedAt, now))
        } else {
            commit(event, now, ShiftPolicy.KEEP)
        }
    }

    fun onConfirmTime(time: LocalDateTime) = pending.update { p -> p?.copy(confirmedAt = time, delay = delayBetween(p.event.plannedAt, time)) }

    fun onPolicyChosen(policy: ShiftPolicy) {
        val p = pending.value ?: return
        pending.value = null
        commit(p.event, p.confirmedAt, policy)
    }

    fun dismissConfirm() { pending.value = null }

    fun toggleSkipPause() {
        viewModelScope.launch {
            val config = uiState.value.state?.config ?: repo.currentState()?.config ?: return@launch
            if (config.skipPauseOnce) repo.cancelSkipPause() else repo.skipPause()
            notifier.notifyChanged()
        }
    }

    private fun commit(event: ScheduledEvent, at: LocalDateTime, policy: ShiftPolicy) {
        viewModelScope.launch {
            repo.confirm(event.id, at, policy)
            notifier.notifyChanged()
        }
    }

    private fun delayBetween(from: LocalDateTime, to: LocalDateTime): Duration =
        to.toInstant(TimeZone.UTC) - from.toInstant(TimeZone.UTC)
}
```

- [ ] **Step 4: Write the dialog and screen**

`ui/home/ConfirmDialog.kt`:
```kotlin
package com.nobby.ring.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nobby.ring.R
import com.nobby.ring.domain.engine.ShiftPolicy
import com.nobby.ring.ui.components.DatePickerField
import com.nobby.ring.ui.components.TimePickerField
import com.nobby.ring.ui.components.formatDateTime
import com.nobby.ring.ui.components.formatDuration
import kotlinx.datetime.LocalDateTime
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes

@Composable
fun ConfirmDialog(
    pending: PendingConfirm,
    onTimeChange: (LocalDateTime) -> Unit,
    onPolicy: (ShiftPolicy) -> Unit,
    onDismiss: () -> Unit,
) {
    val absDelay = abs(pending.delay.inWholeMinutes).minutes
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.confirm_body, formatDateTime(pending.event.plannedAt), formatDuration(absDelay)))
                Text(stringResource(R.string.confirm_when))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DatePickerField(pending.confirmedAt.date, { onTimeChange(LocalDateTime(it, pending.confirmedAt.time)) }, stringResource(R.string.onb_anchor_date))
                    TimePickerField(pending.confirmedAt.time, { onTimeChange(LocalDateTime(pending.confirmedAt.date, it)) }, stringResource(R.string.onb_anchor_time))
                }
            }
        },
        confirmButton = { Button(onClick = { onPolicy(ShiftPolicy.SHIFT) }) { Text(stringResource(R.string.confirm_shift)) } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { onPolicy(ShiftPolicy.KEEP) }) { Text(stringResource(R.string.confirm_keep)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
```

`ui/home/HomeScreen.kt`:
```kotlin
package com.nobby.ring.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.domain.model.ScheduleState
import com.nobby.ring.ui.components.EventCard
import com.nobby.ring.ui.components.RingProgress
import com.nobby.ring.ui.components.formatDateTime
import com.nobby.ring.ui.components.label
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val state = ui.state ?: return

    ui.pendingConfirm?.let {
        ConfirmDialog(it, viewModel::onConfirmTime, viewModel::onPolicyChosen, viewModel::dismissConfirm)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_home)) },
                actions = {
                    TextButton(onClick = viewModel::toggleSkipPause) {
                        Text(stringResource(if (state.config.skipPauseOnce) R.string.home_skip_pause_cancel else R.string.home_skip_pause))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            PhaseChip(state)
            Countdown(state)
            NextCard(state, viewModel::onDoneTapped)
        }
    }
}

@Composable
private fun PhaseChip(state: ScheduleState) {
    val p = state.position
    AssistChip(onClick = {}, label = { Text(stringResource(R.string.phase_day, p.phase.label(), p.dayInPhase, p.phaseLengthDays)) })
}

@Composable
private fun Countdown(state: ScheduleState) {
    val days = state.daysUntilNext ?: 0
    val p = state.position
    val progress = (p.dayInPhase.toFloat() / p.phaseLengthDays.coerceAtLeast(1)).coerceIn(0f, 1f)
    val overdue = days < 0
    val label = when {
        overdue -> stringResource(R.string.home_overdue_days)
        days == 0 -> stringResource(R.string.home_today)
        days == 1 -> stringResource(R.string.home_day_remaining)
        else -> stringResource(R.string.home_days_remaining)
    }
    RingProgress(
        progress = progress,
        modifier = Modifier.size(280.dp),
        progressColor = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (days != 0) Text("${abs(days)}", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun NextCard(state: ScheduleState, onDone: () -> Unit) {
    val next = state.nextEvent ?: return
    val overdue = state.overdue
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (overdue != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.home_overdue_title, overdue.event.kind.label()), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.home_overdue_body, formatDateTime(overdue.event.plannedAt)))
                    if (overdue.safetyBreached) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.home_overdue_safety), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        EventCard(next.kind, next.plannedAt, overdue = overdue != null, onDone = onDone)
        Button(onClick = onDone, Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_mark_done)) }
    }
}
```

- [ ] **Step 5: Wire into the nav host**

Replace `composable(Routes.HOME) { Text("home") }` with `composable(Routes.HOME) { com.nobby.ring.ui.home.HomeScreen() }`.

- [ ] **Step 6: Run tests and build**

```bash
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain -q
```

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat(ui): home screen with countdown, done flow and skip pause"
```

---

### Task 11: Calendar screen with notes, and History screen

**Files:**
- Create: `ui/calendar/CalendarViewModel.kt`, `ui/calendar/MonthGrid.kt`, `ui/calendar/CalendarScreen.kt`
- Create: `ui/history/HistoryViewModel.kt`, `ui/history/HistoryScreen.kt`
- Modify: `ui/navigation/NobbyNavHost.kt`
- Test: `app/src/test/java/com/nobby/ring/ui/calendar/DayInfoTest.kt`

**Interfaces:**
- Produces:
  - `enum class DayPhase { WEAR, PAUSE, NONE }`, `data class DayInfo(date: LocalDate, phase: DayPhase, event: EventKind?, hasNote: Boolean, isToday: Boolean)`.
  - `object DayInfoBuilder { fun build(state: ScheduleState, month: YearMonth(year: Int, month: Int), notes: Set<LocalDate>): List<DayInfo> }` in `ui/calendar/DayInfoBuilder.kt` — pure, tested. A day is WEAR if it lies in `[cycleStart, removeAt)` of any generated cycle, PAUSE if in `[removeAt, insertAt)`; days before the anchor are NONE.
  - `CalendarViewModel { uiState: StateFlow<CalendarUiState(month, days, selected, noteText)>; fun prevMonth(); fun nextMonth(); fun select(LocalDate); fun setNote(String) }` — `setNote` debounces 500 ms then saves via `NotesRepository`.
  - `@Composable fun CalendarScreen()`, `@Composable fun MonthGrid(days: List<DayInfo>, selected: LocalDate, onSelect)`.
  - `HistoryViewModel { entries: StateFlow<List<HistoryEntry(kind, plannedAt, confirmedAt, delay: Duration)>> }`, `@Composable fun HistoryScreen()`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.nobby.ring.ui.calendar

import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayInfoTest {
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0), 3)
    private val state = ScheduleRepository.buildState(config, emptyList(), LocalDateTime(2026, 11, 10, 12, 0))

    @Test
    fun `november is wear until the 22nd then pause`() {
        val days = DayInfoBuilder.build(state, 2026, 11, setOf(LocalDate(2026, 11, 8)))
        assertEquals(30, days.size)
        assertEquals(DayPhase.WEAR, days[0].phase)
        assertEquals(DayPhase.WEAR, days[20].phase)      // Nov 21
        assertEquals(DayPhase.PAUSE, days[21].phase)     // Nov 22 (remove day counts as pause)
        assertEquals(EventKind.REMOVE, days[21].event)
        assertEquals(EventKind.INSERT, days[28].event)   // Nov 29
        assertEquals(DayPhase.WEAR, days[28].phase)
        assertTrue(days[7].hasNote)
        assertTrue(days[9].isToday)
    }

    @Test
    fun `days before the anchor have no phase`() {
        val days = DayInfoBuilder.build(state, 2026, 10, emptySet())
        assertTrue(days.all { it.phase == DayPhase.NONE })
    }
}
```

Add to `ScheduleRepository` a companion so tests and the widget can build a state without Hilt:
```kotlin
    companion object {
        fun buildState(config: ScheduleConfig, logs: List<EventLog>, now: LocalDateTime): ScheduleState {
            val events = ScheduleEngine.generate(config)
            return ScheduleState(
                config, events, logs, now,
                position = CycleCalculator.positionAt(config, logs, now),
                nextEvent = OverdueCalculator.nextPending(events, logs),
                overdue = OverdueCalculator.overdue(config, events, logs, now),
            )
        }
    }
```
and make the instance `build` delegate to it: `fun build(config, logs, now) = buildState(config, logs, now)`.

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.calendar.DayInfoTest" --console=plain -q
```

- [ ] **Step 3: Write DayInfoBuilder**

`ui/calendar/DayInfoBuilder.kt`:
```kotlin
package com.nobby.ring.ui.calendar

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.ScheduleState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

enum class DayPhase { WEAR, PAUSE, NONE }

data class DayInfo(val date: LocalDate, val phase: DayPhase, val event: EventKind?, val hasNote: Boolean, val isToday: Boolean)

object DayInfoBuilder {
    fun build(state: ScheduleState, year: Int, month: Int, notes: Set<LocalDate>): List<DayInfo> {
        val first = LocalDate(year, month, 1)
        val length = first.plus(1, DateTimeUnit.MONTH).toEpochDays() - first.toEpochDays()
        val eventsByDate = state.events.associateBy({ it.plannedAt.date }, { it.kind })
        // pause windows: [REMOVE date, INSERT date)
        val pauseWindows = state.events.filter { it.kind == EventKind.REMOVE }.map { remove ->
            val insert = state.events.firstOrNull { it.kind == EventKind.INSERT && it.plannedAt > remove.plannedAt }
            remove.plannedAt.date to (insert?.plannedAt?.date ?: remove.plannedAt.date.plus(state.config.method.pauseDays, DateTimeUnit.DAY))
        }
        val anchorDate = state.config.anchor.date
        val horizonEnd = state.events.lastOrNull()?.plannedAt?.date ?: anchorDate
        return (0 until length).map { i ->
            val date = first.plus(i, DateTimeUnit.DAY)
            val phase = when {
                date < anchorDate || date > horizonEnd -> DayPhase.NONE
                pauseWindows.any { (from, to) -> date >= from && date < to } -> DayPhase.PAUSE
                else -> DayPhase.WEAR
            }
            DayInfo(date, phase, eventsByDate[date], date in notes, date == state.now.date)
        }
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Same command. Expected: 2 tests pass.

- [ ] **Step 5: Write the calendar ViewModel, grid and screen**

`ui/calendar/CalendarViewModel.kt`:
```kotlin
package com.nobby.ring.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobby.ring.data.repo.NotesRepository
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import javax.inject.Inject

data class CalendarUiState(
    val year: Int,
    val month: Int,
    val days: List<DayInfo> = emptyList(),
    val selected: LocalDate,
    val noteText: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val schedule: ScheduleRepository,
    private val notes: NotesRepository,
    clock: NobbyClock,
) : ViewModel() {
    private val today = clock.now().date
    private val month = MutableStateFlow(LocalDate(today.year, today.monthNumber, 1))
    private val selected = MutableStateFlow(today)
    private val draft = MutableStateFlow<String?>(null)

    private val storedNote = selected.flatMapLatest { notes.note(it) }

    val uiState: StateFlow<CalendarUiState> = combine(schedule.state, notes.datesWithNotes(), month, selected, combine(storedNote, draft) { s, d -> d ?: s ?: "" }) { state, noteDates, m, sel, text ->
        CalendarUiState(
            year = m.year,
            month = m.monthNumber,
            days = state?.let { DayInfoBuilder.build(it, m.year, m.monthNumber, noteDates) } ?: emptyList(),
            selected = sel,
            noteText = text,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState(today.year, today.monthNumber, selected = today))

    init {
        viewModelScope.launch {
            draft.debounce(500).collect { text -> if (text != null) notes.save(selected.value, text) }
        }
    }

    fun prevMonth() = month.update { it.minus(1, DateTimeUnit.MONTH) }
    fun nextMonth() = month.update { it.plus(1, DateTimeUnit.MONTH) }
    fun select(date: LocalDate) { draft.value = null; selected.value = date }
    fun setNote(text: String) { draft.value = text }
}
```

`ui/calendar/MonthGrid.kt`:
```kotlin
package com.nobby.ring.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MonthGrid(days: List<DayInfo>, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val leading = days.firstOrNull()?.date?.dayOfWeek?.let { (it.ordinal - DayOfWeek.MONDAY.ordinal + 7) % 7 } ?: 0
    val cells: List<DayInfo?> = List(leading) { null } + days
    LazyVerticalGrid(GridCells.Fixed(7), Modifier.fillMaxWidth(), userScrollEnabled = false) {
        items(DayOfWeek.entries) { dow ->
            Text(
                java.time.DayOfWeek.valueOf(dow.name).getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                Modifier.padding(4.dp), style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        items(cells) { day -> if (day == null) Box(Modifier.aspectRatio(1f)) else DayCell(day, day.date == selected) { onSelect(day.date) } }
    }
}

@Composable
private fun DayCell(day: DayInfo, selected: Boolean, onClick: () -> Unit) {
    val ring = when (day.phase) {
        DayPhase.WEAR -> MaterialTheme.colorScheme.primary
        DayPhase.PAUSE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        DayPhase.NONE -> MaterialTheme.colorScheme.surface
    }
    val fill = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Box(
        Modifier.aspectRatio(1f).padding(3.dp).clip(CircleShape).background(fill)
            .border(if (day.phase == DayPhase.NONE) 0.dp else 3.dp, ring, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${day.date.dayOfMonth}", style = if (day.isToday) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
            if (day.hasNote || day.event != null) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (day.event != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary))
            }
        }
    }
}
```

`ui/calendar/CalendarScreen.kt`:
```kotlin
package com.nobby.ring.ui.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.ui.components.formatDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_calendar)) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::prevMonth) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(
                    "${Month.of(ui.month).getDisplayName(TextStyle.FULL, Locale.getDefault())} ${ui.year}",
                    Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = viewModel::nextMonth) { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
            }
            MonthGrid(ui.days, ui.selected, viewModel::select)
            OutlinedTextField(
                value = ui.noteText,
                onValueChange = viewModel::setNote,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                label = { Text(stringResource(R.string.calendar_notes_title, formatDate(ui.selected))) },
                placeholder = { Text(stringResource(R.string.calendar_notes_hint)) },
                minLines = 6,
            )
        }
    }
}
```

- [ ] **Step 6: Write the history ViewModel and screen**

`ui/history/HistoryViewModel.kt`:
```kotlin
package com.nobby.ring.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.model.EventKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import javax.inject.Inject
import kotlin.time.Duration

data class HistoryEntry(val kind: EventKind, val plannedAt: LocalDateTime, val confirmedAt: LocalDateTime, val delay: Duration)

@HiltViewModel
class HistoryViewModel @Inject constructor(repo: ScheduleRepository) : ViewModel() {
    val entries: StateFlow<List<HistoryEntry>> = repo.state.map { s ->
        (s?.logs ?: emptyList())
            .sortedByDescending { it.confirmedAt }
            .map { HistoryEntry(it.kind, it.plannedAt, it.confirmedAt, it.confirmedAt.toInstant(TimeZone.UTC) - it.plannedAt.toInstant(TimeZone.UTC)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
```

`ui/history/HistoryScreen.kt`:
```kotlin
package com.nobby.ring.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.ui.components.formatDateTime
import com.nobby.ring.ui.components.formatDuration
import com.nobby.ring.ui.components.label
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_history)) }) }) { padding ->
        if (entries.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.history_empty)) }
        } else LazyColumn(Modifier.padding(padding)) {
            items(entries) { e ->
                val mins = e.delay.inWholeMinutes
                val status = when {
                    abs(mins) < 60 -> stringResource(R.string.history_on_time)
                    mins > 0 -> stringResource(R.string.history_late, formatDuration(mins.minutes))
                    else -> stringResource(R.string.history_early, formatDuration(abs(mins).minutes))
                }
                ListItem(
                    headlineContent = { Text(e.kind.label()) },
                    supportingContent = { Text("${formatDateTime(e.confirmedAt)} · $status") },
                )
            }
        }
    }
}
```

- [ ] **Step 7: Wire into the nav host and build**

Replace the CALENDAR and HISTORY placeholders with `com.nobby.ring.ui.calendar.CalendarScreen()` and `com.nobby.ring.ui.history.HistoryScreen()`.

```bash
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain -q
```

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "feat(ui): calendar with phase colouring and notes, history list"
```

---

### Task 12: Notifications (channels, exact alarms, overdue nag, actions, boot)

**Files:**
- Create: `notifications/Channels.kt`, `notifications/Notifier.kt`, `notifications/ReminderScheduler.kt`, `notifications/AlarmReceiver.kt`, `notifications/ActionReceiver.kt`, `notifications/BootReceiver.kt`, `notifications/NotificationsModule.kt`
- Modify: `NobbyApp.kt` (create channels), `AndroidManifest.xml` (receivers), `ui/home/HomeScreen.kt` (exact-alarm warning banner)
- Test: `app/src/test/java/com/nobby/ring/notifications/ReminderSchedulerTest.kt` (Robolectric, `ShadowAlarmManager`)

**Interfaces:**
- Produces:
  - `object Channels { const val LOW="nobby_low"; NORMAL="nobby_normal"; HIGH="nobby_high"; URGENT="nobby_urgent"; fun create(context); fun idFor(importance): String }`.
  - `class Notifier @Inject constructor(@ApplicationContext ctx) { fun showReminder(kind: EventKind, plannedAt: LocalDateTime, importance: Importance, eventId: String); fun showOverdue(kind, plannedAt, eventId, safetyBreached: Boolean); fun cancelOverdue() }`. Notification ids: reminders use `eventId.hashCode()`, overdue uses the constant `OVERDUE_ID = 1`.
  - `class ReminderScheduler @Inject constructor(ctx, ScheduleRepository, ReminderRepository, OverdueConfigStore, NobbyClock) : ScheduleChangeListener { suspend fun rescheduleAll(); fun canScheduleExact(): Boolean }` — cancels all previously set alarms (tracked in a DataStore string set `scheduled_alarm_ids`), then sets one exact alarm per `PlannedReminder` (request code `fireAt.hashCode() xor ruleId`) and, if an event is overdue or the next event is due, one overdue alarm at `max(now, plannedAt)` repeating every `repeatEveryHours` (the receiver re-arms it).
  - `AlarmReceiver` handles intents with extras `type` = `"reminder" | "overdue"`, `eventId`, `kind`, `plannedAt`, `importance`; on overdue it re-checks `ScheduleRepository.currentState()` and shows/clears accordingly, then re-arms.
  - `ActionReceiver` handles `ACTION_DONE` (confirms with `ShiftPolicy.KEEP` at `now`, notifies change) and `ACTION_SNOOZE` (re-arms overdue alarm at `now + snoozeHours`).
  - `BootReceiver` calls `rescheduleAll()` via a `goAsync()` coroutine.
  - `NotificationsModule` binds `ReminderScheduler` into the `Set<ScheduleChangeListener>`.

- [ ] **Step 1: Write the failing scheduler test**

```kotlin
package com.nobby.ring.notifications

import android.app.AlarmManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.data.db.NobbyDatabase
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.data.prefs.OverdueConfigStore
import com.nobby.ring.data.repo.ReminderRepository
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ReminderRule
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ReminderSchedulerTest {
    private lateinit var context: Context
    private lateinit var scheduler: ReminderScheduler
    private lateinit var alarmManager: AlarmManager
    private val clock = object : NobbyClock {
        override fun now() = LocalDateTime(2026, 11, 2, 12, 0)
        override val zone = TimeZone.UTC
    }

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(AlarmManager::class.java)
        val db = Room.inMemoryDatabaseBuilder(context, NobbyDatabase::class.java).allowMainThreadQueries().build()
        val ds = PreferenceDataStoreFactory.create { File(context.cacheDir, "sched-${System.nanoTime()}.preferences_pb") }
        val configStore = MethodConfigStore(ds)
        val schedule = ScheduleRepository(configStore, db.eventLogDao(), clock)
        val reminders = ReminderRepository(db.reminderRuleDao())
        schedule.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0), 1))
        reminders.replaceAll(ReminderRule.defaults(LocalTime(21, 0)))
        scheduler = ReminderScheduler(context, schedule, reminders, OverdueConfigStore(ds), clock, ds)
    }

    @Test
    fun `rescheduleAll sets one alarm per planned reminder`() = runTest {
        scheduler.rescheduleAll()
        val alarms = shadowOf(alarmManager).scheduledAlarms
        // 1 month horizon: REMOVE (Nov 22) + INSERT (Nov 29) → 2 rules × 2 events = 4 reminders + 1 overdue alarm
        assertEquals(5, alarms.size)
        assertTrue(alarms.all { it.triggerAtTime > 0 })
    }

    @Test
    fun `rescheduleAll is idempotent`() = runTest {
        scheduler.rescheduleAll()
        scheduler.rescheduleAll()
        assertEquals(5, shadowOf(alarmManager).scheduledAlarms.size)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.notifications.ReminderSchedulerTest" --console=plain -q
```

- [ ] **Step 3: Write channels and notifier**

`notifications/Channels.kt`:
```kotlin
package com.nobby.ring.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.nobby.ring.R
import com.nobby.ring.domain.model.Importance

object Channels {
    const val LOW = "nobby_low"
    const val NORMAL = "nobby_normal"
    const val HIGH = "nobby_high"
    const val URGENT = "nobby_urgent"

    fun idFor(importance: Importance): String = when (importance) {
        Importance.LOW -> LOW
        Importance.NORMAL -> NORMAL
        Importance.HIGH -> HIGH
        Importance.URGENT -> URGENT
    }

    fun create(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(LOW, context.getString(R.string.channel_low), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(NORMAL, context.getString(R.string.channel_normal), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(
            NotificationChannel(HIGH, context.getString(R.string.channel_high), NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true) },
        )
        nm.createNotificationChannel(
            NotificationChannel(URGENT, context.getString(R.string.channel_urgent), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
                setBypassDnd(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
            },
        )
    }
}
```

`notifications/Notifier.kt`:
```kotlin
package com.nobby.ring.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nobby.ring.MainActivity
import com.nobby.ring.R
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Importance
import com.nobby.ring.ui.components.formatDateTime
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.datetime.LocalDateTime
import javax.inject.Inject

class Notifier @Inject constructor(@ApplicationContext private val context: Context) {

    fun showReminder(kind: EventKind, plannedAt: LocalDateTime, importance: Importance, eventId: String) {
        val n = NotificationCompat.Builder(context, Channels.idFor(importance))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_reminder_title, kindLabel(kind)))
            .setContentText(context.getString(R.string.notif_reminder_body, formatDateTime(plannedAt)))
            .setPriority(priorityFor(importance))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        notify(eventId.hashCode(), n)
    }

    fun showOverdue(kind: EventKind, plannedAt: LocalDateTime, eventId: String, safetyBreached: Boolean) {
        val text = buildString {
            append(context.getString(R.string.notif_overdue_body, formatDateTime(plannedAt)))
            if (safetyBreached) append("\n").append(context.getString(R.string.notif_overdue_safety))
        }
        val n = NotificationCompat.Builder(context, Channels.URGENT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_overdue_title, kindLabel(kind)))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(openApp())
            .addAction(0, context.getString(R.string.action_done), action(ActionReceiver.ACTION_DONE, eventId))
            .addAction(0, context.getString(R.string.home_snooze), action(ActionReceiver.ACTION_SNOOZE, eventId))
            .build()
        notify(OVERDUE_ID, n)
    }

    fun cancelOverdue() = NotificationManagerCompat.from(context).cancel(OVERDUE_ID)

    private fun notify(id: Int, n: android.app.Notification) {
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

- [ ] **Step 4: Write the scheduler and receivers**

`notifications/ReminderScheduler.kt`:
```kotlin
package com.nobby.ring.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nobby.ring.data.prefs.OverdueConfigStore
import com.nobby.ring.data.repo.ReminderRepository
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.engine.ReminderPlanner
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Importance
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val schedule: ScheduleRepository,
    private val reminders: ReminderRepository,
    private val overdueConfig: OverdueConfigStore,
    private val clock: NobbyClock,
    private val dataStore: DataStore<Preferences>,
) : ScheduleChangeListener {
    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)
    private val trackedKey = stringSetPreferencesKey("scheduled_alarm_ids")

    override suspend fun onScheduleChanged() = rescheduleAll()

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    suspend fun rescheduleAll() {
        cancelTracked()
        val state = schedule.currentState() ?: return
        val now = clock.now()
        val planned = ReminderPlanner.plan(reminders.rulesOnce(), state.events, state.logs, now)
        val codes = mutableSetOf<String>()
        planned.forEach { r ->
            val code = (r.fireAt.hashCode() * 31) xor r.ruleId.toInt()
            val intent = Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_REMINDER)
                .putExtra(AlarmReceiver.EXTRA_EVENT_ID, r.eventId)
                .putExtra(AlarmReceiver.EXTRA_KIND, r.kind.name)
                .putExtra(AlarmReceiver.EXTRA_PLANNED_AT, state.events.first { it.id == r.eventId }.plannedAt.toString())
                .putExtra(AlarmReceiver.EXTRA_IMPORTANCE, r.importance.name)
            set(code, intent, r.fireAt)
            codes += code.toString()
        }
        state.nextEvent?.let { next ->
            val fireAt = if (next.plannedAt > now) next.plannedAt else now
            codes += OVERDUE_CODE.toString()
            set(OVERDUE_CODE, overdueIntent(next.id), fireAt)
        }
        dataStore.edit { it[trackedKey] = codes }
    }

    suspend fun armOverdueIn(hours: Int, eventId: String) {
        val at = (clock.now().toInstant(clock.zone) + hours.hours)
        setAt(OVERDUE_CODE, overdueIntent(eventId), at.toEpochMilliseconds())
    }

    suspend fun repeatIntervalHours(): Int = overdueConfig.config.first().repeatEveryHours
    suspend fun snoozeHours(): Int = overdueConfig.config.first().snoozeHours

    private fun overdueIntent(eventId: String) = Intent(context, AlarmReceiver::class.java)
        .setAction(AlarmReceiver.ACTION_OVERDUE)
        .putExtra(AlarmReceiver.EXTRA_EVENT_ID, eventId)

    private fun set(code: Int, intent: Intent, at: LocalDateTime) = setAt(code, intent, at.toInstant(clock.zone).toEpochMilliseconds())

    private fun setAt(code: Int, intent: Intent, millis: Long) {
        val pi = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (canScheduleExact()) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
    }

    private suspend fun cancelTracked() {
        val codes = dataStore.data.first()[trackedKey] ?: emptySet()
        codes.mapNotNull { it.toIntOrNull() }.forEach { code ->
            val intent = Intent(context, AlarmReceiver::class.java)
            val pi = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            pi?.let { alarmManager.cancel(it); it.cancel() }
        }
        // cancel both action variants explicitly (FLAG_NO_CREATE matches on action too)
        listOf(AlarmReceiver.ACTION_REMINDER, AlarmReceiver.ACTION_OVERDUE).forEach { action ->
            codes.mapNotNull { it.toIntOrNull() }.forEach { code ->
                val pi = PendingIntent.getBroadcast(context, code, Intent(context, AlarmReceiver::class.java).setAction(action), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                pi?.let { alarmManager.cancel(it); it.cancel() }
            }
        }
    }

    companion object { const val OVERDUE_CODE = 7_000_001 }
}
```

`notifications/AlarmReceiver.kt`:
```kotlin
package com.nobby.ring.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Importance
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var schedule: ScheduleRepository
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_REMINDER -> notifier.showReminder(
                        EventKind.valueOf(intent.getStringExtra(EXTRA_KIND)!!),
                        LocalDateTime.parse(intent.getStringExtra(EXTRA_PLANNED_AT)!!),
                        Importance.valueOf(intent.getStringExtra(EXTRA_IMPORTANCE)!!),
                        intent.getStringExtra(EXTRA_EVENT_ID)!!,
                    )
                    ACTION_OVERDUE -> handleOverdue()
                }
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun handleOverdue() {
        val state = schedule.currentState() ?: return
        val overdue = state.overdue
        if (overdue == null) {
            notifier.cancelOverdue()
            return
        }
        notifier.showOverdue(overdue.event.kind, overdue.event.plannedAt, overdue.event.id, overdue.safetyBreached)
        scheduler.armOverdueIn(scheduler.repeatIntervalHours(), overdue.event.id)
    }

    companion object {
        const val ACTION_REMINDER = "com.nobby.ring.REMINDER"
        const val ACTION_OVERDUE = "com.nobby.ring.OVERDUE"
        const val EXTRA_EVENT_ID = "eventId"
        const val EXTRA_KIND = "kind"
        const val EXTRA_PLANNED_AT = "plannedAt"
        const val EXTRA_IMPORTANCE = "importance"
    }
}
```

`notifications/ActionReceiver.kt`:
```kotlin
package com.nobby.ring.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.engine.ShiftPolicy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ActionReceiver : BroadcastReceiver() {
    @Inject lateinit var schedule: ScheduleRepository
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var changeNotifier: ScheduleChangeNotifier
    @Inject lateinit var clock: NobbyClock

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_DONE -> {
                        schedule.confirm(eventId, clock.now(), ShiftPolicy.KEEP)
                        notifier.cancelOverdue()
                        changeNotifier.notifyChanged()
                    }
                    ACTION_SNOOZE -> {
                        notifier.cancelOverdue()
                        scheduler.armOverdueIn(scheduler.snoozeHours(), eventId)
                    }
                }
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.nobby.ring.ACTION_DONE"
        const val ACTION_SNOOZE = "com.nobby.ring.ACTION_SNOOZE"
        const val EXTRA_EVENT_ID = "eventId"
    }
}
```

`notifications/BootReceiver.kt`:
```kotlin
package com.nobby.ring.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { scheduler.rescheduleAll() } finally { result.finish() }
        }
    }
}
```

`notifications/NotificationsModule.kt`:
```kotlin
package com.nobby.ring.notifications

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {
    @Binds @IntoSet abstract fun scheduler(impl: ReminderScheduler): ScheduleChangeListener
}
```

- [ ] **Step 5: Register receivers and channels**

In `AndroidManifest.xml` inside `<application>`:
```xml
        <receiver android:name=".notifications.AlarmReceiver" android:exported="false" />
        <receiver android:name=".notifications.ActionReceiver" android:exported="false" />
        <receiver android:name=".notifications.BootReceiver" android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>
```

In `NobbyApp.kt` add:
```kotlin
    override fun onCreate() {
        super.onCreate()
        com.nobby.ring.notifications.Channels.create(this)
    }
```

In `HomeScreen.kt`, inject `ReminderScheduler` into `HomeViewModel` (constructor param `private val scheduler: ReminderScheduler`, expose `val exactAlarmsAllowed: Boolean get() = scheduler.canScheduleExact()`), and above `PhaseChip` render:
```kotlin
            if (!viewModel.exactAlarmsAllowed) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                androidx.compose.material3.TextButton(onClick = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                }) { Text(stringResource(R.string.exact_alarm_warning), color = MaterialTheme.colorScheme.error) }
            }
```
Update `HomeViewModelTest.vm()` to pass a `ReminderScheduler` built like in `ReminderSchedulerTest.setUp` (same db/datastore instances).

- [ ] **Step 6: Run tests and build**

```bash
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain -q
```
Expected: all pass (4 reminder alarms + 1 overdue alarm = 5 scheduled alarms).

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat(notifications): channels, exact alarm reminders, overdue nag with done/snooze, boot reschedule"
```

---

### Task 13: Calendar sync (Calendar Provider) + clean-up tool

**Files:**
- Create: `data/calendar/DeviceCalendarSource.kt`, `data/calendar/CalendarSyncer.kt`, `data/calendar/CalendarSyncWorker.kt`, `data/prefs/CalendarSyncStore.kt`, `data/calendar/CalendarModule.kt`
- Create: `ui/settings/CalendarSettingsScreen.kt`, `ui/settings/CalendarCleanupScreen.kt`, `ui/settings/CalendarSettingsViewModel.kt`
- Modify: `ui/navigation/NobbyNavHost.kt`
- Test: `app/src/test/java/com/nobby/ring/data/calendar/CalendarSyncerTest.kt` (fake source)

**Interfaces:**
- Produces:
  - `data class DeviceCalendar(id: Long, name: String, account: String, color: Int)`, `data class FoundEvent(id: Long, title: String, startMillis: Long)`.
  - `interface CalendarSource { suspend fun listWritable(): List<DeviceCalendar>; suspend fun insert(calendarId, entry: CalendarEntry, zone: TimeZone): Long; suspend fun update(providerId, entry, zone); suspend fun delete(providerId); suspend fun search(calendarId, pattern: Regex, fromMillis, toMillis): List<FoundEvent> }` + `class DeviceCalendarSource(@ApplicationContext ctx) : CalendarSource` over `CalendarContract.Calendars/Events` (insert sets `TITLE`, `DESCRIPTION`, `DTSTART`, `DTEND`, `EVENT_TIMEZONE`, `CALENDAR_ID`, `HAS_ALARM=0`).
  - `class CalendarSyncStore(dataStore) { val enabled: Flow<Boolean>; val calendarId: Flow<Long?>; val lastSync: Flow<LocalDateTime?>; suspend fun save(enabled, calendarId); suspend fun markSynced(now) }`.
  - `class CalendarSyncer(source, linkDao, syncStore, schedule, clock, @ApplicationContext ctx) : ScheduleChangeListener { suspend fun syncNow(): Result<Int>; suspend fun disconnect() }` — builds `CalendarEntry`s from `state.pendingEvents` (localised titles from string resources via `ctx.getString`), runs `CalendarDiff.compute`, applies it through the source, updates `CalendarLinkDao`. `onScheduleChanged()` enqueues `CalendarSyncWorker` (unique work, REPLACE) so sync never blocks the UI. `disconnect()` deletes all linked events and clears links.
  - `CalendarSyncWorker` (`@HiltWorker`) calls `syncer.syncNow()`.
  - `CalendarSettingsViewModel { uiState(enabled, calendars, selectedId, lastSync, permissionGranted, error); fun setEnabled(Boolean); fun select(id); fun syncNow(); fun disconnect(); fun search(query, from, to); fun toggleSelection(id); fun deleteSelected() }`.

- [ ] **Step 1: Write the failing syncer test with a fake source**

```kotlin
package com.nobby.ring.data.calendar

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.data.db.NobbyDatabase
import com.nobby.ring.data.prefs.CalendarSyncStore
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.engine.CalendarEntry
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

class FakeCalendarSource : CalendarSource {
    val events = linkedMapOf<Long, CalendarEntry>()
    private var nextId = 100L
    override suspend fun listWritable() = listOf(DeviceCalendar(1, "Test", "test@example.com", 0))
    override suspend fun insert(calendarId: Long, entry: CalendarEntry, zone: TimeZone): Long { val id = nextId++; events[id] = entry; return id }
    override suspend fun update(providerId: Long, entry: CalendarEntry, zone: TimeZone) { events[providerId] = entry }
    override suspend fun delete(providerId: Long) { events.remove(providerId) }
    override suspend fun search(calendarId: Long, pattern: Regex, fromMillis: Long, toMillis: Long) =
        events.filter { pattern.containsMatchIn(it.value.title) }.map { FoundEvent(it.key, it.value.title, 0L) }
}

@RunWith(RobolectricTestRunner::class)
class CalendarSyncerTest {
    private lateinit var syncer: CalendarSyncer
    private lateinit var source: FakeCalendarSource
    private lateinit var schedule: ScheduleRepository
    private val clock = object : NobbyClock {
        override fun now() = LocalDateTime(2026, 11, 2, 12, 0)
        override val zone = TimeZone.UTC
    }

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NobbyDatabase::class.java).allowMainThreadQueries().build()
        val ds = PreferenceDataStoreFactory.create { File(context.cacheDir, "cal-${System.nanoTime()}.preferences_pb") }
        schedule = ScheduleRepository(MethodConfigStore(ds), db.eventLogDao(), clock)
        schedule.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0), 2))
        val syncStore = CalendarSyncStore(ds)
        syncStore.save(enabled = true, calendarId = 1)
        source = FakeCalendarSource()
        syncer = CalendarSyncer(source, db.calendarLinkDao(), syncStore, schedule, clock, context)
    }

    @Test
    fun `first sync creates one provider event per pending event`() = runTest {
        val created = syncer.syncNow().getOrThrow()
        assertEquals(4, created)
        assertEquals(4, source.events.size)
    }

    @Test
    fun `second sync changes nothing, config change updates in place`() = runTest {
        syncer.syncNow()
        assertEquals(0, syncer.syncNow().getOrThrow())
        schedule.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(8, 0)), LocalDateTime(2026, 11, 1, 8, 0), 2))
        syncer.syncNow()
        assertEquals(4, source.events.size)
        assertEquals(8, source.events.values.first().startAt.hour)
    }

    @Test
    fun `disconnect removes everything`() = runTest {
        syncer.syncNow()
        syncer.disconnect()
        assertEquals(0, source.events.size)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.data.calendar.CalendarSyncerTest" --console=plain -q
```

- [ ] **Step 3: Write store, source, syncer, worker, module**

`data/prefs/CalendarSyncStore.kt`:
```kotlin
package com.nobby.ring.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime
import javax.inject.Inject

class CalendarSyncStore @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private val enabledKey = booleanPreferencesKey("cal_enabled")
    private val idKey = longPreferencesKey("cal_id")
    private val lastKey = stringPreferencesKey("cal_last_sync")

    val enabled: Flow<Boolean> = dataStore.data.map { it[enabledKey] ?: false }
    val calendarId: Flow<Long?> = dataStore.data.map { it[idKey] }
    val lastSync: Flow<LocalDateTime?> = dataStore.data.map { p -> p[lastKey]?.let { LocalDateTime.parse(it) } }

    suspend fun save(enabled: Boolean, calendarId: Long?) {
        dataStore.edit { p ->
            p[enabledKey] = enabled
            if (calendarId != null) p[idKey] = calendarId else p.remove(idKey)
        }
    }

    suspend fun markSynced(now: LocalDateTime) { dataStore.edit { it[lastKey] = now.toString() } }
}
```

`data/calendar/DeviceCalendarSource.kt`:
```kotlin
package com.nobby.ring.data.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.nobby.ring.domain.engine.CalendarEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

data class DeviceCalendar(val id: Long, val name: String, val account: String, val color: Int)
data class FoundEvent(val id: Long, val title: String, val startMillis: Long)

interface CalendarSource {
    suspend fun listWritable(): List<DeviceCalendar>
    suspend fun insert(calendarId: Long, entry: CalendarEntry, zone: TimeZone): Long
    suspend fun update(providerId: Long, entry: CalendarEntry, zone: TimeZone)
    suspend fun delete(providerId: Long)
    suspend fun search(calendarId: Long, pattern: Regex, fromMillis: Long, toMillis: Long): List<FoundEvent>
}

class DeviceCalendarSource @Inject constructor(@ApplicationContext private val context: Context) : CalendarSource {
    private val resolver get() = context.contentResolver

    override suspend fun listWritable(): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.CALENDAR_COLOR,
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"
        resolver.query(CalendarContract.Calendars.CONTENT_URI, projection, selection, null, null)?.use { c ->
            buildList { while (c.moveToNext()) add(DeviceCalendar(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getInt(3))) }
        } ?: emptyList()
    }

    override suspend fun insert(calendarId: Long, entry: CalendarEntry, zone: TimeZone): Long = withContext(Dispatchers.IO) {
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values(entry, zone).apply { put(CalendarContract.Events.CALENDAR_ID, calendarId) })
            ?: throw IllegalStateException("Calendar insert failed")
        ContentUris.parseId(uri)
    }

    override suspend fun update(providerId: Long, entry: CalendarEntry, zone: TimeZone) = withContext(Dispatchers.IO) {
        resolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, providerId), values(entry, zone), null, null)
        Unit
    }

    override suspend fun delete(providerId: Long) = withContext(Dispatchers.IO) {
        resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, providerId), null, null)
        Unit
    }

    override suspend fun search(calendarId: Long, pattern: Regex, fromMillis: Long, toMillis: Long): List<FoundEvent> = withContext(Dispatchers.IO) {
        val projection = arrayOf(CalendarContract.Events._ID, CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART)
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DTSTART} BETWEEN ? AND ? AND ${CalendarContract.Events.DELETED} = 0"
        resolver.query(CalendarContract.Events.CONTENT_URI, projection, selection, arrayOf("$calendarId", "$fromMillis", "$toMillis"), CalendarContract.Events.DTSTART)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val title = c.getString(1) ?: ""
                    if (pattern.containsMatchIn(title)) add(FoundEvent(c.getLong(0), title, c.getLong(2)))
                }
            }
        } ?: emptyList()
    }

    private fun values(entry: CalendarEntry, zone: TimeZone): ContentValues {
        val start = entry.startAt.toInstant(zone)
        return ContentValues().apply {
            put(CalendarContract.Events.TITLE, entry.title)
            put(CalendarContract.Events.DESCRIPTION, entry.description)
            put(CalendarContract.Events.DTSTART, start.toEpochMilliseconds())
            put(CalendarContract.Events.DTEND, (start + entry.durationMinutes.minutes).toEpochMilliseconds())
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            put(CalendarContract.Events.HAS_ALARM, 0)
        }
    }
}
```

`data/calendar/CalendarSyncer.kt`:
```kotlin
package com.nobby.ring.data.calendar

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.nobby.ring.R
import com.nobby.ring.data.db.CalendarLinkDao
import com.nobby.ring.data.db.CalendarLinkEntity
import com.nobby.ring.data.prefs.CalendarSyncStore
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.engine.CalendarDiff
import com.nobby.ring.domain.engine.CalendarEntry
import com.nobby.ring.domain.engine.CalendarLink
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.notifications.ScheduleChangeListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarSyncer @Inject constructor(
    private val source: CalendarSource,
    private val linkDao: CalendarLinkDao,
    private val syncStore: CalendarSyncStore,
    private val schedule: ScheduleRepository,
    private val clock: NobbyClock,
    @ApplicationContext private val context: Context,
) : ScheduleChangeListener {

    override suspend fun onScheduleChanged() {
        if (!syncStore.enabled.first()) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<CalendarSyncWorker>().build(),
        )
    }

    /** Returns the number of provider writes performed (create + update + delete). */
    suspend fun syncNow(): Result<Int> = runCatching {
        if (!syncStore.enabled.first()) return@runCatching 0
        val calendarId = syncStore.calendarId.first() ?: return@runCatching 0
        val state = schedule.currentState() ?: return@runCatching 0
        val desired = state.pendingEvents.map { e ->
            CalendarEntry(e.id, titleFor(e.kind), e.plannedAt, 30, CalendarEntry.marker(e.id))
        }
        val links = linkDao.allOnce().map { CalendarLink(it.eventId, it.providerEventId, LocalDateTime.parse(it.startAt), it.title) }
        val diff = CalendarDiff.compute(desired, links)

        diff.delete.forEach { source.delete(it.providerEventId) }
        linkDao.delete(diff.delete.map { it.eventId })
        val updated = diff.update.map { (link, entry) ->
            source.update(link.providerEventId, entry, clock.zone)
            CalendarLinkEntity(entry.eventId, link.providerEventId, entry.startAt.toString(), entry.title)
        }
        val created = diff.create.map { entry ->
            val id = source.insert(calendarId, entry, clock.zone)
            CalendarLinkEntity(entry.eventId, id, entry.startAt.toString(), entry.title)
        }
        linkDao.upsert(updated + created)
        syncStore.markSynced(clock.now())
        diff.create.size + diff.update.size + diff.delete.size
    }

    suspend fun disconnect() {
        linkDao.allOnce().forEach { runCatching { source.delete(it.providerEventId) } }
        linkDao.clear()
        syncStore.save(enabled = false, calendarId = null)
    }

    private fun titleFor(kind: EventKind): String = context.getString(
        when (kind) {
            EventKind.REMOVE -> R.string.event_remove
            EventKind.INSERT -> R.string.event_insert
            EventKind.SWAP -> R.string.event_swap
        },
    )

    companion object { const val WORK_NAME = "calendar_sync" }
}
```

`data/calendar/CalendarSyncWorker.kt`:
```kotlin
package com.nobby.ring.data.calendar

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CalendarSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncer: CalendarSyncer,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (syncer.syncNow().isSuccess) Result.success() else Result.retry()
}
```

`data/calendar/CalendarModule.kt`:
```kotlin
package com.nobby.ring.data.calendar

import com.nobby.ring.notifications.ScheduleChangeListener
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarModule {
    @Binds abstract fun source(impl: DeviceCalendarSource): CalendarSource
    @Binds @IntoSet abstract fun listener(impl: CalendarSyncer): ScheduleChangeListener
}
```

- [ ] **Step 4: Run the syncer test**

Same command as Step 2. Expected: 3 tests pass.

- [ ] **Step 5: Write the settings ViewModel and the two screens**

`ui/settings/CalendarSettingsViewModel.kt`:
```kotlin
package com.nobby.ring.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobby.ring.data.calendar.CalendarSource
import com.nobby.ring.data.calendar.CalendarSyncer
import com.nobby.ring.data.calendar.DeviceCalendar
import com.nobby.ring.data.calendar.FoundEvent
import com.nobby.ring.data.prefs.CalendarSyncStore
import com.nobby.ring.domain.NobbyClock
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import javax.inject.Inject

data class CalendarSettingsUiState(
    val enabled: Boolean = false,
    val calendars: List<DeviceCalendar> = emptyList(),
    val selectedId: Long? = null,
    val lastSync: LocalDateTime? = null,
    val permissionGranted: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val from: LocalDate,
    val to: LocalDate,
    val results: List<FoundEvent> = emptyList(),
    val selectedResults: Set<Long> = emptySet(),
)

@HiltViewModel
class CalendarSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val source: CalendarSource,
    private val syncer: CalendarSyncer,
    private val store: CalendarSyncStore,
    private val clock: NobbyClock,
) : ViewModel() {
    private val local = MutableStateFlow(
        CalendarSettingsUiState(from = clock.now().date, to = LocalDate(clock.now().year + 1, clock.now().monthNumber, 1), permissionGranted = hasPermission()),
    )

    val uiState: StateFlow<CalendarSettingsUiState> = combine(local, store.enabled, store.calendarId, store.lastSync) { l, enabled, id, last ->
        l.copy(enabled = enabled, selectedId = id, lastSync = last)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    fun refreshPermission() {
        local.update { it.copy(permissionGranted = hasPermission()) }
        if (hasPermission()) viewModelScope.launch { local.update { it.copy(calendars = source.listWritable()) } }
    }

    fun setEnabled(enabled: Boolean) = viewModelScope.launch {
        if (!enabled) syncer.disconnect() else store.save(true, uiState.value.selectedId ?: uiState.value.calendars.firstOrNull()?.id).also { syncNow() }
    }

    fun select(id: Long) = viewModelScope.launch { store.save(true, id); syncNow() }

    fun syncNow() = viewModelScope.launch {
        syncer.syncNow().onFailure { e -> local.update { it.copy(error = e.message) } }
    }

    fun setQuery(q: String) = local.update { it.copy(query = q) }
    fun setFrom(d: LocalDate) = local.update { it.copy(from = d) }
    fun setTo(d: LocalDate) = local.update { it.copy(to = d) }

    fun search() = viewModelScope.launch {
        val s = uiState.value
        val id = s.selectedId ?: return@launch
        val regex = runCatching { Regex(s.query, RegexOption.IGNORE_CASE) }.getOrElse { Regex(Regex.escape(s.query), RegexOption.IGNORE_CASE) }
        val from = s.from.atStartOfDayIn(clock.zone).toEpochMilliseconds()
        val to = s.to.atTime(23, 59).toInstant(clock.zone).toEpochMilliseconds()
        runCatching { source.search(id, regex, from, to) }
            .onSuccess { r -> local.update { it.copy(results = r, selectedResults = r.map { f -> f.id }.toSet(), error = null) } }
            .onFailure { e -> local.update { it.copy(error = e.message) } }
    }

    fun toggleSelection(id: Long) = local.update { it.copy(selectedResults = if (id in it.selectedResults) it.selectedResults - id else it.selectedResults + id) }

    fun deleteSelected() = viewModelScope.launch {
        val ids = uiState.value.selectedResults
        ids.forEach { runCatching { source.delete(it) } }
        local.update { it.copy(results = it.results.filter { r -> r.id !in ids }, selectedResults = emptySet()) }
    }

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
}
```

`ui/settings/CalendarSettingsScreen.kt`:
```kotlin
package com.nobby.ring.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.ui.components.formatDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSettingsScreen(onBack: () -> Unit, onCleanup: () -> Unit, viewModel: CalendarSettingsViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.refreshPermission() }
    LaunchedEffect(Unit) { viewModel.refreshPermission() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_calendar)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (!ui.permissionGranted) {
                Text(stringResource(R.string.calendar_sync_permission))
                Button(onClick = { launcher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) }) {
                    Text(stringResource(R.string.onb_calendar_connect))
                }
                return@Column
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.calendar_sync_enable), Modifier.weight(1f))
                Switch(checked = ui.enabled, onCheckedChange = viewModel::setEnabled)
            }
            if (ui.enabled) {
                Text(stringResource(R.string.calendar_sync_choose), style = MaterialTheme.typography.titleSmall)
                ui.calendars.forEach { cal ->
                    ListItem(
                        headlineContent = { Text(cal.name) },
                        supportingContent = { Text(cal.account) },
                        leadingContent = { RadioButton(selected = cal.id == ui.selectedId, onClick = { viewModel.select(cal.id) }) },
                    )
                }
                ui.lastSync?.let { Text(stringResource(R.string.calendar_sync_last, formatDateTime(it)), style = MaterialTheme.typography.bodySmall) }
                Row { OutlinedButton(onClick = viewModel::syncNow) { Text(stringResource(R.string.calendar_sync_now)) } }
                OutlinedButton(onClick = onCleanup, Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.calendar_cleanup)) }
            }
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
```

`ui/settings/CalendarCleanupScreen.kt`:
```kotlin
package com.nobby.ring.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.ui.components.DatePickerField
import com.nobby.ring.ui.components.formatDateTime
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarCleanupScreen(onBack: () -> Unit, viewModel: CalendarSettingsViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.calendar_cleanup)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            OutlinedTextField(ui.query, viewModel::setQuery, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.cleanup_query)) })
            Row(Modifier.padding(vertical = 8.dp)) {
                DatePickerField(ui.from, viewModel::setFrom, stringResource(R.string.cleanup_from), Modifier.weight(1f))
                DatePickerField(ui.to, viewModel::setTo, stringResource(R.string.cleanup_to), Modifier.weight(1f))
            }
            Button(onClick = viewModel::search, enabled = ui.query.isNotBlank()) { Text(stringResource(R.string.cleanup_search)) }
            if (ui.results.isEmpty()) Text(stringResource(R.string.cleanup_none), Modifier.padding(top = 16.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(ui.results, key = { it.id }) { r ->
                    ListItem(
                        headlineContent = { Text(r.title) },
                        supportingContent = { Text(formatDateTime(Instant.fromEpochMilliseconds(r.startMillis).toLocalDateTime(TimeZone.currentSystemDefault()))) },
                        leadingContent = { Checkbox(checked = r.id in ui.selectedResults, onCheckedChange = { viewModel.toggleSelection(r.id) }) },
                    )
                }
            }
            Button(onClick = viewModel::deleteSelected, enabled = ui.selectedResults.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cleanup_delete_selected, ui.selectedResults.size))
            }
        }
    }
}
```

- [ ] **Step 6: Add routes to the nav host**

In `NobbyNavHost.kt` add:
```kotlin
            composable(Routes.SETTINGS_CALENDAR) {
                com.nobby.ring.ui.settings.CalendarSettingsScreen(onBack = { navController.popBackStack() }, onCleanup = { navController.navigate(Routes.SETTINGS_CALENDAR_CLEANUP) })
            }
            composable(Routes.SETTINGS_CALENDAR_CLEANUP) {
                com.nobby.ring.ui.settings.CalendarCleanupScreen(onBack = { navController.popBackStack() })
            }
```

- [ ] **Step 7: Run tests and build**

```bash
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain -q
```

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "feat(calendar): device calendar sync with diffing, worker and cleanup tool"
```

---

### Task 14: Glance widget with Single / Multi modes

**Files:**
- Create: `widget/WidgetModel.kt`, `widget/RingWidget.kt`, `widget/RingWidgetReceiver.kt`, `widget/WidgetUpdater.kt`, `widget/WidgetWorker.kt`, `widget/WidgetModule.kt`
- Create: `res/xml/ring_widget_info.xml`, `res/layout/widget_loading.xml`
- Modify: `AndroidManifest.xml`, `NobbyApp.kt` (schedule daily refresh)
- Test: `app/src/test/java/com/nobby/ring/widget/WidgetModelTest.kt`

**Interfaces:**
- Produces:
  - `data class WidgetRowData(row: WidgetRow, label: String, value: String, urgent: Boolean)`, `data class WidgetData(rows: List<WidgetRowData>, dueEventId: String?)`.
  - `object WidgetModel { fun build(state: ScheduleState?, config: WidgetConfig, strings: WidgetStrings): WidgetData }` with `data class WidgetStrings(nextAny, nextRemove, nextInsert, overdue, cycleDay, none, inDays: (Int) -> String, overdueDays: (Int) -> String, cycleDayFmt: (Int, Int) -> String, kindLabel: (EventKind) -> String)` — pure, tested.
  - `class RingWidget : GlanceAppWidget` (`SizeMode.Responsive` with `DpSize(110,50)`, `DpSize(110,110)`, `DpSize(250,110)`), `class RingWidgetReceiver : GlanceAppWidgetReceiver`.
  - `class WidgetUpdater(@ApplicationContext ctx) : ScheduleChangeListener { suspend fun updateAll() }` calling `RingWidget().updateAll(context)`.
  - `WidgetWorker` (`@HiltWorker`, periodic 24 h, unique name `widget_refresh`) calls `updateAll()`.
  - `DoneAction : ActionCallback` confirms the due event with `ShiftPolicy.KEEP` through an `EntryPoint`.

- [ ] **Step 1: Write the failing model test**

```kotlin
package com.nobby.ring.widget

import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.domain.model.WidgetMode
import com.nobby.ring.domain.model.WidgetRow
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetModelTest {
    private val strings = WidgetStrings(
        nextAny = "Next", nextRemove = "Remove", nextInsert = "Insert", overdue = "Overdue", cycleDay = "Cycle", none = "All done",
        inDays = { "in $it d" }, overdueDays = { "$it d overdue" }, cycleDayFmt = { d, n -> "Day $d of $n" },
        kindLabel = { it.name.lowercase() },
    )
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0), 2)

    @Test
    fun `single mode shows the next event countdown`() {
        val state = ScheduleRepository.buildState(config, emptyList(), LocalDateTime(2026, 11, 12, 12, 0))
        val data = WidgetModel.build(state, WidgetConfig(WidgetMode.SINGLE, emptyList()), strings)
        assertEquals(1, data.rows.size)
        assertEquals("remove", data.rows[0].label)
        assertEquals("in 10 d", data.rows[0].value)
        assertNull(data.dueEventId)
    }

    @Test
    fun `multi mode shows configured rows and overdue is urgent`() {
        val state = ScheduleRepository.buildState(config, emptyList(), LocalDateTime(2026, 11, 24, 12, 0))
        val data = WidgetModel.build(state, WidgetConfig(WidgetMode.MULTI, listOf(WidgetRow.NEXT_INSERT, WidgetRow.OVERDUE, WidgetRow.CYCLE_DAY)), strings)
        assertEquals(3, data.rows.size)
        assertEquals("in 5 d", data.rows[0].value)
        assertEquals("2 d overdue", data.rows[1].value)
        assertTrue(data.rows[1].urgent)
        assertEquals("Day 24 of 28", data.rows[2].value)
        assertEquals("REMOVE@2026-11-22T21:00", data.dueEventId)
    }

    @Test
    fun `null state yields the none row`() {
        val data = WidgetModel.build(null, WidgetConfig(), strings)
        assertEquals("All done", data.rows[0].value)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.widget.WidgetModelTest" --console=plain -q
```

- [ ] **Step 3: Write WidgetModel**

`widget/WidgetModel.kt`:
```kotlin
package com.nobby.ring.widget

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.ScheduleState
import com.nobby.ring.domain.model.ScheduledEvent
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.domain.model.WidgetMode
import com.nobby.ring.domain.model.WidgetRow

data class WidgetStrings(
    val nextAny: String, val nextRemove: String, val nextInsert: String, val overdue: String, val cycleDay: String, val none: String,
    val inDays: (Int) -> String, val overdueDays: (Int) -> String, val cycleDayFmt: (Int, Int) -> String, val kindLabel: (EventKind) -> String,
)

data class WidgetRowData(val row: WidgetRow, val label: String, val value: String, val urgent: Boolean)
data class WidgetData(val rows: List<WidgetRowData>, val dueEventId: String?)

object WidgetModel {
    fun build(state: ScheduleState?, config: WidgetConfig, s: WidgetStrings): WidgetData {
        if (state == null) return WidgetData(listOf(WidgetRowData(WidgetRow.NEXT_ANY, s.nextAny, s.none, false)), null)
        val due = state.nextEvent?.takeIf { state.daysUntilNext != null && state.daysUntilNext!! <= 0 }?.id
        val rows = when (config.mode) {
            WidgetMode.SINGLE -> listOf(WidgetRow.NEXT_ANY)
            WidgetMode.MULTI -> config.rows.take(WidgetConfig.MAX_ROWS).ifEmpty { listOf(WidgetRow.NEXT_ANY) }
        }
        return WidgetData(rows.map { row(it, state, s) }, due)
    }

    private fun row(row: WidgetRow, state: ScheduleState, s: WidgetStrings): WidgetRowData = when (row) {
        WidgetRow.NEXT_ANY -> countdown(row, state.nextEvent, state, s, labelOverride = null)
        WidgetRow.NEXT_REMOVE -> countdown(row, state.pendingEvents.firstOrNull { it.kind != EventKind.INSERT }, state, s, s.nextRemove)
        WidgetRow.NEXT_INSERT -> countdown(row, state.pendingEvents.firstOrNull { it.kind != EventKind.REMOVE }, state, s, s.nextInsert)
        WidgetRow.OVERDUE -> {
            val o = state.overdue
            if (o == null) WidgetRowData(row, s.overdue, "–", false)
            else WidgetRowData(row, s.overdue, s.overdueDays(-(daysUntil(o.event, state))), true)
        }
        WidgetRow.CYCLE_DAY -> WidgetRowData(row, s.cycleDay, s.cycleDayFmt(state.position.dayInCycle, state.position.cycleLengthDays), false)
    }

    private fun countdown(row: WidgetRow, event: ScheduledEvent?, state: ScheduleState, s: WidgetStrings, labelOverride: String?): WidgetRowData {
        if (event == null) return WidgetRowData(row, labelOverride ?: s.nextAny, s.none, false)
        val days = daysUntil(event, state)
        val label = labelOverride ?: s.kindLabel(event.kind)
        return if (days < 0) WidgetRowData(row, label, s.overdueDays(-days), true) else WidgetRowData(row, label, s.inDays(days), false)
    }

    private fun daysUntil(event: ScheduledEvent, state: ScheduleState): Int =
        event.plannedAt.date.toEpochDays() - state.now.date.toEpochDays()
}
```

- [ ] **Step 4: Run to verify it passes**

Same command. Expected: 3 tests pass.

- [ ] **Step 5: Write the Glance widget, receiver, updater, worker, module**

`widget/RingWidget.kt`:
```kotlin
package com.nobby.ring.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
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
import com.nobby.ring.domain.engine.ShiftPolicy
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.ThemeMode
import com.nobby.ring.domain.model.WidgetMode
import com.nobby.ring.notifications.ScheduleChangeNotifier
import com.nobby.ring.ui.theme.accent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
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

class RingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val state = ep.schedule().currentState()
        val config = ep.widgetConfig().config.first()
        val theme = ep.theme().theme.first()
        val strings = WidgetStrings(
            nextAny = context.getString(R.string.widget_row_next_any),
            nextRemove = context.getString(R.string.widget_row_next_remove),
            nextInsert = context.getString(R.string.widget_row_next_insert),
            overdue = context.getString(R.string.widget_row_overdue),
            cycleDay = context.getString(R.string.widget_row_cycle_day),
            none = context.getString(R.string.widget_none),
            inDays = { context.getString(R.string.widget_in_days, it) },
            overdueDays = { context.getString(R.string.widget_overdue_days, it) },
            cycleDayFmt = { d, n -> context.getString(R.string.cycle_day, d, n) },
            kindLabel = { k ->
                context.getString(when (k) { EventKind.REMOVE -> R.string.event_remove; EventKind.INSERT -> R.string.event_insert; EventKind.SWAP -> R.string.event_swap })
            },
        )
        val data = WidgetModel.build(state, config, strings)
        val dark = when (theme.mode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        val accent = theme.palette.accent(dark)
        val bg = if (dark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
        val fg = if (dark) Color(0xFFF2F2F2) else Color(0xFF1C1C1E)

        provideContent { WidgetContent(data, config.mode, accent, bg, fg) }
    }

    @Composable
    private fun WidgetContent(data: WidgetData, mode: WidgetMode, accent: Color, bg: Color, fg: Color) {
        val size = LocalSize.current
        val context = LocalContext.current
        Column(
            GlanceModifier.fillMaxSize().background(ColorProvider(bg)).cornerRadius(20.dp).padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (mode == WidgetMode.SINGLE || size.height < MEDIUM.height) {
                val row = data.rows.first()
                Text(row.label, style = TextStyle(color = ColorProvider(fg), fontSize = 12.sp))
                Text(row.value, style = TextStyle(color = ColorProvider(if (row.urgent) Color(0xFFD32F2F) else accent), fontSize = if (size.height < MEDIUM.height) 20.sp else 32.sp, fontWeight = FontWeight.Bold))
            } else {
                data.rows.forEach { row ->
                    Row(GlanceModifier.fillMaxWidth()) {
                        Text(row.label, GlanceModifier.defaultWeight(), style = TextStyle(color = ColorProvider(fg), fontSize = 12.sp))
                        Text(row.value, style = TextStyle(color = ColorProvider(if (row.urgent) Color(0xFFD32F2F) else accent), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    }
                    Spacer(GlanceModifier.height(4.dp))
                }
            }
            if (data.dueEventId != null && size.height >= MEDIUM.height) {
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    context.getString(R.string.home_mark_done),
                    GlanceModifier.background(ColorProvider(accent)).cornerRadius(12.dp).padding(horizontal = 12.dp, vertical = 6.dp)
                        .clickable(actionRunCallback<DoneAction>()),
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 50.dp)
        val MEDIUM = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
    }
}

class DoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: androidx.glance.action.ActionParameters) {
        val ep = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val state = ep.schedule().currentState() ?: return
        val next = state.nextEvent ?: return
        ep.schedule().confirm(next.id, ep.clock().now(), ShiftPolicy.KEEP)
        ep.changeNotifier().notifyChanged()
    }
}
```

`widget/RingWidgetReceiver.kt`:
```kotlin
package com.nobby.ring.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class RingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RingWidget()
}
```

`widget/WidgetUpdater.kt`:
```kotlin
package com.nobby.ring.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.nobby.ring.notifications.ScheduleChangeListener
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetUpdater @Inject constructor(@ApplicationContext private val context: Context) : ScheduleChangeListener {
    override suspend fun onScheduleChanged() = updateAll()
    suspend fun updateAll() = RingWidget().updateAll(context)
}
```

`widget/WidgetWorker.kt`:
```kotlin
package com.nobby.ring.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class WidgetWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val updater: WidgetUpdater,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result { updater.updateAll(); return Result.success() }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "widget_refresh", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetWorker>(24, TimeUnit.HOURS).build(),
            )
        }
    }
}
```

`widget/WidgetModule.kt`:
```kotlin
package com.nobby.ring.widget

import com.nobby.ring.notifications.ScheduleChangeListener
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {
    @Binds @IntoSet abstract fun updater(impl: WidgetUpdater): ScheduleChangeListener
}
```

- [ ] **Step 6: Resources, manifest and app hook**

`res/xml/ring_widget_info.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="110dp"
    android:minHeight="50dp"
    android:targetCellWidth="2"
    android:targetCellHeight="1"
    android:maxResizeWidth="320dp"
    android:maxResizeHeight="180dp"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/widget_loading"
    android:description="@string/widget_description"
    android:widgetCategory="home_screen" />
```

`res/layout/widget_loading.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:gravity="center"
    android:text="@string/app_name" />
```

Manifest, inside `<application>`:
```xml
        <receiver
            android:name=".widget.RingWidgetReceiver"
            android:exported="true"
            android:label="@string/app_name">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data android:name="android.appwidget.provider" android:resource="@xml/ring_widget_info" />
        </receiver>
```

`NobbyApp.onCreate()` add: `com.nobby.ring.widget.WidgetWorker.schedule(this)`.

- [ ] **Step 7: Run tests and build**

```bash
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain -q
```

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "feat(widget): Glance widget with single/multi layouts, done action and daily refresh"
```

---

### Task 15: Settings (hub, schedule edit with preview, reminders CRUD, appearance, widget)

**Files:**
- Create: `ui/settings/SettingsScreen.kt`, `ui/settings/SettingsViewModel.kt`, `ui/settings/ScheduleSettingsScreen.kt`, `ui/settings/RemindersScreen.kt`, `ui/settings/ReminderEditDialog.kt`, `ui/settings/AppearanceScreen.kt`, `ui/settings/WidgetSettingsScreen.kt`
- Modify: `ui/navigation/NobbyNavHost.kt`
- Test: `app/src/test/java/com/nobby/ring/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Produces:
  - `SettingsViewModel { theme: StateFlow<ThemeSpec>; widget: StateFlow<WidgetConfig>; rules: StateFlow<List<ReminderRule>>; overdue: StateFlow<OverdueConfig>; schedule: StateFlow<ScheduleState?>; draft: StateFlow<ScheduleDraft?>; fun setTheme(ThemeSpec); fun setWidget(WidgetConfig); fun saveRule(ReminderRule); fun deleteRule(Long); fun setOverdue(OverdueConfig); fun startScheduleDraft(); fun updateDraft((ScheduleDraft)->ScheduleDraft); fun applyDraft(); fun discardDraft() }`.
  - `data class ScheduleDraft(wearDays, pauseDays, timeOfDay, anchor: LocalDateTime, horizonMonths)` with `fun toConfig(): ScheduleConfig` and `val preview: List<ScheduledEvent>` = first 4 events of `ScheduleEngine.generate(toConfig())`.
  - `applyDraft()` saves the config, then `ScheduleChangeNotifier.notifyChanged()` (which reschedules alarms, re-syncs the calendar and refreshes the widget).
  - Screens: `SettingsScreen(onNavigate: (String) -> Unit)`, `ScheduleSettingsScreen(onBack)`, `RemindersScreen(onBack)`, `AppearanceScreen(onBack)`, `WidgetSettingsScreen(onBack)`.

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
package com.nobby.ring.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.data.db.NobbyDatabase
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.data.prefs.OverdueConfigStore
import com.nobby.ring.data.prefs.ThemeStore
import com.nobby.ring.data.prefs.WidgetConfigStore
import com.nobby.ring.data.repo.ReminderRepository
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.notifications.ScheduleChangeListener
import com.nobby.ring.notifications.ScheduleChangeNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
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
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var vm: SettingsViewModel
    private lateinit var configStore: MethodConfigStore
    private var changes = 0

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NobbyDatabase::class.java).allowMainThreadQueries().build()
        val ds = PreferenceDataStoreFactory.create { File(context.cacheDir, "set-${System.nanoTime()}.preferences_pb") }
        val clock = object : NobbyClock { override fun now() = LocalDateTime(2026, 11, 5, 12, 0); override val zone = TimeZone.UTC }
        configStore = MethodConfigStore(ds)
        val schedule = ScheduleRepository(configStore, db.eventLogDao(), clock)
        schedule.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0)))
        vm = SettingsViewModel(
            schedule, ReminderRepository(db.reminderRuleDao()), ThemeStore(ds), WidgetConfigStore(ds), OverdueConfigStore(ds),
            ScheduleChangeNotifier(setOf(object : ScheduleChangeListener { override suspend fun onScheduleChanged() { changes++ } })),
        )
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `draft preview reflects a one-week shift and apply persists it`() = runTest(dispatcher) {
        dispatcher.scheduler.advanceUntilIdle()
        vm.startScheduleDraft(); dispatcher.scheduler.advanceUntilIdle()
        vm.updateDraft { it.copy(anchor = LocalDateTime(2026, 11, 8, 21, 0)) }
        assertEquals(LocalDateTime(2026, 11, 29, 21, 0), vm.draft.value!!.preview[0].plannedAt)
        vm.applyDraft(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(LocalDateTime(2026, 11, 8, 21, 0), configStore.config.first()!!.anchor)
        assertEquals(1, changes)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.nobby.ring.ui.settings.SettingsViewModelTest" --console=plain -q
```

- [ ] **Step 3: Write the ViewModel**

`ui/settings/SettingsViewModel.kt`:
```kotlin
package com.nobby.ring.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobby.ring.data.prefs.OverdueConfigStore
import com.nobby.ring.data.prefs.ThemeStore
import com.nobby.ring.data.prefs.WidgetConfigStore
import com.nobby.ring.data.repo.ReminderRepository
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.engine.ScheduleEngine
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.OverdueConfig
import com.nobby.ring.domain.model.ReminderRule
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.ScheduleState
import com.nobby.ring.domain.model.ScheduledEvent
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.notifications.ScheduleChangeNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import javax.inject.Inject

data class ScheduleDraft(
    val wearDays: Int,
    val pauseDays: Int,
    val timeOfDay: LocalTime,
    val anchor: LocalDateTime,
    val horizonMonths: Int,
) {
    fun toConfig(): ScheduleConfig = ScheduleConfig(Method.ring(wearDays, pauseDays, timeOfDay), anchor, horizonMonths)
    val preview: List<ScheduledEvent> get() = ScheduleEngine.generate(toConfig()).take(4)
    val wearWarning: Boolean get() = wearDays > ScheduleConfig.SAFE_MAX_WEAR
    val pauseWarning: Boolean get() = pauseDays > ScheduleConfig.SAFE_MAX_PAUSE

    companion object {
        fun from(config: ScheduleConfig) = ScheduleDraft(config.method.wearDays, config.method.pauseDays, config.method.timeOfDay, config.anchor, config.horizonMonths)
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val scheduleRepo: ScheduleRepository,
    private val reminders: ReminderRepository,
    private val themeStore: ThemeStore,
    private val widgetStore: WidgetConfigStore,
    private val overdueStore: OverdueConfigStore,
    private val notifier: ScheduleChangeNotifier,
) : ViewModel() {
    val schedule: StateFlow<ScheduleState?> = scheduleRepo.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val theme: StateFlow<ThemeSpec> = themeStore.theme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeSpec())
    val widget: StateFlow<WidgetConfig> = widgetStore.config.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WidgetConfig())
    val rules: StateFlow<List<ReminderRule>> = reminders.rules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val overdue: StateFlow<OverdueConfig> = overdueStore.config.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverdueConfig())

    private val _draft = MutableStateFlow<ScheduleDraft?>(null)
    val draft: StateFlow<ScheduleDraft?> = _draft

    fun setTheme(theme: ThemeSpec) = viewModelScope.launch { themeStore.save(theme); notifier.notifyChanged() }
    fun setWidget(config: WidgetConfig) = viewModelScope.launch { widgetStore.save(config); notifier.notifyChanged() }
    fun setOverdue(config: OverdueConfig) = viewModelScope.launch { overdueStore.save(config); notifier.notifyChanged() }
    fun saveRule(rule: ReminderRule) = viewModelScope.launch { reminders.upsert(rule); notifier.notifyChanged() }
    fun deleteRule(id: Long) = viewModelScope.launch { reminders.delete(id); notifier.notifyChanged() }

    fun startScheduleDraft() = viewModelScope.launch {
        val config = scheduleRepo.currentState()?.config ?: return@launch
        _draft.value = ScheduleDraft.from(config)
    }

    fun updateDraft(transform: (ScheduleDraft) -> ScheduleDraft) = _draft.update { it?.let(transform) }

    fun applyDraft() = viewModelScope.launch {
        val d = _draft.value ?: return@launch
        scheduleRepo.updateConfig(d.toConfig())
        _draft.value = null
        notifier.notifyChanged()
    }

    fun discardDraft() { _draft.value = null }
}
```

- [ ] **Step 4: Run the test**

Same command. Expected: 1 test passes.

- [ ] **Step 5: Write the screens**

`ui/settings/SettingsScreen.kt`:
```kotlin
package com.nobby.ring.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nobby.ring.BuildConfig
import com.nobby.ring.R
import com.nobby.ring.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigate: (String) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) }) { padding ->
        Column(Modifier.padding(padding)) {
            listOf(
                Triple(R.string.settings_schedule, Icons.Default.Schedule, Routes.SETTINGS_SCHEDULE),
                Triple(R.string.settings_reminders, Icons.Default.Notifications, Routes.SETTINGS_REMINDERS),
                Triple(R.string.settings_calendar, Icons.Default.CalendarMonth, Routes.SETTINGS_CALENDAR),
                Triple(R.string.settings_widget, Icons.Default.Widgets, Routes.SETTINGS_WIDGET),
                Triple(R.string.settings_appearance, Icons.Default.Palette, Routes.SETTINGS_APPEARANCE),
            ).forEach { (label, icon, route) ->
                ListItem(
                    headlineContent = { Text(stringResource(label)) },
                    leadingContent = { Icon(icon, null) },
                    modifier = Modifier.clickable { onNavigate(route) },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about)) },
                supportingContent = { Text(stringResource(R.string.settings_about_body, BuildConfig.VERSION_NAME)) },
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
```
(`BuildConfig` needs `buildFeatures { buildConfig = true }` in `app/build.gradle.kts`.)

`ui/settings/ScheduleSettingsScreen.kt`:
```kotlin
package com.nobby.ring.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.ui.components.DatePickerField
import com.nobby.ring.ui.components.NumberStepper
import com.nobby.ring.ui.components.TimePickerField
import com.nobby.ring.ui.components.formatDateTime
import com.nobby.ring.ui.components.label
import kotlinx.datetime.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.startScheduleDraft() }
    val d = draft ?: return

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_schedule)) },
            navigationIcon = { IconButton(onClick = { viewModel.discardDraft(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberStepper(d.wearDays, { v -> viewModel.updateDraft { it.copy(wearDays = v) } }, ScheduleConfig.MIN_WEAR..ScheduleConfig.MAX_WEAR, stringResource(R.string.onb_wear_days))
            if (d.wearWarning) Text(stringResource(R.string.onb_safety_wear), color = MaterialTheme.colorScheme.error)
            NumberStepper(d.pauseDays, { v -> viewModel.updateDraft { it.copy(pauseDays = v) } }, ScheduleConfig.MIN_PAUSE..ScheduleConfig.MAX_PAUSE, stringResource(R.string.onb_pause_days))
            if (d.pauseWarning) Text(stringResource(R.string.onb_safety_pause), color = MaterialTheme.colorScheme.error)
            TimePickerField(d.timeOfDay, { t -> viewModel.updateDraft { it.copy(timeOfDay = t, anchor = LocalDateTime(it.anchor.date, t)) } }, stringResource(R.string.onb_time_of_day))
            Text(stringResource(R.string.onb_anchor_title), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DatePickerField(d.anchor.date, { date -> viewModel.updateDraft { it.copy(anchor = LocalDateTime(date, it.anchor.time)) } }, stringResource(R.string.onb_anchor_date))
                TimePickerField(d.anchor.time, { t -> viewModel.updateDraft { it.copy(anchor = LocalDateTime(it.anchor.date, t)) } }, stringResource(R.string.onb_anchor_time))
            }
            NumberStepper(d.horizonMonths, { v -> viewModel.updateDraft { it.copy(horizonMonths = v) } }, ScheduleConfig.MIN_HORIZON..ScheduleConfig.MAX_HORIZON, stringResource(R.string.schedule_horizon))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.schedule_preview_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.schedule_preview_body))
                    d.preview.forEach { e -> Text("• ${e.kind.label()} — ${formatDateTime(e.plannedAt)}") }
                }
            }
            Button(onClick = { viewModel.applyDraft(); onBack() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.schedule_apply)) }
        }
    }
}
```

`ui/settings/ReminderEditDialog.kt`:
```kotlin
package com.nobby.ring.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nobby.ring.R
import com.nobby.ring.domain.model.Importance
import com.nobby.ring.domain.model.ReminderRule
import com.nobby.ring.domain.model.ReminderTarget
import com.nobby.ring.ui.components.NumberStepper
import com.nobby.ring.ui.components.TimePickerField
import com.nobby.ring.ui.components.label
import kotlinx.datetime.LocalTime

@Composable
fun ReminderEditDialog(initial: ReminderRule, onSave: (ReminderRule) -> Unit, onDismiss: () -> Unit) {
    var rule by remember { mutableStateOf(initial) }
    var hoursMode by remember { mutableStateOf(initial.offsetHours != null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.id == 0L) R.string.reminder_add else R.string.reminder_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ReminderTarget.entries.forEachIndexed { i, t ->
                        SegmentedButton(selected = rule.target == t, onClick = { rule = rule.copy(target = t) }, shape = SegmentedButtonDefaults.itemShape(i, ReminderTarget.entries.size)) {
                            Text(stringResource(when (t) { ReminderTarget.ANY -> R.string.reminder_target_any; ReminderTarget.REMOVE -> R.string.reminder_target_remove; ReminderTarget.INSERT -> R.string.reminder_target_insert }))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !hoursMode, onClick = { hoursMode = false; rule = rule.copy(offsetHours = null, timeOfDay = rule.timeOfDay ?: LocalTime(20, 0)) }, label = { Text(stringResource(R.string.reminder_mode_days)) })
                    FilterChip(selected = hoursMode, onClick = { hoursMode = true; rule = rule.copy(offsetHours = rule.offsetHours ?: 2) }, label = { Text(stringResource(R.string.reminder_mode_hours)) })
                }
                if (hoursMode) {
                    NumberStepper(rule.offsetHours ?: 2, { rule = rule.copy(offsetHours = it) }, 1..72, stringResource(R.string.reminder_mode_hours))
                } else {
                    NumberStepper(rule.offsetDays, { rule = rule.copy(offsetDays = it) }, 0..14, stringResource(R.string.reminder_mode_days))
                    TimePickerField(rule.timeOfDay ?: LocalTime(20, 0), { rule = rule.copy(timeOfDay = it) }, stringResource(R.string.onb_time_of_day))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Importance.entries.forEach { imp ->
                        FilterChip(selected = rule.importance == imp, onClick = { rule = rule.copy(importance = imp) }, label = { Text(imp.label()) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.reminder_enabled), Modifier.weight(1f))
                    Switch(checked = rule.enabled, onCheckedChange = { rule = rule.copy(enabled = it) })
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(rule) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
```

`ui/settings/RemindersScreen.kt`:
```kotlin
package com.nobby.ring.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.domain.model.Importance
import com.nobby.ring.domain.model.ReminderRule
import com.nobby.ring.domain.model.ReminderTarget
import com.nobby.ring.ui.components.NumberStepper
import com.nobby.ring.ui.components.formatTime
import com.nobby.ring.ui.components.label
import kotlinx.datetime.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val overdue by viewModel.overdue.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ReminderRule?>(null) }

    editing?.let { rule ->
        ReminderEditDialog(rule, onSave = { viewModel.saveRule(it); editing = null }, onDismiss = { editing = null })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_reminders)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = ReminderRule(0, ReminderTarget.ANY, 1, LocalTime(20, 0), null, Importance.NORMAL, true) }) {
                Icon(Icons.Default.Add, stringResource(R.string.reminder_add))
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            items(rules, key = { it.id }) { rule ->
                ListItem(
                    headlineContent = { Text(ruleText(rule)) },
                    supportingContent = { Text("${targetText(rule.target)} · ${rule.importance.label()}" + if (!rule.enabled) " · off" else "") },
                    trailingContent = { IconButton(onClick = { viewModel.deleteRule(rule.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.action_delete)) } },
                    modifier = Modifier.clickable { editing = rule },
                )
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    NumberStepper(overdue.repeatEveryHours, { viewModel.setOverdue(overdue.copy(repeatEveryHours = it)) }, 1..24, stringResource(R.string.overdue_repeat))
                    NumberStepper(overdue.snoozeHours, { viewModel.setOverdue(overdue.copy(snoozeHours = it)) }, 1..12, stringResource(R.string.overdue_snooze))
                }
            }
        }
    }
}

@Composable
private fun ruleText(rule: ReminderRule): String = when {
    rule.offsetHours != null -> stringResource(R.string.reminder_hours_before, rule.offsetHours)
    rule.offsetDays == 0 -> stringResource(R.string.reminder_same_day, formatTime(rule.timeOfDay ?: LocalTime(20, 0)))
    else -> stringResource(R.string.reminder_day_before, rule.offsetDays, formatTime(rule.timeOfDay ?: LocalTime(20, 0)))
}

@Composable
private fun targetText(t: ReminderTarget): String = stringResource(
    when (t) { ReminderTarget.ANY -> R.string.reminder_target_any; ReminderTarget.REMOVE -> R.string.reminder_target_remove; ReminderTarget.INSERT -> R.string.reminder_target_insert },
)
```

`ui/settings/AppearanceScreen.kt`:
```kotlin
package com.nobby.ring.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.ThemeMode
import com.nobby.ring.ui.components.label
import com.nobby.ring.ui.theme.accent

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_appearance)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.appearance_palette), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Palette.entries.forEach { p ->
                    FilterChip(
                        selected = theme.palette == p,
                        onClick = { viewModel.setTheme(theme.copy(palette = p)) },
                        label = { Text(p.label()) },
                        leadingIcon = { Box(Modifier.size(16.dp).clip(CircleShape).background(p.accent(false))) },
                    )
                }
            }
            Text(stringResource(R.string.appearance_mode), style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(selected = theme.mode == m, onClick = { viewModel.setTheme(theme.copy(mode = m)) }, shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)) {
                        Text(stringResource(when (m) { ThemeMode.SYSTEM -> R.string.mode_system; ThemeMode.LIGHT -> R.string.mode_light; ThemeMode.DARK -> R.string.mode_dark }))
                    }
                }
            }
        }
    }
}
```

`ui/settings/WidgetSettingsScreen.kt`:
```kotlin
package com.nobby.ring.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.domain.model.WidgetMode
import com.nobby.ring.domain.model.WidgetRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val cfg by viewModel.widget.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_widget)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.widget_mode), style = MaterialTheme.typography.titleMedium)
            WidgetMode.entries.forEach { m ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = cfg.mode == m, onClick = { viewModel.setWidget(cfg.copy(mode = m)) })
                    Text(stringResource(if (m == WidgetMode.SINGLE) R.string.widget_mode_single else R.string.widget_mode_multi))
                }
            }
            if (cfg.mode == WidgetMode.MULTI) {
                Text(stringResource(R.string.widget_rows), style = MaterialTheme.typography.titleMedium)
                WidgetRow.entries.forEach { row ->
                    val checked = row in cfg.rows
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = checked,
                            enabled = checked || cfg.rows.size < WidgetConfig.MAX_ROWS,
                            onCheckedChange = { on -> viewModel.setWidget(cfg.copy(rows = if (on) cfg.rows + row else cfg.rows - row)) },
                        )
                        Text(stringResource(when (row) {
                            WidgetRow.NEXT_ANY -> R.string.widget_row_next_any
                            WidgetRow.NEXT_REMOVE -> R.string.widget_row_next_remove
                            WidgetRow.NEXT_INSERT -> R.string.widget_row_next_insert
                            WidgetRow.OVERDUE -> R.string.widget_row_overdue
                            WidgetRow.CYCLE_DAY -> R.string.widget_row_cycle_day
                        }))
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 6: Wire the nav host**

Replace the SETTINGS placeholder and add the sub-routes:
```kotlin
            composable(Routes.SETTINGS) { com.nobby.ring.ui.settings.SettingsScreen(onNavigate = { navController.navigate(it) }) }
            composable(Routes.SETTINGS_SCHEDULE) { com.nobby.ring.ui.settings.ScheduleSettingsScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SETTINGS_REMINDERS) { com.nobby.ring.ui.settings.RemindersScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SETTINGS_APPEARANCE) { com.nobby.ring.ui.settings.AppearanceScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SETTINGS_WIDGET) { com.nobby.ring.ui.settings.WidgetSettingsScreen(onBack = { navController.popBackStack() }) }
```

- [ ] **Step 7: Run all tests and build**

```bash
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain -q
```

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "feat(ui): settings hub, schedule editor with preview, reminders, appearance, widget config"
```

---

### Task 16: Emulator verification

**Files:** none created; findings fixed in place in whichever file is responsible.

- [ ] **Step 1: Boot the emulator and install**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
SDK="$LOCALAPPDATA/Android/Sdk"
"$SDK/emulator/emulator.exe" -avd Pixel_3a_API_34_extension_level_7_x86_64 -no-snapshot-load -no-boot-anim &
"$SDK/platform-tools/adb.exe" wait-for-device
until [ "$("$SDK/platform-tools/adb.exe" shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 3; done
./gradlew.bat :app:installDebug --console=plain -q
"$SDK/platform-tools/adb.exe" shell am start -n com.nobby.ring/.MainActivity
```

- [ ] **Step 2: Walk the onboarding** (use Windows-MCP `Screenshot`/`Click`/`Type` on the emulator window): set 21/7, time 21:00, anchor = today − 20 days. Expected: Home shows "1 · day remaining", phase chip "Ring in · day 21 of 21".

- [ ] **Step 3: Verify a reminder fires**

```bash
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
"$ADB" shell dumpsys alarm | grep -A3 com.nobby.ring | head -40
```
Expected: alarms listed for the next REMOVE. Then set the device clock past the fire time with `"$ADB" shell "date $(date -d '+1 day 20:01' +%m%d%H%M%Y)"` (or change the anchor in Settings so an event is overdue) and confirm the URGENT notification appears with Done / Snooze.

- [ ] **Step 4: Verify Done from the notification clears the overdue state and Home updates.**

- [ ] **Step 5: Widget**: long-press the home screen → Widgets → NoBby; add 2×1, 2×2 and 4×2 instances; switch Single/Multi in Settings → Widget; confirm all three re-render within a second and the palette colour follows Appearance.

- [ ] **Step 6: Calendar sync**: on the emulator add a Google-less local calendar is not possible, so create one via adb:
```bash
"$ADB" shell content insert --uri content://com.android.calendar/calendars --bind account_name:s:local --bind account_type:s:LOCAL --bind name:s:Local --bind calendar_displayName:s:Local --bind calendar_access_level:i:700 --bind visible:i:1 --bind sync_events:i:1 --bind ownerAccount:s:local
```
Then Settings → Calendar sync → grant permission → enable → pick "Local" → Sync now. Verify with:
```bash
"$ADB" shell content query --uri content://com.android.calendar/events --projection title:dtstart:description
```
Expected: one row per pending event with `nobby:<id>` in the description. Change the anchor by +7 days in Settings → Schedule → Apply; re-query: dates moved, no duplicates. Run the clean-up tool with query `Ring` and delete; re-query: rows gone.

- [ ] **Step 7: Language**: `"$ADB" shell "setprop persist.sys.locale de-DE; setprop ctl.restart zygote"` (or Settings → System → Languages) and confirm the UI is German.

- [ ] **Step 8: Fix anything found, re-run the full test suite, commit**

```bash
./gradlew.bat :app:testDebugUnitTest --console=plain -q
git add -A && git commit -m "fix: emulator verification fixes"
```

---

## Self-review notes

- Spec §3 events: REMOVE/INSERT/SWAP (Task 2), overdue + safety (Task 3), cycle day/phase (Task 3 + Home), v2 items excluded. ✔
- Spec §5 model: `Method/Phase`, anchor, `ScheduledEvent` ids, `EventLog`, `OverdueInfo`, `ShiftPolicy`, skip-pause, `ReminderRule`, `OverdueConfig` — all in Tasks 2–5/7. ✔
- Spec §6 screens: Onboarding (9), Home (10), Calendar + notes (11), History (11), Settings (13, 15). ✔
- Spec §7 notifications: four channels, exact alarms with fallback, overdue repeat + snooze + escalation, boot reschedule, POST_NOTIFICATIONS in onboarding. ✔ (Task 12)
- Spec §8 calendar: permission, calendar pick, marker, diff sync, clean-up by regex + range, reschedule regenerates. ✔ (Tasks 6, 13, 15)
- Spec §9 widget: responsive 2×1/2×2/4×2, SINGLE/MULTI with rows, palette colours, tap opens app, Done button in larger sizes, daily refresh. ✔ (Task 14). The spec's separate `WidgetConfigActivity` is replaced by the Settings → Widget screen (same `WidgetConfigStore`); no per-instance config in v1.
- Spec §10 theming: 5 palettes × 3 modes. ✔ (Tasks 8, 15)
- Spec §11 error handling: calendar permission/denial paths and exact-alarm fallback (Tasks 12, 13); validation ranges enforced by steppers (Tasks 9, 15). ✔
- Spec §12 testing: domain JVM tests (2–6), Robolectric data/VM tests (7, 9, 10, 12, 13, 15), manual emulator pass (16). Compose UI tests are deferred to the next iteration; the ViewModel tests cover the same flows. ✔
- Type consistency checked: `ScheduleRepository.buildState` companion is introduced in Task 11 and used by Tasks 11 and 14; `ReminderScheduler` constructor gains `DataStore<Preferences>` as its 6th parameter in Task 12 and the Task 10 test is updated there; `HomeViewModel` gains `scheduler` in Task 12.


