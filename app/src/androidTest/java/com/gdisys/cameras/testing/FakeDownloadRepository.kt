package com.gdisys.cameras.testing

import com.gdisys.cameras.core.downloads.domain.DownloadRepository
import com.gdisys.cameras.core.downloads.domain.model.DownloadRequestResult
import com.gdisys.cameras.core.downloads.domain.model.DownloadStatus
import com.gdisys.cameras.core.downloads.domain.model.InterruptedDownload
import com.gdisys.cameras.core.recordings.domain.model.Recording
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads without WorkManager, which does not start under `HiltTestApplication`. A request is
 * recorded and answered with [result]; [statuses] and [interrupted] are set by the test. Resume and
 * discard are recorded and remove the downloads, as the real ones do.
 */
@Singleton
class FakeDownloadRepository @Inject constructor() : DownloadRepository {

  @Volatile var result: DownloadRequestResult = DownloadRequestResult.STARTED

  val requested = CopyOnWriteArrayList<Recording>()
  val resumed = CopyOnWriteArrayList<InterruptedDownload>()
  val discarded = CopyOnWriteArrayList<InterruptedDownload>()
  @Volatile var interruptAllCalls = 0

  val statuses = MutableStateFlow<Map<Recording, DownloadStatus>>(emptyMap())
  val interrupted = MutableStateFlow<List<InterruptedDownload>>(emptyList())

  override suspend fun download(recording: Recording): DownloadRequestResult {
    requested += recording
    return result
  }

  override fun observe(recording: Recording): Flow<DownloadStatus?> = statuses.map { it[recording] }

  override fun interrupted(): Flow<List<InterruptedDownload>> = interrupted

  override suspend fun resume(downloads: List<InterruptedDownload>) {
    resumed += downloads
    interrupted.update { it - downloads.toSet() }
  }

  override suspend fun discard(downloads: List<InterruptedDownload>) {
    discarded += downloads
    interrupted.update { it - downloads.toSet() }
  }

  override suspend fun interruptAll() {
    interruptAllCalls++
  }
}
