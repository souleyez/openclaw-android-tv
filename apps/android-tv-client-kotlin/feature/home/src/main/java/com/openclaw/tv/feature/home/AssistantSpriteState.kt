package com.openclaw.tv.feature.home

import androidx.annotation.DrawableRes

enum class AssistantSpriteState(
    @param:DrawableRes val atlasResId: Int,
    val frameCount: Int = 4,
    val frameDurationMs: Long = 160L,
) {
    IDLE(R.drawable.assistant_sprite_idle, frameDurationMs = 220L),
    TALK(R.drawable.assistant_sprite_talk, frameDurationMs = 125L),
    THINK(R.drawable.assistant_sprite_think, frameDurationMs = 180L),
    POINT_LEFT(R.drawable.assistant_sprite_point_left, frameDurationMs = 160L),
    GUIDE(R.drawable.assistant_sprite_guide, frameDurationMs = 180L),
    WORRIED(R.drawable.assistant_sprite_worried, frameDurationMs = 200L),
}
