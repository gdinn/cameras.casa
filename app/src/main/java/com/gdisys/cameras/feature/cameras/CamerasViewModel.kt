package com.gdisys.cameras.feature.cameras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.storage.DataStoreManager
import com.gdisys.cameras.core.storage.toVpnConfigOrNull
import com.gdisys.cameras.core.vpn.domain.VpnRepository
import com.gdisys.cameras.core.vpn.domain.usecase.ConnectVpnUseCase
import com.gdisys.cameras.core.vpn.domain.usecase.DisconnectVpnUseCase
import com.wireguard.android.backend.Tunnel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CamerasViewModel @Inject constructor(
    private val vpnRepository: VpnRepository,
    private val connectVpnUseCase: ConnectVpnUseCase,
    private val disconnectVpnUseCase: DisconnectVpnUseCase,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {
    val vpnState: StateFlow<Tunnel.State> = vpnRepository.vpnState

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    fun connectVpn() {
        viewModelScope.launch {
            _isConnecting.value = true
            try {
                dataStoreManager.userPrefsState.first().toVpnConfigOrNull()?.let { config ->
                    connectVpnUseCase(config)
                }
            } finally {
                _isConnecting.value = false
            }
        }
    }

    fun disconnectVpn() {
        viewModelScope.launch {
            disconnectVpnUseCase()
        }
    }
}
