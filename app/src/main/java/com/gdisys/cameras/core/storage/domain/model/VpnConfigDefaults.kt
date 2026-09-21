package com.gdisys.cameras.core.storage.domain.model

import kotlinx.serialization.Serializable

/**
 * WireGuard network parameters shared by every device (received via QR code / provisioning),
 * matching the `[Interface]`/`[Peer]` sections of a WireGuard configuration file. Prefix `i` =
 * `[Interface]`, prefix `p` = `[Peer]`.
 *
 * Unlike [VpnConfigTokens], which carries the values unique to this device.
 *
 * @property iDns the tunnel's DNS server (`[Interface] DNS`)
 * @property iMtu MTU of the tunnel interface (`[Interface] MTU`)
 * @property pPuk public key of the peer/server (`[Peer] PublicKey`)
 * @property pAllowedips IP ranges routed through the tunnel (`[Peer] AllowedIPs`)
 * @property pEndpoint `host:port` address of the WireGuard server (`[Peer] Endpoint`)
 * @property pPersistentKeepAlive keepalive interval with the peer, in seconds (`[Peer] PersistentKeepalive`)
 */
@Serializable
data class VpnConfigDefaults(
  val iDns: String? = null,
  val iMtu: String? = null,
  val pPuk: String? = null,
  val pAllowedips: String? = null,
  val pEndpoint: String? = null,
  val pPersistentKeepAlive: String? = null
)