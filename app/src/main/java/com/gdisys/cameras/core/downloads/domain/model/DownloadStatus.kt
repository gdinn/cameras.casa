package com.gdisys.cameras.core.downloads.domain.model

/** Where the download of one recording stands. */
sealed interface DownloadStatus {
  /** Queued or copying. [progress] is 0..1, `null` until the size is known. */
  data class Running(val progress: Float? = null) : DownloadStatus

  /** Saved in `Download/Cameras` as [fileName], which may carry a `_n` suffix. */
  data class Succeeded(val fileName: String?) : DownloadStatus

  /** It stopped: a read or write failure, or the tunnel was gone when it started. */
  data object Failed : DownloadStatus
}

/** What became of a download request. */
enum class DownloadRequestResult {
  STARTED,

  /** The same recording is already downloading: the request is ignored. */
  ALREADY_RUNNING,

  /** A download never raises the tunnel, and it is not up. */
  TUNNEL_DOWN
}
