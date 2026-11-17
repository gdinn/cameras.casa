package com.gdisys.cameras.core.downloads.domain.usecase

import com.gdisys.cameras.core.downloads.domain.DownloadRepository
import com.gdisys.cameras.core.downloads.domain.model.InterruptedDownload
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveInterruptedDownloadsUseCase @Inject constructor(
  private val downloadRepository: DownloadRepository
) {
  operator fun invoke(): Flow<List<InterruptedDownload>> = downloadRepository.interrupted()
}
