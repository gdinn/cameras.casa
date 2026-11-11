package com.gdisys.cameras.core.coroutines

import javax.inject.Qualifier

/**
 * The dispatchers used for work off the main thread, injected instead of read from `Dispatchers`
 * so a test can replace them. Instrumented tests swap in dispatchers that Espresso, and therefore
 * the Compose test rule, waits for; unit tests pass a `TestDispatcher`.
 *
 * `Dispatchers.Main` stays hardcoded: the main looper is already what the Compose test rule syncs
 * with, and unit tests replace it through `MainDispatcherRule`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** See [IoDispatcher]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/**
 * A process-wide scope for work that outlives every screen, such as the tunnel coordinator. It runs
 * on `Dispatchers.Main.immediate`, like `viewModelScope`, and a `SupervisorJob` keeps one failed
 * child from cancelling the others. Blocking work still moves to the injected dispatchers.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
