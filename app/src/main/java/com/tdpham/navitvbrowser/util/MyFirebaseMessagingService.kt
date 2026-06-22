package com.tdpham.navitvbrowser.util

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New Token: $token")
        FirebaseInitializer.logEvent("fcm_token_received", mapOf("token_length" to token.length))
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM", "Message received from: ${remoteMessage.from}")
        FirebaseInitializer.logEvent(
            "fcm_message_received",
            mapOf("from" to (remoteMessage.from ?: "unknown"))
        )
    }
}
