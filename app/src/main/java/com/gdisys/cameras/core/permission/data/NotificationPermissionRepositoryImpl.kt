package com.gdisys.cameras.core.permission.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.gdisys.cameras.core.permission.domain.NotificationPermission
import com.gdisys.cameras.core.permission.domain.NotificationPermissionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** [sdkInt] is a parameter so the unit tests can run both sides of Android 13. */
@Singleton
class NotificationPermissionRepositoryImpl internal constructor(
  private val context: Context,
  private val sdkInt: Int
) : NotificationPermissionRepository {

  @Inject
  constructor(@ApplicationContext context: Context) : this(context, Build.VERSION.SDK_INT)

  override fun getNotificationPermission(): NotificationPermission = when {
    sdkInt < Build.VERSION_CODES.TIRAMISU -> NotificationPermission.NOT_REQUIRED
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
      PackageManager.PERMISSION_GRANTED -> NotificationPermission.GRANTED
    else -> NotificationPermission.NOT_GRANTED
  }
}
