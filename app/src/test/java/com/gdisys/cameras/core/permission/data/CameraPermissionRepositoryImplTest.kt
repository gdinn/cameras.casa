package com.gdisys.cameras.core.permission.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CameraPermissionRepositoryImplTest {

  private val context = mockk<Context>(relaxed = true)
  private lateinit var repository: CameraPermissionRepositoryImpl

  @Before
  fun setUp() {
    mockkStatic(ContextCompat::class)
    repository = CameraPermissionRepositoryImpl(context)
  }

  @After
  fun tearDown() {
    unmockkStatic(ContextCompat::class)
  }

  @Test
  fun `returns true when camera permission is granted`() {
    every {
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
    } returns PackageManager.PERMISSION_GRANTED

    assertTrue(repository.hasCameraPermission())
  }

  @Test
  fun `returns false when camera permission is denied`() {
    every {
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
    } returns PackageManager.PERMISSION_DENIED

    assertFalse(repository.hasCameraPermission())
  }

  @Test
  fun `queries ContextCompat with the camera permission`() {
    every {
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
    } returns PackageManager.PERMISSION_GRANTED

    repository.hasCameraPermission()

    verify(exactly = 1) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
    }
  }
}
