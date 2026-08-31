package com.gdisys.cameras.core.storage.domain

import com.gdisys.cameras.core.storage.UserPreferences
import com.gdisys.cameras.core.vpn.domain.model.VpnConfig

fun UserPreferences.toVpnConfigOrNull(): VpnConfig? {
  // Validação de dados críticos. Se algum for nulo, a função aborta e retorna null.
  val prk = vpnConfigTokens?.iPrk ?: return null
  val puk = vpnConfigDefaults?.pPuk ?: return null
  val addr = vpnConfigTokens.iAddr ?: return null
  val dns = vpnConfigDefaults.iDns ?: return null
  val pPsk = vpnConfigTokens.pPsk ?: return null
  val keepAlive = vpnConfigDefaults.pPersistentKeepAlive ?: return null
  val mtu = vpnConfigDefaults.iMtu ?: return null
  val endpoint = vpnConfigDefaults.pEndpoint ?: return null
  val allowedIps = vpnConfigDefaults.pAllowedips ?: return null

  return VpnConfig(
    privateKey = prk,
    publicKey = puk,
    address = addr,
    endpoint = endpoint,
    allowedIps = allowedIps,
    dns = dns,
    preSharedKey = pPsk,
    keepAlive = keepAlive,
    mtu = mtu
  )
}