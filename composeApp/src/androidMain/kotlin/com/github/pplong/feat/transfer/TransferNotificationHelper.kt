package com.github.pplong.feat.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.github.pplong.feat.transfer.model.TransferDirection
import com.github.pplong.feat.transfer.model.TransferTask

/**
 * Helper class for managing transfer notifications
 */
class TransferNotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "transfer_channel"
        private const val CHANNEL_NAME = "File Transfers"
        private const val CHANNEL_DESCRIPTION = "Notifications for file upload and download progress"
    }

    init {
        createNotificationChannel()
    }

    /**
     * Create notification channel (Android 8.0+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // Low importance for less intrusive notifications
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create progress notification
     */
    fun createProgressNotification(
        task: TransferTask,
        progress: Int,
        maxProgress: Int
    ): Notification {
        val title = when (task.direction) {
            TransferDirection.DOWNLOAD -> "Downloading ${task.fileName}"
            TransferDirection.UPLOAD -> "Uploading ${task.fileName}"
        }

        val iconResId = android.R.drawable.stat_sys_download

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("${progress}%")
            .setSmallIcon(iconResId)
            .setProgress(maxProgress, progress, false)
            .setOngoing(true) // Cannot be dismissed while in progress
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    /**
     * Update progress notification
     */
    fun updateProgressNotification(task: TransferTask, progress: Int, maxProgress: Int) {
        val notification = createProgressNotification(task, progress, maxProgress)
        notificationManager.notify(task.id.hashCode(), notification)
    }

    /**
     * Show completion notification
     */
    fun showCompletionNotification(task: TransferTask) {
        val title = when (task.direction) {
            TransferDirection.DOWNLOAD -> "Download complete"
            TransferDirection.UPLOAD -> "Upload complete"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(task.fileName)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true) // Dismiss when clicked
            .build()

        notificationManager.notify(task.id.hashCode(), notification)
    }

    /**
     * Show error notification
     */
    fun showErrorNotification(task: TransferTask, errorMessage: String?) {
        val title = when (task.direction) {
            TransferDirection.DOWNLOAD -> "Download failed"
            TransferDirection.UPLOAD -> "Upload failed"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(errorMessage ?: "An error occurred")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${task.fileName}\n${errorMessage ?: "Unknown error"}")
            )
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(task.id.hashCode(), notification)
    }

    /**
     * Cancel notification
     */
    fun cancelNotification(taskId: String) {
        notificationManager.cancel(taskId.hashCode())
    }
}
