package com.gdisys.cameras.core.permission.domain.usecase

import com.gdisys.cameras.core.permission.domain.CameraPermissionRepository
import javax.inject.Inject

class HasCameraPermissionUseCase @Inject constructor(
  private val cameraPermissionRepository: CameraPermissionRepository
) {
  operator fun invoke(): Boolean = cameraPermissionRepository.hasCameraPermission()
}
