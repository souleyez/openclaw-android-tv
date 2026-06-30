package com.openclaw.tv

import android.app.DownloadManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.openclaw.tv.core.network.OkHttpPlatformApi
import com.openclaw.tv.core.network.dto.OwnApkUpdateReportRequestDto
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
import com.openclaw.tv.feature.appdelivery.AppInstallStateTracker
import com.openclaw.tv.feature.appdelivery.DownloadManagerAppDownloadEnqueuer
import com.openclaw.tv.feature.appdelivery.DownloadManagerTrackedAppDownloadStatusResolver
import com.openclaw.tv.feature.appdelivery.FileSha256ChecksumVerifier
import com.openclaw.tv.feature.appdelivery.InstalledPackageChecker
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
import com.openclaw.tv.runtime.AppDownloadStartupReconciler
import com.openclaw.tv.runtime.AppDownloadStartupRecoverySummary
import com.openclaw.tv.runtime.AndroidDeviceTelemetrySampler
import com.openclaw.tv.runtime.ApplicationRuntimeCoordinator
import com.openclaw.tv.runtime.LoggingRuntimeDiagnosticsReporter
import com.openclaw.tv.runtime.OwnApkUpdateRuntimeSyncAdapter
import com.openclaw.tv.runtime.PlatformDeviceTelemetryReporter
import com.openclaw.tv.runtime.ResourceSessionRuntimeSyncAdapter
import com.openclaw.tv.runtime.RuntimeConfigLoader
import com.openclaw.tv.runtime.resolvePlatformApiEndpointSummary
import com.openclaw.tv.runtime.RuntimeEntitlementSyncAdapter
import com.openclaw.tv.runtime.SystemDeviceActivityProvider
import com.openclaw.tv.runtime.TvRuntimeRequestContextResolver
import com.openclaw.tv.upgrade.DownloadManagerOwnApkDownloadEnqueuer
import com.openclaw.tv.upgrade.OwnApkAutoInstallCoordinator
import com.openclaw.tv.upgrade.OwnApkDownloadCoordinator
import com.openclaw.tv.upgrade.OwnApkUpdateAgent
import com.openclaw.tv.upgrade.OwnApkUpdateInstaller
import com.openclaw.tv.upgrade.PlatformOwnApkUpdateRepository
import com.openclaw.tv.upgrade.SharedPreferencesOwnApkDownloadStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OpenClawTvApplication : Application(), BootstrapRuntimeOwner {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var appDownloadReceiver: BroadcastReceiver? = null
    private var packageInstallReceiver: BroadcastReceiver? = null

    override lateinit var bootstrapRuntime: BootstrapRuntime
        private set

    override fun onCreate() {
        super.onCreate()

        val runtimeDiagnosticsReporter = LoggingRuntimeDiagnosticsReporter(
            logInfo = { message -> Log.i(RUNTIME_TAG, message) },
            logWarning = { message -> Log.w(RUNTIME_TAG, message) },
        )
        runtimeDiagnosticsReporter.onPlatformApiConfigured(
            resolvePlatformApiEndpointSummary(BuildConfig.PLATFORM_API_BASE_URL),
        )
        val sessionStore = DataStoreSessionStore(this)
        val leaseStore = DataStoreLeaseStore(this)
        val deviceIdentityStore = DataStoreDeviceIdentityStore(this)
        val upgradeStateStore = DataStoreUpgradeStateStore(this)
        val tvHomeConfigStore = DataStoreTvHomeConfigStore(this)
        val runtimeManifestStore = DataStoreRuntimeManifestStore(this)
        val entitlementStore = DataStoreEntitlementStore(this)
        val resourceSessionStore = DataStoreResourceSessionStore(this)
        val appDownloadStore = DataStoreAppDownloadStore(this)
        val trackedAppDownloadStatusResolver = DownloadManagerTrackedAppDownloadStatusResolver(this)
        val deviceActivityProvider = SystemDeviceActivityProvider(this)
        val runtimeRequestContextResolver = TvRuntimeRequestContextResolver()
        val platformApi = OkHttpPlatformApi(
            BuildConfig.PLATFORM_API_BASE_URL,
            tvRuntimeRequestContextProvider = runtimeRequestContextResolver::resolve,
            tvRuntimeSessionTokenProvider = { sessionStore.read()?.sessionToken },
        )
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
            legacyLeaseCompatibilityEnabled = false,
            logWarning = { message, error ->
                Log.w(RUNTIME_TAG, message, error)
            },
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
        val ownApkUpdateRepository = PlatformOwnApkUpdateRepository(platformApi)
        val ownApkDownloadStore = SharedPreferencesOwnApkDownloadStore(this)
        val ownApkUpdateAgent = OwnApkUpdateAgent(
            repository = ownApkUpdateRepository,
            currentVersionCodeProvider = { BuildConfig.VERSION_CODE.toLong() },
            currentResourceVersionProvider = { null },
            isIdleForLargeDownload = { !SystemDeviceActivityProvider(this).isDeviceActive() },
            downloadStore = ownApkDownloadStore,
            downloadEnqueuer = DownloadManagerOwnApkDownloadEnqueuer(this),
            logInfo = { message -> Log.i(UPGRADE_TAG, message) },
            logWarning = { message, error -> Log.w(UPGRADE_TAG, message, error) },
        )
        val ownApkDownloadCoordinator = OwnApkDownloadCoordinator(
            store = ownApkDownloadStore,
            repository = ownApkUpdateRepository,
            statusResolver = trackedAppDownloadStatusResolver,
            checksumVerifier = FileSha256ChecksumVerifier(),
        )
        val ownApkAutoInstallCoordinator = OwnApkAutoInstallCoordinator(
            store = ownApkDownloadStore,
            installer = OwnApkUpdateInstaller(this),
            currentVersionCodeProvider = { BuildConfig.VERSION_CODE.toLong() },
            isIdleForInstall = { !deviceActivityProvider.isDeviceActive() },
            reportInstalling = { sessionToken, update, currentVersionCode ->
                ownApkUpdateRepository.report(
                    sessionToken = sessionToken,
                    request = OwnApkUpdateReportRequestDto(
                        releaseId = update.releaseId,
                        currentVersionCode = currentVersionCode,
                        targetVersionCode = update.targetVersionCode,
                        status = "installing",
                        progressPercent = 100,
                        note = "silent install submitted",
                    ),
                )
            },
            logInfo = { message -> Log.i(UPGRADE_TAG, message) },
            logWarning = { message, error ->
                if (error == null) {
                    Log.w(UPGRADE_TAG, message)
                } else {
                    Log.w(UPGRADE_TAG, message, error)
                }
            },
        )
        val appDownloadCoordinator = AppDownloadCoordinator(
            downloadStore = appDownloadStore,
            enqueuer = DownloadManagerAppDownloadEnqueuer(this),
            checksumVerifier = FileSha256ChecksumVerifier(),
            installedPackageChecker = InstalledPackageChecker(::isPackageInstalled),
        )
        val appInstallStateTracker = AppInstallStateTracker(
            downloadStore = appDownloadStore,
            installedPackageChecker = InstalledPackageChecker(::isPackageInstalled),
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
            ownApkUpdateSync = OwnApkUpdateRuntimeSyncAdapter(
                agent = ownApkUpdateAgent,
                downloadCoordinator = ownApkDownloadCoordinator,
                autoInstallCoordinator = ownApkAutoInstallCoordinator,
            ),
            deviceActivityProvider = deviceActivityProvider,
            resourceSessionLeaseProfile = BuildConfig.OPENCLAW_LEASE_PROFILE,
            diagnosticsReporter = runtimeDiagnosticsReporter,
            deviceTelemetryReporter = PlatformDeviceTelemetryReporter(
                platformApi = platformApi,
                sampler = AndroidDeviceTelemetrySampler(this),
            ),
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
                statusResolver = trackedAppDownloadStatusResolver,
            ),
            ownApkDownloadCoordinator = ownApkDownloadCoordinator,
            ownApkAutoInstallCoordinator = ownApkAutoInstallCoordinator,
            sessionTokenProvider = { sessionStore.read()?.sessionToken },
        )
        registerPackageInstallReceiver(appInstallStateTracker)
        applicationScope.launch {
            val reconciledAppIds = appInstallStateTracker.reconcileInstalledPackages()
            if (reconciledAppIds.isNotEmpty()) {
                Log.i(
                    RUNTIME_TAG,
                    "Cleared ${reconciledAppIds.size} stale app download records after startup reconcile",
                )
            }
            val startupRecovery = AppDownloadStartupReconciler(
                downloadStore = appDownloadStore,
                coordinator = appDownloadCoordinator,
                statusResolver = trackedAppDownloadStatusResolver,
            ).reconcile()
            if (startupRecovery != AppDownloadStartupRecoverySummary()) {
                Log.i(
                    RUNTIME_TAG,
                    "Recovered app downloads resumed=${startupRecovery.resumedCount} completed=${startupRecovery.completedCount} failed=${startupRecovery.failedCount}",
                )
            }
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
        packageInstallReceiver?.let { receiver ->
            unregisterReceiver(receiver)
        }
        packageInstallReceiver = null
        super.onTerminate()
    }

    private fun registerAppDownloadReceiver(
        tracker: AppDownloadCompletionTracker,
        ownApkDownloadCoordinator: OwnApkDownloadCoordinator,
        ownApkAutoInstallCoordinator: OwnApkAutoInstallCoordinator,
        sessionTokenProvider: suspend () -> String?,
    ) {
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
                    val sessionToken = sessionTokenProvider()
                    tracker.handleCompletedDownload(downloadId)
                    ownApkDownloadCoordinator.handleCompletedDownload(
                        sessionToken = sessionToken,
                        downloadId = downloadId,
                        currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
                    )
                    ownApkAutoInstallCoordinator.maybeInstallVerifiedUpdate(sessionToken)
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

    private fun registerPackageInstallReceiver(tracker: AppInstallStateTracker) {
        if (packageInstallReceiver != null) {
            return
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                val packageName = intent?.data?.schemeSpecificPart?.trim().orEmpty()
                if (packageName.isBlank()) {
                    return
                }
                applicationScope.launch {
                    val clearedAppIds = tracker.handlePackageInstalled(packageName)
                    if (clearedAppIds.isNotEmpty()) {
                        Log.i(
                            RUNTIME_TAG,
                            "Cleared ${clearedAppIds.size} app download records for installed package=$packageName",
                        )
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        packageInstallReceiver = receiver
    }

    private fun isPackageInstalled(packageName: String): Boolean {
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

    private companion object {
        const val UPGRADE_TAG = "OpenClawUpgrade"
        const val RUNTIME_TAG = "OpenClawRuntime"
    }
}
