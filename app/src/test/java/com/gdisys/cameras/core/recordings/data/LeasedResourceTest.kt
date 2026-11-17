package com.gdisys.cameras.core.recordings.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private const val IDLE_MS = 30_000L

private data class Resource(val key: String, val id: Int)

private class Broken : IOException("connection lost")

@OptIn(ExperimentalCoroutinesApi::class)
class LeasedResourceTest {

  private val opened = mutableListOf<Resource>()
  private val closed = mutableListOf<Resource>()
  private var openFailure: Exception? = null
  private var closeFailure: Exception? = null
  private var closeGate: CompletableDeferred<Unit>? = null

  private fun TestScope.leased() = LeasedResource<String, Resource>(
    scope = backgroundScope,
    idleMillis = IDLE_MS,
    open = { key ->
      openFailure?.let { throw it }
      Resource(key, opened.size + 1).also { opened += it }
    },
    close = { resource ->
      closeGate?.await()
      closed += resource
      closeFailure?.let { throw it }
    },
    isBroken = { it is Broken }
  )

  @Test
  fun `the resource is opened on first use and reused afterwards`() = runTest {
    val leased = leased()

    val first = leased.use("login") { it }
    val second = leased.use("login") { it }

    assertEquals(first, second)
    assertEquals(1, opened.size)
  }

  @Test
  fun `concurrent users share one resource`() = runTest {
    val leased = leased()

    val used = List(5) { async { leased.use("login") { delay(100); it } } }.awaitAll()

    assertEquals(1, opened.size)
    assertEquals(setOf(opened.single()), used.toSet())
  }

  @Test
  fun `the resource is closed once idle, not before`() = runTest {
    val leased = leased()
    leased.use("login") { }

    advanceTimeBy(IDLE_MS - 1)
    assertEquals(emptyList<Resource>(), closed)

    advanceTimeBy(2)
    assertEquals(opened, closed)
  }

  @Test
  fun `the idle time counts from the last user leaving`() = runTest {
    val leased = leased()
    val long = async { leased.use("login") { delay(IDLE_MS * 2) } }
    runCurrent()
    leased.use("login") { }

    advanceTimeBy(IDLE_MS * 2 - 1)
    assertEquals(emptyList<Resource>(), closed)

    long.await()
    advanceTimeBy(IDLE_MS + 1)
    assertEquals(opened, closed)
  }

  @Test
  fun `a use during the idle wait keeps the resource`() = runTest {
    val leased = leased()
    leased.use("login") { }
    advanceTimeBy(IDLE_MS / 2)

    leased.use("login") { }
    advanceTimeBy(IDLE_MS - 1)

    assertEquals(1, opened.size)
    assertEquals(emptyList<Resource>(), closed)
  }

  @Test
  fun `the next use after the idle close opens a new resource`() = runTest {
    val leased = leased()
    leased.use("login") { }
    advanceTimeBy(IDLE_MS + 1)

    leased.use("login") { }

    assertEquals(2, opened.size)
  }

  @Test
  fun `another key replaces the resource`() = runTest {
    val leased = leased()
    val old = leased.use("old login") { it }

    val new = leased.use("new login") { it }
    runCurrent()

    assertEquals("new login", new.key)
    assertEquals(listOf(old), closed)
  }

  @Test
  fun `a broken reused resource is closed and the use retried on a fresh one`() = runTest {
    val leased = leased()
    leased.use("login") { }
    var calls = 0

    val result = leased.use("login") { resource ->
      calls++
      if (resource.id == 1) throw Broken()
      resource
    }

    runCurrent()
    assertEquals(2, calls)
    assertEquals(2, result.id)
    assertEquals(listOf(opened.first()), closed)
  }

  @Test
  fun `a broken fresh resource is closed and the failure thrown`() = runTest {
    val leased = leased()
    var calls = 0

    val thrown = runCatching {
      leased.use("login") {
        calls++
        throw Broken()
      }
    }.exceptionOrNull()

    runCurrent()
    assertTrue(thrown is Broken)
    assertEquals(1, calls)
    assertEquals(opened, closed)
  }

  @Test
  fun `a broken retry is closed and thrown`() = runTest {
    val leased = leased()
    leased.use("login") { }

    val thrown = runCatching { leased.use("login") { throw Broken() } }.exceptionOrNull()
    runCurrent()

    assertTrue(thrown is Broken)
    assertEquals(2, opened.size)
    assertEquals(opened, closed)
  }

  @Test
  fun `a failure that does not break the resource keeps it`() = runTest {
    val leased = leased()

    val thrown = runCatching { leased.use("login") { error("not found") } }.exceptionOrNull()
    leased.use("login") { }

    assertTrue(thrown is IllegalStateException)
    assertEquals(1, opened.size)
    assertEquals(emptyList<Resource>(), closed)
  }

  @Test
  fun `a failed open is thrown and the next use opens again`() = runTest {
    val leased = leased()
    openFailure = IOException("unreachable")

    val thrown = runCatching { leased.use("login") { } }.exceptionOrNull()
    openFailure = null
    leased.use("login") { }

    assertEquals("unreachable", thrown?.message)
    assertEquals(1, opened.size)
  }

  @Test
  fun `a failed close is ignored`() = runTest {
    val leased = leased()
    closeFailure = IOException("already gone")
    leased.use("old login") { }

    val new = leased.use("new login") { it }
    advanceTimeBy(IDLE_MS + 1)

    assertEquals("new login", new.key)
    assertEquals(opened, closed)
  }

  @Test
  fun `a slow close holds no one up`() = runTest {
    val leased = leased()
    leased.use("old login") { }
    closeGate = CompletableDeferred() // Closing a dead connection waits for timeouts.

    val new = leased.use("new login") { it }
    val again = leased.use("new login") { it }

    assertEquals("new login", new.key)
    assertEquals(new, again)
    assertEquals(emptyList<Resource>(), closed)

    closeGate?.complete(Unit)
    runCurrent()
    assertEquals(listOf(opened.first()), closed)
  }

  @Test
  fun `invalidate drops the resource and the next use opens a new one`() = runTest {
    val leased = leased()
    val first = leased.use("login") { it }

    leased.invalidate()
    runCurrent()
    val second = leased.use("login") { it }

    assertEquals(listOf(first), closed)
    assertEquals(2, second.id)
  }

  @Test
  fun `invalidate with nothing open does nothing`() = runTest {
    leased().invalidate()
    runCurrent()

    assertEquals(emptyList<Resource>(), closed)
  }

  @Test
  fun `a resource dropped while in use is not closed again when its user leaves`() = runTest {
    val leased = leased()
    val inUse = async { leased.use("login") { delay(100); it } }
    runCurrent()

    leased.invalidate()
    inUse.await()
    advanceTimeBy(IDLE_MS * 2)

    assertEquals(opened, closed)
  }
}
