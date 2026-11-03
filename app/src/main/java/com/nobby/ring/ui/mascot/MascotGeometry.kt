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
