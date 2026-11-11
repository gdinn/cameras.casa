package com.gdisys.cameras.core.recordings.data

import com.gdisys.cameras.core.recordings.domain.model.Recording
import com.gdisys.cameras.core.storage.domain.UserPreferencesRepository
import com.gdisys.cameras.core.storage.domain.model.isValid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Creates the [RecordingFileSource]s the player and the downloads read through: each one an
 * [SmbFileSession] with its own client, logged in with the stored admin login.
 */
class RecordingFileSources @Inject constructor(
  private val userPreferencesRepository: UserPreferencesRepository
) {
  fun create(): RecordingFileSource = SmbFileSession {
    // Read when the session connects, which is on a loader or worker thread, never the main one.
    runBlocking { userPreferencesRepository.userPreferences.first().smbCredentials?.takeIf { it.isValid() } }
  }
}

/** Opens [recording]'s file. Throws [RecordingReadException]. */
fun RecordingFileSource.open(recording: Recording): RemoteFile = open(RecordingPaths.file(recording))
