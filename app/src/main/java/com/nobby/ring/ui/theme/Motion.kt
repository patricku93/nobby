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
