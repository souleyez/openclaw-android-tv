package com.openclaw.tv

import com.openclaw.tv.feature.bootstrap.BootstrapRuntimePhase
import com.openclaw.tv.feature.bootstrap.BootstrapRuntimeState
import com.openclaw.tv.feature.bootstrap.RuntimeUpgradeState
import com.openclaw.tv.feature.bootstrap.RuntimeUpgradeStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MainRouteResolverTest {

    @Test
    fun resolve_routes_to_upgrade_screen_when_upgrade_is_required() {
        val route = MainRouteResolver.resolve(
            BootstrapRuntimeState(
                phase = BootstrapRuntimePhase.READY,
                upgradeStatus = RuntimeUpgradeStatus(
                    state = RuntimeUpgradeState.REQUIRED,
                    currentVersion = "0.1.0",
                ),
            ),
        )

        assertEquals(MainRoute.REQUIRED_UPGRADE, route)
    }

    @Test
    fun resolve_routes_to_home_when_upgrade_is_not_required() {
        val route = MainRouteResolver.resolve(
            BootstrapRuntimeState(
                phase = BootstrapRuntimePhase.READY,
                upgradeStatus = RuntimeUpgradeStatus(
                    state = RuntimeUpgradeState.AVAILABLE,
                    currentVersion = "0.1.0",
                ),
            ),
        )

        assertEquals(MainRoute.HOME, route)
    }
}
