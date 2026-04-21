package com.openclaw.tv.feature.appdelivery

import com.openclaw.tv.core.storage.AppDownloadStore

fun interface InstalledPackageChecker {
    fun isInstalled(packageName: String): Boolean
}

class AppInstallStateTracker(
    private val downloadStore: AppDownloadStore,
    private val installedPackageChecker: InstalledPackageChecker,
) {

    suspend fun reconcileInstalledPackages(): List<String> {
        val installedAppIds = downloadStore.readAll()
            .values
            .filter { state ->
                val packageName = state.packageName.trim()
                packageName.isNotBlank() && installedPackageChecker.isInstalled(packageName)
            }
            .map { it.appId }
            .distinct()
        for (appId in installedAppIds) {
            downloadStore.remove(appId)
        }
        return installedAppIds
    }

    suspend fun handlePackageInstalled(packageName: String): List<String> {
        val normalizedPackageName = packageName.trim()
        if (normalizedPackageName.isBlank()) {
            return emptyList()
        }
        val matchedAppIds = downloadStore.readAll()
            .values
            .filter { it.packageName.trim() == normalizedPackageName }
            .map { it.appId }
            .distinct()
        for (appId in matchedAppIds) {
            downloadStore.remove(appId)
        }
        return matchedAppIds
    }
}
