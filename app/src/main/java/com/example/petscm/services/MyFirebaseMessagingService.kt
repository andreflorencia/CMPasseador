package com.example.petscm.services

import com.example.petscm.utils.Constants
import com.example.petscm.utils.UserUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (FirebaseAuth.getInstance().currentUser != null) {
            UserUtils.updateToken(this, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        Constants.showNotification(
            this, kotlin.random.Random.nextInt(),
            data[Constants.NOTI_TITLE],
            data[Constants.NOTI_BODY],
            null
        )
    }
}