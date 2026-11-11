package com.gdisys.cameras.core.downloads.domain.usecase

import com.gdisys.cameras.core.downloads.domain.DownloadRepository
import com.gdisys.cameras.core.downloads.domain.model.DownloadRequestResult
import com.gdisys.cameras.core.downloads.domain.model.DownloadStatus
import com.gdisys.cameras.core.recordings.domain.model.Recording
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class DownloadUseCasesTest {

  private val recording = Recording("cam_10", LocalDateTime.of(2026, 11, 10, 15, 0), "cam_10_2026-11-10_15-00-00.mp4")
  private val repository = mockk<DownloadRepository>()

  @Test
  fun `a download request goes to the repository`() = runTest {
    coEvery { repository.download(recording) } returns DownloadRequestResult.ALREADY_RUNNING

    assertEquals(DownloadRequestResult.ALREADY_RUNNING, DownloadRecordingUseCase(repository)(recording))
  }

  @Test
  fun `the download status comes from the repository`() = runTest {
    every { repository.observe(recording) } returns flowOf(DownloadStatus.Running(0.5f))

    assertEquals(DownloadStatus.Running(0.5f), ObserveRecordingDownloadUseCase(repository)(recording).first())
  }
}
