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
