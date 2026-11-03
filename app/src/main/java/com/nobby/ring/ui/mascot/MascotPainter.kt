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
