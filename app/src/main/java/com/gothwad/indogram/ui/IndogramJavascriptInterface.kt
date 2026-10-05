package com.gothwad.indogram.ui

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.gothwad.indogram.utils.IndogramNotificationHelper
import org.json.JSONObject
import java.util.UUID

class IndogramJavascriptInterface(
    private val context: Context,
    private val viewModel: IndogramViewModel
) {
    private val tag = "AndroidBridge"
    private val prefs = context.getSharedPreferences("indogram_prefs", Context.MODE_PRIVATE)

    /**
     * Requirement 2.1: returns the cached Firebase Cloud Messaging registration token as a String.
     * JavaScript call: window.AndroidBridge.getFCMToken();
     */
    @JavascriptInterface
    fun getFCMToken(): String {
        val cachedToken = prefs.getString("fcm_token", null)
            ?: context.getSharedPreferences("grix_prefs", Context.MODE_PRIVATE).getString("fcm_token", null)
        Log.d(tag, "getFCMToken called. Token present: ${cachedToken != null}")
        return cachedToken ?: ""
    }

    /**
     * Requirement 2.2: stores the active logged-in user state.
     * JavaScript call: window.AndroidBridge.onUserLoggedIn(userId, phone, extraJson);
     */
    @JavascriptInterface
    fun onUserLoggedIn(userId: String, phone: String, extraJson: String) {
        Log.d(tag, "onUserLoggedIn: userId=$userId, phone=$phone")
        prefs.edit()
            .putString("logged_in_user_id", userId)
            .putString("logged_in_phone", phone)
            .putString("logged_in_extra_json", extraJson)
            .putBoolean("is_logged_in", true)
            .apply()
    }

    /**
     * Requirement 2.3: clears native user sessions and unregisters token if needed.
     * JavaScript call: window.AndroidBridge.onUserLoggedOut();
     */
    @JavascriptInterface
    fun onUserLoggedOut() {
        Log.d(tag, "onUserLoggedOut called. Clearing session.")
        prefs.edit()
            .remove("logged_in_user_id")
            .remove("logged_in_phone")
            .remove("logged_in_extra_json")
            .putBoolean("is_logged_in", false)
            .apply()

        IndogramNotificationHelper.cancelCallNotification(context)
    }

    /**
     * Requirement 2.4: handles incoming/outgoing call notifications.
     * JavaScript call: window.AndroidBridge.onCallStateChanged(state, callDataJson);
     */
    @JavascriptInterface
    fun onCallStateChanged(state: String, callDataJson: String) {
        Log.d(tag, "onCallStateChanged: state=$state, callDataJson=$callDataJson")
        val normalizedState = state.lowercase().trim()
        when (normalizedState) {
            "incoming", "ringing" -> {
                var callerName = "Indogram Call"
                var callType = "Incoming Call"
                try {
                    val json = JSONObject(callDataJson)
                    callerName = json.optString("callerName", json.optString("name", "Indogram Contact"))
                    callType = if (json.optBoolean("isVideo", false)) "Incoming Video Call" else "Incoming Voice Call"
                } catch (e: Exception) {
                    // Fallback to simple title/body
                }
                IndogramNotificationHelper.showCallNotification(
                    context = context,
                    title = callerName,
                    message = callType,
                    callDataJson = callDataJson
                )
            }
            "ended", "idle", "rejected", "missed", "busy" -> {
                IndogramNotificationHelper.cancelCallNotification(context)
            }
            "connected", "active" -> {
                IndogramNotificationHelper.showCallNotification(
                    context = context,
                    title = "Indogram Call",
                    message = "Call in progress...",
                    callDataJson = callDataJson
                )
            }
        }
    }

    /**
     * Requirement 2.5: shows an Android native status bar notification.
     * JavaScript call: window.AndroidBridge.showNotification(title, body, payloadJson);
     */
    @JavascriptInterface
    fun showNotification(title: String, body: String, payloadJson: String) {
        Log.d(tag, "showNotification: title=$title, body=$body, payloadJson=$payloadJson")
        var chatId: String? = null
        try {
            if (payloadJson.isNotBlank()) {
                val json = JSONObject(payloadJson)
                chatId = json.optString("chatId", json.optString("id", null))
            }
        } catch (e: Exception) {
            // Not a JSON object or no chatId
        }

        viewModel.triggerLocalNotification(title, body)
        IndogramNotificationHelper.showNotification(
            context = context,
            title = title,
            message = body,
            chatId = chatId,
            payloadJson = payloadJson
        )
    }

    // --- Backward Compatibility & Helper APIs ---

    @JavascriptInterface
    fun getPushToken(): String {
        return getFCMToken()
    }

    @JavascriptInterface
    fun postNotification(title: String, message: String) {
        showNotification(title, message, "")
    }

    @JavascriptInterface
    fun isDeviceOnline(): Boolean {
        return viewModel.isOnline.value
    }

    @JavascriptInterface
    fun saveOfflineDraft(content: String) {
        viewModel.saveDraft(content)
    }

    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun setTheme(isDark: Boolean) {
        viewModel.setDarkThemeOverride(isDark)
    }

    @JavascriptInterface
    fun setTheme(theme: String) {
        val isDark = when (theme.lowercase().trim()) {
            "dark" -> true
            "light" -> false
            else -> null
        }
        viewModel.setDarkThemeOverride(isDark)
    }
}
