package com.openclaw.tv

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.openclaw.tv.feature.bootstrap.BootstrapRuntimeOwner
import com.openclaw.tv.feature.home.HomeFragment
import com.openclaw.tv.upgrade.RequiredUpgradeFragment
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var currentRoute: MainRoute? = null
    private var currentHomeDebugForceOffline: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val runtimeOwner = application as? BootstrapRuntimeOwner

        if (savedInstanceState == null) {
            renderRoute(MainRouteResolver.resolve(runtimeOwner?.bootstrapRuntime?.state?.value))
        }

        if (runtimeOwner != null) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    runtimeOwner.bootstrapRuntime.state.collect { state ->
                        renderRoute(MainRouteResolver.resolve(state))
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val runtimeOwner = application as? BootstrapRuntimeOwner
        renderRoute(MainRouteResolver.resolve(runtimeOwner?.bootstrapRuntime?.state?.value))
    }

    private fun renderRoute(route: MainRoute) {
        val debugForceOffline = shouldForceHomeOfflinePreview()
        val homePreviewChanged = route == MainRoute.HOME && currentHomeDebugForceOffline != debugForceOffline
        if (currentRoute == route &&
            supportFragmentManager.findFragmentById(R.id.main_content) != null &&
            !homePreviewChanged
        ) {
            return
        }
        currentRoute = route
        currentHomeDebugForceOffline = if (route == MainRoute.HOME) debugForceOffline else false
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(
                R.id.main_content,
                when (route) {
                    MainRoute.HOME -> HomeFragment.newInstance(
                        platformBaseUrl = BuildConfig.PLATFORM_API_BASE_URL,
                        debugForceOffline = debugForceOffline,
                    )
                    MainRoute.REQUIRED_UPGRADE -> RequiredUpgradeFragment()
                },
            )
        }
    }

    private fun shouldForceHomeOfflinePreview(): Boolean {
        return BuildConfig.DEBUG && intent?.getBooleanExtra(EXTRA_DEBUG_FORCE_HOME_OFFLINE, false) == true
    }

    private companion object {
        const val EXTRA_DEBUG_FORCE_HOME_OFFLINE = "debug_force_home_offline"
    }
}
