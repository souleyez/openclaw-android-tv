package com.openclaw.tv

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

    private fun renderRoute(route: MainRoute) {
        if (currentRoute == route && supportFragmentManager.findFragmentById(R.id.main_content) != null) {
            return
        }
        currentRoute = route
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(
                R.id.main_content,
                when (route) {
                    MainRoute.HOME -> HomeFragment.newInstance(platformBaseUrl = BuildConfig.PLATFORM_API_BASE_URL)
                    MainRoute.REQUIRED_UPGRADE -> RequiredUpgradeFragment()
                },
            )
        }
    }
}
