package com.gdisys.cameras.core.permission.domain

interface CameraPermissionRepository {
  fun hasCameraPermission(): Boolean
}
