package com.gdisys.cameras.core.vpn.domain

import com.gdisys.cameras.core.storage.DataStoreManager
import com.gdisys.cameras.core.storage.isInvalid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

class VpnConfigStatusProvider @Inject constructor(
  private val dataStoreManager: DataStoreManager
) {
  fun observe(scope: CoroutineScope): StateFlow<VpnDataUiState> =
    dataStoreManager.userPrefsState.map { prefs ->
      VpnDataUiState.Success(
        vpnConfigTokensEmpty =
          prefs.vpnConfigTokens?.isInvalid() ?: true ||
          prefs.vpnConfigDefaults?.isInvalid() ?: true
      )
    }.stateIn(
      scope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = VpnDataUiState.Loading)
}
