package com.example.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.BroadcastMessage

object NotificationHelper {
    const val CHANNEL_ID = "broadcast_notifications"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Pengumuman Sekolah"
            val descriptionText = "Notifikasi pembaruan dan instruksi absensi penting"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showBroadcastNotification(context: Context, broadcast: BroadcastMessage) {
        // Create the notification channel (safe to call multiple times)
        createNotificationChannel(context)

        val isUpdate = broadcast.type == "UPDATE"
        val typeLabel = if (isUpdate) "[PEMBARUAN]" else "[INSTRUKSI]"

        // Intent to open Main Activity
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val appPendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("$typeLabel ${broadcast.title}")
            .setContentText(broadcast.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(broadcast.message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(appPendingIntent)
            .setAutoCancel(true)

        if (broadcast.driveLink.isNotBlank()) {
            try {
                val driveIntent = Intent(Intent.ACTION_VIEW, Uri.parse(broadcast.driveLink))
                val drivePendingIntent = PendingIntent.getActivity(
                    context,
                    broadcast.updatedId.toInt(),
                    driveIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val actionTitle = if (isUpdate) "Unduh Pembaruan" else "Buka Lampiran"
                builder.addAction(R.mipmap.ic_launcher_round, actionTitle, drivePendingIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        with(NotificationManagerCompat.from(context)) {
            try {
                if (Build.VERSION.SDK_INT < 33 || 
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context, 
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notify(broadcast.updatedId.toInt(), builder.build())
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }
}
