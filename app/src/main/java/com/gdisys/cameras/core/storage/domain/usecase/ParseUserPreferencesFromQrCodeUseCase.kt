package com.gdisys.cameras.core.storage.domain.usecase

import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.core.storage.domain.model.isValid
import kotlinx.serialization.json.Json
import javax.inject.Inject

class ParseUserPreferencesFromQrCodeUseCase @Inject constructor() {
  /**
   * Decodes the QR payload. Success with `null` means valid JSON with unusable content: incomplete
   * VPN fields, or an `smbCredentials` object without both a user and a password. An absent
   * `smbCredentials` is fine: it is a regular (non-admin) user.
   */
  operator fun invoke(rawJson: String): Result<UserPreferences?> {
    val sanitizedJson = rawJson
      .trim()
      .replace("﻿", "")
    return try {
      val decoded = Json.decodeFromString<UserPreferences>(sanitizedJson)
      val vpnValid = decoded.vpnConfigDefaults?.isValid() == true && decoded.vpnConfigTokens?.isValid() == true
      val smbValid = decoded.smbCredentials?.isValid() ?: true
      Result.success(if (vpnValid && smbValid) decoded else null)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
