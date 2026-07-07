package com.openclaw.tv.feature.home

internal data class HomeThemeInput(
    val defaultMode: String,
    val switcherEnabled: Boolean,
    val dayPalette: Map<String, String>,
    val nightPalette: Map<String, String>,
)

internal data class HomeThemePresentation(
    val activeMode: String,
    val switcherVisible: Boolean,
    val toggleText: String,
    val backgroundColorHex: String,
    val overlayColorHex: String,
    val chipFillColorHex: String,
    val chipStrokeColorHex: String,
    val chipTextColorHex: String,
)

internal class HomeThemeController {

    fun resolve(
        input: HomeThemeInput,
        manualMode: String?,
    ): HomeThemePresentation {
        val activeMode = normalizeMode(manualMode)
            ?: normalizeMode(input.defaultMode)
            ?: MODE_NIGHT
        val palette = if (activeMode == MODE_DAY) input.dayPalette else input.nightPalette
        val isDay = activeMode == MODE_DAY

        return HomeThemePresentation(
            activeMode = activeMode,
            switcherVisible = input.switcherEnabled,
            toggleText = if (isDay) "白日" else "夜晚",
            backgroundColorHex = palette.firstPresent("background", "surface")
                ?: if (isDay) DAY_BACKGROUND else NIGHT_BACKGROUND,
            overlayColorHex = if (isDay) DAY_OVERLAY else NIGHT_OVERLAY,
            chipFillColorHex = palette["chip"]
                ?: if (isDay) DAY_CHIP else NIGHT_CHIP,
            chipStrokeColorHex = palette["accent"] ?: DEFAULT_ACCENT,
            chipTextColorHex = palette["text"]
                ?: if (isDay) DAY_TEXT else NIGHT_TEXT,
        )
    }

    fun resolve(
        state: HomeUiState,
        manualMode: String?,
    ): HomeThemePresentation {
        return resolve(
            input = HomeThemeInput(
                defaultMode = state.themeDefaultMode,
                switcherEnabled = state.themeSwitcherEnabled,
                dayPalette = state.themeDayPalette,
                nightPalette = state.themeNightPalette,
            ),
            manualMode = manualMode,
        )
    }

    fun nextMode(activeMode: String): String {
        return if (normalizeMode(activeMode) == MODE_DAY) MODE_NIGHT else MODE_DAY
    }

    fun isSupportedMode(mode: String?): Boolean {
        return normalizeMode(mode) != null
    }

    private fun normalizeMode(mode: String?): String? {
        return when (mode?.trim()?.lowercase()) {
            MODE_DAY -> MODE_DAY
            MODE_NIGHT -> MODE_NIGHT
            else -> null
        }
    }

    private fun Map<String, String>.firstPresent(vararg keys: String): String? {
        for (key in keys) {
            val value = this[key]?.trim()
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        return null
    }

    companion object {
        const val MODE_DAY = "day"
        const val MODE_NIGHT = "night"
        private const val DAY_BACKGROUND = "#EEF3F6"
        private const val NIGHT_BACKGROUND = "#091019"
        private const val DAY_OVERLAY = "#66FFFFFF"
        private const val NIGHT_OVERLAY = "#D9091019"
        private const val DAY_CHIP = "#DDE7EF"
        private const val NIGHT_CHIP = "#1F2731"
        private const val DAY_TEXT = "#13202E"
        private const val NIGHT_TEXT = "#F7FBFF"
        private const val DEFAULT_ACCENT = "#6FA6FF"
    }
}
