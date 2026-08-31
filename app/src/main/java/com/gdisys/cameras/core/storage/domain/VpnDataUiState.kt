package com.gdisys.cameras.core.storage.domain

sealed interface VpnDataUiState {
  data object Loading : VpnDataUiState // Estado inicial real e semântico
  data class Success(val vpnConfigTokensEmpty: Boolean) : VpnDataUiState
}