package com.gdisys.cameras.core.storage

import androidx.datastore.core.Serializer
import com.gdisys.cameras.core.vpn.domain.model.VpnConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64

@Serializable
data class UserPreferences(
  val vpnConfigDefaults: VpnConfigDefaults? = null,
  val vpnConfigTokens: VpnConfigTokens? = null

)

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
    mtu = mtu,
    dnsSearchDomain = "casa"
  )
}

object UserPreferencesSerializer: Serializer<UserPreferences> {
  override val defaultValue: UserPreferences
    get() = UserPreferences()

  override suspend fun readFrom(input: InputStream): UserPreferences {
    val encryptedBytes = withContext(Dispatchers.IO) {
      input.use { it.readBytes() }
    }

    if (encryptedBytes.isEmpty()) {
      return defaultValue
    }

    return try {
      val encryptedBytesDecoded = Base64.getDecoder().decode(encryptedBytes)
      val decryptedBytes = Crypto.decrypt(encryptedBytesDecoded)
      val decodedJsonString = decryptedBytes.decodeToString()
      Json.decodeFromString(decodedJsonString)
    } catch (e: Exception) {
      e.printStackTrace()
      // If decryption fails (e.g. BadPaddingException), return default value to avoid crash
      defaultValue
    }
  }

  override suspend fun writeTo(t: UserPreferences, output: OutputStream) {
    val json = Json.encodeToString(t)
    val bytes = json.toByteArray()
    val encryptedBytes = Crypto.encrypt(bytes)
    val encryptedBytesBase64 = Base64.getEncoder().encode(encryptedBytes)
    withContext(Dispatchers.IO) {
      output.use {
        it.write(encryptedBytesBase64)
      }
    }
  }
}