package com.gdisys.cameras.core.permission.domain.usecase

import com.gdisys.cameras.core.permission.domain.CameraPermissionRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HasCameraPermissionUseCaseTest {

  private val cameraPermissionRepository = mockk<CameraPermissionRepository>()
  private lateinit var useCase: HasCameraPermissionUseCase

  @Before
  fun setUp() {
    useCase = HasCameraPermissionUseCase(cameraPermissionRepository)
  }

  @Test
  fun `returns true when repository grants permission`() {
    every { cameraPermissionRepository.hasCameraPermission() } returns true

    assertTrue(useCase())
  }

  @Test
  fun `returns false when repository denies permission`() {
    every { cameraPermissionRepository.hasCameraPermission() } returns false

    assertFalse(useCase())
  }
}
