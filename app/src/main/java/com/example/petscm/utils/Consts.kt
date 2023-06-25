package com.example.petscm.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.petscm.R
import com.example.petscm.models.DriverInfoModel
import com.google.android.gms.maps.model.Marker

object Constants {

    fun buildWelcomeMessage(): String {
        return StringBuilder("Welcome, ")
            .append(currentUser?.firstName)
            .append(" ")
            .append(currentUser?.lastName)
            .toString()
    }

    fun showNotification(
        context: Context,
        id: Int,
        title: String?,
        body: String?,
        intent: Intent?
    ) {
        var pendingIntent: PendingIntent? = null
        if (intent != null)
            pendingIntent =
                PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val NOTIFICATION_CHANNEL_ID = "com.example.uberclone"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID, "Uber Clone",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationChannel.description = "Uber Clone"
            notificationChannel.enableLights(true)
            notificationChannel.lightColor = Color.RED
            notificationChannel.vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            notificationChannel.enableVibration(true)

            notificationManager.createNotificationChannel(notificationChannel)
        }


        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        builder.setContentTitle(title)

        builder.setAutoCancel(false)
        builder.setContentText(body)
        builder.setPriority(NotificationCompat.PRIORITY_HIGH)
        builder.setDefaults(android.app.Notification.DEFAULT_VIBRATE)
        builder.setSmallIcon(R.drawable.baseline_person_24)
        builder.setLargeIcon(
            BitmapFactory.decodeResource(
                context.resources,
                R.drawable.baseline_person_24
            )
        )

        if (pendingIntent != null)
            builder.setContentIntent(pendingIntent)
        val notification = builder.build()
        notificationManager.notify(id, notification)


    }

    fun buildName(firstName: String, lastName: String): String {
        return StringBuilder(firstName).append(" ").append(lastName).toString()
    }

    val markerList: MutableMap<String, Marker> = HashMap()
    val DRIVER_INFO_REFERNCE: String = "DriverInfo"
    val DRIVER_LOCATION_REFERENCE: String = "Driverlocation"
    val NOTI_BODY: String = "body"
    val NOTI_TITLE = "title"
    val TOKEN_REFERENCE = "Token"
    var currentUser: DriverInfoModel? = null

    const val RIDER_INFO_REFERENCE = "DriverInfo"
    const val RIDERS_LOCATION_REFERENCE = "DriversLocation"
}