package com.newspecs.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.newspecs.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Foreground service that handles the network fetch + widget update.
 *
 * Why a ForegroundService instead of goAsync()?
 * goAsync() has a hard 10-second kill on Android 12+.  A network request can
 * take 3–8 s under normal conditions, and any hiccup (slow DNS, cell radio
 * wake-up) pushes it over the limit — the OS kills the receiver before the
 * RemoteViewsFactory is told to reload, so the widget stays blank forever.
 *
 * A started Service has NO time limit.  We start it from onReceive() (fast,
 * never blocks), immediately call startForeground() with a silent notification,
 * fetch news in a coroutine, push the widget, then stop ourselves.
 */
class NewsRefreshService : Service() {

    private val job   = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        val ctx = applicationContext
        scope.launch {
            try {
                NewsWidget.triggerUpdate(ctx)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun startForegroundCompat() {
        val channelId = "newspecs_refresh"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        "News refresh",
                        NotificationManager.IMPORTANCE_MIN
                    ).apply {
                        setShowBadge(false)
                        enableLights(false)
                        enableVibration(false)
                    }
                )
            }
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_reload)
            .setContentTitle("newspecs")
            .setContentText("Refreshing news…")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()

        // API 29+ requires the foreground service type when declared in the manifest
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    companion object {
        private const val NOTIF_ID = 7001
    }
}
