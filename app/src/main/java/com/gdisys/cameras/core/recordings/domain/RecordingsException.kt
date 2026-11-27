package com.gdisys.cameras.core.recordings.domain

/** Why the share could not be read. Screens tell these apart; nothing else is thrown. */
sealed class RecordingsException(message: String, cause: Throwable? = null) : Exception(message, cause) {

  /** No complete Samba login is stored: the user has no access to the recordings. */
  class NoSambaLogin : RecordingsException("No Samba login is stored")

  /** The server refused the stored login (wrong password, user removed from the Pi). */
  class AuthenticationFailed(cause: Throwable) : RecordingsException("The Samba login was refused", cause)

  /** The server could not be reached or stopped answering: no tunnel, Pi down, timeout. */
  class Unreachable(cause: Throwable) : RecordingsException("The recordings server is unreachable", cause)

  /** Anything else the server answered with. */
  class Failed(cause: Throwable) : RecordingsException("Reading the recordings failed", cause)
}
