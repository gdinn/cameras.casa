package com.gdisys.cameras.core.vpn.domain.usecase

import com.gdisys.cameras.core.coroutines.DefaultDispatcher
import com.gdisys.cameras.core.vpn.domain.VpnLifecycleController
import com.gdisys.cameras.core.vpn.domain.VpnRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DisconnectVpnUseCase @Inject constructor(
  private val vpnRepository: VpnRepository,
  private val vpnLifecycleController: VpnLifecycleController,
  @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) {
  suspend operator fun invoke(): Result<Unit> = withContext(defaultDispatcher) {
    return@withContext try {
      vpnRepository.disconnect()
      vpnLifecycleController.stop()
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
