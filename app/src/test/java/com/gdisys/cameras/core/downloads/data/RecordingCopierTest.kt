package com.gdisys.cameras.core.downloads.data

import com.gdisys.cameras.core.recordings.data.RecordingReadException
import com.gdisys.cameras.core.recordings.data.RemoteFile
import com.gdisys.cameras.core.recordings.domain.RecordingsException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException

private class FakeFile(private val content: ByteArray, private val failAt: Long? = null) : RemoteFile {
  val reads = mutableListOf<Long>()
  override val length = content.size.toLong()

  override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
    reads += position
    if (failAt != null && position >= failAt) throw RecordingReadException(RecordingsException.Unreachable(IOException()))
    if (position >= content.size) return -1
    val count = minOf(length.toLong(), content.size - position).toInt()
    System.arraycopy(content, position.toInt(), buffer, offset, count)
    return count
  }

  override fun close() = Unit
}

class RecordingCopierTest {

  private val content = ByteArray(1000) { (it % 251).toByte() }

  @Test
  fun `the whole file is copied in chunks, with the progress after each`() {
    val file = FakeFile(content)
    val output = ByteArrayOutputStream()
    val progress = mutableListOf<Pair<Long, Long>>()

    val complete = copyRecording(file, output, chunkBytes = 400) { copied, total -> progress += copied to total }

    assertTrue(complete)
    assertArrayEquals(content, output.toByteArray())
    assertEquals(listOf(0L, 400L, 800L), file.reads)
    assertEquals(listOf(400L to 1000L, 800L to 1000L, 1000L to 1000L), progress)
  }

  @Test
  fun `a stop is honoured between chunks`() {
    val output = ByteArrayOutputStream()
    var chunks = 0

    val complete = copyRecording(
      FakeFile(content),
      output,
      chunkBytes = 400,
      isStopped = { chunks >= 1 },
      onProgress = { _, _ -> chunks++ }
    )

    assertFalse(complete)
    assertEquals(400, output.size())
  }

  @Test
  fun `a stop after the last chunk is still a stop`() {
    var stopped = false

    val complete = copyRecording(FakeFile(content), ByteArrayOutputStream(), chunkBytes = 2000, isStopped = { stopped }) { _, _ ->
      stopped = true
    }

    assertFalse(complete)
  }

  @Test
  fun `a file shorter than announced ends the copy`() {
    val file = object : RemoteFile {
      override val length = 1000L
      override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int) = if (position == 0L) 300 else -1
      override fun close() = Unit
    }
    val output = ByteArrayOutputStream()

    assertTrue(copyRecording(file, output, chunkBytes = 400))
    assertEquals(300, output.size())
  }

  @Test
  fun `a read failure reaches the caller`() {
    val thrown = runCatching {
      copyRecording(FakeFile(content, failAt = 400), ByteArrayOutputStream(), chunkBytes = 400)
    }.exceptionOrNull()

    assertTrue(thrown is RecordingReadException)
  }

  @Test
  fun `an empty file copies nothing`() {
    val output = ByteArrayOutputStream()

    assertTrue(copyRecording(FakeFile(ByteArray(0)), output))
    assertEquals(0, output.size())
  }
}
