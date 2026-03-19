package com.openclaw.assistant.openclaw_android_tv_client

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.view.KeyEvent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.net.wifi.WifiManager
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private var pendingVoiceResult: MethodChannel.Result? = null
    private var pendingVoiceLocale: String = "en-US"
    private var speechRecognizer: SpeechRecognizer? = null
    private var backgroundStandbyEnabled: Boolean = false
    private var pendingWakeCommand: String? = null
    private var pendingWakeSource: String = "none"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeWakeIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeWakeIntent(intent)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "com.openclaw.assistant/voice"
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "getSpeechStatus" -> {
                    result.success(
                        mapOf(
                            "recognitionAvailable" to SpeechRecognizer.isRecognitionAvailable(this),
                            "recordAudioPermission" to hasRecordAudioPermission(),
                            "backgroundStandbyEnabled" to backgroundStandbyEnabled,
                            "backgroundStandbyRunning" to VoiceStandbyForegroundService.isRunning,
                            "backgroundStandbyStatus" to VoiceStandbyForegroundService.lastStatus,
                            "systemHotwordPrivilege" to hasSystemHotwordPrivilege(),
                            "pendingWakeCommand" to pendingWakeCommand,
                            "pendingWakeSource" to pendingWakeSource,
                        )
                    )
                }
                "getStandbyStatus" -> {
                    result.success(standbyStatusPayload())
                }
                "setBackgroundStandby" -> {
                    val arguments = call.arguments as? Map<*, *>
                    val enabled = arguments?.get("enabled") as? Boolean ?: false
                    val statusText = arguments?.get("statusText") as? String
                        ?: "Background standby armed. Waiting for voice input."
                    backgroundStandbyEnabled = enabled
                    if (enabled) {
                        VoiceStandbyForegroundService.hasPrivilegedHotwordPath = hasSystemHotwordPrivilege()
                        VoiceStandbyForegroundService.start(this, statusText)
                    } else {
                        VoiceStandbyForegroundService.stop(this)
                    }
                    result.success(standbyStatusPayload())
                }
                "updateStandbyExecutionState" -> {
                    val arguments = call.arguments as? Map<*, *>
                    val statusText = arguments?.get("statusText") as? String
                        ?: "Background standby armed. Waiting for voice input."
                    VoiceStandbyForegroundService.updateStatus(this, statusText)
                    result.success(standbyStatusPayload())
                }
                "simulateHotwordTrigger" -> {
                    val arguments = call.arguments as? Map<*, *>
                    val commandText = arguments?.get("commandText") as? String ?: "Open YouTube"
                    VoiceStandbyForegroundService.hasPrivilegedHotwordPath = hasSystemHotwordPrivilege()
                    VoiceStandbyForegroundService.simulateHotwordTrigger(this, commandText)
                    pendingWakeCommand = commandText
                    pendingWakeSource =
                        if (hasSystemHotwordPrivilege()) "system_hotword_stub" else "foreground_hotword_stub"
                    result.success(standbyStatusPayload())
                }
                "consumeWakeEvent" -> {
                    result.success(
                        mapOf(
                            "commandText" to pendingWakeCommand,
                            "source" to pendingWakeSource,
                        )
                    )
                    pendingWakeCommand = null
                    pendingWakeSource = "none"
                    VoiceStandbyForegroundService.pendingWakeCommand = null
                    VoiceStandbyForegroundService.pendingWakeSource = "none"
                }
                "pressToTalk" -> {
                    val arguments = call.arguments as? Map<*, *>
                    val locale = arguments?.get("locale") as? String ?: "en-US"
                    startVoiceRecognition(locale, result)
                }
                else -> result.notImplemented()
            }
        }

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "com.openclaw.assistant/control"
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "executeControl" -> {
                    val arguments = call.arguments as? Map<*, *>
                    val appId = arguments?.get("appId") as? String ?: "unknown"
                    val action = arguments?.get("action") as? String ?: "unsupported"
                    val queryText = arguments?.get("queryText") as? String

                    val response = when {
                        action == "openApp" && appId == "youtube" -> {
                            launchTargetApp(
                                packageCandidates = listOf(
                                    "com.google.android.youtube.tv",
                                    "com.google.android.youtube",
                                ),
                                displayName = "YouTube",
                            )
                        }
                        action == "openApp" && appId == "vlc" -> {
                            launchTargetApp(
                                packageCandidates = listOf("org.videolan.vlc"),
                                displayName = "VLC",
                            )
                        }
                        action == "openApp" && appId == "spotify" -> {
                            launchTargetApp(
                                packageCandidates = listOf("com.spotify.tv.android", "com.spotify.music"),
                                displayName = "Spotify",
                            )
                        }
                        action == "openApp" && appId == "settings" -> {
                            openSystemSettings()
                        }
                        action == "openApp" && appId == "cast" -> {
                            openCastSettings()
                        }
                        action == "openApp" && appId == "local_files" -> {
                            openLocalFiles()
                        }
                        appId == "system" && action == "back" -> {
                            sendSystemBack()
                        }
                        (appId == "system" || appId == "youtube" || appId == "vlc" || appId == "spotify" || appId == "settings") && (
                            action == "up" ||
                                action == "down" ||
                                action == "left" ||
                                action == "right" ||
                                action == "select" ||
                                action == "home" ||
                                action == "menu"
                            ) -> {
                            dispatchNavigationAction(appId, action)
                        }
                        (appId == "system" || appId == "youtube" || appId == "vlc" || appId == "spotify" || appId == "settings" || appId == "media") && (
                            action == "volume_up" ||
                                action == "volume_down" ||
                                action == "mute"
                            ) -> {
                            dispatchVolumeAction(appId, action)
                        }
                        (appId == "youtube" || appId == "vlc" || appId == "spotify") && action == "back" -> {
                            sendSystemBackForApp(appId)
                        }
                        (appId == "youtube" || appId == "vlc" || appId == "spotify") && action == "search" -> {
                            dispatchAppSearch(appId, queryText)
                        }
                        (appId == "youtube" || appId == "vlc" || appId == "spotify") && (
                            action == "play" ||
                                action == "pause" ||
                                action == "resume" ||
                                action == "next" ||
                                action == "previous" ||
                                action == "fastForward" ||
                                action == "rewind"
                            ) -> {
                            dispatchAppMediaAction(appId, action)
                        }
                        appId == "media" && (
                            action == "play" ||
                                action == "pause" ||
                                action == "resume" ||
                                action == "next" ||
                                action == "previous" ||
                                action == "fastForward" ||
                                action == "rewind"
                            ) -> {
                            dispatchMediaAction(action)
                        }
                        appId == "youtube" || appId == "vlc" || appId == "spotify" || appId == "settings" -> {
                            mapOf(
                                "success" to false,
                                "message" to "Action $action is not implemented yet",
                                "strategyUsed" to "android_action_stub",
                                "errorCode" to "CTRL-EXEC-002"
                            )
                        }
                        else -> {
                            mapOf(
                                "success" to false,
                                "message" to "Unsupported app in Android bridge",
                                "strategyUsed" to "none",
                                "errorCode" to "CTRL-CONF-001"
                            )
                        }
                    }

                    result.success(response)
                }
                else -> result.notImplemented()
            }
        }

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "com.openclaw.assistant/network"
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "getNetworkSnapshot" -> {
                    result.success(buildNetworkSnapshot())
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun standbyStatusPayload(): Map<String, Any?> {
        return mapOf(
            "enabled" to backgroundStandbyEnabled,
            "running" to VoiceStandbyForegroundService.isRunning,
            "statusText" to VoiceStandbyForegroundService.lastStatus,
            "systemHotwordPrivilege" to hasSystemHotwordPrivilege(),
            "pendingWakeCommand" to pendingWakeCommand,
            "pendingWakeSource" to pendingWakeSource,
        )
    }

    private fun consumeWakeIntent(intent: Intent?) {
        val commandText = intent?.getStringExtra(VoiceStandbyForegroundService.EXTRA_WAKE_COMMAND)
        val wakeSource = intent?.getStringExtra(VoiceStandbyForegroundService.EXTRA_WAKE_SOURCE)
        if (!commandText.isNullOrBlank()) {
            pendingWakeCommand = commandText
            pendingWakeSource = wakeSource ?: "foreground_hotword_stub"
        }
    }

    private fun launchTargetApp(
        packageCandidates: List<String>,
        displayName: String,
    ): Map<String, Any?> {
        val packageManager = applicationContext.packageManager

        for (packageName in packageCandidates) {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                return mapOf(
                    "success" to true,
                    "message" to "Opening $displayName from Android bridge",
                    "strategyUsed" to "android_package_launch",
                    "errorCode" to null
                )
            }
        }

        return mapOf(
            "success" to false,
            "message" to "$displayName is not installed on this device",
            "strategyUsed" to "android_package_lookup",
            "errorCode" to "CTRL-APP-001"
        )
    }

    private fun openSystemSettings(): Map<String, Any?> {
        val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        return mapOf(
            "success" to true,
            "message" to "Opening system settings",
            "strategyUsed" to "android_system_settings_intent",
            "errorCode" to null
        )
    }

    private fun openCastSettings(): Map<String, Any?> {
        val intents = listOf(
            Intent("android.settings.CAST_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        val packageManager = applicationContext.packageManager
        val targetIntent = intents.firstOrNull { intent ->
            intent.resolveActivity(packageManager) != null
        } ?: return mapOf(
            "success" to false,
            "message" to "Cast settings are unavailable on this device",
            "strategyUsed" to "android_cast_lookup",
            "errorCode" to "CTRL-CAST-001"
        )

        startActivity(targetIntent)
        return mapOf(
            "success" to true,
            "message" to "Opening screen cast settings",
            "strategyUsed" to "android_cast_settings_intent",
            "errorCode" to null
        )
    }

    private fun openLocalFiles(): Map<String, Any?> {
        val packageManager = applicationContext.packageManager
        val pickerIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("video/*", "audio/*"))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (pickerIntent.resolveActivity(packageManager) != null) {
            startActivity(pickerIntent)
            return mapOf(
                "success" to true,
                "message" to "Opening local file picker",
                "strategyUsed" to "android_document_picker",
                "errorCode" to null
            )
        }

        return launchTargetApp(
            packageCandidates = listOf("org.videolan.vlc"),
            displayName = "Local Files",
        )
    }

    private fun sendSystemBack(): Map<String, Any?> {
        @Suppress("DEPRECATION")
        onBackPressed()
        return mapOf(
            "success" to true,
            "message" to "Sent back command",
            "strategyUsed" to "android_back_dispatcher",
            "errorCode" to null
        )
    }

    private fun sendSystemBackForApp(appId: String): Map<String, Any?> {
        @Suppress("DEPRECATION")
        onBackPressed()
        return mapOf(
            "success" to true,
            "message" to "Sent back command for $appId",
            "strategyUsed" to "android_app_back_dispatcher",
            "errorCode" to null
        )
    }

    private fun dispatchNavigationAction(appId: String, action: String): Map<String, Any?> {
        if (action == "home") {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            return mapOf(
                "success" to true,
                "message" to "Sent home command for $appId",
                "strategyUsed" to "android_home_intent",
                "errorCode" to null
            )
        }

        val keyCode = when (action) {
            "up" -> KeyEvent.KEYCODE_DPAD_UP
            "down" -> KeyEvent.KEYCODE_DPAD_DOWN
            "left" -> KeyEvent.KEYCODE_DPAD_LEFT
            "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "select" -> KeyEvent.KEYCODE_DPAD_CENTER
            "menu" -> KeyEvent.KEYCODE_MENU
            else -> return mapOf(
                "success" to false,
                "message" to "Unsupported navigation action $action",
                "strategyUsed" to "android_navigation_unsupported",
                "errorCode" to "CTRL-NAV-001"
            )
        }

        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        runOnUiThread {
            window.decorView.dispatchKeyEvent(downEvent)
            window.decorView.dispatchKeyEvent(upEvent)
        }

        return mapOf(
            "success" to true,
            "message" to "Sent $action command for $appId",
            "strategyUsed" to "android_dpad_key_event",
            "errorCode" to null
        )
    }

    private fun dispatchVolumeAction(appId: String, action: String): Map<String, Any?> {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        when (action) {
            "volume_up" -> audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI
            )
            "volume_down" -> audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
            "mute" -> audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_TOGGLE_MUTE,
                AudioManager.FLAG_SHOW_UI
            )
            else -> return mapOf(
                "success" to false,
                "message" to "Unsupported volume action $action",
                "strategyUsed" to "android_volume_unsupported",
                "errorCode" to "CTRL-VOLUME-001"
            )
        }

        return mapOf(
            "success" to true,
            "message" to "Adjusted volume for $appId with $action",
            "strategyUsed" to "android_stream_volume",
            "errorCode" to null
        )
    }

    private fun buildNetworkSnapshot(): Map<String, Any?> {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        val transport = when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ethernet"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
            else -> "offline"
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val hasWifiPermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) ==
                PackageManager.PERMISSION_GRANTED
        val hasLocationPermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        val currentSsid = if (hasWifiPermission) {
            try {
                sanitizeSsid(wifiManager.connectionInfo?.ssid)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        val visibleNetworks = mutableListOf<String>()
        if (hasWifiPermission && hasLocationPermission) {
            try {
                wifiManager.scanResults
                    ?.mapNotNull { scanResult -> sanitizeSsid(scanResult.SSID) }
                    ?.distinct()
                    ?.take(8)
                    ?.let { visibleNetworks.addAll(it) }
            } catch (_: Exception) {
                // Best effort only.
            }
        }

        if (visibleNetworks.isEmpty() && currentSsid != null) {
            visibleNetworks.add(currentSsid)
        }

        val statusText = when {
            !isConnected -> "Offline. Ask me to connect Wi-Fi."
            currentSsid != null -> "Connected to $currentSsid"
            else -> "Connected via $transport"
        }

        return mapOf(
            "isConnected" to isConnected,
            "transport" to transport,
            "currentSsid" to currentSsid,
            "visibleNetworks" to visibleNetworks,
            "canReadWifiList" to (hasWifiPermission && hasLocationPermission),
            "statusText" to statusText,
        )
    }

    private fun sanitizeSsid(rawSsid: String?): String? {
        if (rawSsid == null || rawSsid.isBlank()) {
            return null
        }
        val cleaned = rawSsid.replace("\"", "").trim()
        return if (cleaned.isBlank() || cleaned.equals("<unknown ssid>", ignoreCase = true)) {
            null
        } else {
            cleaned
        }
    }

    private fun dispatchAppMediaAction(appId: String, action: String): Map<String, Any?> {
        val mediaResult = dispatchMediaAction(action)
        return mapOf(
            "success" to (mediaResult["success"] as? Boolean ?: false),
            "message" to "Sent $action to $appId",
            "strategyUsed" to "android_app_media_bridge/${mediaResult["strategyUsed"]}",
            "errorCode" to mediaResult["errorCode"]
        )
    }

    private fun dispatchAppSearch(appId: String, queryText: String?): Map<String, Any?> {
        if (queryText.isNullOrBlank()) {
            return mapOf(
                "success" to false,
                "message" to "Search query is missing",
                "strategyUsed" to "android_search_validation",
                "errorCode" to "CTRL-SEARCH-001"
            )
        }

        val packageManager = applicationContext.packageManager
        for (intent in buildSearchIntentCandidates(appId, queryText)) {
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                return mapOf(
                    "success" to true,
                    "message" to "Searching $appId for $queryText",
                    "strategyUsed" to "android_app_search_intent",
                    "errorCode" to null
                )
            }
        }

        return mapOf(
            "success" to false,
            "message" to "Search intent is unavailable for $appId",
            "strategyUsed" to "android_app_search_lookup",
            "errorCode" to "CTRL-SEARCH-002"
        )
    }

    private fun buildSearchIntentCandidates(appId: String, queryText: String): List<Intent> {
        val encodedQuery = Uri.encode(queryText)
        val intents = mutableListOf<Intent>()

        when (appId) {
            "youtube" -> {
                intents += Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")).apply {
                    setPackage("com.google.android.youtube.tv")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                intents += Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")).apply {
                    setPackage("com.google.android.youtube")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            "spotify" -> {
                intents += Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$encodedQuery")).apply {
                    setPackage("com.spotify.tv.android")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                intents += Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$encodedQuery")).apply {
                    setPackage("com.spotify.music")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }

        val packageCandidates = when (appId) {
            "youtube" -> listOf("com.google.android.youtube.tv", "com.google.android.youtube")
            "vlc" -> listOf("org.videolan.vlc")
            "spotify" -> listOf("com.spotify.tv.android", "com.spotify.music")
            else -> emptyList()
        }

        for (packageName in packageCandidates) {
            intents += Intent(Intent.ACTION_SEARCH).apply {
                setPackage(packageName)
                putExtra(SearchManager.QUERY, queryText)
                putExtra("query", queryText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        return intents
    }

    private fun dispatchMediaAction(action: String): Map<String, Any?> {
        val mediaSessionResult = tryDispatchMediaSessionAction(action)
        if (mediaSessionResult != null) {
            return mediaSessionResult
        }

        val keyCode = when (action) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "resume" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "fastForward" -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            "rewind" -> KeyEvent.KEYCODE_MEDIA_REWIND
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return mapOf(
            "success" to true,
            "message" to "Dispatched media action $action with media key fallback",
            "strategyUsed" to "android_media_key_event",
            "errorCode" to null
        )
    }

    private fun tryDispatchMediaSessionAction(action: String): Map<String, Any?>? {
        return try {
            val mediaSessionManager =
                getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val controllers: List<MediaController> = mediaSessionManager.getActiveSessions(null)
            val controller = controllers.firstOrNull() ?: return null
            val transportControls = controller.transportControls

            when (action) {
                "play", "resume" -> transportControls.play()
                "pause" -> transportControls.pause()
                "next" -> transportControls.skipToNext()
                "previous" -> transportControls.skipToPrevious()
                "fastForward" -> transportControls.fastForward()
                "rewind" -> transportControls.rewind()
                else -> return null
            }

            mapOf(
                "success" to true,
                "message" to "Dispatched media action $action with transport controls",
                "strategyUsed" to "android_media_session_transport",
                "errorCode" to null
            )
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun startVoiceRecognition(
        locale: String,
        result: MethodChannel.Result,
    ) {
        if (pendingVoiceResult != null) {
            result.success(
                mapOf(
                    "recognizedText" to "",
                    "feedbackText" to "Voice recognition is already running",
                    "provider" to "android_speech_busy"
                )
            )
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            result.success(
                mapOf(
                    "recognizedText" to "Open YouTube",
                    "feedbackText" to "Speech recognition is unavailable on this device",
                    "provider" to "android_speech_unavailable"
                )
            )
            return
        }

        pendingVoiceResult = result
        pendingVoiceLocale = locale

        if (!hasRecordAudioPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
            return
        }

        beginSpeechRecognition(locale)
    }

    private fun beginSpeechRecognition(locale: String) {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit

                    override fun onResults(results: Bundle?) {
                        val matches = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val bestMatch = matches?.firstOrNull().orEmpty()

                        completeVoiceResult(
                            mapOf(
                                "recognizedText" to bestMatch.ifBlank { "Open YouTube" },
                                "feedbackText" to "Heard: ${bestMatch.ifBlank { "Open YouTube" }}",
                                "provider" to "android_speech_recognizer"
                            )
                        )
                    }

                    override fun onError(error: Int) {
                        completeVoiceResult(
                            mapOf(
                                "recognizedText" to "Open YouTube",
                                "feedbackText" to mapSpeechError(error),
                                "provider" to "android_speech_error_$error"
                            )
                        )
                    }
                }
            )
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        speechRecognizer?.startListening(intent)
    }

    private fun completeVoiceResult(payload: Map<String, Any?>) {
        pendingVoiceResult?.success(payload)
        pendingVoiceResult = null
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasSystemHotwordPrivilege(): Boolean {
        val privilegedPermissions = listOf(
            "android.permission.CAPTURE_AUDIO_HOTWORD",
            "android.permission.BIND_VOICE_INTERACTION",
            "com.openclaw.permission.SYSTEM_HOTWORD",
        )
        return privilegedPermissions.any { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun mapSpeechError(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio capture failed"
            SpeechRecognizer.ERROR_CLIENT -> "Speech client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing"
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during speech recognition"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match found"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
            SpeechRecognizer.ERROR_SERVER -> "Speech server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech input timed out"
            else -> "Speech recognition failed"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_RECORD_AUDIO_PERMISSION) {
            return
        }

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            beginSpeechRecognition(pendingVoiceLocale)
            return
        }

        completeVoiceResult(
            mapOf(
                "recognizedText" to "Open YouTube",
                "feedbackText" to "Microphone permission was denied",
                "provider" to "android_permission_denied"
            )
        )
    }

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 1101
    }
}
