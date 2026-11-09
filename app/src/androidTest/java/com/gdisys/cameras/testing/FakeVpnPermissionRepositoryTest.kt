package com.gdisys.cameras.testing

import android.app.Activity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.intent.rule.IntentsRule
import com.gdisys.cameras.core.permission.domain.VpnConsent
import com.gdisys.cameras.core.permission.domain.VpnPermissionRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * Checks that `TestPermissionModule` puts the fake in the graph, and that a stubbed consent dialog
 * answers the way the system one does, through the same `ActivityResultRegistry` the Config uses.
 */
@HiltAndroidTest
class FakeVpnPermissionRepositoryTest {

  @get:Rule(order = 0)
  val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1)
  val intentsRule = IntentsRule()

  @get:Rule(order = 2)
  val composeRule = createAndroidComposeRule<HiltTestActivity>()

  @Inject
  lateinit var repository: VpnPermissionRepository

  @Inject
  lateinit var fake: FakeVpnPermissionRepository

  @Before
  fun injectDependencies() {
    hiltRule.inject()
  }

  @Test
  fun theAppGetsTheFake() {
    assertSame(fake, repository)
  }

  @Test
  fun acceptingTheDialogGrantsTheConsent() {
    fake.stubConsentDialog(accept = true)

    val result = launchConsentDialog()

    assertEquals(Activity.RESULT_OK, result.resultCode)
    assertTrue(fake.granted)
    assertEquals(VpnConsent.Granted, repository.getVpnConsent())
  }

  @Test
  fun refusingTheDialogKeepsTheConsentRequired() {
    fake.stubConsentDialog(accept = false)

    val result = launchConsentDialog()

    assertEquals(Activity.RESULT_CANCELED, result.resultCode)
    assertFalse(fake.granted)
    assertTrue(repository.getVpnConsent() is VpnConsent.Required)
  }

  /** Launches the Intent the repository reports, as `ConfigRoute` does, and returns its result. */
  private fun launchConsentDialog(): ActivityResult {
    val consent = repository.getVpnConsent() as VpnConsent.Required
    var result: ActivityResult? = null
    composeRule.runOnUiThread {
      composeRule.activity.activityResultRegistry
        .register("vpn-consent", ActivityResultContracts.StartActivityForResult()) { result = it }
        .launch(consent.intent)
    }
    // The stubbed result is delivered through the main looper, so idle means it arrived.
    return composeRule.runOnIdle { checkNotNull(result) { "The consent dialog returned no result" } }
  }
}
