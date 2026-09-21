package com.gdisys.cameras.core.permission.domain

interface VpnPermissionRepository {
  fun hasVpnPermission(): Boolean
}