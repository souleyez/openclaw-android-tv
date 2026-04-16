package com.openclaw.tv.feature.home

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val scenarioRule = ActivityScenarioRule(HomeTestActivity::class.java)

    @Test
    fun home_shell_shows_degraded_status_and_primary_action() {
        onView(withId(R.id.assistant_hero)).check(matches(isDisplayed()))
        onView(withId(R.id.assistant_status)).check(matches(isDisplayed()))
        onView(withId(R.id.app_rail)).check(matches(isDisplayed()))
        onView(withText("基础版")).check(matches(isDisplayed()))
        onView(withId(R.id.primary_action)).check(matches(isDisplayed()))
    }
}
