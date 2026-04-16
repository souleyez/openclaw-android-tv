package com.openclaw.tv.core.network

import com.openclaw.tv.core.network.dto.BootstrapAuthRequestDto
import com.openclaw.tv.core.network.dto.IssueLeaseRequestDto
import com.openclaw.tv.core.network.dto.LeaseStatusRequestDto
import com.openclaw.tv.core.network.dto.ReleaseLeaseRequestDto
import com.openclaw.tv.core.network.dto.RenewLeaseRequestDto
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlatformApiContractTest {

    private lateinit var server: MockWebServer
    private lateinit var api: PlatformApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = OkHttpPlatformApi(server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun bootstrap_posts_expected_contract() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "status":"ok",
                  "user":{"id":"user_1","principalType":"phone","principalKey":"18800001111","principalLabel":"ATV Test","phone":"18800001111","source":"self_registered","status":"active"},
                  "device":{"id":"device_1"},
                  "session":{"token":"token_123","expiresAt":"2026-04-16T12:00:00.000Z"},
                  "upgrade":{"state":"ok","channel":"stable","currentVersion":"0.1.0","minSupportedVersion":"0.1.0","latestVersion":"0.2.0","targetVersion":"0.2.0"},
                  "modelAccess":{"mode":"lease","providers":["moonshot"],"defaultModel":"moonshot-v1","allowedModels":["moonshot-v1"]}
                }
                """.trimIndent(),
            ),
        )

        val response = api.bootstrapAuth(
            BootstrapAuthRequestDto(
                principalType = "phone",
                principalKey = "18800001111",
                principalLabel = "ATV Test",
                projectKey = "openclaw",
                deviceFingerprint = "fingerprint-01",
                clientVersion = "0.1.0",
                osFamily = "android",
            ),
        )

        val request = server.takeRequest()
        assertEquals("/client/bootstrap/auth", request.path)
        assertEquals("POST", request.method)
        assertTrue(request.body.readUtf8().contains("\"deviceFingerprint\":\"fingerprint-01\""))
        assertEquals("token_123", response.session.token)
        assertEquals("lease", response.modelAccess.mode)
    }

    @Test
    fun authenticated_routes_follow_home_contract() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "status":"ok",
                  "policy":{
                    "channel":"stable",
                    "minSupportedVersion":"0.1.0",
                    "targetVersion":"0.2.0",
                    "forceUpgrade":false,
                    "allowSelfRegister":true,
                    "modelAccessMode":"lease",
                    "providerScopes":["moonshot","minimax"],
                    "defaultModel":"moonshot-v1",
                    "allowedModels":["moonshot-v1","minimax-v2"]
                  }
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "status":"ok",
                  "lease":{
                    "id":"lease_1",
                    "token":"lease_token_1",
                    "expiresAt":"2026-04-16T12:10:00.000Z",
                    "providerScope":"moonshot",
                    "leaseMode":"direct_provider_temporary",
                    "leaseProfile":"client_short",
                    "lastUsedAt":"2026-04-16T12:00:00.000Z",
                    "lastRenewedAt":"2026-04-16T12:00:00.000Z",
                    "sticky":false
                  },
                  "proxy":{"baseUrl":"https://proxy.example.com"}
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "status":"ok",
                  "lease":{
                    "id":"lease_1",
                    "expiresAt":"2026-04-16T12:10:00.000Z",
                    "providerScope":"moonshot",
                    "leaseMode":"direct_provider_temporary",
                    "leaseProfile":"client_short",
                    "lastUsedAt":"2026-04-16T12:00:00.000Z",
                    "lastRenewedAt":"2026-04-16T12:00:00.000Z",
                    "sticky":false
                  },
                  "availableProfiles":[{"id":"client_short","ttlMinutes":5,"leaseMode":"direct_provider_temporary","renewWindowSeconds":45,"contentionPriority":10,"contentionIdleReleaseMinutes":0,"sticky":false}],
                  "proxy":{"baseUrl":"https://proxy.example.com"}
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "status":"ok",
                  "lease":{
                    "id":"lease_1",
                    "expiresAt":"2026-04-16T12:15:00.000Z",
                    "providerScope":"moonshot",
                    "leaseMode":"direct_provider_temporary",
                    "leaseProfile":"client_short",
                    "lastUsedAt":"2026-04-16T12:00:00.000Z",
                    "lastRenewedAt":"2026-04-16T12:05:00.000Z",
                    "sticky":false
                  }
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "status":"ok",
                  "released":true,
                  "leaseId":"lease_1"
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "status":"ok",
                  "release":{
                    "id":"rel_1",
                    "projectKey":"openclaw",
                    "channel":"stable",
                    "version":"0.2.0",
                    "status":"published",
                    "artifactType":"apk",
                    "artifactUrl":"https://cdn.example.com/app.apk",
                    "artifactSha256":"abc",
                    "artifactSize":12345,
                    "runtimeVersion":"0.2.0",
                    "releaseMetadata":{"track":"stable"},
                    "openclawVersion":"0.2.0",
                    "installerVersion":"1",
                    "minSupportedVersion":"0.1.0",
                    "releaseNotes":"notes",
                    "publishedAt":"2026-04-16T10:00:00.000Z",
                    "createdAt":"2026-04-16T09:00:00.000Z",
                    "updatedAt":"2026-04-16T10:00:00.000Z"
                  }
                }
                """.trimIndent(),
            ),
        )

        val token = "session_token_1"
        val policy = api.getPolicy(token, "openclaw")
        val issuedLease = api.issueLease(
            token,
            IssueLeaseRequestDto(projectKey = "openclaw", providerScope = "moonshot", leaseProfile = "client_short"),
        )
        val status = api.getLeaseStatus(
            token,
            LeaseStatusRequestDto(projectKey = "openclaw", providerScope = "moonshot", leaseId = "lease_1"),
        )
        val renewed = api.renewLease(
            token,
            RenewLeaseRequestDto(
                projectKey = "openclaw",
                providerScope = "moonshot",
                leaseId = "lease_1",
                leaseToken = "lease_token_1",
            ),
        )
        val released = api.releaseLease(
            token,
            ReleaseLeaseRequestDto(
                projectKey = "openclaw",
                providerScope = "moonshot",
                leaseId = "lease_1",
                leaseToken = "lease_token_1",
            ),
        )
        val latestRelease = api.getLatestRelease(token, "stable", "openclaw")

        val policyRequest = server.takeRequest()
        assertEquals("/client/policy?projectKey=openclaw", policyRequest.path)
        assertEquals("Bearer $token", policyRequest.getHeader("Authorization"))

        val issueRequest = server.takeRequest()
        assertEquals("/client/model-lease", issueRequest.path)
        assertTrue(issueRequest.body.readUtf8().contains("\"leaseProfile\":\"client_short\""))

        val statusRequest = server.takeRequest()
        assertEquals(
            "/client/model-lease/status?projectKey=openclaw&providerScope=moonshot&leaseId=lease_1",
            statusRequest.path,
        )

        val renewRequest = server.takeRequest()
        assertEquals("/client/model-lease/renew", renewRequest.path)
        assertTrue(renewRequest.body.readUtf8().contains("\"leaseToken\":\"lease_token_1\""))

        val releaseRequest = server.takeRequest()
        assertEquals("/client/model-lease/release", releaseRequest.path)
        assertTrue(releaseRequest.body.readUtf8().contains("\"leaseId\":\"lease_1\""))

        val latestRequest = server.takeRequest()
        assertEquals("/client/releases/latest?projectKey=openclaw&channel=stable", latestRequest.path)

        assertEquals("moonshot", policy.policy.providerScopes.first())
        assertEquals("lease_token_1", issuedLease.lease.token)
        assertEquals("client_short", status.availableProfiles.first().id)
        assertEquals("2026-04-16T12:05:00.000Z", renewed.lease?.lastRenewedAt)
        assertTrue(released.released)
        assertEquals("0.2.0", latestRelease.release?.version)
    }
}
