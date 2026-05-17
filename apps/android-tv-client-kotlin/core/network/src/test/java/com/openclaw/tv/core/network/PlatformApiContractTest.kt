package com.openclaw.tv.core.network

import com.openclaw.tv.core.network.dto.BootstrapAuthRequestDto
import com.openclaw.tv.core.network.dto.IssueLeaseRequestDto
import com.openclaw.tv.core.network.dto.LeaseStatusRequestDto
import com.openclaw.tv.core.network.dto.ReleaseLeaseRequestDto
import com.openclaw.tv.core.network.dto.RenewLeaseRequestDto
import com.openclaw.tv.core.network.dto.TvResourceSessionReferenceDto
import com.openclaw.tv.core.network.dto.TvResourceSessionRequestDto
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
    fun tv_home_config_uses_authenticated_region_query_contract() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "projectKey":"openclaw-android-tv",
                  "projectLabel":"百万龙虾 TV",
                  "runtimeManifestPath":"/api/me/runtime-manifest",
                  "entitlementPath":"/api/me/entitlement",
                  "resourceSessionBasePath":"/api/client/resource-session",
                  "manifestPollAfterSeconds":900,
                  "resourceSessionPollAfterSeconds":15,
                  "backgroundDownloadEnabled":true,
                  "idleDownloadOnly":true
                }
                """.trimIndent(),
            ),
        )

        val regionAwareApi = OkHttpPlatformApi(
            server.url("/").toString(),
            tvRuntimeRequestContextProvider = { TvRuntimeRequestContext(countryCode = "CN", regionCode = "SH") },
            tvRuntimeSessionTokenProvider = { "session_token_1" },
        )
        val response = regionAwareApi.getTvHomeConfig()

        val request = server.takeRequest()
        assertEquals("/me/tv-home-config?countryCode=CN&regionCode=SH", request.path)
        assertEquals("GET", request.method)
        assertEquals("Bearer session_token_1", request.getHeader("Authorization"))
        assertEquals("openclaw-android-tv", response.projectKey)
        assertEquals("/api/me/runtime-manifest", response.runtimeManifestPath)
        assertEquals(15, response.resourceSessionPollAfterSeconds)
    }

    @Test
    fun runtime_manifest_uses_authenticated_home_contract() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "manifestVersion":"2026-04-20.1",
                  "countryCode":"CN",
                  "regionCode":"SH",
                  "apps":[],
                  "adSlots":[
                    {
                      "slotId":"home.hero",
                      "enabled":true,
                      "creatives":[
                        {
                          "creativeId":"creative-home-hero-001",
                          "mediaType":"image",
                          "assetUrl":"https://cdn.example.com/ads/hero-1.png",
                          "altText":"Spring promotion banner",
                          "clickActionType":"deeplink",
                          "clickActionValue":"openclaw://promo/spring",
                          "startsAt":"2026-04-20T00:00:00.000Z",
                          "endsAt":"2026-05-01T00:00:00.000Z"
                        },
                        {
                          "creativeId":"creative-home-hero-002",
                          "mediaType":"image",
                          "assetUrl":"https://cdn.example.com/ads/hero-2.png",
                          "altText":"VIP campaign banner",
                          "clickActionType":"none",
                          "clickActionValue":null,
                          "startsAt":null,
                          "endsAt":null
                        }
                      ]
                    }
                  ],
                  "pollAfterSeconds":900,
                  "eventCursor":"cursor-001"
                }
                """.trimIndent(),
            ),
        )

        val regionAwareApi = OkHttpPlatformApi(
            server.url("/").toString(),
            tvRuntimeRequestContextProvider = { TvRuntimeRequestContext(countryCode = "CN", regionCode = "SH") },
        )
        val response = regionAwareApi.getRuntimeManifest("session_token_1")

        val request = server.takeRequest()
        assertEquals("/me/runtime-manifest?countryCode=CN&regionCode=SH", request.path)
        assertEquals("GET", request.method)
        assertEquals("Bearer session_token_1", request.getHeader("Authorization"))
        assertEquals("2026-04-20.1", response.manifestVersion)
        assertEquals("home.hero", response.adSlots.first().slotId)
        assertEquals(2, response.adSlots.first().creatives.size)
        assertEquals("image", response.adSlots.first().creatives.first().mediaType)
    }

    @Test
    fun entitlement_uses_authenticated_summary_contract() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "accountId":"acct_001",
                  "displayId":"TV-001",
                  "planCode":"pro-monthly",
                  "paymentState":"paid",
                  "priorityClass":"paid_active",
                  "renewalState":"auto_renewing"
                }
                """.trimIndent(),
            ),
        )

        val response = api.getEntitlement("session_token_1")

        val request = server.takeRequest()
        assertEquals("/me/entitlement", request.path)
        assertEquals("GET", request.method)
        assertEquals("Bearer session_token_1", request.getHeader("Authorization"))
        assertEquals("paid", response.paymentState)
        assertEquals("paid_active", response.priorityClass)
        assertEquals("auto_renewing", response.renewalState)
    }

    @Test
    fun resource_session_routes_follow_single_runtime_contract() = runTest {
        val queuedResponse = """
            {
              "resourceSessionId":"rs_001",
              "queueStatus":"queued",
              "priorityClass":"paid_active",
              "queuePosition":2,
              "estimatedWaitSeconds":45,
              "appAccountLease":null,
              "modelLease":null,
              "entitlementSummary":{
                "accountId":"acct_001",
                "displayId":"TV-001",
                "planCode":"pro-monthly",
                "paymentState":"pending",
                "priorityClass":"paid_active",
                "renewalState":"manual_review"
              },
              "expiresAt":null,
              "updatedAt":"2026-04-20T11:45:00.000Z"
            }
        """.trimIndent()
        val grantedResponse = """
            {
              "resourceSessionId":"rs_001",
              "queueStatus":"granted",
              "priorityClass":"paid_active",
              "queuePosition":null,
              "estimatedWaitSeconds":null,
              "appAccountLease":{
                "leaseId":"aal_001",
                "appId":"youtube",
                "accountLabel":"shared-premium-01",
                "expiresAt":"2026-04-20T12:00:00.000Z"
              },
              "modelLease":{
                "leaseId":"ml_001",
                "providerScope":"moonshot",
                "leaseMode":"proxy",
                "leaseProfile":"server_10m",
                "expiresAt":"2026-04-20T12:00:00.000Z"
              },
              "entitlementSummary":{
                "accountId":"acct_001",
                "displayId":"TV-001",
                "planCode":"pro-monthly",
                "paymentState":"grace_period",
                "priorityClass":"paid_active",
                "renewalState":"auto_renewing"
              },
              "expiresAt":"2026-04-20T12:00:00.000Z",
              "updatedAt":"2026-04-20T11:50:00.000Z"
            }
        """.trimIndent()
        val releasedResponse = """
            {
              "resourceSessionId":"rs_001",
              "queueStatus":"released",
              "priorityClass":"paid_active",
              "queuePosition":null,
              "estimatedWaitSeconds":null,
              "appAccountLease":null,
              "modelLease":null,
              "entitlementSummary":{
                "accountId":"acct_001",
                "displayId":"TV-001",
                "planCode":"pro-monthly",
                "paymentState":"suspended",
                "priorityClass":"paid_active",
                "renewalState":"expired"
              },
              "expiresAt":null,
              "updatedAt":"2026-04-20T11:55:00.000Z"
            }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(queuedResponse))
        server.enqueue(MockResponse().setResponseCode(200).setBody(queuedResponse))
        server.enqueue(MockResponse().setResponseCode(200).setBody(grantedResponse))
        server.enqueue(MockResponse().setResponseCode(200).setBody(releasedResponse))

        val requested = api.requestResourceSession(
            "session_token_1",
            TvResourceSessionRequestDto(
                appId = "youtube",
                providerScope = "moonshot",
                leaseProfile = "server_10m",
            ),
        )
        val status = api.getResourceSessionStatus("session_token_1", "rs_001")
        val renewed = api.renewResourceSession("session_token_1", TvResourceSessionReferenceDto("rs_001"))
        val released = api.releaseResourceSession("session_token_1", TvResourceSessionReferenceDto("rs_001"))

        val requestCall = server.takeRequest()
        assertEquals("/client/resource-session/request", requestCall.path)
        assertEquals("POST", requestCall.method)
        assertEquals("Bearer session_token_1", requestCall.getHeader("Authorization"))
        assertTrue(requestCall.body.readUtf8().contains("\"leaseProfile\":\"server_10m\""))

        val statusCall = server.takeRequest()
        assertEquals("/client/resource-session/status?resourceSessionId=rs_001", statusCall.path)
        assertEquals("GET", statusCall.method)
        assertEquals("Bearer session_token_1", statusCall.getHeader("Authorization"))

        val renewCall = server.takeRequest()
        assertEquals("/client/resource-session/renew", renewCall.path)
        assertTrue(renewCall.body.readUtf8().contains("\"resourceSessionId\":\"rs_001\""))

        val releaseCall = server.takeRequest()
        assertEquals("/client/resource-session/release", releaseCall.path)
        assertTrue(releaseCall.body.readUtf8().contains("\"resourceSessionId\":\"rs_001\""))

        assertEquals("queued", requested.queueStatus)
        assertEquals(2, requested.queuePosition)
        assertEquals("pending", requested.entitlementSummary.paymentState)
        assertEquals("granted", renewed.queueStatus)
        assertEquals("moonshot", renewed.modelLease?.providerScope)
        assertEquals("grace_period", renewed.entitlementSummary.paymentState)
        assertEquals("released", released.queueStatus)
        assertEquals("suspended", released.entitlementSummary.paymentState)
        assertEquals("paid_active", status.priorityClass)
    }

    @Test
    fun model_renewal_payment_routes_follow_home_contract() = runTest {
        val pendingOrderResponse = """
            {
              "status":"ok",
              "order":{
                "orderId":"model-renewal-order-001",
                "sku":"openclaw-tv-model-renewal-30d",
                "title":"OpenClaw TV model renewal",
                "paymentProvider":"wechat_pay",
                "paymentState":"pending",
                "amount":{"totalCents":1,"currency":"CNY","display":"CNY 0.01"},
                "qr":{"codeUrl":"weixin://wxpay/bizpayurl?pr=ocm_test_001","expiresAt":"2026-05-17T12:15:00.000Z"},
                "entitlementSummary":{
                  "accountId":"acct_001",
                  "displayId":"TV-001",
                  "planCode":"free",
                  "paymentState":"pending",
                  "priorityClass":"pending_review",
                  "renewalState":"manual_renewal_pending"
                },
                "createdAt":"2026-05-17T12:00:00.000Z",
                "updatedAt":"2026-05-17T12:00:00.000Z",
                "paidAt":null,
                "durationSeconds":2592000
              }
            }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(pendingOrderResponse))
        server.enqueue(MockResponse().setResponseCode(200).setBody(pendingOrderResponse))

        val created = api.createModelRenewalPaymentOrder(
            sessionToken = "session_token_1",
            request = com.openclaw.tv.core.network.dto.TvModelRenewalPaymentOrderRequestDto(
                sku = "openclaw-tv-ai-service-30d",
            ),
        )
        val status = api.getModelRenewalPaymentOrderStatus(
            sessionToken = "session_token_1",
            orderId = "model-renewal-order-001",
        )

        val createRequest = server.takeRequest()
        assertEquals("/client/model-renewal/orders", createRequest.path)
        assertEquals("POST", createRequest.method)
        assertEquals("Bearer session_token_1", createRequest.getHeader("Authorization"))
        assertTrue(createRequest.body.readUtf8().contains("\"sku\":\"openclaw-tv-ai-service-30d\""))

        val statusRequest = server.takeRequest()
        assertEquals("/client/model-renewal/orders/model-renewal-order-001", statusRequest.path)
        assertEquals("GET", statusRequest.method)
        assertEquals("Bearer session_token_1", statusRequest.getHeader("Authorization"))

        assertEquals("model-renewal-order-001", created.order.orderId)
        assertEquals("wechat_pay", created.order.paymentProvider)
        assertEquals("CNY 0.01", created.order.amount.display)
        assertEquals("weixin://wxpay/bizpayurl?pr=ocm_test_001", created.order.qr.codeUrl)
        assertEquals(2592000L, created.order.durationSeconds)
        assertEquals("pending", status.order.entitlementSummary.paymentState)
    }

    @Test
    fun canonical_base_url_preserves_api_prefix_for_runtime_routes() = runTest {
        val prefixedApi = OkHttpPlatformApi(
            server.url("/api/").toString(),
            tvRuntimeRequestContextProvider = { TvRuntimeRequestContext(countryCode = "CN", regionCode = "SH") },
            tvRuntimeSessionTokenProvider = { "session_token_1" },
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(tvHomeConfigResponseBody()))
        server.enqueue(MockResponse().setResponseCode(200).setBody(runtimeManifestResponseBody()))
        server.enqueue(MockResponse().setResponseCode(200).setBody(entitlementResponseBody()))
        server.enqueue(MockResponse().setResponseCode(200).setBody(queuedResourceSessionResponseBody()))

        prefixedApi.getTvHomeConfig()
        prefixedApi.getRuntimeManifest("session_token_1")
        prefixedApi.getEntitlement("session_token_1")
        prefixedApi.getResourceSessionStatus("session_token_1", "rs_001")

        val configRequest = server.takeRequest()
        assertEquals("/api/me/tv-home-config?countryCode=CN&regionCode=SH", configRequest.path)
        assertEquals("Bearer session_token_1", configRequest.getHeader("Authorization"))

        val manifestRequest = server.takeRequest()
        assertEquals("/api/me/runtime-manifest?countryCode=CN&regionCode=SH", manifestRequest.path)
        assertEquals("Bearer session_token_1", manifestRequest.getHeader("Authorization"))

        val entitlementRequest = server.takeRequest()
        assertEquals("/api/me/entitlement", entitlementRequest.path)
        assertEquals("Bearer session_token_1", entitlementRequest.getHeader("Authorization"))

        val statusRequest = server.takeRequest()
        assertEquals("/api/client/resource-session/status?resourceSessionId=rs_001", statusRequest.path)
        assertEquals("Bearer session_token_1", statusRequest.getHeader("Authorization"))
    }

    @Test
    fun compatibility_base_url_preserves_platform_api_prefix_for_authenticated_routes() = runTest {
        val prefixedApi = OkHttpPlatformApi(server.url("/platform-api/").toString())
        server.enqueue(MockResponse().setResponseCode(200).setBody(policyResponseBody()))
        server.enqueue(MockResponse().setResponseCode(200).setBody(queuedResourceSessionResponseBody()))
        server.enqueue(MockResponse().setResponseCode(200).setBody(latestReleaseResponseBody()))

        prefixedApi.getPolicy("session_token_1", "openclaw")
        prefixedApi.requestResourceSession(
            "session_token_1",
            TvResourceSessionRequestDto(
                appId = "youtube",
                providerScope = "moonshot",
                leaseProfile = "server_10m",
            ),
        )
        prefixedApi.getLatestRelease("session_token_1", "stable", "openclaw")

        val policyRequest = server.takeRequest()
        assertEquals("/platform-api/client/policy?projectKey=openclaw", policyRequest.path)
        assertEquals("Bearer session_token_1", policyRequest.getHeader("Authorization"))

        val resourceRequest = server.takeRequest()
        assertEquals("/platform-api/client/resource-session/request", resourceRequest.path)
        assertEquals("Bearer session_token_1", resourceRequest.getHeader("Authorization"))
        assertTrue(resourceRequest.body.readUtf8().contains("\"providerScope\":\"moonshot\""))

        val latestReleaseRequest = server.takeRequest()
        assertEquals("/platform-api/client/releases/latest?projectKey=openclaw&channel=stable", latestReleaseRequest.path)
        assertEquals("Bearer session_token_1", latestReleaseRequest.getHeader("Authorization"))
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

    private fun tvHomeConfigResponseBody(): String {
        return """
            {
              "projectKey":"openclaw-android-tv",
              "projectLabel":"百万龙虾 TV",
              "runtimeManifestPath":"/api/me/runtime-manifest",
              "entitlementPath":"/api/me/entitlement",
              "resourceSessionBasePath":"/api/client/resource-session",
              "manifestPollAfterSeconds":900,
              "resourceSessionPollAfterSeconds":15,
              "backgroundDownloadEnabled":true,
              "idleDownloadOnly":true
            }
        """.trimIndent()
    }

    private fun runtimeManifestResponseBody(): String {
        return """
            {
              "manifestVersion":"2026-04-20.1",
              "countryCode":"CN",
              "regionCode":"SH",
              "apps":[],
              "adSlots":[],
              "pollAfterSeconds":900,
              "eventCursor":"cursor-001"
            }
        """.trimIndent()
    }

    private fun entitlementResponseBody(): String {
        return """
            {
              "accountId":"acct_001",
              "displayId":"TV-001",
              "planCode":"pro-monthly",
              "paymentState":"paid",
              "priorityClass":"paid_active",
              "renewalState":"auto_renewing"
            }
        """.trimIndent()
    }

    private fun queuedResourceSessionResponseBody(): String {
        return """
            {
              "resourceSessionId":"rs_001",
              "queueStatus":"queued",
              "priorityClass":"paid_active",
              "queuePosition":2,
              "estimatedWaitSeconds":45,
              "appAccountLease":null,
              "modelLease":null,
              "entitlementSummary":{
                "accountId":"acct_001",
                "displayId":"TV-001",
                "planCode":"pro-monthly",
                "paymentState":"pending",
                "priorityClass":"paid_active",
                "renewalState":"manual_review"
              },
              "expiresAt":null,
              "updatedAt":"2026-04-20T11:45:00.000Z"
            }
        """.trimIndent()
    }

    private fun policyResponseBody(): String {
        return """
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
        """.trimIndent()
    }

    private fun latestReleaseResponseBody(): String {
        return """
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
        """.trimIndent()
    }
}
