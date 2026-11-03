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
