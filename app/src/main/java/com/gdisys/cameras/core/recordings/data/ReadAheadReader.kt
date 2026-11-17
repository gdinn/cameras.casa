package com.gdisys.cameras.core.recordings.data

/**
 * Sequential reads of a [RemoteFile] from [start], in chunks of [chunkSize].
 *
 * Every read of a [RemoteFile] is a round trip through the tunnel. A media player asks for a few
 * kilobytes at a time, which would make it latency-bound: 64 KB reads gave 12 Mbit/s and 1 MB reads
 * 33 Mbit/s on the real link. The reader asks the file for a whole chunk and serves the small reads
 * from it.
 */
internal class ReadAheadReader(
  private val file: RemoteFile,
  start: Long,
  chunkSize: Int
) {
  private val chunk = ByteArray(chunkSize)
  private var chunkLength = 0
  private var chunkPosition = 0
  private var nextFilePosition = start

  /** Like [RemoteFile.read], from where the previous read stopped. */
  fun read(buffer: ByteArray, offset: Int, length: Int): Int {
    if (length == 0) return 0
    if (chunkPosition == chunkLength) {
      val read = file.read(nextFilePosition, chunk, 0, chunk.size)
      if (read <= 0) return -1
      nextFilePosition += read
      chunkLength = read
      chunkPosition = 0
    }
    val count = minOf(length, chunkLength - chunkPosition)
    System.arraycopy(chunk, chunkPosition, buffer, offset, count)
    chunkPosition += count
    return count
  }
}
