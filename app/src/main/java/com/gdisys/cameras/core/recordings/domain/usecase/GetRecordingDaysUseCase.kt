package com.gdisys.cameras.core.recordings.domain.usecase

import com.gdisys.cameras.core.recordings.domain.RecordingsException
import com.gdisys.cameras.core.recordings.domain.RecordingsRepository
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * The days of a month with a finished recording, in calendar order. A failure is a
 * [RecordingsException].
 */
class GetRecordingDaysUseCase @Inject constructor(
  private val recordingsRepository: RecordingsRepository
) {
  suspend operator fun invoke(camera: String, month: YearMonth): Result<List<LocalDate>> = recordingsResult {
    recordingsRepository.days(camera, month)
  }
}
