package com.gdisys.cameras.core.storage.domain.usecase

import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.core.storage.domain.model.isValid
import kotlinx.serialization.json.Json
import javax.inject.Inject

class ParseUserPreferencesFromQrCodeUseCase @Inject constructor() {
  operator fun invoke(rawJson: String): Result<UserPreferences?> {
    val sanitizedJson = rawJson
      .trim()
      .replace("﻿", "")
    return try {
      val decoded = Json.decodeFromString<UserPreferences>(sanitizedJson)
      val userPreferences = if (decoded.vpnConfigDefaults?.isValid() == true && decoded.vpnConfigTokens?.isValid() == true) {
        decoded
      } else {
        null
      }
      Result.success(userPreferences)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
