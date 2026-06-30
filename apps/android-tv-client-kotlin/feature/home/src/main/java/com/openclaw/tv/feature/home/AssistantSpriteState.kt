package com.openclaw.tv.feature.home

import androidx.annotation.DrawableRes

enum class AssistantSpriteState(
    @param:DrawableRes val atlasResId: Int,
    val frameCount: Int = 4,
    val frameDurationMs: Long = 160L,
    val loopPauseMs: Long = 0L,
) {
    SUMMER_IDLE(R.drawable.assistant_sprite_summer_idle, frameCount = 8, frameDurationMs = 420L, loopPauseMs = 1_800L),
    IDLE(R.drawable.assistant_sprite_summer_idle, frameCount = 8, frameDurationMs = 420L, loopPauseMs = 1_800L),
    TALK(R.drawable.assistant_sprite_summer_wave, frameCount = 10, frameDurationMs = 180L, loopPauseMs = 2_200L),
    THINK(R.drawable.assistant_sprite_summer_think, frameCount = 8, frameDurationMs = 440L, loopPauseMs = 1_900L),
    POINT_LEFT(R.drawable.assistant_sprite_summer_lean, frameCount = 8, frameDurationMs = 330L, loopPauseMs = 1_700L),
    GUIDE(R.drawable.assistant_sprite_summer_offline, frameCount = 8, frameDurationMs = 540L, loopPauseMs = 2_400L),
    WORRIED(R.drawable.assistant_sprite_summer_think, frameCount = 8, frameDurationMs = 360L, loopPauseMs = 1_600L),
}
