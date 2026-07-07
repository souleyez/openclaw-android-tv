package com.openclaw.tv.feature.home

import com.openclaw.tv.core.network.PlatformApi
import com.openclaw.tv.core.network.dto.TvVoiceCommandAppDto
import com.openclaw.tv.core.network.dto.TvVoiceCommandDto
import com.openclaw.tv.core.network.dto.TvVoiceCommandRequestDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.Locale

internal data class ResolvedHomeVoiceCommand(
    val command: HomeVoiceCommand,
    val reply: String,
)

internal open class HomeVoiceCommandRepository(
    private val platformApi: PlatformApi,
    private val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
) {
    open suspend fun resolve(
        sessionToken: String,
        transcript: String,
        targets: List<HomeVoiceAppTarget> = HomeVoiceCommandInterpreter.defaultTargets(),
    ): ResolvedHomeVoiceCommand? {
        val normalizedTranscript = transcript.trim()
        if (sessionToken.isBlank() || normalizedTranscript.isBlank()) {
            return null
        }
        return try {
            withTimeout(requestTimeoutMillis) {
                val envelope = platformApi.resolveTvVoiceCommand(
                    sessionToken = sessionToken,
                    request = TvVoiceCommandRequestDto(
                        transcript = normalizedTranscript,
                        availableApps = targets.map { target ->
                            TvVoiceCommandAppDto(
                                title = target.title,
                                packageName = target.packageName,
                                aliases = target.aliases,
                            )
                        },
                    ),
                )
                envelope.command.toResolvedHomeVoiceCommand(targets)
            }
        } catch (error: Exception) {
            error.rethrowIfExternalCancellation()
            null
        }
    }

    private companion object {
        const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 2_500L
    }
}

private fun Throwable.rethrowIfExternalCancellation() {
    if (this is CancellationException && this !is TimeoutCancellationException) {
        throw this
    }
}

private fun TvVoiceCommandDto.toResolvedHomeVoiceCommand(
    targets: List<HomeVoiceAppTarget>,
): ResolvedHomeVoiceCommand {
    val command = when (action.trim().lowercase(Locale.ROOT)) {
        "open_app" -> resolveOpenAppCommand(targets)
        "open_cast" -> HomeVoiceCommand.OpenCast
        "open_local_apps" -> HomeVoiceCommand.OpenLocalApps
        "open_service_center" -> HomeVoiceCommand.OpenServiceCenter
        "open_settings" -> HomeVoiceCommand.OpenSettings
        else -> HomeVoiceCommand.Unknown
    }
    return ResolvedHomeVoiceCommand(
        command = command,
        reply = reply.trim(),
    )
}

private fun TvVoiceCommandDto.resolveOpenAppCommand(
    targets: List<HomeVoiceAppTarget>,
): HomeVoiceCommand {
    val normalizedPackage = packageName.trim()
    val normalizedTitle = targetTitle.normalizeVoiceModelText()
    val target = targets.firstOrNull { target ->
        target.packageName == normalizedPackage
    } ?: targets.firstOrNull { target ->
        target.title.normalizeVoiceModelText() == normalizedTitle ||
            target.aliases.any { alias -> alias.normalizeVoiceModelText() == normalizedTitle }
    }
    return if (target == null) {
        HomeVoiceCommand.Unknown
    } else {
        HomeVoiceCommand.OpenApp(
            title = target.title,
            packageName = target.packageName,
        )
    }
}

private fun String.normalizeVoiceModelText(): String {
    return lowercase(Locale.ROOT)
        .replace(Regex("[\\s,，。.!！?？:：;；\"'“”‘’]"), "")
}
