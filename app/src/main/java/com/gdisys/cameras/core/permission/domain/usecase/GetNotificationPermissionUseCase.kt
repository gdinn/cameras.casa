package com.gdisys.cameras.core.permission.domain.usecase

import com.gdisys.cameras.core.permission.domain.NotificationPermission
import com.gdisys.cameras.core.permission.domain.NotificationPermissionRepository
import javax.inject.Inject

class GetNotificationPermissionUseCase @Inject constructor(
  private val notificationPermissionRepository: NotificationPermissionRepository
) {
  operator fun invoke(): NotificationPermission = notificationPermissionRepository.getNotificationPermission()
}
