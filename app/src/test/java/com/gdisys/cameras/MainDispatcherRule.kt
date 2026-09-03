package com.gdisys.cameras

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Troca o `Dispatchers.Main` (usado por `viewModelScope`) por um `TestDispatcher` durante
 * os testes. Usa `UnconfinedTestDispatcher` por padrão para que as corrotinas lançadas em
 * `viewModelScope` executem imediatamente, o que simplifica a sincronização com Turbine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
  private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {

  override fun starting(description: Description) {
    Dispatchers.setMain(testDispatcher)
  }

  override fun finished(description: Description) {
    Dispatchers.resetMain()
  }
}
