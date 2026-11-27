package com.gdisys.cameras.core.storage.domain.model

import kotlinx.serialization.Serializable

/**
 * Everything the QR code provisions. The same shape is the QR payload and the on-disk schema of
 * the `user-preferences` store.
 *
 * @property vpnConfigDefaults WireGuard values shared by every device
 * @property vpnConfigTokens WireGuard values unique to this device
 * @property smbCredentials Samba login for the recordings; present only for peers generated with
 *   Samba access. Old QR codes and stores lack it and decode it as `null`, which means "no access
 *   to the recordings"
 */
@Serializable
data class UserPreferences(
  val vpnConfigDefaults: VpnConfigDefaults? = null,
  val vpnConfigTokens: VpnConfigTokens? = null,
  val smbCredentials: SmbCredentials? = null
)
