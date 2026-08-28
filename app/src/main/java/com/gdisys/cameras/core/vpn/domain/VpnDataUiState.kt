package com.gdisys.cameras.core.vpn.domain

sealed interface VpnDataUiState {
  data object Loading : VpnDataUiState // Estado inicial real e semântico
  data class Success(val vpnConfigTokensEmpty: Boolean) : VpnDataUiState
}