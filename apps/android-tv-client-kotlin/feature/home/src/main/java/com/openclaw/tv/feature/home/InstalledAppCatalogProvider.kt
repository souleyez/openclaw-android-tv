package com.openclaw.tv.feature.home

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import java.util.Locale

internal data class InstalledLaunchableAppItem(
    val title: String,
    val packageName: String,
    val summary: String,
    val isSystemApp: Boolean,
)

internal class InstalledAppCatalogProvider(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val packageManager = applicationContext.packageManager

    fun loadLaunchableApps(): List<InstalledLaunchableAppItem> {
        return listOf(
            queryMainActivities(Intent.CATEGORY_LEANBACK_LAUNCHER),
            queryMainActivities(Intent.CATEGORY_LAUNCHER),
        )
            .flatMap { it }
            .mapNotNull(::toLaunchableItem)
            .filterNot { it.packageName == applicationContext.packageName }
            .distinctBy { it.packageName }
            .sortedWith(
                compareBy<InstalledLaunchableAppItem> { it.isSystemApp }
                    .thenBy { it.title.lowercase(Locale.getDefault()) }
                    .thenBy { it.packageName.lowercase(Locale.getDefault()) },
            )
            .toList()
    }

    private fun queryMainActivities(category: String): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(category)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
    }

    private fun toLaunchableItem(resolveInfo: ResolveInfo): InstalledLaunchableAppItem? {
        val activityInfo = resolveInfo.activityInfo ?: return null
        val packageName = activityInfo.packageName?.trim().orEmpty()
        if (packageName.isEmpty()) {
            return null
        }
        val label = resolveInfo.loadLabel(packageManager)
            ?.toString()
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: packageName
        val isSystemApp = (activityInfo.applicationInfo?.flags ?: 0 and ApplicationInfo.FLAG_SYSTEM) != 0
        return InstalledLaunchableAppItem(
            title = label,
            packageName = packageName,
            summary = if (isSystemApp) {
                "系统应用 · $packageName"
            } else {
                packageName
            },
            isSystemApp = isSystemApp,
        )
    }
}
