package com.gdisys.cameras.testing

import com.gdisys.cameras.core.recordings.data.RecordingsShare
import com.gdisys.cameras.core.recordings.data.ShareEntry
import com.gdisys.cameras.core.recordings.domain.RecordingsException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The recordings share without Samba: a set of paths relative to the share root
 * (`cam_10/2026/11/10/cam_10_2026-11-10_15-00-00.mp4`). Every folder on the way to a file exists;
 * [emptyFolders] adds folders with nothing in them, like the recorder's folder for tomorrow. Set
 * [failure] to make every listing throw.
 *
 * The production `RecordingsRepositoryImpl` runs over it (see `TestRecordingsModule`), so the
 * flows exercise the real folder rules. Listings run on the IO dispatcher, hence the volatile
 * fields holding immutable values.
 */
@Singleton
class FakeRecordingsShare @Inject constructor() : RecordingsShare {

  @Volatile var files: List<String> = emptyList()
  @Volatile var emptyFolders: Set<String> = emptySet()
  @Volatile var failure: RecordingsException? = null

  override suspend fun list(path: String): List<ShareEntry> {
    failure?.let { throw it }
    val folders = emptyFolders
    return (files + folders)
      .filter { it.startsWith("$path/") }
      .map { full ->
        val rest = full.removePrefix("$path/")
        val name = rest.substringBefore('/')
        ShareEntry(name, isDirectory = rest.contains('/') || "$path/$name" in folders)
      }
      .distinct()
  }
}
