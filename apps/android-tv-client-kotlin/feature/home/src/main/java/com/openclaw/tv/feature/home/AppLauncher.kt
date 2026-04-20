package com.openclaw.tv.feature.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

internal sealed interface AppLaunchResult {
    data object Launched : AppLaunchResult
    data object NotInstalled : AppLaunchResult
    data object NoLaunchActivity : AppLaunchResult
}

internal interface LaunchCommand {
    fun withNewTask(): LaunchCommand
}

internal class AppLauncher internal constructor(
    private val resolver: LaunchIntentResolver,
    private val launch: (LaunchCommand) -> Unit,
) {

    constructor(context: Context) : this(
        resolver = AndroidLaunchIntentResolver(context.applicationContext.packageManager),
        launch = { command ->
            val androidCommand = command as? AndroidLaunchCommand
                ?: error("Unsupported launch command: ${command::class.java.simpleName}")
            context.startActivity(androidCommand.intent)
        },
    )

    fun launch(packageName: String): AppLaunchResult {
        val command = resolver.resolve(packageName)?.withNewTask() ?: return if (resolver.isInstalled(packageName)) {
            AppLaunchResult.NoLaunchActivity
        } else {
            AppLaunchResult.NotInstalled
        }
        launch(command)
        return AppLaunchResult.Launched
    }
}

internal interface LaunchIntentResolver {
    fun isInstalled(packageName: String): Boolean
    fun resolve(packageName: String): LaunchCommand?
}

private class AndroidLaunchIntentResolver(
    private val packageManager: PackageManager,
) : LaunchIntentResolver {

    override fun isInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    override fun resolve(packageName: String): LaunchCommand? {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
        return intent?.let(::AndroidLaunchCommand)
    }
}

private data class AndroidLaunchCommand(
    val intent: Intent,
) : LaunchCommand {
    override fun withNewTask(): AndroidLaunchCommand {
        return copy(intent = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
