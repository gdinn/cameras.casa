package com.gdisys.cameras.core.recordings.data

import com.gdisys.cameras.core.coroutines.ApplicationScope
import com.gdisys.cameras.core.coroutines.IoDispatcher
import com.gdisys.cameras.core.recordings.domain.RecordingsException
import com.gdisys.cameras.core.storage.domain.UserPreferencesRepository
import com.gdisys.cameras.core.storage.domain.model.SmbCredentials
import com.gdisys.cameras.core.storage.domain.model.isValid
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Long enough to cover browsing from one screen to the next, short enough not to sit open. */
private const val IDLE_CLOSE_MS = 30_000L

/**
 * [RecordingsShare] over smbj: one connection, session and share for listing, opened with the
 * stored admin login on first use and closed when idle (see [LeasedResource]). It has its own
 * client (see [SmbConnector]). smbj multiplexes concurrent requests over the connection, which is
 * what lets a month's days be listed in parallel.
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

  private val client by lazy { SmbConnector.newClient() }

  private val shares = LeasedResource<SmbCredentials, SmbShareHandle>(
    scope = scope,
    idleMillis = IDLE_CLOSE_MS,
    open = { credentials -> withContext(io) { SmbConnector.open(client, credentials) } },
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

  private companion object {
    val DIRECTORY = FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value
  }
}
