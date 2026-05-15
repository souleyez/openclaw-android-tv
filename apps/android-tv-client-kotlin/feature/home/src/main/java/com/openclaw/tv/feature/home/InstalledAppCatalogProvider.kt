package com.openclaw.tv.feature.home

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Environment
import java.io.File
import java.util.Locale

internal data class InstalledLaunchableAppItem(
    val title: String,
    val packageName: String,
    val summary: String,
    val isSystemApp: Boolean,
)

internal enum class AppManagementItemKind {
    ADD_SHORTCUT,
    INSTALLED,
    APK_INSTALL,
    APK_UPGRADE,
}

internal data class AppManagementItem(
    val id: String,
    val kind: AppManagementItemKind,
    val title: String,
    val packageName: String,
    val summary: String,
    val typeLabel: String,
    val primaryActionLabel: String,
    val secondaryActionLabel: String? = null,
    val monogram: String = "",
    val isSystemApp: Boolean = false,
    val apkPath: String? = null,
)

internal data class AppManagementCatalog(
    val installedApps: List<InstalledLaunchableAppItem>,
    val managementItems: List<AppManagementItem>,
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

    fun loadAppManagementCatalog(scanLocalApks: Boolean): AppManagementCatalog {
        val installedApps = loadLaunchableApps()
        val installedPackages = installedApps.associateBy { it.packageName }
        val installedItems = installedApps.map { app ->
            AppManagementItem(
                id = "installed:${app.packageName}",
                kind = AppManagementItemKind.INSTALLED,
                title = app.title,
                packageName = app.packageName,
                summary = app.summary,
                typeLabel = if (app.isSystemApp) "系统" else "应用",
                primaryActionLabel = "按确定打开",
                secondaryActionLabel = if (app.isSystemApp) {
                    "系统应用不可卸载"
                } else {
                    "长按确定卸载"
                },
                monogram = HomeAppCatalog.fallbackMonogram(app.title, app.packageName),
                isSystemApp = app.isSystemApp,
            )
        }
        val apkItems = if (scanLocalApks) {
            scanLocalApkItems(installedPackages)
        } else {
            emptyList()
        }
        return AppManagementCatalog(
            installedApps = installedApps,
            managementItems = listOf(addShortcutItem()) + apkItems + installedItems,
        )
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
        val isSystemApp = ((activityInfo.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0
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

    private fun addShortcutItem(): AppManagementItem {
        return AppManagementItem(
            id = "add_app",
            kind = AppManagementItemKind.ADD_SHORTCUT,
            title = "新增应用",
            packageName = "",
            summary = "打开应用商店，或从 U 盘/本机 APK 安装",
            typeLabel = "添加",
            primaryActionLabel = "按确定新增",
            monogram = "+",
        )
    }

    private fun scanLocalApkItems(
        installedPackages: Map<String, InstalledLaunchableAppItem>,
    ): List<AppManagementItem> {
        return storageRoots()
            .flatMap(::scanApkFiles)
            .distinctBy { file -> file.absolutePath }
            .mapNotNull { file -> file.toApkManagementItem(installedPackages) }
            .sortedWith(
                compareBy<AppManagementItem> { it.kind != AppManagementItemKind.APK_UPGRADE }
                    .thenBy { it.title.lowercase(Locale.getDefault()) }
                    .thenBy { it.apkPath.orEmpty().lowercase(Locale.getDefault()) },
            )
            .take(MAX_APK_ITEMS)
    }

    private fun storageRoots(): List<File> {
        val roots = linkedSetOf<File>()
        runCatching { Environment.getExternalStorageDirectory() }
            .getOrNull()
            ?.let(roots::add)
        applicationContext.getExternalFilesDirs(null)
            ?.forEach { appExternalDir ->
                appExternalDir.storageRoot()?.let(roots::add)
            }
        listOf(File("/storage"), File("/mnt/media_rw"))
            .forEach { parent ->
                runCatching { parent.listFiles() }
                    .getOrNull()
                    .orEmpty()
                    .filter { file -> file.isDirectory && file.name !in SKIPPED_STORAGE_ROOTS }
                    .forEach(roots::add)
            }
        return roots
            .filter { root -> runCatching { root.exists() && root.canRead() }.getOrDefault(false) }
            .toList()
    }

    private fun File.storageRoot(): File? {
        val segments = absolutePath.split(File.separatorChar).filter(String::isNotBlank)
        if (segments.size < 2 || segments.first() != "storage") {
            return null
        }
        return File(File.separator + segments.take(2).joinToString(File.separator))
    }

    private fun scanApkFiles(root: File): List<File> {
        val found = mutableListOf<File>()
        val pending = ArrayDeque<Pair<File, Int>>()
        pending += root to 0
        while (pending.isNotEmpty() && found.size < MAX_APK_SCAN_RESULTS) {
            val (current, depth) = pending.removeFirst()
            if (current.name in SKIPPED_DIRECTORY_NAMES) {
                continue
            }
            val children = runCatching { current.listFiles() }.getOrNull().orEmpty()
            for (child in children) {
                if (found.size >= MAX_APK_SCAN_RESULTS) {
                    break
                }
                when {
                    child.isFile && child.name.endsWith(".apk", ignoreCase = true) -> found += child
                    child.isDirectory && depth < MAX_APK_SCAN_DEPTH -> pending += child to depth + 1
                }
            }
        }
        return found
    }

    private fun File.toApkManagementItem(
        installedPackages: Map<String, InstalledLaunchableAppItem>,
    ): AppManagementItem? {
        val packageInfo = readArchivePackageInfo() ?: return null
        val packageName = packageInfo.packageName?.trim().orEmpty()
        if (packageName.isBlank()) {
            return null
        }
        val applicationInfo = packageInfo.applicationInfo
        applicationInfo?.sourceDir = absolutePath
        applicationInfo?.publicSourceDir = absolutePath
        val title = applicationInfo
            ?.loadLabel(packageManager)
            ?.toString()
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: nameWithoutExtension
        val versionLabel = packageInfo.versionName
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: "v${packageInfo.versionCodeCompat()}"
        val installedApp = installedPackages[packageName]
        val kind = if (installedApp != null) {
            AppManagementItemKind.APK_UPGRADE
        } else {
            AppManagementItemKind.APK_INSTALL
        }
        return AppManagementItem(
            id = "apk:$absolutePath",
            kind = kind,
            title = title,
            packageName = packageName,
            summary = if (installedApp != null) {
                "USB/本机 APK · $versionLabel · 可升级或重装 ${installedApp.title}"
            } else {
                "USB/本机 APK · $versionLabel · ${name}"
            },
            typeLabel = if (kind == AppManagementItemKind.APK_UPGRADE) "升级" else "安装",
            primaryActionLabel = if (kind == AppManagementItemKind.APK_UPGRADE) {
                "按确定升级"
            } else {
                "按确定安装"
            },
            monogram = HomeAppCatalog.fallbackMonogram(title, packageName),
            apkPath = absolutePath,
        )
    }

    private fun File.readArchivePackageInfo(): PackageInfo? {
        return runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(absolutePath, 0)
        }.getOrNull()
    }

    private fun PackageInfo.versionCodeCompat(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
        }
    }

    private companion object {
        const val MAX_APK_SCAN_DEPTH = 5
        const val MAX_APK_SCAN_RESULTS = 120
        const val MAX_APK_ITEMS = 40
        val SKIPPED_STORAGE_ROOTS = setOf("self", "emulated")
        val SKIPPED_DIRECTORY_NAMES = setOf(
            ".android_secure",
            "Android",
            "LOST.DIR",
            "System Volume Information",
        )
    }
}
