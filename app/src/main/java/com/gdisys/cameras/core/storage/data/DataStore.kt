package com.gdisys.cameras.core.storage.data

import android.content.Context
import androidx.datastore.dataStore

// Released values: renaming either one orphans every installed user's settings.
internal const val USER_PREFERENCES_FILE_NAME = "user-preferences"
internal const val USER_PREFERENCES_KEY_ALIAS = "gdi-sys-storage"

val Context.userPreferencesDataStore by dataStore(
  fileName = USER_PREFERENCES_FILE_NAME,
  serializer = userPreferencesSerializer(KeystoreCryptoEngine(USER_PREFERENCES_KEY_ALIAS))
)
