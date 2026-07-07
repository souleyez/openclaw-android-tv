package com.openclaw.tv.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import org.json.JSONObject

object XiaJieAiSpeechBridge {
    const val PEASUN_AISPEECH_PACKAGE = "com.peasun.aispeech"
    const val ACTION_APP_REGISTER = "com.peasun.aispeech.action.app.register"
    const val ACTION_APP_REGISTER_REQUIRE = "com.peasun.aispeech.action.app.register.require"
    const val ACTION_QUERY_RECEIVE = "com.peasun.aispeech.action.query.recieve"

    private const val TAG = "OpenClawVoice"
    private const val AIOPEN_SERVICE_CLASS = "com.peasun.aispeech.aiopen.AIOpenService"

    private val textExtraKeys = listOf(
        VoiceIntentContract.EXTRA_SPEECH_TEXT,
        VoiceIntentContract.EXTRA_TEXT,
        VoiceIntentContract.EXTRA_COMMAND,
        "query",
        "user_query",
        "asr_recognize_result",
        "result",
        "result_",
        "semantic",
        "semanticInfo",
        "data",
    )

    fun register(context: Context) {
        if (!isAiSpeechInstalled(context)) {
            return
        }
        val packageName = context.packageName
        val intent = Intent(ACTION_APP_REGISTER).apply {
            setClassName(PEASUN_AISPEECH_PACKAGE, AIOPEN_SERVICE_CLASS)
            putExtra("packageName", packageName)
            putExtra("pkgName", packageName)
            putExtra("appName", resolveAppName(context))
        }
        runCatching {
            context.startService(intent)
        }.onFailure { error ->
            Log.w(TAG, "XiaJie aiopen register failed: ${error.message}")
        }
    }

    @Suppress("DEPRECATION")
    fun extractRecognizedText(intent: Intent?): String? {
        val extras = intent?.extras ?: return null
        return textExtraKeys.firstNotNullOfOrNull { key ->
            extras.get(key)?.toRecognizedText()
        }
    }

    @Suppress("DEPRECATION")
    fun describeExtras(intent: Intent?): String {
        val extras = intent?.extras ?: return "none"
        return extras.keySet().sorted().joinToString(separator = ",") { key ->
            "$key=${extras.get(key)?.javaClass?.simpleName ?: "null"}"
        }
    }

    private fun isAiSpeechInstalled(context: Context): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(PEASUN_AISPEECH_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }

    private fun resolveAppName(context: Context): String {
        return runCatching {
            val applicationInfo = context.applicationInfo
            context.packageManager.getApplicationLabel(applicationInfo).toString()
        }.getOrDefault(context.packageName)
    }

    @Suppress("DEPRECATION")
    private fun Any.toRecognizedText(): String? {
        return when (this) {
            is String -> extractFromString(this)
            is CharSequence -> extractFromString(toString())
            is Bundle -> textExtraKeys.firstNotNullOfOrNull { key -> get(key)?.toRecognizedText() }
            else -> null
        }
    }

    private fun extractFromString(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            return null
        }
        if (!trimmed.startsWith("{")) {
            return trimmed
        }
        return runCatching {
            val json = JSONObject(trimmed)
            textExtraKeys.firstNotNullOfOrNull { key ->
                json.optString(key).trim().takeIf { it.isNotBlank() }
            }
        }.getOrNull() ?: trimmed
    }
}
