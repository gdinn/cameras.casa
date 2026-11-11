package com.gdisys.cameras.core.recordings.domain.usecase

import com.gdisys.cameras.core.recordings.domain.RecordingsException
import com.gdisys.cameras.core.recordings.domain.RecordingsRepository
import java.time.YearMonth
import javax.inject.Inject

/** The months with recordings of a camera, newest first. A failure is a [RecordingsException]. */
class GetRecordingMonthsUseCase @Inject constructor(
  private val recordingsRepository: RecordingsRepository
) {
  suspend operator fun invoke(camera: String): Result<List<YearMonth>> = recordingsResult {
    recordingsRepository.months(camera).asReversed()
  }
}
