package com.gdisys.cameras.core.recordings.data

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gdisys.cameras.core.recordings.domain.RecordingsException
import com.gdisys.cameras.core.recordings.domain.model.Recording
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime

private val RECORDING = Recording("cam_10", LocalDateTime.of(2026, 11, 10, 15, 0), "cam_10_2026-11-10_15-00-00.mp4")
private const val PATH = "cam_10/2026/11/10/cam_10_2026-11-10_15-00-00.mp4"

/** On a device because `DataSpec` carries an `android.net.Uri`, which the JVM tests only stub. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class SmbDataSourceTest {

  private val content = ByteArray(3 * 1024 * 1024 + 17) { (it % 251).toByte() }
  private val files = FakeFiles(mapOf(PATH to content))
  private val dataSource = SmbDataSource(files)

  private fun spec(position: Long = 0, length: Long = C.LENGTH_UNSET.toLong()) =
    DataSpec.Builder().setUri(recordingUri(RECORDING)).setPosition(position).setLength(length).build()

  private fun readAll(): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
      val read = dataSource.read(buffer, 0, buffer.size)
      if (read == C.RESULT_END_OF_INPUT) return out.toByteArray()
      out.write(buffer, 0, read)
    }
  }

  @Test
  fun theUriPointsAtTheFileOnTheShare() {
    val uri = recordingUri(RECORDING)

    assertEquals(RECORDING_URI_SCHEME, uri.scheme)
    assertEquals("/$PATH", uri.path)
  }

  @Test
  fun aWholeFileIsReadInLargeChunks() {
    assertEquals(content.size.toLong(), dataSource.open(spec()))

    assertArrayEquals(content, readAll())
    assertTrue(files.reads.all { it == READ_AHEAD_BYTES })
    assertEquals(listOf(PATH), files.opened)
  }

  @Test
  fun aSeekOpensAtThePosition() {
    assertEquals(content.size - 1000L, dataSource.open(spec(position = 1000)))

    assertArrayEquals(content.copyOfRange(1000, content.size), readAll())
  }

  @Test
  fun aBoundedSpecStopsAtItsLength() {
    assertEquals(500L, dataSource.open(spec(position = 10, length = 500)))

    assertArrayEquals(content.copyOfRange(10, 510), readAll())
  }

  @Test
  fun aPositionPastTheEndIsRefused() {
    val thrown = runCatching { dataSource.open(spec(position = content.size + 1L)) }.exceptionOrNull()

    assertTrue(thrown is DataSourceException)
  }

  @Test
  fun aReadFailureReachesThePlayerAsAnIoException() {
    val failing = SmbDataSource(object : RecordingFileSource {
      override fun open(path: String): RemoteFile = throw RecordingReadException(RecordingsException.NotAdmin())
      override fun close() = Unit
    })

    val thrown = runCatching { failing.open(spec()) }.exceptionOrNull()

    assertTrue((thrown as RecordingReadException).reason is RecordingsException.NotAdmin)
  }

  @Test
  fun closeClosesTheFileAndForgetsTheUri() {
    dataSource.open(spec())
    assertEquals(recordingUri(RECORDING), dataSource.uri)

    dataSource.close()

    assertNull(dataSource.uri)
    assertEquals(1, files.closed)
  }

  private class FakeFiles(private val content: Map<String, ByteArray>) : RecordingFileSource {
    val opened = mutableListOf<String>()
    val reads = mutableListOf<Int>()
    var closed = 0

    override fun open(path: String): RemoteFile {
      opened += path
      val bytes = checkNotNull(content[path]) { "No file at $path" }
      return object : RemoteFile {
        override val length = bytes.size.toLong()

        override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
          reads += length
          if (position >= bytes.size) return -1
          val count = minOf(length.toLong(), bytes.size - position).toInt()
          System.arraycopy(bytes, position.toInt(), buffer, offset, count)
          return count
        }

        override fun close() {
          closed++
        }
      }
    }

    override fun close() = Unit
  }
}
