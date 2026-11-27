package com.gdisys.cameras.core.recordings.data

import com.gdisys.cameras.core.recordings.domain.RecordingsException
import com.gdisys.cameras.core.storage.domain.model.SmbCredentials
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import java.util.EnumSet

/**
 * [RecordingFileSource] over its own smbj client, connected on the first open and kept for the
 * next ones: a player opens the file again on every seek. When a reused connection turns out dead
 * (the tunnel dropped meanwhile), the open is retried once on a new one.
 *
 * Not unit-tested (excluded from coverage): it only talks to the server. Thread-safe: the player
 * opens files on its loader thread and the screen closes the session from another.
 */
internal class SmbFileSession(
  private val credentials: () -> SmbCredentials?
) : RecordingFileSource {

  private val lock = Any()
  private var client: SMBClient? = null
  private var handle: SmbShareHandle? = null
  private var closed = false

  override fun open(path: String): RemoteFile = synchronized(lock) {
    check(!closed) { "The session is closed" }
    val reused = handle != null
    try {
      openOn(share(), path)
    } catch (e: Exception) {
      if (e.isConnectionLost()) dropConnection()
      if (!reused || !e.isConnectionLost()) throw e.toReadException()
      try {
        openOn(share(), path)
      } catch (retry: Exception) {
        if (retry.isConnectionLost()) dropConnection()
        throw retry.toReadException()
      }
    }
  }

  override fun close() = synchronized(lock) {
    closed = true
    dropConnection()
    runCatching { client?.close() }
    client = null
  }

  private fun share(): DiskShare {
    handle?.let { return it.share }
    val login = credentials() ?: throw RecordingsException.NoSambaLogin()
    val smbClient = client ?: SmbConnector.newClient().also { client = it }
    return SmbConnector.open(smbClient, login).also { handle = it }.share
  }

  private fun dropConnection() {
    handle?.close()
    handle = null
  }

  private fun openOn(share: DiskShare, path: String): RemoteFile {
    val file = share.openFile(
      path.replace('/', '\\'),
      EnumSet.of(AccessMask.GENERIC_READ),
      null,
      SMB2ShareAccess.ALL,
      SMB2CreateDisposition.FILE_OPEN,
      null
    )
    return try {
      SmbRemoteFile(file, file.fileInformation.standardInformation.endOfFile)
    } catch (e: Exception) {
      runCatching { file.close() }
      throw e
    }
  }

  private class SmbRemoteFile(private val file: File, override val length: Long) : RemoteFile {
    override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int = try {
      file.read(buffer, position, offset, length)
    } catch (e: Exception) {
      throw e.toReadException()
    }

    override fun close() {
      runCatching { file.close() }
    }
  }
}
