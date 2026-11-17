package com.gdisys.cameras.core.storage.domain

import com.gdisys.cameras.core.storage.domain.model.DownloadRecord
import kotlinx.coroutines.flow.Flow

interface DownloadRecordsRepository {
  val records: Flow<List<DownloadRecord>>

  /** Rewrites the records atomically; [transform] gets the list persisted at write time. */
  suspend fun update(transform: (List<DownloadRecord>) -> List<DownloadRecord>)
}
