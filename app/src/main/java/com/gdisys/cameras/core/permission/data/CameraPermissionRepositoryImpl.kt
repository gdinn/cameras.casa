package com.gdisys.cameras.core.permission.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.gdisys.cameras.core.permission.domain.CameraPermissionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraPermissionRepositoryImpl @Inject constructor(
  @ApplicationContext private val context: Context
) : CameraPermissionRepository {

  override fun hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}
