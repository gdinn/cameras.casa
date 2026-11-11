package com.gdisys.cameras.core.recordings.data

import com.gdisys.cameras.core.recordings.domain.RecordingsException
import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMB2MessageCommandCode
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.protocol.transport.TransportException
import com.hierynomus.smbj.common.SMBRuntimeException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

class SmbErrorsTest {

  private fun status(status: NtStatus) = SMBApiException(status.value, SMB2MessageCommandCode.SMB2_SESSION_SETUP, null)

  @Test
  fun `a refused login is an authentication failure, not a lost connection`() {
    listOf(
      NtStatus.STATUS_LOGON_FAILURE,
      NtStatus.STATUS_ACCOUNT_DISABLED,
      NtStatus.STATUS_PASSWORD_EXPIRED,
      NtStatus.STATUS_LOGON_TYPE_NOT_GRANTED,
      NtStatus.STATUS_ACCESS_DENIED
    ).forEach { code ->
      val error = status(code)
      assertTrue(code.name, error.toRecordingsException() is RecordingsException.AuthenticationFailed)
      assertFalse(code.name, error.isConnectionLost())
    }
  }

  @Test
  fun `a wrapped refusal is still an authentication failure`() {
    val error = SMBRuntimeException(status(NtStatus.STATUS_LOGON_FAILURE))

    assertTrue(error.toRecordingsException() is RecordingsException.AuthenticationFailed)
  }

  @Test
  fun `network failures, wrapped or not, are unreachable and lose the connection`() {
    listOf(
      TransportException("broken pipe"),
      SMBRuntimeException(TransportException(SocketTimeoutException())),
      SMBRuntimeException(TimeoutException()),
      SocketTimeoutException()
    ).forEach { error ->
      assertTrue(error.toString(), error.toRecordingsException() is RecordingsException.Unreachable)
      assertTrue(error.toString(), error.isConnectionLost())
    }
  }

  @Test
  fun `an expired or deleted session loses the connection`() {
    listOf(
      NtStatus.STATUS_NETWORK_SESSION_EXPIRED,
      NtStatus.STATUS_USER_SESSION_DELETED,
      NtStatus.STATUS_NETWORK_NAME_DELETED,
      NtStatus.STATUS_CONNECTION_DISCONNECTED,
      NtStatus.STATUS_CONNECTION_RESET
    ).forEach { code ->
      assertTrue(code.name, status(code).isConnectionLost())
      assertTrue(code.name, status(code).toRecordingsException() is RecordingsException.Unreachable)
    }
  }

  @Test
  fun `any other answer is a plain failure`() {
    val error = status(NtStatus.STATUS_INVALID_PARAMETER)

    assertTrue(error.toRecordingsException() is RecordingsException.Failed)
    assertFalse(error.isConnectionLost())
    assertTrue(IllegalStateException().toRecordingsException() is RecordingsException.Failed)
  }

  @Test
  fun `a domain failure passes through`() {
    val error = RecordingsException.NotAdmin()

    assertSame(error, error.toRecordingsException())
  }

  @Test
  fun `a missing folder or file is not found`() {
    assertTrue(status(NtStatus.STATUS_OBJECT_NAME_NOT_FOUND).isNotFound())
    assertTrue(status(NtStatus.STATUS_OBJECT_PATH_NOT_FOUND).isNotFound())
    assertFalse(status(NtStatus.STATUS_ACCESS_DENIED).isNotFound())
    assertFalse(TransportException("broken pipe").isNotFound())
  }
}
