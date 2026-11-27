package com.gdisys.cameras.core.storage.domain.model

import kotlinx.serialization.Serializable

/**
 * Samba login for the recordings share, received via QR code by admin peers and by the client
 * peers of a batch generated with Samba access. Its presence, not the peer's profile, is what gives
 * the user the recordings in the app (history, playback and downloads). The host and the share are
 * fixed, so only the login travels.
 *
 * Hiding the recordings screens is UX only. The Pi enforces access on its own: the router and ufw
 * let only the admin and client blocks (`fd00:10::a:0/112`, `fd00:10::c:0/112`) reach port 445, and
 * Samba checks the address again and asks for this login.
 *
 * @property smbUser the Samba user, one per peer, named after it (e.g. `admin-a-7`, `client-c-5`)
 * @property smbPassword that user's Samba password
 */
@Serializable
data class SmbCredentials(
  val smbUser: String? = null,
  val smbPassword: String? = null
) {
  /** Never prints the password, so a log or a failure message cannot leak it. */
  override fun toString(): String =
    "SmbCredentials(smbUser=$smbUser, smbPassword=${if (smbPassword == null) "null" else "***"})"
}

/** Both fields present and non-blank: a login that can be attempted. */
fun SmbCredentials.isValid(): Boolean = !smbUser.isNullOrBlank() && !smbPassword.isNullOrBlank()
