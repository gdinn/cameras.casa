package com.gdisys.cameras.core.downloads.domain

import com.gdisys.cameras.core.downloads.domain.model.DownloadRequestResult
import com.gdisys.cameras.core.downloads.domain.model.DownloadStatus
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
}
