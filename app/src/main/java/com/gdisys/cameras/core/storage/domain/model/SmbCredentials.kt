package com.gdisys.cameras.core.storage.domain.model

import kotlinx.serialization.Serializable

/**
 * Samba login for the recordings share (received via QR code, admin peers only). Its presence is
 * what makes the user an admin in the app. The host and the share are fixed, so only the login
 * travels.
 *
 * Hiding the admin screens is UX only. The Pi enforces access on its own: the router and ufw let
 * only the admin block reach port 445, and Samba checks the address again and asks for this login.
 *
 * @property smbUser the Samba user, one per admin peer (e.g. `admin-a-7`)
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
