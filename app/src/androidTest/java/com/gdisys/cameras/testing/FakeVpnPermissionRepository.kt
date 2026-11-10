package com.gdisys.cameras.testing

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import com.gdisys.cameras.core.permission.data.VpnPermissionRepositoryImpl
import com.gdisys.cameras.core.permission.domain.VpnConsent
import com.gdisys.cameras.core.permission.domain.VpnPermissionRepository
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VPN consent without `VpnService.prepare`. While [granted] is false it reports
 * [VpnConsent.Required] with an Intent no app resolves, so it must be answered by
 * [stubConsentDialog]: launched unstubbed, it fails instead of opening the system dialog.
 *
 * Being the single source of the consent (`VpnPermissionRepository`), it keeps the Config step's
 * initial state and the click on that step in agreement, as `VpnService.prepare` does in production.
 *
 * The level C tests switch it to the system's answer with [useSystemConsent]: Hilt cannot uninstall
 * a `@TestInstallIn` module for one class, so the fake hands over to the production repository.
 */
@Singleton
class FakeVpnPermissionRepository @Inject constructor(
  private val system: VpnPermissionRepositoryImpl
) : VpnPermissionRepository {

  /**
   * Answers with the production repository (`VpnService.prepare`) instead, so the app launches the
   * real system dialog. Set it before `launchApp()`; [granted] is then ignored.
   */
  @Volatile var useSystemConsent = false

  /** Whether the user has already consented. Set it before `launchApp()` to start with consent. */
  @Volatile var granted = false

  /** How many times the app asked for the consent: once for the step's state, once per click. */
  val consentChecks = AtomicInteger()

  override fun getVpnConsent(): VpnConsent {
    consentChecks.incrementAndGet()
    if (useSystemConsent) return system.getVpnConsent()
    return if (granted) VpnConsent.Granted else VpnConsent.Required(Intent(CONSENT_ACTION))
  }

  /**
   * Answers the consent dialog the app launches with [accept], the way the system dialog would:
   * accepting also grants the consent, so later reads see it. Needs Espresso-Intents to be
   * recording (`IntentsRule`).
   */
  fun stubConsentDialog(accept: Boolean) {
    intending(hasAction(CONSENT_ACTION)).respondWithFunction {
      if (accept) granted = true
      ActivityResult(if (accept) Activity.RESULT_OK else Activity.RESULT_CANCELED, null)
    }
  }

  companion object {
    /** The action of the consent Intent, for tests that assert it was (or was not) launched. */
    const val CONSENT_ACTION = "com.gdisys.cameras.test.action.VPN_CONSENT"
  }
}
