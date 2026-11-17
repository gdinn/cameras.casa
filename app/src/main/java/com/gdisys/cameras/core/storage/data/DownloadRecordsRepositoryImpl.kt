package com.gdisys.cameras.core.storage.data

import android.util.Log
import androidx.datastore.core.DataStore
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.storage.di.DownloadRecordsStore
import com.gdisys.cameras.core.storage.domain.DownloadRecordsRepository
import com.gdisys.cameras.core.storage.domain.model.DownloadRecord
import com.gdisys.cameras.core.storage.domain.model.DownloadRecords
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRecordsRepositoryImpl @Inject constructor(
  @param:DownloadRecordsStore private val dataStore: DataStore<DownloadRecords>
) : DownloadRecordsRepository {

  override val records: Flow<List<DownloadRecord>> = dataStore.data
    .map { it.records }
    .catch { e ->
      Log.e(DEBUG_TAG, "Failed to read download records", e)
      throw e
    }

  override suspend fun update(transform: (List<DownloadRecord>) -> List<DownloadRecord>) {
    dataStore.updateData { it.copy(records = transform(it.records)) }
  }
}
