package com.tdpham.navitvbrowser.util

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        try {
            super.onNewToken(token)
            Log.d("FCM", "New Token: $token")
            FirebaseInitializer.logEvent("fcm_token_received", mapOf("token_length" to token.length))
        } catch (e: Exception) {
            Log.w("FCM", "Error handling FCM new token: ${e.message}")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        try {
            super.onMessageReceived(remoteMessage)
            Log.d("FCM", "Message received from: ${remoteMessage.from}")
            FirebaseInitializer.logEvent(
                "fcm_message_received",
                mapOf("from" to (remoteMessage.from ?: "unknown"))
            )
        } catch (e: Exception) {
            Log.w("FCM", "Error handling FCM message: ${e.message}")
        }
    }
}
