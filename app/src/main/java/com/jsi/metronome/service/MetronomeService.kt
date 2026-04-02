package com.jsi.metronome.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.jsi.metronome.MainActivity
import com.jsi.metronome.R
import kotlin.math.sin

class MetronomeService : Service() {

    companion object {
        const val CHANNEL_ID = "metronome_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.jsi.metronome.STOP"
        private const val SAMPLE_RATE = 44100
    }

    inner class LocalBinder : Binder() {
        val service: MetronomeService get() = this@MetronomeService
    }

    private val binder = LocalBinder()
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var isPlaying = false
    private var bpm: Int = 120
    private var subdivision: Int = 1
    private var clickSamples: ShortArray? = null
    private var subClickSamples: ShortArray? = null
    private var audioTrack: AudioTrack? = null
    private var subAudioTrack: AudioTrack? = null

    // Drift-free timing using monotonic clock
    private var epochUptime: Long = 0L
    private var subTickCount: Long = 0L

    /** Called on the tick thread each time a main beat plays. */
    var onTick: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        generateClickSamples()
        generateSubClickSamples()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMetronome()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopMetronome()
        audioTrack?.release()
        audioTrack = null
        subAudioTrack?.release()
        subAudioTrack = null
        super.onDestroy()
    }

    fun startMetronome(bpm: Int, subdivision: Int = 1) {
        this.bpm = bpm
        this.subdivision = subdivision.coerceIn(1, 9)
        if (isPlaying) {
            return
        }
        isPlaying = true
        startForeground(NOTIFICATION_ID, buildNotification())

        handlerThread = HandlerThread("MetronomeTick").apply { start() }
        handler = Handler(handlerThread!!.looper)

        epochUptime = SystemClock.uptimeMillis()
        subTickCount = 0L
        // Schedule first tick at epoch (immediate)
        handler?.postAtTime({ tick() }, epochUptime)
    }

    fun stopMetronome() {
        isPlaying = false
        handler?.removeCallbacksAndMessages(null)
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
    }

    fun updateBpm(newBpm: Int) {
        val old = this.bpm
        this.bpm = newBpm.coerceIn(30, 250)
        if (isPlaying && old != this.bpm) {
            handler?.removeCallbacksAndMessages(null)
            epochUptime = SystemClock.uptimeMillis()
            subTickCount = 0L
            handler?.postAtTime({ tick() }, epochUptime)
        }
        updateNotification()
    }

    fun updateSubdivision(newSubdivision: Int) {
        val old = this.subdivision
        this.subdivision = newSubdivision.coerceIn(1, 9)
        if (isPlaying && old != this.subdivision) {
            handler?.removeCallbacksAndMessages(null)
            epochUptime = SystemClock.uptimeMillis()
            subTickCount = 0L
            handler?.postAtTime({ tick() }, epochUptime)
        }
    }

    fun getIsPlaying(): Boolean = isPlaying

    private fun tick() {
        if (!isPlaying) return

        val isMainBeat = subTickCount % subdivision == 0L
        if (isMainBeat) {
            playClick(audioTrack, clickSamples)
            onTick?.invoke()
        } else {
            playClick(subAudioTrack, subClickSamples)
        }
        subTickCount++
        val subIntervalMs = 60_000.0 / (bpm * subdivision)
        val nextTickUptime = epochUptime + (subTickCount * subIntervalMs).toLong()
        handler?.postAtTime({ tick() }, nextTickUptime)
    }

    private fun playClick(track: AudioTrack?, samples: ShortArray?) {
        samples ?: return
        track ?: return
        try {
            track.apply {
                stop()
                reloadStaticData()
                play()
            }
        } catch (_: Exception) {
            // Recover on next tick
        }
    }

    private fun buildAudioTrack(samples: ShortArray): AudioTrack {
        val bufSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufSize.coerceAtLeast(samples.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size)
        return track
    }

    private fun generateClickSamples() {
        val durationMs = 30
        val numSamples = SAMPLE_RATE * durationMs / 1000
        val samples = ShortArray(numSamples)
        val frequency = 1000.0
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = 1.0 - (i.toDouble() / numSamples)
            val sample = (sin(2.0 * Math.PI * frequency * t) * envelope * Short.MAX_VALUE).toInt()
            samples[i] = sample.toShort()
        }
        clickSamples = samples
        audioTrack = buildAudioTrack(samples)
    }

    private fun generateSubClickSamples() {
        val durationMs = 20
        val numSamples = SAMPLE_RATE * durationMs / 1000
        val samples = ShortArray(numSamples)
        val frequency = 1500.0
        val volume = 0.35
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = 1.0 - (i.toDouble() / numSamples)
            val sample = (sin(2.0 * Math.PI * frequency * t) * envelope * volume * Short.MAX_VALUE).toInt()
            samples[i] = sample.toShort()
        }
        subClickSamples = samples
        subAudioTrack = buildAudioTrack(samples)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Metronome",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Metronome playback"
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val stopIntent = Intent(this, MetronomeService::class.java).apply {
            action = ACTION_STOP
        }.let {
            PendingIntent.getService(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Metronome")
            .setContentText("$bpm BPM")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        if (isPlaying) {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification())
        }
    }
}
