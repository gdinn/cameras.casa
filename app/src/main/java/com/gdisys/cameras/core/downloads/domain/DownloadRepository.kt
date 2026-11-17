package com.gdisys.cameras.core.downloads.domain

import com.gdisys.cameras.core.downloads.domain.model.DownloadRequestResult
import com.gdisys.cameras.core.downloads.domain.model.DownloadStatus
import com.gdisys.cameras.core.downloads.domain.model.InterruptedDownload
import com.gdisys.cameras.core.recordings.domain.model.Recording
import kotlinx.coroutines.flow.Flow

/**
 * Downloads of recordings to `Download/Cameras`, one request per recording, several at once.
 *
 * A download runs in the background, keeps the tunnel up while it runs, and never raises it: it can
 * only start while the tunnel is already up.
 */
interface DownloadRepository {

  suspend fun download(recording: Recording): DownloadRequestResult

  /** The latest download of [recording], `null` when there is none or it was cancelled. */
  fun observe(recording: Recording): Flow<DownloadStatus?>

  /**
   * The downloads that did not end: failed, or interrupted by the app closing or the process
   * dying (a download still marked running that this process does not hold the tunnel for).
   */
  fun interrupted(): Flow<List<InterruptedDownload>>

  /** Deletes what [downloads] left behind and downloads their recordings again. */
  suspend fun resume(downloads: List<InterruptedDownload>)

  /** Deletes what [downloads] left behind and forgets them. */
  suspend fun discard(downloads: List<InterruptedDownload>)

  /** Marks every running download interrupted and stops it: the app is being closed. */
  suspend fun interruptAll()
}
