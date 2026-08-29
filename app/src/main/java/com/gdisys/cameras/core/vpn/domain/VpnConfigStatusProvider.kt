package com.gdisys.cameras.core.vpn.domain

import com.gdisys.cameras.core.storage.DataStoreManager
import com.gdisys.cameras.core.storage.isInvalid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class VpnConfigStatusProvider @Inject constructor(
  private val dataStoreManager: DataStoreManager
) {
  val uiState: Flow<VpnDataUiState> =
    dataStoreManager.userPrefsState.map { prefs ->
      VpnDataUiState.Success(
        vpnConfigTokensEmpty =
          prefs.vpnConfigTokens?.isInvalid() ?: true ||
          prefs.vpnConfigDefaults?.isInvalid() ?: true
      )
    }
}
