package com.gdisys.cameras.core.storage.data

import android.content.Context
import androidx.datastore.dataStore
import com.gdisys.cameras.core.storage.domain.model.UserPreferences

private const val USER_PREFERENCES_KEY_ALIAS = "gdi-sys-storage"

val Context.userPreferencesDataStore by dataStore(
  fileName = "user-preferences",
  serializer = EncryptedPreferencesSerializer(
    serializer = UserPreferences.serializer(),
    defaultValue = UserPreferences(),
    crypto = KeystoreCryptoEngine(USER_PREFERENCES_KEY_ALIAS)
  )
)
