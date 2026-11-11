package com.gdisys.cameras.core.recordings.data

import com.gdisys.cameras.core.coroutines.ApplicationScope
import com.gdisys.cameras.core.coroutines.IoDispatcher
import com.gdisys.cameras.core.network.STREAM_HOST
import com.gdisys.cameras.core.recordings.domain.RecordingsException
import com.gdisys.cameras.core.storage.domain.UserPreferencesRepository
import com.gdisys.cameras.core.storage.domain.model.SmbCredentials
import com.gdisys.cameras.core.storage.domain.model.isValid
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.security.bc.BCSecurityProvider
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** The Pi's recordings share (`nvr-setup/smb.conf`), served by the stream host. */
private const val SHARE_NAME = "backup_cams"
private val SHARE_HOST = STREAM_HOST.removeSurrounding("[", "]")

private const val CONNECT_TIMEOUT_MS = 5_000
private const val REQUEST_TIMEOUT_S = 15L

/** Long enough to cover browsing from one screen to the next, short enough not to sit open. */
private const val IDLE_CLOSE_MS = 30_000L

/**
 * [RecordingsShare] over smbj: one connection, session and share for the whole process, opened
 * with the stored admin login on first use and closed when idle (see [LeasedResource]). smbj
 * multiplexes concurrent requests over it, which is what lets a month's days be listed in parallel.
 *
 * Not unit-tested (excluded from coverage): every line talks to a server. The decisions it relies
 * on — error mapping, connect timeout, connection reuse — live in tested classes.
 */
@Singleton
class SmbjRecordingsShare @Inject constructor(
  private val userPreferencesRepository: UserPreferencesRepository,
  @ApplicationScope scope: CoroutineScope,
  @param:IoDispatcher private val io: CoroutineDispatcher
) : RecordingsShare {

  private class OpenShare(val connection: Connection, val session: Session, val share: DiskShare) {
    fun close() {
      runCatching { share.close() }
      runCatching { session.close() }
      runCatching { connection.close() }
    }
  }

  private val client by lazy {
    SMBClient(
      SmbConfig.builder()
        .withSecurityProvider(BCSecurityProvider())
        .withSocketFactory(ConnectTimeoutSocketFactory(CONNECT_TIMEOUT_MS))
        .withTimeout(REQUEST_TIMEOUT_S, TimeUnit.SECONDS)
        .build()
    )
  }

  private val shares = LeasedResource<SmbCredentials, OpenShare>(
    scope = scope,
    idleMillis = IDLE_CLOSE_MS,
    open = { credentials -> withContext(io) { open(credentials) } },
    close = { open -> withContext(io) { open.close() } },
    isBroken = { it.isConnectionLost() }
  )

  override suspend fun list(path: String): List<ShareEntry> = onShare { share ->
    val entries = try {
      share.list(path.replace('/', '\\'))
    } catch (e: SMBApiException) {
      if (e.isNotFound()) emptyList() else throw e
    }
    entries
      .filterNot { it.fileName == "." || it.fileName == ".." }
      .map { ShareEntry(it.fileName, isDirectory = (it.fileAttributes and DIRECTORY) != 0L) }
  }

  private suspend fun <T> onShare(block: (DiskShare) -> T): T {
    val credentials = userPreferencesRepository.userPreferences.first().smbCredentials
      ?.takeIf { it.isValid() }
      ?: throw RecordingsException.NotAdmin()
    return try {
      shares.use(credentials) { open -> withContext(io) { block(open.share) } }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      throw e.toRecordingsException()
    }
  }

  private fun open(credentials: SmbCredentials): OpenShare {
    val connection = client.connect(SHARE_HOST)
    try {
      val login = AuthenticationContext(
        credentials.smbUser,
        requireNotNull(credentials.smbPassword).toCharArray(),
        null
      )
      val session = connection.authenticate(login)
      return OpenShare(connection, session, session.connectShare(SHARE_NAME) as DiskShare)
    } catch (e: Exception) {
      runCatching { connection.close() }
      throw e
    }
  }

  private companion object {
    val DIRECTORY = FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value
  }
}
