package com.gdisys.cameras.core.downloads.domain.usecase

import com.gdisys.cameras.core.downloads.domain.DownloadRepository
import javax.inject.Inject

/** The app is being closed (swiped from recents): running downloads stop and are reported next time. */
class InterruptDownloadsUseCase @Inject constructor(
  private val downloadRepository: DownloadRepository
) {
  suspend operator fun invoke() = downloadRepository.interruptAll()
}
