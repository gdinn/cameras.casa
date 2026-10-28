package com.gdisys.cameras

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.gdisys.cameras.app.navigation.NavigationRoute
import com.gdisys.cameras.testing.HiltTestActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.webrtc.EglBase
import javax.inject.Inject

/**
 * Smoke test for the shared composition root: the real navigation graph and Hilt ViewModels,
 * rendered in [HiltTestActivity] with a [NavHostController] the test owns.
 *
 * Only storage is replaced, by `TestStorageModule`, which gives each test new, empty stores. So
 * there are never stored credentials: startup always lands on Config and the real VPN, still the
 * production binding, is never brought up.
 */
@HiltAndroidTest
class CamerasAppContentTest {

  @get:Rule(order = 0)
  val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1)
  val composeRule = createAndroidComposeRule<HiltTestActivity>()

  @Inject
  lateinit var eglBase: EglBase

  @Before
  fun injectDependencies() {
    hiltRule.inject()
  }

  @Test
  fun firstRunLandsOnConfigOverLoading() {
    lateinit var navController: NavHostController
    composeRule.setContent {
      navController = rememberNavController()
      CamerasAppContent(eglBase = eglBase, navController = navController)
    }

    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.runOnUiThread {
        navController.currentDestination?.hasRoute<NavigationRoute.Config>() == true
      }
    }
    // Loading stays below Config: InitRoute navigates without popping itself.
    composeRule.runOnUiThread {
      assertTrue(navController.previousBackStackEntry?.destination?.hasRoute<NavigationRoute.Loading>() == true)
    }
  }
}
