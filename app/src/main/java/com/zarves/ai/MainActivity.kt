package com.zarves.ai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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

    private fun startWakeService() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 7)
            info.text = "Microphone permission dein, phir Wake word ON dabayein."
            return
        }
        val intent = Intent(this, WakeWordService::class.java)
        try {
            startForegroundService(intent)
            info.text = "Wake word chalu. 'ZARVES' bolkar bulayein. Internet zaroori ho sakta hai."
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
                val a = if (q.isBlank()) "Kuch sunai nahi diya" else "Aapne kaha: $q. Main ZARVES AI starter hoon."
                info.text = a
                speech.speak(a, TextToSpeech.QUEUE_FLUSH, null, "reply")
            }
            override fun onPartialResults(b: Bundle?) {}
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        })
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