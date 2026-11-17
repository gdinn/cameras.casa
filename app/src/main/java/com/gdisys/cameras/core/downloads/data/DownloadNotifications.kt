package com.gdisys.cameras.core.downloads.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import com.gdisys.cameras.R
import com.gdisys.cameras.core.recordings.domain.model.Recording
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

private const val CHANNEL_ID = "recording-downloads"

/**
 * The download notifications: an ongoing one with the progress and a cancel action, which is the
 * foreground service's, and a final one when the download ends.
 *
 * Without `POST_NOTIFICATIONS` (refused on Android 13+) nothing shows, but the download still runs.
 *
 * Not unit-tested (excluded from coverage): notifications need a device.
 */
class DownloadNotifications @Inject constructor(
  @param:ApplicationContext private val context: Context
) {
  private val manager = NotificationManagerCompat.from(context)

  fun foregroundInfo(workId: UUID, recording: Recording, percent: Int?): ForegroundInfo {
    val notification = progress(workId, recording, percent)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ForegroundInfo(progressId(workId), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
      ForegroundInfo(progressId(workId), notification)
    }
  }

  fun showProgress(workId: UUID, recording: Recording, percent: Int) = notify(progressId(workId), progress(workId, recording, percent))

  /**
   * Removes the progress notification. WorkManager removes it too when the work stops, but a
   * progress update racing the stop would post it again, ongoing and with nothing left to remove it.
   */
  fun cancelProgress(workId: UUID) = manager.cancel(progressId(workId))

  fun showFinished(workId: UUID, recording: Recording, savedAs: String) = notify(
    resultId(workId),
    builder()
      .setSmallIcon(android.R.drawable.stat_sys_download_done)
      .setContentTitle(context.getString(R.string.download_notification_finished))
      .setContentText(savedAs)
      .setSubText(recording.camera)
      .setAutoCancel(true)
      .build()
  )

  fun showFailed(workId: UUID, recording: Recording) = notify(
    resultId(workId),
    builder()
      .setSmallIcon(android.R.drawable.stat_notify_error)
      .setContentTitle(context.getString(R.string.download_notification_failed))
      .setContentText(recording.fileName)
      .setSubText(recording.camera)
      .setAutoCancel(true)
      .build()
  )

  private fun progress(workId: UUID, recording: Recording, percent: Int?): Notification =
    builder()
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle(context.getString(R.string.download_notification_downloading))
      .setContentText(recording.fileName)
      .setSubText(recording.camera)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setProgress(100, percent ?: 0, percent == null)
      .addAction(
        android.R.drawable.ic_menu_close_clear_cancel,
        context.getString(R.string.download_notification_cancel),
        WorkManager.getInstance(context).createCancelPendingIntent(workId)
      )
      .build()

  private fun builder(): NotificationCompat.Builder {
    manager.createNotificationChannel(
      NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
        .setName(context.getString(R.string.download_notification_channel))
        .build()
    )
    return NotificationCompat.Builder(context, CHANNEL_ID)
  }

  @SuppressLint("MissingPermission") // Checked right here; lint does not follow the variable.
  private fun notify(id: Int, notification: Notification) {
    val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    if (allowed) manager.notify(id, notification)
  }

  // The result gets an id of its own: WorkManager removes the foreground notification when the work
  // ends, which would take a result posted under the same id with it.
  private fun progressId(workId: UUID) = workId.hashCode()
  private fun resultId(workId: UUID) = workId.hashCode() + 1
}
