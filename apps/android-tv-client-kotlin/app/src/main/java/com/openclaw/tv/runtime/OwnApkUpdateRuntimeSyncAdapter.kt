package com.openclaw.tv.runtime

import com.openclaw.tv.BuildConfig
import com.openclaw.tv.upgrade.OwnApkDownloadCoordinator
import com.openclaw.tv.upgrade.OwnApkUpdateAgent

class OwnApkUpdateRuntimeSyncAdapter(
    private val agent: OwnApkUpdateAgent,
    private val downloadCoordinator: OwnApkDownloadCoordinator? = null,
    private val currentVersionCodeProvider: () -> Long = { BuildConfig.VERSION_CODE.toLong() },
) : OwnApkUpdateRuntimeSync {
    override suspend fun sync(sessionToken: String): Boolean {
        val currentVersionCode = currentVersionCodeProvider().coerceAtLeast(0L)
        downloadCoordinator?.reconcile(
            sessionToken = sessionToken,
            currentVersionCode = currentVersionCode,
        )
        agent.sync(sessionToken)
        return true
    }
}
