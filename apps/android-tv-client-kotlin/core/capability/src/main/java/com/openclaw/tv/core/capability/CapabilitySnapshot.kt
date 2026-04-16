package com.openclaw.tv.core.capability

data class CapabilitySnapshot(
    val hasVendorVoiceService: Boolean,
    val hasVendorMediaService: Boolean,
    val hasVendorProjectorService: Boolean,
    val hasVendorDeviceOpsService: Boolean,
    val hasSpeechRecognizer: Boolean,
    val hasTextToSpeech: Boolean,
    val installedPackages: Map<String, Boolean> = emptyMap(),
) {
    fun isAppInstalled(packageName: String): Boolean = installedPackages[packageName] == true
}
