package com.openclaw.tv.upgrade

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.openclaw.tv.R
import com.openclaw.tv.core.storage.DataStoreUpgradeStateStore
import com.openclaw.tv.feature.bootstrap.BootstrapRuntimeOwner
import com.openclaw.tv.feature.bootstrap.BootstrapRuntimeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RequiredUpgradeFragment : Fragment(R.layout.fragment_required_upgrade) {

    private var updateInstaller: ApkUpdateInstaller? = null
    private var upgradeStateStore: DataStoreUpgradeStateStore? = null
    private var activeDownloadId: Long? = null
    private var installState: UpgradeInstallState = UpgradeInstallState.Idle
    private var downloadMonitorJob: Job? = null

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                return
            }
            val completedDownloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (completedDownloadId <= 0L || completedDownloadId != activeDownloadId) {
                return
            }
            installState = restoreInstallState(completedDownloadId)
            render(lastRuntimeState)
        }
    }

    private var lastRuntimeState: BootstrapRuntimeState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateInstaller = ApkUpdateInstaller(requireContext())
        upgradeStateStore = DataStoreUpgradeStateStore(requireContext().applicationContext)
        activeDownloadId = savedInstanceState?.getLong(STATE_DOWNLOAD_ID)
            ?.takeIf { it > 0L }
        if (activeDownloadId != null) {
            installState = restoreInstallState(checkNotNull(activeDownloadId))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            },
        )

        val primaryAction = view.findViewById<Button>(R.id.required_upgrade_primary_action)
        primaryAction.requestFocus()

        val runtimeOwner = requireContext().applicationContext as? BootstrapRuntimeOwner ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                runtimeOwner.bootstrapRuntime.state.collect { state ->
                    lastRuntimeState = state
                    render(state)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val runtimeOwner = requireContext().applicationContext as? BootstrapRuntimeOwner
        if (installState is UpgradeInstallState.Installing || installState is UpgradeInstallState.InstallPermissionRequired) {
            Log.i(UPGRADE_TAG, "Resuming required-upgrade screen and refreshing runtime policy")
            runtimeOwner?.bootstrapRuntime?.refresh()
        }
        syncDownloadMonitor()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            requireContext().registerReceiver(downloadReceiver, filter)
        }
    }

    override fun onStop() {
        downloadMonitorJob?.cancel()
        downloadMonitorJob = null
        runCatching {
            requireContext().unregisterReceiver(downloadReceiver)
        }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        activeDownloadId?.let { outState.putLong(STATE_DOWNLOAD_ID, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        upgradeStateStore = null
        super.onDestroy()
    }

    private fun render(state: BootstrapRuntimeState?) {
        val root = view ?: return
        val title = root.findViewById<TextView>(R.id.required_upgrade_title)
        val subtitle = root.findViewById<TextView>(R.id.required_upgrade_subtitle)
        val versionSummary = root.findViewById<TextView>(R.id.required_upgrade_versions)
        val notes = root.findViewById<TextView>(R.id.required_upgrade_notes)
        val hint = root.findViewById<TextView>(R.id.required_upgrade_hint)
        val primaryAction = root.findViewById<Button>(R.id.required_upgrade_primary_action)
        val upgrade = state?.upgradeStatus

        title.text = "需要升级后才能继续"
        subtitle.text = "当前版本已不满足后台策略要求，首页和能力入口已被拦截。"
        versionSummary.text = buildVersionSummary(state)
        notes.text = state?.release?.releaseNotes?.ifBlank { "当前版本需要先安装新包，再继续进入首页。" }
            ?: "当前版本需要先安装新包，再继续进入首页。"
        hint.text = buildHint(installState)
        syncDownloadMonitor()

        val actionLabel = when (installState) {
            UpgradeInstallState.Idle -> if (upgrade?.artifactUrl.isNullOrBlank()) "等待升级包" else "下载并安装"
            is UpgradeInstallState.Downloading -> "下载中..."
            is UpgradeInstallState.Downloaded -> "开始安装"
            is UpgradeInstallState.Installing -> "重新打开安装器"
            is UpgradeInstallState.InstallPermissionRequired -> "打开安装权限"
            is UpgradeInstallState.Failed -> "重新下载"
        }
        primaryAction.text = actionLabel
        primaryAction.isEnabled = when (installState) {
            UpgradeInstallState.Idle -> !upgrade?.artifactUrl.isNullOrBlank()
            is UpgradeInstallState.Downloading -> false
            is UpgradeInstallState.Downloaded -> true
            is UpgradeInstallState.Installing -> true
            is UpgradeInstallState.InstallPermissionRequired -> true
            is UpgradeInstallState.Failed -> !upgrade?.artifactUrl.isNullOrBlank()
        }
        primaryAction.setOnClickListener {
            handlePrimaryAction(state)
        }
    }

    private fun handlePrimaryAction(state: BootstrapRuntimeState?) {
        val installer = updateInstaller ?: return
        val upgrade = state?.upgradeStatus ?: return
        installState = when (val currentInstallState = installState) {
            UpgradeInstallState.Idle,
            is UpgradeInstallState.Failed -> {
                val started = installer.startDownload(upgrade)
                if (started is UpgradeInstallState.Downloading) {
                    activeDownloadId = started.downloadId
                }
                started
            }

            is UpgradeInstallState.Downloading -> currentInstallState

            is UpgradeInstallState.Downloaded -> {
                activeDownloadId = currentInstallState.downloadId.takeIf { it > 0L } ?: activeDownloadId
                installer.openInstaller(
                    apkUri = currentInstallState.apkUri,
                    downloadId = activeDownloadId,
                    expectedVersion = upgrade.targetVersion ?: upgrade.latestVersion,
                ).normalizeDownloadId(activeDownloadId)
            }

            is UpgradeInstallState.Installing -> {
                activeDownloadId = currentInstallState.downloadId.takeIf { it > 0L } ?: activeDownloadId
                installer.openInstaller(
                    apkUri = currentInstallState.apkUri,
                    downloadId = activeDownloadId,
                    expectedVersion = currentInstallState.expectedVersion,
                ).normalizeDownloadId(activeDownloadId)
            }

            is UpgradeInstallState.InstallPermissionRequired -> {
                activeDownloadId = currentInstallState.downloadId.takeIf { it > 0L } ?: activeDownloadId
                installer.openInstaller(
                    apkUri = currentInstallState.apkUri,
                    downloadId = activeDownloadId,
                    expectedVersion = upgrade.targetVersion ?: upgrade.latestVersion,
                ).normalizeDownloadId(activeDownloadId)
            }
        }
        persistInstallPromptIfNeeded(
            state = installState,
            targetVersion = upgrade.targetVersion ?: upgrade.latestVersion,
        )
        render(state)
    }

    private fun buildVersionSummary(state: BootstrapRuntimeState?): String {
        val upgrade = state?.upgradeStatus
        val currentVersion = upgrade?.currentVersion ?: "unknown"
        val minSupportedVersion = upgrade?.minSupportedVersion ?: "unknown"
        val targetVersion = upgrade?.targetVersion ?: upgrade?.latestVersion ?: "unknown"
        val channel = upgrade?.channel ?: state?.policy?.channel ?: "unknown"
        return "当前版本 $currentVersion\n最低支持 $minSupportedVersion\n目标版本 $targetVersion\n发布通道 $channel"
    }

    private fun buildHint(state: UpgradeInstallState): String {
        return when (state) {
            UpgradeInstallState.Idle -> "按“下载并安装”开始获取最新 APK。"
            is UpgradeInstallState.Downloading -> state.progressPercent
                ?.let { "升级包正在后台下载，当前进度 $it%。" }
                ?: "升级包正在后台下载，下载完成后可直接安装。"
            is UpgradeInstallState.Downloaded -> "升级包已就绪，按“开始安装”进入系统安装流程。"
            is UpgradeInstallState.Installing -> buildString {
                append("系统安装器已打开，安装完成后会自动返回首页。")
                state.expectedVersion?.takeIf(String::isNotBlank)?.let { append(" 预期版本 $it。") }
            }
            is UpgradeInstallState.InstallPermissionRequired -> "系统当前未允许本应用安装未知来源包，先打开权限，再返回继续安装。"
            is UpgradeInstallState.Failed -> state.message
        }
    }

    private fun restoreInstallState(downloadId: Long): UpgradeInstallState {
        val restored = updateInstaller?.restore(downloadId) ?: UpgradeInstallState.Idle
        return restored.normalizeDownloadId(downloadId)
    }

    private fun syncDownloadMonitor() {
        val downloadId = activeDownloadId
        if (downloadId == null || installState !is UpgradeInstallState.Downloading || view == null) {
            downloadMonitorJob?.cancel()
            downloadMonitorJob = null
            return
        }
        if (downloadMonitorJob?.isActive == true) {
            return
        }
        downloadMonitorJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val restored = restoreInstallState(downloadId)
                if (restored != installState) {
                    installState = restored
                    if (restored is UpgradeInstallState.Downloaded) {
                        Log.i(UPGRADE_TAG, "Upgrade download completed downloadId=$downloadId")
                    } else if (restored is UpgradeInstallState.Failed) {
                        Log.w(UPGRADE_TAG, "Upgrade download failed downloadId=$downloadId message=${restored.message}")
                    }
                    render(lastRuntimeState)
                }
                if (restored !is UpgradeInstallState.Downloading) {
                    break
                }
                delay(DOWNLOAD_POLL_INTERVAL_MS)
            }
            downloadMonitorJob = null
        }
    }

    private fun persistInstallPromptIfNeeded(
        state: UpgradeInstallState,
        targetVersion: String?,
    ) {
        if (state !is UpgradeInstallState.Installing && state !is UpgradeInstallState.InstallPermissionRequired) {
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            upgradeStateStore?.markInstallPromptOpened(
                targetVersion = targetVersion,
                downloadId = activeDownloadId,
            )
        }
    }

    companion object {
        private const val DOWNLOAD_POLL_INTERVAL_MS = 1_000L
        private const val STATE_DOWNLOAD_ID = "state_download_id"
        private const val UPGRADE_TAG = "OpenClawUpgrade"
    }
}

private fun UpgradeInstallState.normalizeDownloadId(downloadId: Long?): UpgradeInstallState {
    return when (this) {
        is UpgradeInstallState.Downloaded -> {
            if (this.downloadId > 0L) this else downloadId?.let { copy(downloadId = it) } ?: this
        }

        is UpgradeInstallState.Installing -> {
            if (this.downloadId > 0L) this else downloadId?.let { copy(downloadId = it) } ?: this
        }

        is UpgradeInstallState.InstallPermissionRequired -> {
            if (this.downloadId > 0L) this else downloadId?.let { copy(downloadId = it) } ?: this
        }

        else -> this
    }
}
