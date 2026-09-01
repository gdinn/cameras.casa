package com.gdisys.cameras.core.vpn.domain.usecase

import com.gdisys.cameras.core.vpn.domain.model.VpnConfig
import com.gdisys.cameras.core.vpn.domain.VpnLifecycleController
import com.gdisys.cameras.core.vpn.domain.VpnRepository
import com.gdisys.cameras.core.vpn.domain.model.isValid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ConnectVpnUseCase @Inject constructor(
  private val vpnRepository: VpnRepository,
  private val vpnLifecycleController: VpnLifecycleController
) {
  suspend operator fun invoke(config: VpnConfig?): Result<Unit> = withContext(Dispatchers.Default) {
    if (config == null) return@withContext Result.failure(IllegalArgumentException("Configuração nula"))
    if (!config.isValid()) return@withContext Result.failure(IllegalArgumentException("Algum parâmetro está nulo"))

    return@withContext try {
      vpnRepository.connect(config)
      // Inicia o serviço de ciclo de vida para monitorar se o app é fechado
      vpnLifecycleController.start()
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
