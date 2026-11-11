package com.gdisys.cameras.core.downloads.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNamesTest {

  @Test
  fun `a free name is kept`() {
    assertEquals("cam_10_2026-11-10_15-00-00.mp4", freeFileName("cam_10_2026-11-10_15-00-00.mp4") { false })
  }

  @Test
  fun `a taken name gets the first free suffix`() {
    val taken = setOf("a.mp4", "a_1.mp4", "a_2.mp4")

    assertEquals("a_3.mp4", freeFileName("a.mp4") { it in taken })
  }

  @Test
  fun `a gap in the suffixes is filled`() {
    val taken = setOf("a.mp4", "a_2.mp4")

    assertEquals("a_1.mp4", freeFileName("a.mp4") { it in taken })
  }

  @Test
  fun `the suffix goes before the last extension only`() {
    assertEquals("a.b_1.mp4", freeFileName("a.b.mp4") { it == "a.b.mp4" })
  }

  @Test
  fun `a name without an extension gets the suffix at the end`() {
    assertEquals("notes_1", freeFileName("notes") { it == "notes" })
    assertEquals(".hidden_1", freeFileName(".hidden") { it == ".hidden" })
  }
}
