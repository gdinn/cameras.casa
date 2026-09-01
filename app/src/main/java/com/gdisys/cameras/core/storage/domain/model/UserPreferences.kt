package com.gdisys.cameras.core.storage.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class UserPreferences(
  val vpnConfigDefaults: VpnConfigDefaults? = null,
  val vpnConfigTokens: VpnConfigTokens? = null

)