package com.devjugal.airscan.logging

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.devjugal.airscan.MainActivity
import com.devjugal.airscan.R
import com.devjugal.airscan.permissions.readGrants
import com.devjugal.airscan.radio.Radios
import com.devjugal.airscan.radio.cellular.CellularState
import com.devjugal.airscan.radio.wifi.WifiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Records signal once a second to a CSV and a GPX file. It runs as a foreground service of type location,
 * started from the visible screen. Everything stops when the service stops.
 */
@SuppressLint("MissingPermission")
class RecorderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var listener: LocationListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            finish()
            return START_NOT_STICKY
        }
        // Go to the foreground first. Android gives only a few seconds for this.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        if (job == null) record()
        return START_NOT_STICKY
    }

    private fun record() {
        val grants = readGrants(this)
        if (!grants.fineLocation) {
            finish()
            return
        }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val dir = LogFiles.dir(this)
        val csv = File(dir, "airscan-$stamp.csv").apply { writeText(CSV_HEADER + "\n") }
        val gpx = GpxFile(File(dir, "airscan-$stamp.gpx"), "Airscan $stamp")
        SessionStore.start(csv.name)

        var wifi: WifiState? = null
        var cellular: CellularState? = null
        var fix: Location? = null
        listener =
            LocationListener { fix = it }.also {
                try {
                    getSystemService(LocationManager::class.java)
                        .requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, it, Looper.getMainLooper())
                } catch (_: SecurityException) {
                    // Without a fix the CSV still records signal. The GPX track stays empty.
                } catch (_: IllegalArgumentException) {
                    // No GPS provider on this device.
                }
            }
        job =
            scope.launch {
                launch { Radios.wifi(this@RecorderService).states(grants).collect { wifi = it } }
                launch { Radios.cellular(this@RecorderService).states(grants).collect { cellular = it } }
                var count = 0
                while (true) {
                    delay(INTERVAL_MS)
                    val place =
                        fix?.let {
                            LocationFix(
                                it.latitude,
                                it.longitude,
                                it.altitude.takeIf { _ ->
                                    it.hasAltitude()
                                },
                                it.accuracy.takeIf { _ -> it.hasAccuracy() },
                            )
                        }
                    val sample = sampleOf(System.currentTimeMillis(), wifi?.connection, cellular, place)
                    csvRows(sample).forEach { csv.appendText(it + "\n") }
                    gpxPoint(sample)?.let { gpx.append(it) }
                    SessionStore.add(sample)
                    count++
                    if (count % NOTIFICATION_EVERY == 0) notificationManager().notify(NOTIFICATION_ID, notification(count))
                }
            }
    }

    private fun finish() {
        job?.cancel()
        job = null
        listener?.let { getSystemService(LocationManager::class.java).removeUpdates(it) }
        listener = null
        SessionStore.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (job != null) finish()
        scope.cancel()
        super.onDestroy()
    }

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    private fun notification(samples: Int): Notification {
        notificationManager().createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.recording_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop =
            PendingIntent.getService(
                this,
                1,
                Intent(this, RecorderService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.recording_title))
            .setContentText(getString(R.string.recording_text, samples))
            .setContentIntent(open)
            .addAction(0, getString(R.string.recording_stop), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.devjugal.airscan.STOP_RECORDING"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1
        private const val INTERVAL_MS = 1_000L
        private const val NOTIFICATION_EVERY = 10

        fun start(context: Context) {
            androidx.core.content.ContextCompat
                .startForegroundService(context, Intent(context, RecorderService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RecorderService::class.java).setAction(ACTION_STOP))
        }
    }
}
