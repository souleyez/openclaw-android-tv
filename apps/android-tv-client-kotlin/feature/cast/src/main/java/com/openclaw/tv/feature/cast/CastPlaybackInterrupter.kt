package com.openclaw.tv.feature.cast

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

object CastPlaybackInterrupter {
    fun interruptCurrentPlayback(context: Context) {
        val audioManager = context.applicationContext
            .getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return
        dispatchMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_PAUSE)
        dispatchMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_STOP)
    }

    private fun dispatchMediaKey(
        audioManager: AudioManager,
        keyCode: Int,
    ) {
        val now = System.currentTimeMillis()
        audioManager.dispatchMediaKeyEvent(
            KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0),
        )
        audioManager.dispatchMediaKeyEvent(
            KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0),
        )
    }
}
