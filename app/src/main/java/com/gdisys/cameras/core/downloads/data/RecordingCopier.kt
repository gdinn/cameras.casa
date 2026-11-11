package com.gdisys.cameras.core.downloads.data

import com.gdisys.cameras.core.recordings.data.RemoteFile
import java.io.OutputStream

/**
 * Each read is a round trip through the tunnel, so downloads read in large chunks: 1 MB reads
 * already reached 33 Mbit/s on the real link, against 12 Mbit/s for 64 KB. 4 MB stays well under
 * the server's 8 MiB maximum read.
 */
internal const val DOWNLOAD_CHUNK_BYTES = 4 shl 20

/**
 * Copies [file] into [output], blocking, chunk by chunk.
 *
 * [isStopped] is checked between chunks, so a cancelled download stops within one chunk.
 * [onProgress] gets the bytes copied and the total after each chunk.
 *
 * @return `true` when the whole file was copied, `false` when it stopped first
 */
internal fun copyRecording(
  file: RemoteFile,
  output: OutputStream,
  chunkBytes: Int = DOWNLOAD_CHUNK_BYTES,
  isStopped: () -> Boolean = { false },
  onProgress: (copied: Long, total: Long) -> Unit = { _, _ -> }
): Boolean {
  val buffer = ByteArray(chunkBytes)
  var position = 0L
  while (position < file.length) {
    if (isStopped()) return false
    val read = file.read(position, buffer, 0, buffer.size)
    if (read <= 0) break
    output.write(buffer, 0, read)
    position += read
    onProgress(position, file.length)
  }
  output.flush()
  return !isStopped()
}
