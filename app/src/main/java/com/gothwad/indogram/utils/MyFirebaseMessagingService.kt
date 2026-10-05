package com.gothwad.indogram.utils

import android.util.Log
import com.gothwad.indogram.MainActivity
import com.gothwad.indogram.data.IndogramDatabase
import com.gothwad.indogram.data.IndogramRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val tag = "IndogramFCMService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Requirement 3.1 & 3.2:
     * On new token, pass the token into the WebView via evaluateJavascript("window.setDeviceFCMToken('$token')")
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(tag, "Refreshed FCM Token: $token")

        // 1. Cache the token in SharedPreferences
        val sharedPrefs = getSharedPreferences("indogram_prefs", MODE_PRIVATE)
        sharedPrefs.edit().putString("fcm_token", token).apply()
        getSharedPreferences("grix_prefs", MODE_PRIVATE).edit().putString("fcm_token", token).apply()

        // 2. Deliver directly into the active WebView
        MainActivity.sendFCMTokenToWebView(token)
    }

    /**
     * Requirement 3.3 & 3.4:
     * In onMessageReceived, build and display a standard Android Notification with NotificationCompat.Builder
     * Attach a PendingIntent to the notification with a target "chatId" extra.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(tag, "From: ${remoteMessage.from}")

        // 1. Extract Title and Message Body from Notification payload or Data payload
        var title = remoteMessage.notification?.title
        var body = remoteMessage.notification?.body

        if (remoteMessage.data.isNotEmpty()) {
            Log.d(tag, "Message data payload: ${remoteMessage.data}")
            if (title.isNullOrEmpty()) {
                title = remoteMessage.data["title"]
            }
            if (body.isNullOrEmpty()) {
                body = remoteMessage.data["message"] ?: remoteMessage.data["body"]
            }
        }

        val finalTitle = title ?: "Indogram Message"
        val finalBody = body ?: "You have received a new message."

        // Extract target chatId extra
        val chatId = remoteMessage.data["chatId"]
            ?: remoteMessage.data["chat_id"]
            ?: remoteMessage.data["id"]

        Log.d(tag, "Displaying notification: Title=$finalTitle, Body=$finalBody, chatId=$chatId")

        // 2. Persist the notification in the local room database for offline logs
        saveNotificationToLocalDb(finalTitle, finalBody)

        // 3. Show native system notification with PendingIntent containing target chatId
        IndogramNotificationHelper.showNotification(
            context = applicationContext,
            title = finalTitle,
            message = finalBody,
            chatId = chatId,
            payloadJson = remoteMessage.data.toString()
        )
    }

    private fun saveNotificationToLocalDb(title: String, message: String) {
        serviceScope.launch {
            try {
                val db = IndogramDatabase.getDatabase(applicationContext)
                val repository = IndogramRepository(db.indogramDao())
                repository.saveNotification(title, message)
            } catch (e: Exception) {
                Log.e(tag, "Failed to persist notification in Room DB", e)
            }
        }
    }
}
