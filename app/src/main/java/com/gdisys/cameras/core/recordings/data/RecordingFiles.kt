package com.gdisys.cameras.core.recordings.data

import com.gdisys.cameras.core.recordings.domain.RecordingsException
import java.io.Closeable
import java.io.IOException

/**
 * Blocking access to the recording files, for the player's loader thread. Closing it closes the
 * connection behind every file it opened.
 */
interface RecordingFileSource : Closeable {

  /** Opens the file at [path] (relative to the share root, `/`-separated). Throws [RecordingReadException]. */
  fun open(path: String): RemoteFile
}

/** A file open for random-access reads. */
interface RemoteFile : Closeable {
  val length: Long

  /**
   * Reads up to [length] bytes at [position] into [buffer] from [offset]. Returns how many were
   * read, or -1 at the end of the file. Throws [RecordingReadException].
   */
  fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int
}

/**
 * A read failure, as the `IOException` a media player expects, carrying the
 * [RecordingsException] that says what went wrong.
 */
class RecordingReadException(val reason: RecordingsException) : IOException(reason.message, reason)
