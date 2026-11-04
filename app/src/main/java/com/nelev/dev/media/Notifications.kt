package com.nelev.dev.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.nelev.dev.ui.strings.Strings

// ─── Notifications ────────────────────────────────────────────────────────────

private const val NOTIF_CHANNEL_CONN    = "nelev_conn_events"
private const val NOTIF_ID_CONNECTED    = 1002
private const val NOTIF_ID_DISCONNECTED = 1003

fun showConnectionNotification(ctx: Context, connected: Boolean, lang: String) {
    try {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(NOTIF_CHANNEL_CONN, "nelev connection",
            NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null); enableLights(true)
            lightColor = if (connected) 0xFF4CAF50.toInt() else 0xFFFF4444.toInt(); setShowBadge(false)
        })
        val title = if (connected) Strings.get("conn_notif_connected_title", lang) else Strings.get("conn_notif_disconnected_title", lang)
        val body  = if (connected) Strings.get("conn_notif_connected_body", lang) else Strings.get("conn_notif_disconnected_body", lang)
        nm.notify(if (connected) NOTIF_ID_CONNECTED else NOTIF_ID_DISCONNECTED,
            NotificationCompat.Builder(ctx, NOTIF_CHANNEL_CONN)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(title).setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true).setTimeoutAfter(5_000L).build())
    } catch (_: Exception) {}
}
