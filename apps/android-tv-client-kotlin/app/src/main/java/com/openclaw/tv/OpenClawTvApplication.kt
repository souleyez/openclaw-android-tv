package com.openclaw.tv

import android.app.Application
import android.util.Log
import com.openclaw.tv.core.network.OkHttpPlatformApi
import com.openclaw.tv.core.storage.DataStoreDeviceIdentityStore
import com.openclaw.tv.core.storage.DataStoreLeaseStore
import com.openclaw.tv.core.storage.DataStoreSessionStore
import com.openclaw.tv.core.storage.DataStoreUpgradeStateStore
import com.openclaw.tv.feature.bootstrap.BootstrapRepository
import com.openclaw.tv.feature.bootstrap.BootstrapRuntimeOwner
import com.openclaw.tv.feature.bootstrap.BootstrapRequestFactory
import com.openclaw.tv.feature.bootstrap.BootstrapRuntime
import com.openclaw.tv.feature.bootstrap.LeaseCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OpenClawTvApplication : Application(), BootstrapRuntimeOwner {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override lateinit var bootstrapRuntime: BootstrapRuntime
        private set

    override fun onCreate() {
        super.onCreate()

        val platformApi = OkHttpPlatformApi(BuildConfig.PLATFORM_API_BASE_URL)
        val sessionStore = DataStoreSessionStore(this)
        val leaseStore = DataStoreLeaseStore(this)
        val deviceIdentityStore = DataStoreDeviceIdentityStore(this)
        val upgradeStateStore = DataStoreUpgradeStateStore(this)
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
        bootstrapRuntime.start()
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

    private companion object {
        const val UPGRADE_TAG = "OpenClawUpgrade"
    }
}
