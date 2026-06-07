package com.gdisys.cameras.feature.init

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.core.storage.DataStoreManager
import com.gdisys.cameras.core.storage.toVpnConfigOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun InitScreen(
  dataStoreManager: DataStoreManager, // desacoplar
  onNavigateToConfig: () -> Unit,
  onNavigateToDashboard: () -> Unit
) {
  val context = LocalContext.current
  val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  // Estado para controlar a exibição da UI de carregamento
  var isCheckingData by remember { mutableStateOf(true) }

  // LaunchedEffect com Unit garante que este bloco execute apenas uma vez
  // quando o Composable entrar na árvore, ideal para checagens iniciais.

  LaunchedEffect(Unit) {
    // 1. Simula uma leitura assíncrona de dados locais
    // (Ex: consultando um banco SQLite, DataStore ou arquivo criptografado)

    applicationScope.launch {
      val userPrefs = dataStoreManager.userPrefsState.first().toVpnConfigOrNull()
      if (userPrefs == null) {
        Toast.makeText(context, "Sem credenciais", Toast.LENGTH_SHORT).show()
        onNavigateToConfig()
      } else {
        onNavigateToDashboard()
      }
      (context as? Activity)?.finish()
    }
  }

  // UI exibida enquanto a checagem acontece (opcional, mas recomendado)
  if (isCheckingData) {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Verificando dados locais...")
      }
    }
  }
}
