package com.ipodnano.player.data.gateway

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ipodnano.player.domain.model.Track
import com.ipodnano.player.presentation.MainActivity

class MusicNotificationHelper(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "nano_player_playback"

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Nano Player Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles de reproducción en segundo plano"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(track: Track, isPlaying: Boolean): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(track.title)
            .setContentText("${track.artist} • ${track.album}")
            .setOngoing(isPlaying)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
            )

        return builder.build()
    }

    fun show(track: Track, isPlaying: Boolean) {
        try {
            val notification = buildNotification(track, isPlaying)
            notificationManager.notify(1001, notification)
        } catch (_: Exception) {}
    }

    fun cancel() {
        try {
            notificationManager.cancel(1001)
        } catch (_: Exception) {}
    }
}
