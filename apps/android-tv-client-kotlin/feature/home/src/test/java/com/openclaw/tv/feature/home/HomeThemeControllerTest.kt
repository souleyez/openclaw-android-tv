package com.openclaw.tv.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeThemeControllerTest {

    private val controller = HomeThemeController()

    @Test
    fun resolves_default_day_palette_and_toggle_copy() {
        val presentation = controller.resolve(
            input = HomeThemeInput(
                defaultMode = "day",
                switcherEnabled = true,
                dayPalette = mapOf(
                    "background" to "#FFFFFF",
                    "chip" to "#EEEEEE",
                    "accent" to "#2255FF",
                    "text" to "#111111",
                ),
                nightPalette = emptyMap(),
            ),
            manualMode = null,
        )

        assertEquals(HomeThemeController.MODE_DAY, presentation.activeMode)
        assertTrue(presentation.switcherVisible)
        assertEquals("白日", presentation.toggleText)
        assertEquals("#FFFFFF", presentation.backgroundColorHex)
        assertEquals("#EEEEEE", presentation.chipFillColorHex)
        assertEquals("#2255FF", presentation.chipStrokeColorHex)
        assertEquals("#111111", presentation.chipTextColorHex)
    }

    @Test
    fun manual_mode_overrides_default_and_falls_back_to_night() {
        val presentation = controller.resolve(
            input = HomeThemeInput(
                defaultMode = "day",
                switcherEnabled = false,
                dayPalette = emptyMap(),
                nightPalette = mapOf("surface" to "#000001"),
            ),
            manualMode = "night",
        )

        assertEquals(HomeThemeController.MODE_NIGHT, presentation.activeMode)
        assertFalse(presentation.switcherVisible)
        assertEquals("夜晚", presentation.toggleText)
        assertEquals("#000001", presentation.backgroundColorHex)
        assertEquals(HomeThemeController.MODE_DAY, controller.nextMode(presentation.activeMode))
    }
}
