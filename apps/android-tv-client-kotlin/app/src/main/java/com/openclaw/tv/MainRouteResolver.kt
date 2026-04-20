package com.openclaw.tv

import com.openclaw.tv.feature.bootstrap.BootstrapRuntimeState
import com.openclaw.tv.feature.bootstrap.RuntimeUpgradeState

enum class MainRoute {
    HOME,
    REQUIRED_UPGRADE,
}

internal object MainRouteResolver {
    fun resolve(state: BootstrapRuntimeState?): MainRoute {
        return if (state?.upgradeStatus?.state == RuntimeUpgradeState.REQUIRED) {
            MainRoute.REQUIRED_UPGRADE
        } else {
            MainRoute.HOME
        }
    }
}
