package com.gdisys.cameras.core.downloads.domain.usecase

import com.gdisys.cameras.core.downloads.domain.DownloadRepository
import com.gdisys.cameras.core.downloads.domain.model.DownloadStatus
import com.gdisys.cameras.core.recordings.domain.model.Recording
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveRecordingDownloadUseCase @Inject constructor(
  private val downloadRepository: DownloadRepository
) {
  operator fun invoke(recording: Recording): Flow<DownloadStatus?> = downloadRepository.observe(recording)
}
