package com.openclaw.tv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.openclaw.tv.feature.bootstrap.BootstrapRuntimeOwner
import com.openclaw.tv.feature.home.HomeFragment
import com.openclaw.tv.upgrade.OwnApkInstallAttemptResult
import com.openclaw.tv.upgrade.OwnApkUpdateInstaller
import com.openclaw.tv.upgrade.RequiredUpgradeFragment
import com.openclaw.tv.upgrade.SharedPreferencesOwnApkDownloadStore
import com.openclaw.tv.upgrade.StoredOwnApkUpdate
import com.openclaw.tv.voice.VoiceIntentContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private var currentRoute: MainRoute? = null
    private var currentHomeDebugForceOffline: Boolean = false
    private lateinit var ownApkUpdateStore: SharedPreferencesOwnApkDownloadStore
    private lateinit var ownApkUpdateInstaller: OwnApkUpdateInstaller
    private var activeOwnApkUpdate: StoredOwnApkUpdate? = null
    private var dismissedOwnApkReleaseId: String? = null
    private var pendingVoiceStart = false
    private var pendingVoiceCommandText: String? = null
    private var pendingVoiceRequestRetryCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ownApkUpdateStore = SharedPreferencesOwnApkDownloadStore(this)
        ownApkUpdateInstaller = OwnApkUpdateInstaller(this)
        bindOwnApkUpdatePrompt()
        val runtimeOwner = application as? BootstrapRuntimeOwner

        rememberVoiceIntent(intent)
        if (savedInstanceState == null) {
            renderRoute(MainRouteResolver.resolve(runtimeOwner?.bootstrapRuntime?.state?.value))
        }
        consumePendingVoiceRequestsWhenReady()

        if (runtimeOwner != null) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    runtimeOwner.bootstrapRuntime.state.collect { state ->
                        renderRoute(MainRouteResolver.resolve(state))
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val runtimeOwner = application as? BootstrapRuntimeOwner
        rememberVoiceIntent(intent)
        renderRoute(MainRouteResolver.resolve(runtimeOwner?.bootstrapRuntime?.state?.value))
        consumePendingVoiceRequestsWhenReady()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0 &&
            event.keyCode.isVoiceShortcutKey()
        ) {
            val handled = (supportFragmentManager.findFragmentById(R.id.main_content) as? HomeFragment)
                ?.handleVoiceShortcutKey() == true
            if (handled) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun rememberVoiceIntent(intent: Intent?) {
        when (intent?.action) {
            VoiceIntentContract.ACTION_START_VOICE_INPUT -> {
                pendingVoiceStart = true
                pendingVoiceRequestRetryCount = 0
            }
            VoiceIntentContract.ACTION_VOICE_COMMAND -> {
                val commandText = VoiceIntentContract.extractSpeechText(intent)
                if (commandText.isNullOrBlank()) {
                    pendingVoiceStart = true
                } else {
                    pendingVoiceCommandText = commandText
                }
                pendingVoiceRequestRetryCount = 0
            }
        }
    }

    private fun consumePendingVoiceRequestsWhenReady() {
        if (!pendingVoiceStart && pendingVoiceCommandText.isNullOrBlank()) {
            return
        }
        findViewById<View>(R.id.main_content)?.post {
            if (!pendingVoiceStart && pendingVoiceCommandText.isNullOrBlank()) {
                return@post
            }
            val homeFragment = supportFragmentManager.findFragmentById(R.id.main_content) as? HomeFragment
            val pendingCommand = pendingVoiceCommandText
            if (!pendingCommand.isNullOrBlank() && homeFragment?.handleExternalVoiceCommand(pendingCommand) == true) {
                pendingVoiceCommandText = null
                pendingVoiceRequestRetryCount = 0
                return@post
            }
            if (pendingVoiceStart && homeFragment?.handleVoiceShortcutKey() == true) {
                pendingVoiceStart = false
                pendingVoiceRequestRetryCount = 0
                return@post
            }
            if (pendingVoiceRequestRetryCount < MAX_PENDING_VOICE_REQUEST_RETRIES) {
                pendingVoiceRequestRetryCount += 1
                findViewById<View>(R.id.main_content)?.postDelayed(
                    { consumePendingVoiceRequestsWhenReady() },
                    PENDING_VOICE_REQUEST_RETRY_DELAY_MS,
                )
            }
        }
    }

    private fun renderRoute(route: MainRoute) {
        val debugForceOffline = shouldForceHomeOfflinePreview()
        val homePreviewChanged = route == MainRoute.HOME && currentHomeDebugForceOffline != debugForceOffline
        if (currentRoute == route &&
            supportFragmentManager.findFragmentById(R.id.main_content) != null &&
            !homePreviewChanged
        ) {
            return
        }
        currentRoute = route
        currentHomeDebugForceOffline = if (route == MainRoute.HOME) debugForceOffline else false
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(
                R.id.main_content,
                when (route) {
                    MainRoute.HOME -> HomeFragment.newInstance(
                        platformBaseUrl = BuildConfig.PLATFORM_API_BASE_URL,
                        debugForceOffline = debugForceOffline,
                    )
                    MainRoute.REQUIRED_UPGRADE -> RequiredUpgradeFragment()
                },
            )
            runOnCommit {
                consumePendingVoiceRequestsWhenReady()
            }
        }
    }

    private fun shouldForceHomeOfflinePreview(): Boolean {
        return BuildConfig.DEBUG && intent?.getBooleanExtra(EXTRA_DEBUG_FORCE_HOME_OFFLINE, false) == true
    }

    private fun bindOwnApkUpdatePrompt() {
        val prompt = findViewById<View>(R.id.own_apk_update_prompt)
        val title = findViewById<TextView>(R.id.own_apk_update_title)
        val body = findViewById<TextView>(R.id.own_apk_update_body)
        val installButton = findViewById<Button>(R.id.own_apk_update_install)
        val laterButton = findViewById<Button>(R.id.own_apk_update_later)

        laterButton.setOnClickListener {
            dismissedOwnApkReleaseId = activeOwnApkUpdate?.releaseId
            activeOwnApkUpdate = null
            prompt.visibility = View.GONE
        }
        installButton.setOnClickListener {
            lifecycleScope.launch {
                installActiveOwnApkUpdate(installButton)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    refreshOwnApkUpdatePrompt(
                        prompt = prompt,
                        title = title,
                        body = body,
                        installButton = installButton,
                    )
                    delay(OWN_APK_PROMPT_REFRESH_MS)
                }
            }
        }
    }

    private suspend fun refreshOwnApkUpdatePrompt(
        prompt: View,
        title: TextView,
        body: TextView,
        installButton: Button,
    ) {
        val update = withContext(Dispatchers.IO) {
            ownApkUpdateStore.read()
        }?.takeIf { stored ->
            stored.targetVersionCode > BuildConfig.VERSION_CODE &&
                stored.releaseId.isNotBlank() &&
                stored.localPath.isNotBlank() &&
                stored.status in PromptableOwnApkStatuses &&
                stored.releaseId != dismissedOwnApkReleaseId
        }
        activeOwnApkUpdate = update
        if (update == null) {
            prompt.visibility = View.GONE
            return
        }

        val version = update.targetVersionName.ifBlank { update.targetVersionCode.toString() }
        val installing = update.status == "installing"
        title.text = if (installing) {
            "正在安装 $version"
        } else {
            "新版本已准备好 $version"
        }
        body.text = if (installing) {
            "如系统安装器没有显示，可重新打开安装流程。安装完成后重新进入首页即可完成版本切换。"
        } else {
            "安装包已下载并校验通过。点立即安装会先尝试静默安装，无权限时会打开系统安装器。"
        }
        installButton.text = if (installing) "继续安装" else "立即安装"
        installButton.isEnabled = true
        prompt.visibility = View.VISIBLE
    }

    private suspend fun installActiveOwnApkUpdate(installButton: Button) {
        val update = activeOwnApkUpdate ?: return
        installButton.isEnabled = false
        val result = withContext(Dispatchers.IO) {
            ownApkUpdateInstaller.install(update)
        }
        when (result) {
            OwnApkInstallAttemptResult.SilentSubmitted -> {
                markOwnApkInstallStarted(update)
                Toast.makeText(this, "已提交静默安装。", Toast.LENGTH_SHORT).show()
            }

            OwnApkInstallAttemptResult.PromptLaunched -> {
                markOwnApkInstallStarted(update)
                Toast.makeText(this, "已打开系统安装器。", Toast.LENGTH_SHORT).show()
            }

            OwnApkInstallAttemptResult.PermissionRequired -> {
                Toast.makeText(this, "请先允许本应用安装未知来源应用。", Toast.LENGTH_SHORT).show()
            }

            is OwnApkInstallAttemptResult.Failed -> {
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
            }
        }
        installButton.isEnabled = true
    }

    private suspend fun markOwnApkInstallStarted(update: StoredOwnApkUpdate) {
        withContext(Dispatchers.IO) {
            val current = ownApkUpdateStore.read()
                ?.takeIf { it.releaseId == update.releaseId }
                ?: update
            ownApkUpdateStore.write(
                current.copy(
                    status = "installing",
                    errorMessage = null,
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    private companion object {
        const val EXTRA_DEBUG_FORCE_HOME_OFFLINE = "debug_force_home_offline"
        const val OWN_APK_PROMPT_REFRESH_MS = 3_000L
        const val MAX_PENDING_VOICE_REQUEST_RETRIES = 20
        const val PENDING_VOICE_REQUEST_RETRY_DELAY_MS = 250L
        val PromptableOwnApkStatuses = setOf("verified", "installing")
    }
}

private fun Int.isVoiceShortcutKey(): Boolean {
    return this == KeyEvent.KEYCODE_SEARCH ||
        this == KeyEvent.KEYCODE_ASSIST ||
        this == KeyEvent.KEYCODE_VOICE_ASSIST ||
        this == KeyEvent.KEYCODE_F12
}
