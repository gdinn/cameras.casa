package com.gdisys.cameras.core.storage.domain.usecase

import com.gdisys.cameras.core.storage.UserPreferences
import com.gdisys.cameras.core.storage.domain.isValid
import kotlinx.serialization.json.Json
import javax.inject.Inject

class ParseUserPreferencesFromQrCodeUseCase @Inject constructor() {
  operator fun invoke(rawJson: String): UserPreferences? {
    val sanitizedJson = rawJson
      .trim()
      .replace("﻿", "")
    val decoded = Json.decodeFromString<UserPreferences>(sanitizedJson)
    return if (decoded.vpnConfigDefaults?.isValid() == true && decoded.vpnConfigTokens?.isValid() == true) {
      decoded
    } else {
      null
    }
  }
}
