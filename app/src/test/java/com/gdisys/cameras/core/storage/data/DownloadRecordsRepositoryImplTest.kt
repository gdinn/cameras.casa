package com.gdisys.cameras.core.storage.data

import androidx.datastore.core.DataStore
import com.gdisys.cameras.core.storage.domain.model.DownloadRecord
import com.gdisys.cameras.core.storage.domain.model.DownloadRecordState
import com.gdisys.cameras.core.storage.domain.model.DownloadRecords
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private val RECORD = DownloadRecord("a", "cam_10", "cam_10_2026-11-10_15-00-00.mp4", DownloadRecordState.RUNNING)

class DownloadRecordsRepositoryImplTest {

  private class FakeStore(initial: DownloadRecords = DownloadRecords()) : DataStore<DownloadRecords> {
    val state = MutableStateFlow(initial)
    override val data: Flow<DownloadRecords> = state
    override suspend fun updateData(transform: suspend (t: DownloadRecords) -> DownloadRecords): DownloadRecords =
      transform(state.value).also { state.value = it }
  }

  @Test
  fun `the records are read from the store`() = runTest {
    val repository = DownloadRecordsRepositoryImpl(FakeStore(DownloadRecords(listOf(RECORD))))

    assertEquals(listOf(RECORD), repository.records.first())
  }

  @Test
  fun `an update rewrites only the records`() = runTest {
    val store = FakeStore(DownloadRecords(records = emptyList(), schemaVersion = 7))

    DownloadRecordsRepositoryImpl(store).update { it + RECORD }

    assertEquals(DownloadRecords(listOf(RECORD), schemaVersion = 7), store.state.value)
  }

  @Test
  fun `a read failure reaches the caller`() = runTest {
    val failing = object : DataStore<DownloadRecords> {
      override val data: Flow<DownloadRecords> = flow { throw IOException("unreadable") }
      override suspend fun updateData(transform: suspend (t: DownloadRecords) -> DownloadRecords) = DownloadRecords()
    }

    val thrown = runCatching { DownloadRecordsRepositoryImpl(failing).records.first() }.exceptionOrNull()

    assertTrue(thrown is IOException)
  }
}
