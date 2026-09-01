package com.gdisys.cameras.core.storage.domain.model

fun VpnConfigTokens.isValid(): Boolean {
  return iAddr != null &&
    iPrk != null &&
    pPsk != null
}

fun VpnConfigTokens.isInvalid(): Boolean {
  return !isValid()
}

fun VpnConfigDefaults.isValid(): Boolean {
  return  pPuk != null &&
    iDns != null &&
    pPersistentKeepAlive != null &&
    iMtu != null &&
    pEndpoint != null &&
    pAllowedips != null
}

fun VpnConfigDefaults.isInvalid(): Boolean {
  return !isValid()
}
