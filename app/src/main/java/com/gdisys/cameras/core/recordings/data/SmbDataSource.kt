package com.gdisys.cameras.core.recordings.data

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import com.gdisys.cameras.core.recordings.domain.model.Recording

/** The scheme of a recording's URI; its path is the file's path on the share. */
internal const val RECORDING_URI_SCHEME = "smb-recording"

/**
 * One read of the file costs a round trip through the tunnel; 1 MB reads reached real time with
 * room to spare on the real link (Step 1 of the history plan).
 */
internal const val READ_AHEAD_BYTES = 1 shl 20

/** The URI the player is given for [recording]. */
internal fun recordingUri(recording: Recording): Uri =
  Uri.Builder().scheme(RECORDING_URI_SCHEME).path("/" + RecordingPaths.file(recording)).build()

/**
 * A media3 `DataSource` over a [RecordingFileSource]: each `open` opens the file at the requested
 * position, and reads go through a [ReadAheadReader]. The file is not downloaded first; a seek is a
 * new `open` at another position, which `+faststart` makes cheap.
 *
 * Tested on a device (`SmbDataSourceTest`), because `DataSpec` carries an `android.net.Uri`.
 */
@OptIn(UnstableApi::class)
internal class SmbDataSource(private val files: RecordingFileSource) : BaseDataSource(/* isNetwork = */ true) {

  private var uri: Uri? = null
  private var file: RemoteFile? = null
  private var reader: ReadAheadReader? = null
  private var bytesRemaining = 0L
  private var opened = false

  override fun open(dataSpec: DataSpec): Long {
    uri = dataSpec.uri
    transferInitializing(dataSpec)
    val path = requireNotNull(dataSpec.uri.path) { "No path in ${dataSpec.uri}" }.removePrefix("/")
    val remote = files.open(path).also { file = it }
    if (dataSpec.position > remote.length) {
      throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
    }
    val available = remote.length - dataSpec.position
    bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) available else minOf(dataSpec.length, available)
    reader = ReadAheadReader(remote, dataSpec.position, READ_AHEAD_BYTES)
    opened = true
    transferStarted(dataSpec)
    return bytesRemaining
  }

  override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
    if (length == 0) return 0
    if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
    val read = checkNotNull(reader).read(buffer, offset, minOf(length.toLong(), bytesRemaining).toInt())
    if (read == -1) return C.RESULT_END_OF_INPUT
    bytesRemaining -= read
    bytesTransferred(read)
    return read
  }

  override fun getUri(): Uri? = uri

  override fun close() {
    uri = null
    reader = null
    file?.close()
    file = null
    if (opened) {
      opened = false
      transferEnded()
    }
  }

  class Factory(private val files: RecordingFileSource) : DataSource.Factory {
    override fun createDataSource(): DataSource = SmbDataSource(files)
  }
}
