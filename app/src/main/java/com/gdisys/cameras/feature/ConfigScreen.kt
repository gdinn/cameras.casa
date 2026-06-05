package com.gdisys.cameras.feature

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.core.components.QrCodeScreen
import com.gdisys.cameras.core.storage.DataStoreManager
import com.gdisys.cameras.core.storage.UserPreferences
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val jsonConfig = Json {
  ignoreUnknownKeys = true
  coerceInputValues = true
  isLenient = true
}

@Composable
fun ConfigScreen(
  activity: Activity,
  dataStoreManager: DataStoreManager
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
      val userPrefsState by dataStoreManager.userPrefsState.collectAsState(initial = null)
      var showScanner by remember { mutableStateOf(false) }
      val scope = rememberCoroutineScope()

      val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
      ) { result ->
        if (result.resultCode == RESULT_OK) {
          Toast.makeText(activity, "Permissão VPN aceita", Toast.LENGTH_SHORT).show()
        }
      }

      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        if (userPrefsState?.vpnConfigTokens?.pPsk?.isNotEmpty() == true && !showScanner) {
          Text(text = "Configurações carregadas!")
          Spacer(modifier = Modifier.height(8.dp))
          Button(onClick = { showScanner = true }) {
            Text(text = "Recarregar Configurações")
          }
        } else if (showScanner) {
          QrCodeScreen(
            onCodeScanned = { result ->
              try {
                // Usando o jsonConfig que ignora campos desconhecidos
                val decoded = jsonConfig.decodeFromString<UserPreferences>(result)

                scope.launch {
                  dataStoreManager.updateUserPreferences(decoded)
                  showScanner = false
                }
              } catch (e: Exception) {
                e.printStackTrace()
                scope.launch {
                  Toast.makeText(
                    activity,
                    "Erro no formato do QR Code: ${e.message}",
                    Toast.LENGTH_LONG
                  ).show()
                }
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

        Button(onClick = {
          val intent = VpnService.prepare(activity)
          if (intent != null) {
            vpnLauncher.launch(intent)
          } else {
            Toast.makeText(activity, "Permissão já concedida", Toast.LENGTH_SHORT).show()
          }
        }) {
          Text(text = "Aceitar permissão vpn")
        }

      }
    }
  }
