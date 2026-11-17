package com.gdisys.cameras.core.permission.domain.usecase

import com.gdisys.cameras.core.permission.domain.NotificationPermission
import com.gdisys.cameras.core.permission.domain.NotificationPermissionRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class GetNotificationPermissionUseCaseTest {

  @Test
  fun `the permission comes from the repository`() {
    val repository = mockk<NotificationPermissionRepository>()
    every { repository.getNotificationPermission() } returns NotificationPermission.NOT_GRANTED

    assertEquals(NotificationPermission.NOT_GRANTED, GetNotificationPermissionUseCase(repository)())
  }
}
