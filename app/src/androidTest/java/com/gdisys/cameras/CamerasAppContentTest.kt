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
 * Only storage, the VPN and the dispatchers are replaced, by `TestStorageModule`, `TestVpnModule`
 * and `TestDispatchersModule`. Each test gets new, empty stores, so there are never stored credentials and startup always lands on Config.
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

    // No waitUntil: the stores read on the idling IO dispatcher (TestDispatchersModule), so idle
    // means the credentials were read and the navigation happened.
    composeRule.runOnIdle {
      assertTrue(navController.currentDestination?.hasRoute<NavigationRoute.Config>() == true)
      // Loading stays below Config: InitRoute navigates without popping itself.
      assertTrue(navController.previousBackStackEntry?.destination?.hasRoute<NavigationRoute.Loading>() == true)
    }
  }
}
