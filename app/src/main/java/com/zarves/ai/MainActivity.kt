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
            override fun onError(e: Int) {
                info.text = "Command nahi suna. Dobara ZARVES boliye."
                sendBroadcast(Intent(WakeWordService.ACTION_COMMAND_DONE).setPackage(packageName))
            }
            override fun onResults(b: Bundle?) {
                val q = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                runCommand(q)
                sendBroadcast(Intent(WakeWordService.ACTION_COMMAND_DONE).setPackage(packageName))
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
        fun openPackage(pkg: String, label: String): Boolean {
            val launch = packageManager.getLaunchIntentForPackage(pkg) ?: return false
            startActivity(launch)
            reply("$label khol raha hoon.")
            return true
        }
        when {
            "youtube" in q -> { if (!openPackage("com.google.android.youtube", "YouTube")) reply("YouTube install nahi hai."); return }
            "whatsapp" in q -> { if (!openPackage("com.whatsapp", "WhatsApp")) reply("WhatsApp install nahi hai."); return }
            "chrome" in q || "browser" in q || "ब्राउज़र" in q -> { if (!openPackage("com.android.chrome", "Chrome")) startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"))); return }
            "maps" in q || "map" in q || "मैप" in q -> { if (!openPackage("com.google.android.apps.maps", "Google Maps")) reply("Maps app nahi mili."); return }
            "camera" in q || "कैमरा" in q -> { if (!openPackage("com.motorola.camera3", "Camera")) { try { startActivity(Intent("android.media.action.IMAGE_CAPTURE")) } catch (_: Exception) { reply("Camera nahi khul paya.") } }; return }
            "phone" in q || "call" in q || "फोन" in q || "कॉल" in q -> { startActivity(Intent(Intent.ACTION_DIAL)); reply("Phone dialer khol raha hoon. Number aap khud confirm karein."); return }
            "message" in q || "sms" in q || "मैसेज" in q -> { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)); reply("Messages khol raha hoon."); return }
            "contact" in q || "कॉन्टैक्ट" in q -> { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("content://contacts/people/"))); reply("Contacts khol raha hoon."); return }
            "alarm" in q || "अलार्म" in q -> { try { startActivity(Intent("android.intent.action.SHOW_ALARMS")) } catch (_: Exception) { reply("Alarm app nahi khul payi.") }; return }
            "calendar" in q || "कैलेंडर" in q -> { try { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)) } catch (_: Exception) { reply("Calendar app nahi khul payi.") }; return }
        }
        when {
            "setting" in q || "सेटिंग" in q -> {
                startActivity(Intent(Settings.ACTION_SETTINGS)); reply("Settings khol raha hoon.")
            }
            "wifi" in q || "वाईफाई" in q -> {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)); reply("Wi-Fi settings khol raha hoon.")
            }
            "bluetooth" in q || "ब्लूटूथ" in q -> {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); reply("Bluetooth settings khol raha hoon.")
            }
            "time" in q || "समय" in q -> reply("Abhi phone ka time ${java.text.SimpleDateFormat("hh:mm a", Locale.getDefault()).format(java.util.Date())} hai.")
            "help" in q || "मदद" in q || "क्या कर सकते" in q -> reply("Main YouTube, WhatsApp, Chrome, Maps, Camera, Phone dialer, Messages, Contacts, Alarm, Calendar, Wi-Fi, Bluetooth aur Settings khol sakta hoon. Samay bhi bata sakta hoon.")
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