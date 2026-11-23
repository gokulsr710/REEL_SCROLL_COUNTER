package com.reelcount

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** "Challenge results" notification posted just after 11:59 PM. */
object Notify {
    private const val CHANNEL = "challenge_results"

    fun results(c: Context, st: ChallengeState, myUid: String?) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Challenge results", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Results of your friends' challenge at 11:59 PM" }
        )
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, ChallengeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val body = Challenge.resultText(st, myUid)
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("Challenge results are in")
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { nm.notify(1001, n) } catch (_: SecurityException) { /* notifications not allowed */ }
    }
}
