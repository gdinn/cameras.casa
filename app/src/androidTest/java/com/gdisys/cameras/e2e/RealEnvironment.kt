package com.gdisys.cameras.e2e

/**
 * Marks a test that needs the real world: the real WireGuard tunnel, the home endpoint over IPv6,
 * the Pi streaming video and a dedicated test peer. These tests run only by hand, with
 * `-PrealEnvironment` (see `app/build.gradle.kts` and the README next to this file), and every
 * other run excludes them.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RealEnvironment
