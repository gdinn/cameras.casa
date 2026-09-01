package com.gdisys.cameras.core.vpn.domain.model

data class VpnConfig(
  val privateKey: String,
  val address: String,
  val dns: String,
  val publicKey: String,
  val preSharedKey: String,
  val endpoint: String,
  val allowedIps: String,
  val keepAlive: String,
  val mtu: String
)

fun VpnConfig.isValid(): Boolean {
  val properties = listOf(
    privateKey,
    address,
    dns,
    publicKey,
    preSharedKey,
    endpoint,
    allowedIps,
    keepAlive,
    mtu
  )
  return properties.all { it.isNotBlank() }
}