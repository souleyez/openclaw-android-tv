package com.openclaw.tv

import android.app.DownloadManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.openclaw.tv.core.network.OkHttpPlatformApi
import com.openclaw.tv.core.storage.DataStoreAppDownloadStore
import com.openclaw.tv.core.storage.DataStoreDeviceIdentityStore
import com.openclaw.tv.core.storage.DataStoreEntitlementStore
import com.openclaw.tv.core.storage.DataStoreLeaseStore
import com.openclaw.tv.core.storage.DataStoreResourceSessionStore
import com.openclaw.tv.core.storage.DataStoreRuntimeManifestStore
import com.openclaw.tv.core.storage.DataStoreSessionStore
import com.openclaw.tv.core.storage.DataStoreTvHomeConfigStore
import com.openclaw.tv.core.storage.DataStoreUpgradeStateStore
import com.openclaw.tv.feature.appdelivery.AppDownloadCoordinator
import com.openclaw.tv.feature.appdelivery.RuntimeManifestRepository
import com.openclaw.tv.feature.bootstrap.BootstrapRepository
import com.openclaw.tv.feature.bootstrap.BootstrapRuntimeOwner
import com.openclaw.tv.feature.bootstrap.BootstrapRequestFactory
import com.openclaw.tv.feature.bootstrap.BootstrapRuntime
import com.openclaw.tv.feature.bootstrap.LeaseCoordinator
import com.openclaw.tv.feature.home.TvHomeRepository
import com.openclaw.tv.feature.runtime.ResourceSessionCoordinator
import com.openclaw.tv.feature.runtime.ResourceSessionRepository
import com.openclaw.tv.feature.runtime.RuntimeEntitlementRepository
import com.openclaw.tv.runtime.AppDeliveryRuntimeSyncAdapter
import com.openclaw.tv.runtime.AppDownloadCompletionTracker
import com.openclaw.tv.runtime.ApplicationRuntimeCoordinator
import com.openclaw.tv.runtime.DownloadManagerAppDownloadEnqueuer
import com.openclaw.tv.runtime.FileSha256ChecksumVerifier
import com.openclaw.tv.runtime.ResourceSessionRuntimeSyncAdapter
import com.openclaw.tv.runtime.RuntimeConfigLoader
import com.openclaw.tv.runtime.RuntimeEntitlementSyncAdapter
import com.openclaw.tv.runtime.SystemDeviceActivityProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OpenClawTvApplication : Application(), BootstrapRuntimeOwner {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var appDownloadReceiver: BroadcastReceiver? = null

    override lateinit var bootstrapRuntime: BootstrapRuntime
        private set

    override fun onCreate() {
        super.onCreate()

        val platformApi = OkHttpPlatformApi(BuildConfig.PLATFORM_API_BASE_URL)
        val sessionStore = DataStoreSessionStore(this)
        val leaseStore = DataStoreLeaseStore(this)
        val deviceIdentityStore = DataStoreDeviceIdentityStore(this)
        val upgradeStateStore = DataStoreUpgradeStateStore(this)
        val tvHomeConfigStore = DataStoreTvHomeConfigStore(this)
        val runtimeManifestStore = DataStoreRuntimeManifestStore(this)
        val entitlementStore = DataStoreEntitlementStore(this)
        val resourceSessionStore = DataStoreResourceSessionStore(this)
        val appDownloadStore = DataStoreAppDownloadStore(this)
        val repository = BootstrapRepository(
            platformApi = platformApi,
            sessionStore = sessionStore,
            leaseStore = leaseStore,
        )
        val requestFactory = BootstrapRequestFactory(
            context = this,
            deviceIdentityStore = deviceIdentityStore,
            projectKey = BuildConfig.OPENCLAW_PROJECT_KEY,
            principalType = BuildConfig.OPENCLAW_BOOTSTRAP_PRINCIPAL_TYPE,
            clientVersion = BuildConfig.VERSION_NAME,
        )

        bootstrapRuntime = BootstrapRuntime(
            scope = applicationScope,
            repository = repository,
            leaseCoordinator = LeaseCoordinator(repository),
            requestFactory = requestFactory::create,
            currentClientVersion = BuildConfig.VERSION_NAME,
            leaseProfile = BuildConfig.OPENCLAW_LEASE_PROFILE,
        )
        val runtimeManifestRepository = RuntimeManifestRepository(
            platformApi = platformApi,
            manifestStore = runtimeManifestStore,
        )
        val entitlementRepository = RuntimeEntitlementRepository(
            platformApi = platformApi,
            entitlementStore = entitlementStore,
        )
        val resourceSessionRepository = ResourceSessionRepository(
            platformApi = platformApi,
            sessionStore = sessionStore,
            resourceSessionStore = resourceSessionStore,
        )
        val resourceSessionCoordinator = ResourceSessionCoordinator(resourceSessionRepository)
        val appDownloadCoordinator = AppDownloadCoordinator(
            downloadStore = appDownloadStore,
            enqueuer = DownloadManagerAppDownloadEnqueuer(this),
            checksumVerifier = FileSha256ChecksumVerifier(),
        )
        val runtimeCoordinator = ApplicationRuntimeCoordinator(
            scope = applicationScope,
            bootstrapState = bootstrapRuntime.state,
            configLoader = RuntimeConfigLoader {
                TvHomeRepository(
                    platformApi = platformApi,
                    cacheStore = tvHomeConfigStore,
                ).load()
            },
            manifestLoader = runtimeManifestRepository::load,
            entitlementSync = RuntimeEntitlementSyncAdapter(entitlementRepository),
            resourceSessionSync = ResourceSessionRuntimeSyncAdapter(
                repository = resourceSessionRepository,
                coordinator = resourceSessionCoordinator,
            ),
            appDeliverySync = AppDeliveryRuntimeSyncAdapter(appDownloadCoordinator),
            deviceActivityProvider = SystemDeviceActivityProvider(this),
            logError = { message, error ->
                Log.w(RUNTIME_TAG, message, error)
            },
        )

        bootstrapRuntime.start()
        runtimeCoordinator.start()
        registerAppDownloadReceiver(
            tracker = AppDownloadCompletionTracker(
                downloadStore = appDownloadStore,
                coordinator = appDownloadCoordinator,
            ),
        )
        applicationScope.launch {
            val upgradeState = upgradeStateStore.recordLaunch(BuildConfig.VERSION_NAME)
            if (upgradeState.pendingSuccessVersion == BuildConfig.VERSION_NAME) {
                Log.i(
                    UPGRADE_TAG,
                    "Detected app version change currentVersion=${BuildConfig.VERSION_NAME}",
                )
            }
        }
    }

    override fun onTerminate() {
        appDownloadReceiver?.let { receiver ->
            unregisterReceiver(receiver)
        }
        appDownloadReceiver = null
        super.onTerminate()
    }

    private fun registerAppDownloadReceiver(tracker: AppDownloadCompletionTracker) {
        if (appDownloadReceiver != null) {
            return
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                    return
                }
                val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (downloadId < 0L) {
                    return
                }
                applicationScope.launch {
                    tracker.handleCompletedDownload(downloadId)
                }
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        appDownloadReceiver = receiver
    }

    private companion object {
        const val UPGRADE_TAG = "OpenClawUpgrade"
        const val RUNTIME_TAG = "OpenClawRuntime"
    }
}
