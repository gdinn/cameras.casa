package com.gdisys.cameras.core.permission.domain

import android.content.Intent

/**
 * The user's consent for this app to bring a VPN up, as reported by `VpnService.prepare`.
 * It is the single source both for the Config step's state and for the click on that step.
 */
sealed interface VpnConsent {
  data object Granted : VpnConsent

  /** Consent is still needed: [intent] opens the system consent dialog. */
  data class Required(val intent: Intent) : VpnConsent
}
