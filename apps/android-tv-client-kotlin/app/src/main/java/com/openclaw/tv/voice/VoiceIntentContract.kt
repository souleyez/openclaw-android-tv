package com.openclaw.tv.voice

import android.content.Context
import android.content.Intent
import com.openclaw.tv.MainActivity

object VoiceIntentContract {
    const val ACTION_START_VOICE_INPUT = "com.openclaw.tv.action.START_VOICE_INPUT"
    const val ACTION_VOICE_COMMAND = "com.openclaw.tv.action.VOICE_COMMAND"

    const val EXTRA_SPEECH_TEXT = "speech_text"
    const val EXTRA_TEXT = "text"
    const val EXTRA_COMMAND = "command"
    const val EXTRA_CHANNEL = "channel"
    const val EXTRA_CHANNEL_NAME = "channel_name"
    const val EXTRA_NAME = "name"

    fun extractSpeechText(intent: Intent?): String? {
        if (intent == null) {
            return null
        }
        return listOf(
            EXTRA_SPEECH_TEXT,
            EXTRA_TEXT,
            EXTRA_COMMAND,
            EXTRA_CHANNEL_NAME,
            EXTRA_CHANNEL,
            EXTRA_NAME,
        ).firstNotNullOfOrNull { key ->
            intent.getStringExtra(key)?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    fun createVoiceCommandIntent(context: Context, speechText: String): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = ACTION_VOICE_COMMAND
            putExtra(EXTRA_SPEECH_TEXT, speechText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    }
}
