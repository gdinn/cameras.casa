package com.gdisys.cameras.core.permission.data

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.gdisys.cameras.core.permission.domain.VpnConsent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
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
  fun `returns Granted when VpnService prepare returns null`() {
    every { VpnService.prepare(context) } returns null

    assertEquals(VpnConsent.Granted, repository.getVpnConsent())
  }

  @Test
  fun `returns Required with the consent intent when VpnService prepare returns an intent`() {
    val intent = mockk<Intent>()
    every { VpnService.prepare(context) } returns intent

    assertEquals(VpnConsent.Required(intent), repository.getVpnConsent())
  }

  @Test
  fun `queries VpnService with the application context`() {
    every { VpnService.prepare(context) } returns null

    repository.getVpnConsent()

    verify(exactly = 1) { VpnService.prepare(context) }
  }
}
