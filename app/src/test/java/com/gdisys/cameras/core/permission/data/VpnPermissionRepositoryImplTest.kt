package com.gdisys.cameras.core.permission.data

import android.content.Context
import android.content.Intent
import android.net.VpnService
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

class VpnPermissionRepositoryImplTest {

  private val context = mockk<Context>(relaxed = true)
  private lateinit var repository: VpnPermissionRepositoryImpl

  @Before
  fun setUp() {
    mockkStatic(VpnService::class)
    repository = VpnPermissionRepositoryImpl(context)
  }

  @After
  fun tearDown() {
    unmockkStatic(VpnService::class)
  }

  @Test
  fun `returns true when VpnService prepare returns null`() {
    every { VpnService.prepare(context) } returns null

    assertTrue(repository.hasVpnPermission())
  }

  @Test
  fun `returns false when VpnService prepare returns an intent`() {
    every { VpnService.prepare(context) } returns mockk<Intent>()

    assertFalse(repository.hasVpnPermission())
  }

  @Test
  fun `queries VpnService with the application context`() {
    every { VpnService.prepare(context) } returns null

    repository.hasVpnPermission()

    verify(exactly = 1) { VpnService.prepare(context) }
  }
}
