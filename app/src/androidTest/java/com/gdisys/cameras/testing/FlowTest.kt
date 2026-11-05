package com.gdisys.cameras.testing

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.intent.rule.IntentsRule
import com.gdisys.cameras.CamerasAppContent
import com.gdisys.cameras.app.navigation.NavigationRoute
import com.gdisys.cameras.core.storage.domain.StreamPreferencesRepository
import com.gdisys.cameras.core.storage.domain.UserPreferencesRepository
import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.feature.config.QR_CODE_RESULT_KEY
import dagger.hilt.android.testing.HiltAndroidRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.webrtc.EglBase
import javax.inject.Inject

/** Every destination of the graph, so the back stack reads as routes instead of strings. */
private val ROUTES = listOf(
  NavigationRoute.Loading,
  NavigationRoute.Config,
  NavigationRoute.QrCode,
  NavigationRoute.StreamURLs,
  NavigationRoute.Home
)

/**
 * Base class of the flow tests: the real composition root, navigation, ViewModels, use cases and
 * encrypted DataStore, with only what leaves the process replaced (`TestVpnModule`,
 * `TestPermissionModule`, `TestStreamConnectionModule`; storage and dispatchers are isolated by
 * `TestStorageModule` and `TestDispatchersModule`).
 *
 * Subclasses are annotated `@HiltAndroidTest` (Hilt generates the test component per concrete
 * class). A test prepares the state first, through [seed], [vpnConsent], [streams] and
 * [DeviceState], and then calls [launchApp]: the activity starts empty and the ViewModels are only
 * created there, so they read what was seeded.
 */
abstract class FlowTest {

  @get:Rule(order = 0)
  val hiltRule = HiltAndroidRule(this)

  /** Records the Intents the app launches, so [FakeVpnPermissionRepository.stubConsentDialog] works. */
  @get:Rule(order = 1)
  val intentsRule = IntentsRule()

  @get:Rule(order = 2)
  val composeRule = createAndroidComposeRule<HiltTestActivity>()

  @Inject lateinit var eglBase: EglBase
  @Inject lateinit var vpn: FakeVpnRepository
  @Inject lateinit var vpnLifecycle: FakeVpnLifecycleController
  @Inject lateinit var vpnConsent: FakeVpnPermissionRepository
  @Inject lateinit var streams: FakeStreamConnections
  @Inject lateinit var seed: Seed
  @Inject lateinit var userPreferencesRepository: UserPreferencesRepository
  @Inject lateinit var streamPreferencesRepository: StreamPreferencesRepository

  /** The NavController of the running app. Set by [launchApp]. */
  lateinit var navController: NavHostController
    private set

  @Before
  fun injectDependencies() {
    hiltRule.inject()
  }

  /** Renders the real composition root, in the test locale. Call it after seeding the state. */
  fun launchApp() {
    composeRule.setContent {
      InTestLocale {
        navController = rememberNavController()
        CamerasAppContent(eglBase = eglBase, navController = navController)
      }
    }
  }

  /**
   * The back stack as routes, bottom first, without the graph itself. Read after idle, so the
   * navigations the app was about to make have happened.
   */
  fun backStackRoutes(): List<Any> = composeRule.runOnIdle {
    navController.currentBackStack.value
      .map { it.destination }
      .filterNot { it is NavGraph }
      .map { destination -> ROUTES.first { destination.hasRoute(it::class) } }
  }

  /**
   * Hands [rawJson] to the Config screen as if the scanner had read it: the scanner returns its
   * result through the Config entry's `savedStateHandle` (the QR screen itself stays untested).
   */
  fun injectQr(rawJson: String) {
    composeRule.runOnIdle {
      navController.getBackStackEntry<NavigationRoute.Config>().savedStateHandle[QR_CODE_RESULT_KEY] = rawJson
    }
    composeRule.waitForIdle()
  }

  /**
   * Presses the system back, through the activity's dispatcher like the gesture does. If nothing
   * consumes it, the activity finishes: check `composeRule.activity.isFinishing`.
   */
  fun pressBack() {
    composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
    composeRule.waitForIdle()
  }

  /** What is persisted now, read through the production repository. */
  fun storedUserPreferences(): UserPreferences = runBlocking { userPreferencesRepository.userPreferences.first() }

  /** What is persisted now, read through the production repository. */
  fun storedStreamPreferences(): StreamPreferences =
    runBlocking { streamPreferencesRepository.streamPreferences.first() }
}
