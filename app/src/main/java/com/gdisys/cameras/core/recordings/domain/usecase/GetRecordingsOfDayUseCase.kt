package com.gdisys.cameras.core.recordings.domain.usecase

import com.gdisys.cameras.core.recordings.domain.RecordingsException
import com.gdisys.cameras.core.recordings.domain.RecordingsRepository
import com.gdisys.cameras.core.recordings.domain.model.Recording
import java.time.LocalDate
import javax.inject.Inject

/** The finished recordings of a day, in chronological order. A failure is a [RecordingsException]. */
class GetRecordingsOfDayUseCase @Inject constructor(
  private val recordingsRepository: RecordingsRepository
) {
  suspend operator fun invoke(camera: String, day: LocalDate): Result<List<Recording>> = recordingsResult {
    recordingsRepository.recordings(camera, day)
  }
}
