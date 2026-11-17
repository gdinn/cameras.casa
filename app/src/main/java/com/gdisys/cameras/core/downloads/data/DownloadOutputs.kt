package com.gdisys.cameras.core.downloads.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject

/** The folder under Downloads that recordings are saved in. */
internal const val DOWNLOAD_FOLDER = "Cameras"

/** Where a download writes. */
interface DownloadOutputs {
  /** Starts a file for [fileName]. Nothing is visible to the user until [PendingDownload.publish]. */
  fun create(fileName: String): PendingDownload

  /** Deletes an unfinished file a dead process left, by its [PendingDownload.reference]. */
  fun deletePartial(reference: String)
}

/** A file being written. Exactly one of [publish] or [discard] ends it. */
interface PendingDownload {
  val stream: OutputStream

  /** Where the unfinished file is, as [DownloadOutputs.deletePartial] takes it. */
  val reference: String

  /** Makes the file visible under its final name, which [freeFileName] chose; returns that name. */
  fun publish(): String

  /** Deletes what was written. */
  fun discard()
}

/**
 * [DownloadOutputs] on the device: MediaStore's Downloads collection from Android 10, a plain file
 * in the public Download folder before it (with `WRITE_EXTERNAL_STORAGE`).
 *
 * The free name is chosen when the file is created. Downloads of the same recording never run
 * together (one unique work per recording), so two pending files cannot pick the same name.
 * MediaStore only shows an app its own files, so a file left by a previous install is invisible
 * to the check; MediaStore then renames on its own (`name (1).mp4`).
 *
 * Not unit-tested (excluded from coverage): MediaStore and the file system need a device.
 */
class DeviceDownloadOutputs @Inject constructor(
  @param:ApplicationContext private val context: Context
) : DownloadOutputs {

  override fun create(fileName: String): PendingDownload =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) mediaStore(fileName) else legacy(fileName)

  override fun deletePartial(reference: String) {
    // A MediaStore entry this app created (pending or not), or a .part file before Android 10.
    if (reference.startsWith("content://")) {
      runCatching { context.contentResolver.delete(Uri.parse(reference), null, null) }
    } else {
      File(reference).delete()
    }
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private fun mediaStore(fileName: String): PendingDownload {
    val resolver = context.contentResolver
    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOAD_FOLDER/"
    val name = freeFileName(fileName) { candidate -> mediaStoreHas(collection, relativePath, candidate) }
    val values = ContentValues().apply {
      put(MediaStore.MediaColumns.DISPLAY_NAME, name)
      put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
      put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
      put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore refused $name")
    val stream = try {
      resolver.openOutputStream(uri) ?: throw IOException("No stream for $uri")
    } catch (e: Exception) {
      resolver.delete(uri, null, null)
      throw e
    }
    return object : PendingDownload {
      override val stream = stream
      override val reference = uri.toString()

      override fun publish(): String {
        stream.close()
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return name
      }

      override fun discard() {
        runCatching { stream.close() }
        resolver.delete(uri, null, null)
      }
    }
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private fun mediaStoreHas(collection: Uri, relativePath: String, name: String): Boolean =
    context.contentResolver.query(
      collection,
      arrayOf(MediaStore.MediaColumns._ID),
      "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
      arrayOf(relativePath, name),
      null
    )?.use { it.count > 0 } ?: false

  @Suppress("DEPRECATION") // The public directory is the only way before scoped storage.
  private fun legacy(fileName: String): PendingDownload {
    val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_FOLDER)
    if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Cannot create $folder")
    val name = freeFileName(fileName) { candidate -> File(folder, candidate).exists() }
    val partial = File(folder, ".$name.part")
    val stream = FileOutputStream(partial)
    return object : PendingDownload {
      override val stream = stream
      override val reference: String = partial.path

      override fun publish(): String {
        stream.close()
        val target = File(folder, name)
        if (!partial.renameTo(target)) throw IOException("Cannot rename $partial to $target")
        MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf("video/mp4"), null)
        return name
      }

      override fun discard() {
        runCatching { stream.close() }
        partial.delete()
      }
    }
  }
}
