package com.gdisys.cameras.core.storage.domain.model

import kotlinx.serialization.Serializable

/**
 * WireGuard credentials unique to this device (received via QR code / provisioning), matching the
 * `[Interface]`/`[Peer]` sections of a WireGuard configuration file. Prefix `i` = `[Interface]`,
 * prefix `p` = `[Peer]`.
 *
 * Unlike [VpnConfigDefaults], which carries the values shared by every device.
 *
 * @property iPrk this device's private key (`[Interface] PrivateKey`)
 * @property iAddr tunnel IP address assigned to this device (`[Interface] Address`)
 * @property pPsk pre-shared key with the peer/server (`[Peer] PresharedKey`)
 */
@Serializable
data class VpnConfigTokens(
  val iPrk: String? = null,
  val iAddr: String? = null,
  val pPsk: String? = null,
)