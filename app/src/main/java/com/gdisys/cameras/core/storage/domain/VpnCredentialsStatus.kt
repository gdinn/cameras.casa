package com.gdisys.cameras.core.storage.domain

sealed interface VpnCredentialsStatus {
  data object Loading : VpnCredentialsStatus
  data class Loaded(val hasValidCredentials: Boolean) : VpnCredentialsStatus
}
