package com.nobby.ring.update

/** A release version `major.minor.patch`. Pure Kotlin: no Android types, so it is trivially unit-testable. */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {
    /** Matches the `versionCode` scheme of the build: major * 10000 + minor * 100 + patch. */
    val versionCode: Int get() = major * 10_000 + minor * 100 + patch

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        /**
         * Accepts "2.1.3", "v2.1.3", "2.1" (missing parts are 0) and ignores a pre-release / build suffix
         * ("-rc1", "+5"). Anything else (garbage, more than three parts, negative or huge numbers) is null.
         */
        fun parse(raw: String?): AppVersion? {
            val trimmed = raw?.trim()?.removePrefix("v")?.removePrefix("V") ?: return null
            val core = trimmed.substringBefore("+").substringBefore("-")
            val parts = core.split(".")
            if (parts.size !in 1..3) return null
            val numbers = parts.map { p -> if (p.isNotEmpty() && p.all { it in "0123456789" }) p.toIntOrNull() else null }
            if (numbers.any { it == null }) return null
            val n = numbers.filterNotNull()
            return AppVersion(n[0], n.getOrElse(1) { 0 }, n.getOrElse(2) { 0 })
        }
    }
}
