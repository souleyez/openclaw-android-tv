package com.openclaw.tv.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class XiaJieAiSpeechReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            XiaJieAiSpeechBridge.ACTION_APP_REGISTER_REQUIRE -> {
                XiaJieAiSpeechBridge.register(context.applicationContext)
            }

            XiaJieAiSpeechBridge.ACTION_QUERY_RECEIVE -> {
                val recognizedText = XiaJieAiSpeechBridge.extractRecognizedText(intent)
                if (recognizedText.isNullOrBlank()) {
                    Log.i(TAG, "XiaJie query receive without text extras=${XiaJieAiSpeechBridge.describeExtras(intent)}")
                    return
                }
                Log.i(TAG, "XiaJie query receive text length=${recognizedText.length}")
                context.startActivity(
                    VoiceIntentContract.createVoiceCommandIntent(
                        context = context,
                        speechText = recognizedText,
                    ),
                )
            }
        }
    }

    private companion object {
        const val TAG = "OpenClawVoice"
    }
}
