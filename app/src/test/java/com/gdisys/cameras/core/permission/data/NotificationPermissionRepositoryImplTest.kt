package com.gdisys.cameras.core.permission.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.gdisys.cameras.core.permission.domain.NotificationPermission
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private const val ANDROID_12L = 32
private const val ANDROID_13 = 33

class NotificationPermissionRepositoryImplTest {

  private val context = mockk<Context>(relaxed = true)

  @Before
  fun setUp() {
    mockkStatic(ContextCompat::class)
  }

  @After
  fun tearDown() {
    unmockkStatic(ContextCompat::class)
  }

  @Test
  fun `before Android 13 no permission is needed, and none is checked`() {
    assertEquals(NotificationPermission.NOT_REQUIRED, NotificationPermissionRepositoryImpl(context, ANDROID_12L).getNotificationPermission())

    verify(exactly = 0) { ContextCompat.checkSelfPermission(any(), any()) }
  }

  @Test
  fun `from Android 13 the permission is checked`() {
    val repository = NotificationPermissionRepositoryImpl(context, ANDROID_13)

    every { ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) } returns
      PackageManager.PERMISSION_GRANTED
    assertEquals(NotificationPermission.GRANTED, repository.getNotificationPermission())

    every { ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) } returns
      PackageManager.PERMISSION_DENIED
    assertEquals(NotificationPermission.NOT_GRANTED, repository.getNotificationPermission())
  }
}
