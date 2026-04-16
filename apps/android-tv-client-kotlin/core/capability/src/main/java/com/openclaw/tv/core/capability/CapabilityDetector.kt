package com.openclaw.tv.core.capability

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.content.pm.PackageManager

data class VendorPackages(
    val voicePackage: String = "vendor.voice",
    val mediaPackage: String = "vendor.media",
    val projectorPackage: String = "vendor.projector",
    val deviceOpsPackage: String = "vendor.deviceops",
)

class CapabilityDetector internal constructor(
    private val probe: CapabilityProbe,
    private val vendorPackages: VendorPackages = VendorPackages(),
) {

    constructor(
        context: Context,
        vendorPackages: VendorPackages = VendorPackages(),
    ) : this(
        probe = AndroidCapabilityProbe(context.applicationContext),
        vendorPackages = vendorPackages,
    )

    fun snapshot(targetPackages: List<String> = emptyList()): CapabilitySnapshot {
        return CapabilitySnapshot(
            hasVendorVoiceService = probe.hasPackage(vendorPackages.voicePackage),
            hasVendorMediaService = probe.hasPackage(vendorPackages.mediaPackage),
            hasVendorProjectorService = probe.hasPackage(vendorPackages.projectorPackage),
            hasVendorDeviceOpsService = probe.hasPackage(vendorPackages.deviceOpsPackage),
            hasSpeechRecognizer = probe.hasSpeechRecognizer(),
            hasTextToSpeech = probe.hasTextToSpeech(),
            installedPackages = targetPackages.associateWith(probe::hasPackage),
        )
    }
}

internal interface CapabilityProbe {
    fun hasPackage(packageName: String): Boolean
    fun hasSpeechRecognizer(): Boolean
    fun hasTextToSpeech(): Boolean
}

private class AndroidCapabilityProbe(
    private val context: Context,
) : CapabilityProbe {

    private val packageManager: PackageManager = context.packageManager

    override fun hasPackage(packageName: String): Boolean {
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

    override fun hasSpeechRecognizer(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    override fun hasTextToSpeech(): Boolean {
        val intent = Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return activities.isNotEmpty()
    }
}
