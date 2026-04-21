package com.openclaw.tv.feature.home

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PendingInstallRequestStateTest {

    @Test
    fun bundle_roundtrips_pending_install_request() {
        val bundle = Bundle()
        val request = PendingInstallRequest(
            appId = "youtube",
            title = "YouTube",
            downloadId = 42L,
            localFilePath = "/downloads/youtube.apk",
        )

        bundle.putPendingInstallRequestState(request)

        assertEquals(request, bundle.readPendingInstallRequestState())
    }

    @Test
    fun bundle_returns_null_when_required_fields_are_missing() {
        val bundle = Bundle().apply {
            putString("pending_install_title", "YouTube")
        }

        assertNull(bundle.readPendingInstallRequestState())
    }

    @Test
    fun bundle_clears_pending_install_request_when_request_is_null() {
        val bundle = Bundle().apply {
            putString("pending_install_app_id", "youtube")
            putString("pending_install_title", "YouTube")
            putLong("pending_install_download_id", 42L)
            putString("pending_install_local_file_path", "/downloads/youtube.apk")
        }

        bundle.putPendingInstallRequestState(null)

        assertNull(bundle.readPendingInstallRequestState())
    }
}
