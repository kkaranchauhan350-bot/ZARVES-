package com.zarves.ai

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var info: TextView
    private lateinit var speech: TextToSpeech
    private var recognizer: SpeechRecognizer? = null
    private var wakeReceiverRegistered = false

    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            info.text = "ZARVES sun raha hai. Ab apna command boliye."
            listenOnce()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speech = TextToSpeech(this, this)
        val box = LinearLayout(this).apply {
            orientation = 1
            gravity = Gravity.CENTER
            setPadding(30, 40, 30, 40)
        }
        box.addView(TextView(this).apply {
            text = "ZARVES AI"
            textSize = 30f
            gravity = Gravity.CENTER
        })
        info = TextView(this).apply {
            text = "Namaste! Baat karne ke liye button dabayein."
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 30)
        }
        box.addView(info)
        box.addView(Button(this).apply {
            text = "Baat karein"
            setOnClickListener { listenOnce() }
        })
        box.addView(Button(this).apply {
            text = "Wake word ON (ZARVES)"
            setOnClickListener { startWakeService() }
        })
        box.addView(Button(this).apply {
            text = "Wake word OFF"
            setOnClickListener {
                stopService(Intent(this@MainActivity, WakeWordService::class.java))
                info.text = "Wake word band kar diya."
            }
        })
        setContentView(box)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 7)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!wakeReceiverRegistered) {
            val filter = IntentFilter(WakeWordService.ACTION_WAKE)
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(wakeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else @Suppress("DEPRECATION") registerReceiver(wakeReceiver, filter)
            wakeReceiverRegistered = true
        }
    }

    override fun onPause() {
        if (wakeReceiverRegistered) {
            unregisterReceiver(wakeReceiver)
            wakeReceiverRegistered = false
        }
        super.onPause()
    }

    private fun startWakeService() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 7)
            info.text = "Microphone permission dein, phir Wake word ON dabayein."
            return
        }
        try {
            startForegroundService(Intent(this, WakeWordService::class.java))
            info.text = "Wake word chalu. ZARVES bolkar bulayein. Notification dikhega."
        } catch (e: Exception) {
            info.text = "Wake word start nahi hua: ${e.message}"
        }
    }

    private fun listenOnce() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            info.text = "Speech recognition nahi mil raha."
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { info.text = "Suniye... boliye" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(e: Int) { info.text = "Dobara koshish karein" }
            override fun onResults(b: Bundle?) {
                val q = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                runCommand(q)
            }
            override fun onPartialResults(b: Bundle?) {}
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        })
    }

    private fun runCommand(raw: String) {
        val q = raw.lowercase(Locale.ROOT)
        val packageName = when {
            "youtube" in q -> "com.google.android.youtube"
            "whatsapp" in q -> "com.whatsapp"
            "chrome" in q || "browser" in q || "ब्राउज़र" in q -> "com.android.chrome"
            "maps" in q || "map" in q || "मैप" in q -> "com.google.android.apps.maps"
            "camera" in q || "कैमरा" in q -> "com.motorola.camera3"
            else -> null
        }
        if (packageName != null) {
            val launch = packageManager.getLaunchIntentForPackage(packageName)
            if (launch != null) {
                startActivity(launch)
                reply("Theek hai, app khol raha hoon.")
            } else reply("Yeh app phone mein install nahi hai.")
            return
        }
        when {
            "setting" in q || "सेटिंग" in q -> {
                startActivity(Intent(Settings.ACTION_SETTINGS))
                reply("Settings khol raha hoon.")
            }
            "wifi" in q || "वाईफाई" in q -> {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                reply("Wi-Fi settings khol raha hoon.")
            }
            "bluetooth" in q || "ब्लूटूथ" in q -> {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                reply("Bluetooth settings khol raha hoon.")
            }
            "time" in q || "समय" in q -> reply("Abhi phone ka time ${java.text.SimpleDateFormat("hh:mm a", Locale.getDefault()).format(java.util.Date())} hai.")
            else -> reply("Aapne kaha: $raw. Is command ko abhi nahi pehchana.")
        }
    }

    private fun reply(message: String) {
        info.text = message
        speech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "zarves_reply")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) speech.language = Locale("hi", "IN")
    }

    override fun onDestroy() {
        recognizer?.destroy()
        speech.shutdown()
        super.onDestroy()
    }
}