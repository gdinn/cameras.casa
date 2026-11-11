package com.gdisys.cameras.core.recordings.domain.usecase

import com.gdisys.cameras.core.recordings.domain.RecordingsException
import com.gdisys.cameras.core.recordings.domain.RecordingsRepository
import com.gdisys.cameras.core.recordings.domain.model.Recording
import javax.inject.Inject

enum class RecordingDirection { PREVIOUS, NEXT }

/**
 * The recording right before or after another, across days, months and years, or `null` at either
 * end. A failure is a [RecordingsException].
 */
class GetNeighborRecordingUseCase @Inject constructor(
  private val recordingsRepository: RecordingsRepository
) {
  suspend operator fun invoke(recording: Recording, direction: RecordingDirection): Result<Recording?> =
    recordingsResult {
      when (direction) {
        RecordingDirection.PREVIOUS -> recordingsRepository.previous(recording)
        RecordingDirection.NEXT -> recordingsRepository.next(recording)
      }
    }
}
