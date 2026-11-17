package com.gdisys.cameras.core.storage.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Current schema of the download records store. */
const val DOWNLOAD_RECORDS_SCHEMA_VERSION = 1

/**
 * The downloads that have not ended well yet, kept across process death so the next launch can
 * report them and delete what they left behind. A download that succeeds, or that the user
 * cancels, removes its record.
 *
 * The serial names are explicit for the same reason as in [StreamPreferences]: this is the on-disk
 * schema.
 */
@Serializable
data class DownloadRecords(
  @SerialName("records") val records: List<DownloadRecord> = emptyList(),
  @SerialName("schemaVersion") val schemaVersion: Int = DOWNLOAD_RECORDS_SCHEMA_VERSION
)

/**
 * @property workId the download's WorkManager id, which is also its tunnel holder's key
 * @property partialFile where the unfinished file is (a MediaStore URI, or a path before Android
 *   10), `null` before it exists or once it is deleted
 */
@Serializable
data class DownloadRecord(
  @SerialName("workId") val workId: String,
  @SerialName("camera") val camera: String,
  @SerialName("fileName") val fileName: String,
  @SerialName("state") val state: DownloadRecordState,
  @SerialName("partialFile") val partialFile: String? = null
)

@Serializable
enum class DownloadRecordState {
  /** Running, as far as the record knows: a record left in this state by a dead process was interrupted. */
  @SerialName("running") RUNNING,

  /** Stopped by the app closing or the process dying. */
  @SerialName("interrupted") INTERRUPTED,

  /** Stopped by a read or write failure. */
  @SerialName("failed") FAILED
}
