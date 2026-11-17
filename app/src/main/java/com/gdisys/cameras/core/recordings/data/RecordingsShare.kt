package com.gdisys.cameras.core.recordings.data

import com.gdisys.cameras.core.recordings.domain.RecordingsException

/**
 * The Samba share as [RecordingsRepositoryImpl] needs it, so the folder logic can be tested
 * without a server.
 */
interface RecordingsShare {

  /**
   * The entries of the folder at [path], relative to the share root and `/`-separated, without
   * `.` and `..`. A folder that does not exist is empty. Throws only [RecordingsException].
   */
  suspend fun list(path: String): List<ShareEntry>
}

data class ShareEntry(val name: String, val isDirectory: Boolean)
