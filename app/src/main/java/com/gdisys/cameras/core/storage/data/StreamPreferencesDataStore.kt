package com.gdisys.cameras.core.storage.data

import android.content.Context
import androidx.datastore.dataStore
import com.gdisys.cameras.core.storage.domain.model.StreamPreferences

private const val STREAM_PREFERENCES_KEY_ALIAS = "gdi-sys-stream-preferences"

val Context.streamPreferencesDataStore by dataStore(
  fileName = "stream-preferences",
  serializer = EncryptedPreferencesSerializer(
    serializer = StreamPreferences.serializer(),
    defaultValue = StreamPreferences(),
    crypto = KeystoreCryptoEngine(STREAM_PREFERENCES_KEY_ALIAS)
  )
)
