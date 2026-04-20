package com.openclaw.tv.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLauncherTest {

    @Test
    fun launch_returns_launched_when_resolver_provides_intent() {
        val command = FakeLaunchCommand()
        var capturedCommand: FakeLaunchCommand? = null
        val launcher = AppLauncher(
            resolver = FakeLaunchIntentResolver(
                installedPackages = setOf("com.demo.app"),
                commands = mapOf("com.demo.app" to command),
            ),
            launch = { nextCommand -> capturedCommand = nextCommand as FakeLaunchCommand },
        )

        val result = launcher.launch("com.demo.app")

        assertEquals(AppLaunchResult.Launched, result)
        assertEquals(command.copy(newTask = true), capturedCommand)
    }

    @Test
    fun launch_returns_not_installed_when_package_is_missing() {
        val launcher = AppLauncher(
            resolver = FakeLaunchIntentResolver(),
            launch = { error("Should not launch missing package") },
        )

        val result = launcher.launch("com.missing.app")

        assertEquals(AppLaunchResult.NotInstalled, result)
    }

    @Test
    fun launch_returns_no_launch_activity_when_package_exists_but_has_no_entry() {
        val launcher = AppLauncher(
            resolver = FakeLaunchIntentResolver(installedPackages = setOf("com.demo.service")),
            launch = { error("Should not launch package without entry activity") },
        )

        val result = launcher.launch("com.demo.service")

        assertEquals(AppLaunchResult.NoLaunchActivity, result)
    }

    private class FakeLaunchIntentResolver(
        private val installedPackages: Set<String> = emptySet(),
        private val commands: Map<String, FakeLaunchCommand> = emptyMap(),
    ) : LaunchIntentResolver {

        override fun isInstalled(packageName: String): Boolean = packageName in installedPackages

        override fun resolve(packageName: String): LaunchCommand? = commands[packageName]
    }

    private data class FakeLaunchCommand(
        val newTask: Boolean = false,
    ) : LaunchCommand {
        override fun withNewTask(): FakeLaunchCommand = copy(newTask = true)
    }
}
