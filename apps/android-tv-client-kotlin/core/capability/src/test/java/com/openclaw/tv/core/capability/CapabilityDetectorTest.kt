package com.openclaw.tv.core.capability

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityDetectorTest {

    @Test
    fun detector_reports_vendor_and_public_api_capabilities() {
        val detector = CapabilityDetector(
            probe = FakeCapabilityProbe(
                packages = setOf("vendor.media", "com.netflix.ninja"),
                speechRecognizer = true,
                textToSpeech = false,
            ),
        )

        val snapshot = detector.snapshot(
            targetPackages = listOf("com.netflix.ninja", "com.spotify.tv.android"),
        )

        assertFalse(snapshot.hasVendorVoiceService)
        assertTrue(snapshot.hasVendorMediaService)
        assertFalse(snapshot.hasVendorProjectorService)
        assertFalse(snapshot.hasVendorDeviceOpsService)
        assertTrue(snapshot.hasSpeechRecognizer)
        assertFalse(snapshot.hasTextToSpeech)
        assertTrue(snapshot.isAppInstalled("com.netflix.ninja"))
        assertFalse(snapshot.isAppInstalled("com.spotify.tv.android"))
    }

    private class FakeCapabilityProbe(
        private val packages: Set<String>,
        private val speechRecognizer: Boolean,
        private val textToSpeech: Boolean,
    ) : CapabilityProbe {
        override fun hasPackage(packageName: String): Boolean = packages.contains(packageName)

        override fun hasSpeechRecognizer(): Boolean = speechRecognizer

        override fun hasTextToSpeech(): Boolean = textToSpeech
    }
}
