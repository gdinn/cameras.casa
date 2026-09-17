package com.gdisys.cameras.core.permission.data

import android.content.Context
import android.net.VpnService
import com.gdisys.cameras.core.permission.domain.VpnPermissionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnPermissionRepositoryImpl @Inject constructor(
  @ApplicationContext private val context: Context
) : VpnPermissionRepository {

  override fun hasVpnPermission(): Boolean = VpnService.prepare(context) == null
}