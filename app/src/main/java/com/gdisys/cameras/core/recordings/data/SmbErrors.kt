package com.gdisys.cameras.core.recordings.data

import com.gdisys.cameras.core.recordings.domain.RecordingsException
import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMBApiException
import java.io.IOException
import java.util.concurrent.TimeoutException

/** Statuses that mean the stored login is wrong, not that the network failed. */
private val AUTHENTICATION_STATUSES = setOf(
  NtStatus.STATUS_LOGON_FAILURE,
  NtStatus.STATUS_ACCOUNT_DISABLED,
  NtStatus.STATUS_PASSWORD_EXPIRED,
  NtStatus.STATUS_LOGON_TYPE_NOT_GRANTED,
  NtStatus.STATUS_ACCESS_DENIED
)

/** Statuses that mean the session is gone while the connection may still look alive. */
private val LOST_SESSION_STATUSES = setOf(
  NtStatus.STATUS_NETWORK_SESSION_EXPIRED,
  NtStatus.STATUS_USER_SESSION_DELETED,
  NtStatus.STATUS_NETWORK_NAME_DELETED,
  NtStatus.STATUS_CONNECTION_DISCONNECTED,
  NtStatus.STATUS_CONNECTION_RESET
)

private val NOT_FOUND_STATUSES = setOf(
  NtStatus.STATUS_OBJECT_NAME_NOT_FOUND,
  NtStatus.STATUS_OBJECT_PATH_NOT_FOUND
)

// smbj wraps its failures (an SMBRuntimeException around a TransportException around the socket's
// IOException, for instance), so every check looks at the whole cause chain.
private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { it.cause.takeIf { c -> c !== it } }

private fun Throwable.statuses(): Sequence<NtStatus?> = causes().filterIsInstance<SMBApiException>().map { it.status }

private fun Throwable.isNetworkFailure(): Boolean = causes().any { it is IOException || it is TimeoutException }

/** A folder or file that is not on the share. */
internal fun Throwable.isNotFound(): Boolean = statuses().any { it in NOT_FOUND_STATUSES }

/** The connection or session is dead and must be opened again. */
internal fun Throwable.isConnectionLost(): Boolean =
  isNetworkFailure() || statuses().any { it in LOST_SESSION_STATUSES }

/** The domain's view of an smbj failure. */
internal fun Throwable.toRecordingsException(): RecordingsException = when {
  this is RecordingsException -> this
  statuses().any { it in AUTHENTICATION_STATUSES } -> RecordingsException.AuthenticationFailed(this)
  isConnectionLost() -> RecordingsException.Unreachable(this)
  else -> RecordingsException.Failed(this)
}

/** [toRecordingsException], as the `IOException` the player's data source has to throw. */
internal fun Throwable.toReadException(): RecordingReadException =
  this as? RecordingReadException ?: RecordingReadException(toRecordingsException())
