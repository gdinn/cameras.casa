package com.gdisys.cameras.core.storage

import kotlinx.serialization.Serializable

/**
 * Parâmetros de rede WireGuard comuns a todos os dispositivos (recebidos via QR code /
 * provisionamento), equivalentes às seções `[Interface]`/`[Peer]` de um arquivo de
 * configuração WireGuard. Prefixo `i` = `[Interface]`, prefixo `p` = `[Peer]`.
 *
 * Diferente de [VpnConfigTokens], que traz valores exclusivos deste dispositivo.
 *
 * @property iDns servidor DNS do túnel (`[Interface] DNS`)
 * @property iMtu MTU da interface do túnel (`[Interface] MTU`)
 * @property pPuk chave pública do peer/servidor (`[Peer] PublicKey`)
 * @property pAllowedips faixas de IP roteadas pelo túnel (`[Peer] AllowedIPs`)
 * @property pEndpoint endereço `host:porta` do servidor WireGuard (`[Peer] Endpoint`)
 * @property pPersistentKeepAlive intervalo em segundos de keepalive com o peer (`[Peer] PersistentKeepalive`)
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