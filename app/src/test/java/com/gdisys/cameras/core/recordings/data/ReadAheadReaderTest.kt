package com.gdisys.cameras.core.recordings.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** A file of [content] that records the position and size of every read. */
private class FakeRemoteFile(private val content: ByteArray) : RemoteFile {
  val reads = mutableListOf<Pair<Long, Int>>()
  override val length: Long get() = content.size.toLong()

  override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
    reads += position to length
    if (position >= content.size) return -1
    val count = minOf(length.toLong(), content.size - position).toInt()
    System.arraycopy(content, position.toInt(), buffer, offset, count)
    return count
  }

  override fun close() = Unit
}

class ReadAheadReaderTest {

  private val content = ByteArray(100) { it.toByte() }

  private fun ReadAheadReader.readAll(chunk: Int): ByteArray {
    val out = mutableListOf<Byte>()
    val buffer = ByteArray(chunk)
    while (true) {
      val read = read(buffer, 0, chunk)
      if (read == -1) return out.toByteArray()
      out += buffer.take(read)
    }
  }

  @Test
  fun `small reads are served from one large read of the file`() {
    val file = FakeRemoteFile(content)
    val reader = ReadAheadReader(file, start = 0, chunkSize = 40)

    val first = ByteArray(10)
    assertEquals(10, reader.read(first, 0, 10))
    assertEquals(10, reader.read(ByteArray(10), 0, 10))

    assertArrayEquals(content.copyOfRange(0, 10), first)
    assertEquals(listOf(0L to 40), file.reads)
  }

  @Test
  fun `the whole file comes out in order, chunk after chunk`() {
    val file = FakeRemoteFile(content)

    val all = ReadAheadReader(file, start = 0, chunkSize = 40).readAll(chunk = 7)

    assertArrayEquals(content, all)
    assertEquals(listOf(0L to 40, 40L to 40, 80L to 40, 100L to 40), file.reads)
  }

  @Test
  fun `reading starts at the given position`() {
    val all = ReadAheadReader(FakeRemoteFile(content), start = 90, chunkSize = 40).readAll(chunk = 4)

    assertArrayEquals(content.copyOfRange(90, 100), all)
  }

  @Test
  fun `a read never returns more than what is left in the chunk`() {
    val reader = ReadAheadReader(FakeRemoteFile(content), start = 0, chunkSize = 40)
    reader.read(ByteArray(30), 0, 30)

    assertEquals(10, reader.read(ByteArray(30), 0, 30))
  }

  @Test
  fun `data lands at the requested offset`() {
    val buffer = ByteArray(8)

    ReadAheadReader(FakeRemoteFile(content), start = 5, chunkSize = 40).read(buffer, 4, 4)

    assertArrayEquals(byteArrayOf(0, 0, 0, 0, 5, 6, 7, 8), buffer)
  }

  @Test
  fun `a zero-length read reads nothing`() {
    val file = FakeRemoteFile(content)

    assertEquals(0, ReadAheadReader(file, start = 0, chunkSize = 40).read(ByteArray(4), 0, 0))
    assertEquals(emptyList<Pair<Long, Int>>(), file.reads)
  }

  @Test
  fun `reading past the end returns -1`() {
    assertEquals(-1, ReadAheadReader(FakeRemoteFile(content), start = 100, chunkSize = 40).read(ByteArray(4), 0, 4))
  }
}
