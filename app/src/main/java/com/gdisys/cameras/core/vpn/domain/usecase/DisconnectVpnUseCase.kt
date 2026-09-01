package com.gdisys.cameras.core.vpn.domain.usecase

import com.gdisys.cameras.core.vpn.domain.VpnLifecycleController
import com.gdisys.cameras.core.vpn.domain.VpnRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DisconnectVpnUseCase @Inject constructor(
  private val vpnRepository: VpnRepository,
  private val vpnLifecycleController: VpnLifecycleController
) {
  suspend operator fun invoke(): Result<Unit> = withContext(Dispatchers.Default) {
    return@withContext try {
      vpnRepository.disconnect()
      vpnLifecycleController.stop()
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
