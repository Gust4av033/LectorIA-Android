package com.lectoria.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun appStartsAndNavigates() {
        compose.onNodeWithTag("screen_library").assertIsDisplayed()
        listOf("reader", "player", "settings", "library").forEach { route ->
            compose.onNodeWithTag("nav_$route").performClick()
            compose.onNodeWithTag("screen_$route").assertIsDisplayed()
        }
    }
}
