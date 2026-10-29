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
