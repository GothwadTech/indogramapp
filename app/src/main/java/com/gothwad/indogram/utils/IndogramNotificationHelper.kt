package com.gothwad.indogram.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gothwad.indogram.MainActivity

object IndogramNotificationHelper {
    const val CHANNEL_CHAT_ID = "indogram_chat_notifications"
    private const val CHANNEL_CHAT_NAME = "Indogram Chat Notifications"
    private const val CHANNEL_CHAT_DESC = "Notifications for incoming messages and updates"

    const val CHANNEL_CALL_ID = "indogram_call_notifications"
    private const val CHANNEL_CALL_NAME = "Indogram Call Notifications"
    private const val CHANNEL_CALL_DESC = "Notifications for incoming and active calls"

    private const val CHAT_NOTIFICATION_BASE_ID = 2000
    private const val CALL_NOTIFICATION_ID = 3001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Chat Channel
            val chatChannel = NotificationChannel(
                CHANNEL_CHAT_ID,
                CHANNEL_CHAT_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_CHAT_DESC
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(chatChannel)

            // Call Channel
            val callChannel = NotificationChannel(
                CHANNEL_CALL_ID,
                CHANNEL_CALL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_CALL_DESC
                enableVibration(true)
                setSound(android.provider.Settings.System.DEFAULT_RINGTONE_URI, null)
            }
            notificationManager.createNotificationChannel(callChannel)
        }
    }

    fun showNotification(
        context: Context,
        title: String,
        message: String,
        chatId: String? = null,
        payloadJson: String? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (!chatId.isNullOrEmpty()) {
                putExtra("chatId", chatId)
            }
            if (!payloadJson.isNullOrEmpty()) {
                putExtra("payloadJson", payloadJson)
            }
        }

        val requestCode = (chatId?.hashCode() ?: System.currentTimeMillis().toInt()) and 0xFFFF
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_CHAT_ID)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(CHAT_NOTIFICATION_BASE_ID + requestCode, builder.build())
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    fun showCallNotification(
        context: Context,
        title: String,
        message: String,
        callDataJson: String? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("isCall", true)
            if (!callDataJson.isNullOrEmpty()) {
                putExtra("callDataJson", callDataJson)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            CALL_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_CALL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(CALL_NOTIFICATION_ID, builder.build())
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    fun cancelCallNotification(context: Context) {
        with(NotificationManagerCompat.from(context)) {
            try {
                cancel(CALL_NOTIFICATION_ID)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
