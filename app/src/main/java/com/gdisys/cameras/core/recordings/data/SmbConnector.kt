package com.gdisys.cameras.core.recordings.data

import com.gdisys.cameras.core.network.STREAM_HOST
import com.gdisys.cameras.core.storage.domain.model.SmbCredentials
import com.hierynomus.security.bc.BCSecurityProvider
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import java.io.Closeable
import java.util.concurrent.TimeUnit

/** The Pi's recordings share (`nvr-setup/smb.conf`), served by the stream host. */
private const val SHARE_NAME = "backup_cams"
private val SHARE_HOST = STREAM_HOST.removeSurrounding("[", "]")

private const val CONNECT_TIMEOUT_MS = 5_000
private const val REQUEST_TIMEOUT_S = 15L

/** An open share, with the connection and session it lives on. Closing it closes all three. */
internal class SmbShareHandle(
  private val connection: Connection,
  private val session: Session,
  val share: DiskShare
) : Closeable {
  override fun close() {
    runCatching { share.close() }
    runCatching { session.close() }
    runCatching { connection.close() }
  }
}

/**
 * Opens the recordings share with an admin login.
 *
 * **Each user owns its own [SMBClient].** smbj hands out the client's existing connection to the
 * same host, so the listing and the player sharing one client would share one connection, and
 * whichever closed first would cut the other off.
 *
 * Not unit-tested (excluded from coverage): it only talks to the server.
 */
internal object SmbConnector {

  fun newClient(): SMBClient = SMBClient(
    SmbConfig.builder()
      .withSecurityProvider(BCSecurityProvider())
      .withSocketFactory(ConnectTimeoutSocketFactory(CONNECT_TIMEOUT_MS))
      .withTimeout(REQUEST_TIMEOUT_S, TimeUnit.SECONDS)
      .build()
  )

  /** Blocking: connects, logs in and opens the share. */
  fun open(client: SMBClient, credentials: SmbCredentials): SmbShareHandle {
    val connection = client.connect(SHARE_HOST)
    try {
      val login = AuthenticationContext(
        credentials.smbUser,
        requireNotNull(credentials.smbPassword).toCharArray(),
        null
      )
      val session = connection.authenticate(login)
      return SmbShareHandle(connection, session, session.connectShare(SHARE_NAME) as DiskShare)
    } catch (e: Exception) {
      runCatching { connection.close() }
      throw e
    }
  }
}
