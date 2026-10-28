package com.gdisys.cameras.core.storage.data

import android.content.Context
import androidx.datastore.dataStore

// Released values: renaming either one orphans every installed user's settings.
internal const val STREAM_PREFERENCES_FILE_NAME = "stream-preferences"
internal const val STREAM_PREFERENCES_KEY_ALIAS = "gdi-sys-stream-preferences"

val Context.streamPreferencesDataStore by dataStore(
  fileName = STREAM_PREFERENCES_FILE_NAME,
  serializer = streamPreferencesSerializer(KeystoreCryptoEngine(STREAM_PREFERENCES_KEY_ALIAS))
)
