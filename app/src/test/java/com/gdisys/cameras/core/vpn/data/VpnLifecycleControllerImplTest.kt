package com.gdisys.cameras.core.vpn.data

import android.content.Context
import android.content.Intent
import io.mockk.EqMatcher
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class VpnLifecycleControllerImplTest {

  private val context = mockk<Context>(relaxed = true)
  private lateinit var controller: VpnLifecycleControllerImpl

  @Before
  fun setUp() {
    // A construção real de Intent(context, Class) não popula seus campos sob o android.jar
    // de teste (mockable jar), então validamos o componente alvo via mockkConstructor
    // em vez de inspecionar o Intent retornado.
    mockkConstructor(Intent::class)
    controller = VpnLifecycleControllerImpl(context)
  }

  @After
  fun tearDown() {
    unmockkConstructor(Intent::class)
  }

  @Test
  fun `start starts the VpnLifecycleService`() {
    every { context.startService(any()) } returns null

    controller.start()

    verify(exactly = 1) {
      constructedWith<Intent>(EqMatcher(context), EqMatcher(VpnLifecycleService::class.java))
    }
    verify(exactly = 1) { context.startService(any()) }
  }

  @Test
  fun `stop stops the VpnLifecycleService`() {
    every { context.stopService(any()) } returns true

    controller.stop()

    verify(exactly = 1) {
      constructedWith<Intent>(EqMatcher(context), EqMatcher(VpnLifecycleService::class.java))
    }
    verify(exactly = 1) { context.stopService(any()) }
  }
}
