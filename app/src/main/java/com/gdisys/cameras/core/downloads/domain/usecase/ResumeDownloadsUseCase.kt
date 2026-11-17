package com.gdisys.cameras.core.downloads.domain.usecase

import com.gdisys.cameras.core.downloads.domain.DownloadRepository
import com.gdisys.cameras.core.downloads.domain.model.InterruptedDownload
import javax.inject.Inject

class ResumeDownloadsUseCase @Inject constructor(
  private val downloadRepository: DownloadRepository
) {
  suspend operator fun invoke(downloads: List<InterruptedDownload>) = downloadRepository.resume(downloads)
}
