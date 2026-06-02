package com.gdisys.cameras.core.storage

import android.content.Context
import androidx.datastore.dataStore

val Context.dataStore by dataStore(
  fileName = "user-preferences",
  serializer = UserPreferencesSerializer
)
