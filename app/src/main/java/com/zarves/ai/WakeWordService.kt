package com.zarves.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class WakeWordService : Service() {
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var cpuLock: PowerManager.WakeLock? = null
    private val channelId = "zarves_wake_word"
    private val commandDoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (active) restartSoon()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val power = getSystemService(POWER_SERVICE) as PowerManager
        cpuLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ZARVES:WakeWord").apply {
            setReferenceCounted(false)
        }
        val filter = IntentFilter(ACTION_COMMAND_DONE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(commandDoneReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(commandDoneReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(41, buildNotification())
        active = true
        if (cpuLock?.isHeld != true) cpuLock?.acquire()
        startRecognition()
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "ZARVES wake word", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, channelId) else Notification.Builder(this)
        return builder.setContentTitle("ZARVES AI active")
            .setContentText("Wake word sun raha hai. Band karne ke liye app kholein.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun startRecognition() {
        if (!active) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            handler.postDelayed({ startRecognition() }, 5000)
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { restartSoon() }
            override fun onResults(results: Bundle?) {
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (heard.contains("zarves", ignoreCase = true) || heard.contains("जार्विस")) {
                    // Pause wake-word recognition while the user speaks the actual command.
                    recognizer?.cancel()
                    sendBroadcast(Intent(ACTION_WAKE).setPackage(packageName).putExtra("heard", heard))
                } else {
                    restartSoon()
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        try {
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            restartSoon()
        }
    }

    private fun restartSoon() {
        if (active) handler.postDelayed({ startRecognition() }, 1200)
    }

    override fun onDestroy() {
        active = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        try { unregisterReceiver(commandDoneReceiver) } catch (_: Exception) {}
        if (cpuLock?.isHeld == true) cpuLock?.release()
        cpuLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_WAKE = "com.zarves.ai.ACTION_WAKE"
        const val ACTION_COMMAND_DONE = "com.zarves.ai.ACTION_COMMAND_DONE"
    }
}