package com.gdisys.cameras

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gdisys.cameras.app.navigation.NavigationRoute
import com.gdisys.cameras.testing.HiltTestActivity
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.webrtc.EglBase

/**
 * Smoke test for the shared composition root: the real navigation graph, Hilt ViewModels and
 * storage, rendered in [HiltTestActivity] with a [NavHostController] the test owns.
 */
@RunWith(AndroidJUnit4::class)
class CamerasAppContentTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<HiltTestActivity>()

  // Created here rather than injected: this suite still runs on the plain AndroidJUnitRunner, so
  // there is no HiltAndroidRule to inject the app's singleton.
  private val eglBase = EglBase.create()

  @After
  fun releaseEglBase() {
    eglBase.release()
  }

  @Test
  fun startupLeavesLoadingThroughTheTestOwnedNavController() {
    lateinit var navController: NavHostController
    composeRule.setContent {
      navController = rememberNavController()
      CamerasAppContent(eglBase = eglBase, navController = navController)
    }

    // Where startup lands depends on the credentials already stored on the device, which this
    // suite does not reset yet: Config without them, Home with them.
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.runOnUiThread {
        val destination = navController.currentDestination
        destination?.hasRoute<NavigationRoute.Config>() == true ||
          destination?.hasRoute<NavigationRoute.Home>() == true
      }
    }
  }
}
