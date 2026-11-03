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
