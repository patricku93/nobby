package com.nobby.ring.domain.model

enum class Palette { ROSE, LAVENDER, MINT, OCEAN, GRAPHITE }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
data class ThemeSpec(val palette: Palette = Palette.ROSE, val mode: ThemeMode = ThemeMode.SYSTEM)
