package com.gdisys.cameras.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the coverage report against silent rot.
 *
 * `jacocoTestReport`'s `outOfScopeFilter` names classes by path. Nothing connects those strings to
 * the code: renaming a file, moving a package or deleting a class leaves the pattern behind,
 * matching nothing. The task still succeeds, and the code the project decided *not* to unit-test
 * quietly re-enters the coverage denominator — the report drifts without anyone touching it.
 *
 * This test reads the patterns back out of `build.gradle.kts` and resolves each one against the
 * classes actually on the test classpath, which is the same compiled output JaCoCo filters. A stale
 * pattern therefore fails the build instead of skewing a number.
 */
class JacocoExclusionsTest {

  private val buildFile = File("build.gradle.kts")

  @Test
  fun `every exclusion naming a concrete class still matches a compiled class`() {
    val concretePatterns = outOfScopeFilter().filterNot { it.contains("**") }

    assertTrue(
      "No concrete class patterns found in outOfScopeFilter — did the list move or change shape? " +
        "This test is worthless if it silently matches nothing.",
      concretePatterns.isNotEmpty()
    )

    val stale = concretePatterns.filter { pattern ->
      val packagePath = pattern.substringBeforeLast('/')
      val classGlob = pattern.substringAfterLast('/')
      classFilesIn(packagePath).none { it.matches(globToRegex(classGlob)) }
    }

    assertTrue(
      "These jacocoTestReport exclusions match no compiled class, so the code they claim to " +
        "exclude is being counted in the coverage denominator. Fix the pattern in " +
        "app/build.gradle.kts, or drop it if the class is gone:\n" +
        stale.joinToString("\n") { pattern ->
          val packagePath = pattern.substringBeforeLast('/')
          val present = classFilesIn(packagePath).sorted()
          "  $pattern\n    package contains: " +
            if (present.isEmpty()) "(package not found on the classpath)" else present.joinToString(", ")
        },
      stale.isEmpty()
    )
  }

  @Test
  fun `every exclusion naming a package still resolves to a package`() {
    val packagePatterns = outOfScopeFilter().filter { it.endsWith("/**") }

    assertTrue(
      "No package patterns found in outOfScopeFilter — did the list move or change shape?",
      packagePatterns.isNotEmpty()
    )

    val stale = packagePatterns.filter { pattern ->
      classFilesIn(pattern.removeSuffix("/**")).isEmpty()
    }

    assertTrue(
      "These jacocoTestReport exclusions point at packages that hold no compiled class — the " +
        "package was renamed, moved or removed. Fix them in app/build.gradle.kts:\n" +
        stale.joinToString("\n") { "  $it" },
      stale.isEmpty()
    )
  }

  /**
   * Reads the `outOfScopeFilter` list literal straight out of the build script.
   *
   * Parsing the build file rather than duplicating the list here is the point: a copy would drift
   * from the original exactly the way the patterns drift from the code.
   */
  private fun outOfScopeFilter(): List<String> {
    assertTrue(
      "build.gradle.kts not found at ${buildFile.absolutePath}",
      buildFile.isFile
    )

    val declaration = buildFile.readText().substringAfter("val outOfScopeFilter = listOf(", "")
    assertTrue(
      "`val outOfScopeFilter = listOf(` not found in ${buildFile.name}; update this test to " +
        "match the build script.",
      declaration.isNotEmpty()
    )

    return STRING_LITERAL_REGEX.findAll(declaration.substringBefore(")"))
      .map { it.groupValues[1].replace("\\$", "$") }
      .toList()
  }

  /** Names of the `.class` files the test classpath holds directly under [packagePath]. */
  private fun classFilesIn(packagePath: String): List<String> =
    projectClassFiles
      .filter { it.substringBeforeLast('/') == packagePath }
      .map { it.substringAfterLast('/') }

  /** Translates an Ant-style class glob such as `HomeRouteKt*.class` into a regex. */
  private fun globToRegex(glob: String): Regex =
    Regex(glob.split("*").joinToString(".*") { Regex.escape(it) })

  private companion object {
    val STRING_LITERAL_REGEX = Regex("\"([^\"]*)\"")

    const val PRODUCTION_PACKAGE_PATH = "com/gdisys/cameras/"

    /**
     * Every production class file reachable from the test classpath, as `com/gdisys/.../Foo.class`.
     *
     * The classpath is the ground truth here, and it is not one shape: Gradle hands the unit tests
     * the production code as a jar (`bundleDebugClassesToRuntimeJar`) and the test code as a
     * directory, so both are scanned.
     */
    val projectClassFiles: Set<String> by lazy {
      System.getProperty("java.class.path").orEmpty()
        .split(File.pathSeparator)
        .map(::File)
        .flatMap { entry -> classFileNamesIn(entry) }
        .filter { it.startsWith(PRODUCTION_PACKAGE_PATH) }
        .toSet()
    }

    private fun classFileNamesIn(classpathEntry: File): List<String> = when {
      classpathEntry.isDirectory -> classpathEntry.walkTopDown()
        .filter { it.isFile && it.extension == "class" }
        .map { it.relativeTo(classpathEntry).invariantSeparatorsPath }
        .toList()

      classpathEntry.isFile && classpathEntry.extension == "jar" ->
        runCatching {
          java.util.jar.JarFile(classpathEntry).use { jar ->
            jar.entries().toList().map { it.name }.filter { it.endsWith(".class") }
          }
        }.getOrDefault(emptyList())

      else -> emptyList()
    }
  }
}
