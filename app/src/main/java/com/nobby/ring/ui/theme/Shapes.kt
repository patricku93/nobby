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
