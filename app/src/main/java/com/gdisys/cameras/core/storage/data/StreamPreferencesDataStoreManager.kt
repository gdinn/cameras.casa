package com.gdisys.cameras.core.storage.data

import androidx.datastore.core.DataStore
import com.gdisys.cameras.core.storage.di.StreamPreferencesStore
import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamPreferencesDataStoreManager @Inject constructor(
  @StreamPreferencesStore private val dataStore: DataStore<StreamPreferences>
) {
  val streamPrefsState: Flow<StreamPreferences> = dataStore.data

  suspend fun updateStreamPreferences(transform: (StreamPreferences) -> StreamPreferences) {
    dataStore.updateData {
      transform(it)
    }
  }
}
