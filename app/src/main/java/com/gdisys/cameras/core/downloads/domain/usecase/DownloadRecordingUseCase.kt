package com.gdisys.cameras.core.downloads.domain.usecase

import com.gdisys.cameras.core.downloads.domain.DownloadRepository
import com.gdisys.cameras.core.downloads.domain.model.DownloadRequestResult
import com.gdisys.cameras.core.recordings.domain.model.Recording
import javax.inject.Inject

class DownloadRecordingUseCase @Inject constructor(
  private val downloadRepository: DownloadRepository
) {
  suspend operator fun invoke(recording: Recording): DownloadRequestResult = downloadRepository.download(recording)
}
