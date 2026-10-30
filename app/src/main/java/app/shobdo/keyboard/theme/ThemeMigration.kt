package app.shobdo.keyboard.theme

/**
 * Maps a theme id from an older version to the theme that replaced it.
 *
 * The 1.x gallery was replaced wholesale, so a stored id can point at a theme that no longer
 * ships. Without this the keyboard would silently fall back to the first theme in the
 * alphabet; with it, someone who chose Midnight Glass keeps a dark, cool-toned keyboard
 * instead of being dropped into a light one.
 */
object ThemeMigration {

    /** Old id -> the new id that keeps the same character. */
    private val MAP: Map<String, String> = mapOf(
        "midnight_glass" to "studio_dark",
        "pure_light" to "studio_light",
        "amoled_black" to "onyx",
        "ocean_breeze" to "aurora",
        "sunset_gradient" to "tokyo_night",
        "forest_calm" to "evergreen",
        "rose_gold" to "sakura",
        "neon_cyber" to "aurora",
        "material_you" to "dynamic",
        "paper_minimal" to "cassette",
        "royal_purple" to "obsidian_gold",
        "bangla_flag" to "evergreen",
        "lava" to "obsidian_gold",
        "aurora_borealis" to "aurora",
        "cherry_blossom" to "sakura",
        "desert_dune" to "cassette",
        "deep_space" to "tokyo_night",
        "mint_fresh" to "evergreen",
        "graphite_pro" to "studio_dark",
        "sky_day" to "cloudline",
        "lavender_mist" to "cloudline",
        "monsoon" to "frost",
        "golden_hour" to "cassette",
        "ink_and_paper" to "studio_light"
    )

    /** The replacement id, or the id unchanged when it is not a known older theme. */
    fun migrate(id: String): String = MAP[id] ?: id

    fun isOldId(id: String): Boolean = MAP.containsKey(id)

    val oldIds: Set<String> get() = MAP.keys
}
