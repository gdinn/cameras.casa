package com.gdisys.cameras.core.permission.domain

/** Where the notification permission stands. */
enum class NotificationPermission {
  /** Before Android 13 notifications need no permission. */
  NOT_REQUIRED,
  GRANTED,
  NOT_GRANTED
}

interface NotificationPermissionRepository {
  fun getNotificationPermission(): NotificationPermission
}
