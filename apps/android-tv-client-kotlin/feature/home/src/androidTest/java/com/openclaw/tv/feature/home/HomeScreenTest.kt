package com.openclaw.tv.feature.home

import android.view.View
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility.VISIBLE
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val scenarioRule = ActivityScenarioRule(HomeTestActivity::class.java)

    @Test
    fun home_shell_shows_brand_hero_and_quick_actions() {
        onView(withId(R.id.brand_title)).check(matches(withEffectiveVisibility(VISIBLE)))
        onView(withId(R.id.hero_card)).check(matches(withEffectiveVisibility(VISIBLE)))
        onView(withId(R.id.token_button)).check(matches(withEffectiveVisibility(VISIBLE)))
        onView(withId(R.id.quick_action_rail)).check(matches(withEffectiveVisibility(VISIBLE)))
    }

    @Test
    fun recreate_restores_last_focused_quick_action_card() {
        focusRailItem(R.id.quick_action_rail, 2)
        assertFocusedAdapterPosition(R.id.quick_action_rail, 2)

        scenarioRule.scenario.recreate()

        assertFocusedAdapterPosition(R.id.quick_action_rail, 2)
    }

    @Test
    fun local_apps_quick_action_opens_overlay() {
        focusRailItem(R.id.quick_action_rail, 4)
        scenarioRule.scenario.onActivity { activity ->
            val rail = activity.findViewById<RecyclerView>(R.id.quick_action_rail)
            val targetView = rail.findViewHolderForAdapterPosition(4)?.itemView
                ?: error("Missing local apps quick action")
            targetView.performClick()
        }
        waitForIdle()

        onView(withId(R.id.local_apps_overlay)).check(matches(withEffectiveVisibility(VISIBLE)))
        onView(withId(R.id.local_apps_close_button)).check(matches(withEffectiveVisibility(VISIBLE)))
    }

    @Test
    fun dpad_down_moves_focus_from_featured_row_to_quick_actions() {
        focusRailItem(R.id.featured_rail, 0)
        assertFocusedAdapterPosition(R.id.featured_rail, 0)

        sendKey(android.view.KeyEvent.KEYCODE_DPAD_DOWN)

        assertFocusedAdapterPosition(R.id.quick_action_rail, 0)
    }

    @Test
    fun dpad_up_returns_to_last_focused_featured_card() {
        focusRailItem(R.id.featured_rail, 1)
        assertFocusedAdapterPosition(R.id.featured_rail, 1)

        sendKey(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        assertFocusedAdapterPosition(R.id.quick_action_rail, 0)

        sendKey(android.view.KeyEvent.KEYCODE_DPAD_UP)
        assertFocusedAdapterPosition(R.id.featured_rail, 1)
    }

    private fun focusRailItem(
        recyclerViewId: Int,
        position: Int,
    ) {
        waitForRailItemCount(recyclerViewId, position + 1)
        scenarioRule.scenario.onActivity { activity ->
            val rail = activity.findViewById<RecyclerView>(recyclerViewId)
            rail.scrollToPosition(position)
        }
        waitForIdle()
        scenarioRule.scenario.onActivity { activity ->
            val rail = activity.findViewById<RecyclerView>(recyclerViewId)
            val targetView = rail.findViewHolderForAdapterPosition(position)?.itemView
                ?: error("Missing rail item at position $position for view $recyclerViewId")
            assertTrue(targetView.requestFocus())
        }
        waitForIdle()
    }

    private fun assertFocusedAdapterPosition(
        recyclerViewId: Int,
        expectedPosition: Int,
    ) {
        repeat(20) {
            var resolvedPosition: Int? = null
            scenarioRule.scenario.onActivity { activity ->
                val rail = activity.findViewById<RecyclerView>(recyclerViewId)
                val focusedView = activity.currentFocus
                resolvedPosition = focusedView?.findAdapterPosition(rail)
                    ?: rail.focusedChild?.findAdapterPosition(rail)
            }
            if (resolvedPosition != null) {
                assertEquals(expectedPosition, resolvedPosition)
                return
            }
            waitForIdle()
        }
        error("Focused view is not inside recycler view $recyclerViewId")
    }

    private fun waitForIdle() {
        Thread.sleep(350)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun waitForRailItemCount(
        recyclerViewId: Int,
        minimumCount: Int,
    ) {
        repeat(20) {
            var itemCount = 0
            scenarioRule.scenario.onActivity { activity ->
                val rail = activity.findViewById<RecyclerView>(recyclerViewId)
                itemCount = rail.adapter?.itemCount ?: 0
            }
            if (itemCount >= minimumCount) {
                waitForIdle()
                return
            }
            waitForIdle()
        }
        error("RecyclerView $recyclerViewId did not reach item count $minimumCount")
    }

    private fun sendKey(keyCode: Int) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(keyCode)
        waitForIdle()
    }
}

private fun View.findAdapterPosition(rail: RecyclerView): Int? {
    var current: View? = this
    while (current != null) {
        if (current.parent === rail) {
            return rail.getChildViewHolder(current).bindingAdapterPosition
                .takeIf { it != RecyclerView.NO_POSITION }
        }
        current = current.parent as? View
    }
    return null
}
