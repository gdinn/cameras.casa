package com.gdisys.cameras.core.storage

import kotlinx.serialization.Serializable

/**
 * Credenciais WireGuard exclusivas deste dispositivo (recebidas via QR code /
 * provisionamento), equivalentes às seções `[Interface]`/`[Peer]` de um arquivo de
 * configuração WireGuard. Prefixo `i` = `[Interface]`, prefixo `p` = `[Peer]`.
 *
 * Diferente de [VpnConfigDefaults], que traz valores comuns a todos os dispositivos.
 *
 * @property iPrk chave privada deste dispositivo (`[Interface] PrivateKey`)
 * @property iAddr endereço IP do túnel atribuído a este dispositivo (`[Interface] Address`)
 * @property pPsk chave pré-compartilhada com o peer/servidor (`[Peer] PresharedKey`)
 */
@Serializable
data class VpnConfigTokens(
  val iPrk: String? = null,
  val iAddr: String? = null,
  val pPsk: String? = null,
)
