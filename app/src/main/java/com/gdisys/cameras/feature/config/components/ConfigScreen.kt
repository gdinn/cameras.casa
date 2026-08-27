package com.gdisys.cameras.feature.config.components

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.core.components.LoadingStorageScreen
import com.gdisys.cameras.core.storage.UserPreferences
import com.gdisys.cameras.core.storage.isValid
import com.gdisys.cameras.feature.config.ConfigToastMessage
import com.gdisys.cameras.feature.config.VpnDataUiState
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@Composable
fun ConfigScreen(
  uiState: VpnDataUiState,
  showToast: (ConfigToastMessage) -> Unit,
  updateUserPreferences: (UserPreferences) -> Unit,
  acceptVpnPermission: () -> Unit,
) {
  Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
    var showScanner by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {

      when (uiState) {
        is VpnDataUiState.Loading -> {
          LoadingStorageScreen()
        }

        is VpnDataUiState.Success -> {
          if (!uiState.vpnConfigTokensEmpty && !showScanner) {
            Text(text = "Configurações carregadas!")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { showScanner = true }) {
              Text(text = "Recarregar Configurações")
            }
          } else if (showScanner) {
            QrCodeScreen(
              onCodeScanned = { rawJson ->
                try {
                  val sanitizedJson = rawJson
                    .trim()
                    .replace("\uFEFF", "")
                  val decoded = Json.decodeFromString<UserPreferences>(sanitizedJson)
                  if(decoded.vpnConfigDefaults?.isValid() == true && decoded.vpnConfigTokens?.isValid() == true) {
                    updateUserPreferences(decoded)
                  } else {
                    updateUserPreferences(UserPreferences())
                    showToast(ConfigToastMessage.QR_CODE_INVALID_DATA_ERROR)
                  }
                  showScanner = false
                } catch (e: Exception) {
                  e.printStackTrace()
                  updateUserPreferences(UserPreferences())
                  showToast(ConfigToastMessage.QR_CODE_FORMAT_ERROR)
                  // Se houver erro, fechamos o scanner para permitir tentar de novo (reseta o Analyzer)
                  showScanner = false
                }
              }
            )
          } else {
            Button(onClick = { showScanner = true }) {
              Text(text = "Configurar App")
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          Button(onClick = acceptVpnPermission) {
            Text(text = "Aceitar permissão vpn")
          }
        }
      }
    }
  }
}
