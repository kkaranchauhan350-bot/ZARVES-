package com.zarves.assistant

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var status: TextView
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private val requestAudio = 41

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        status = TextView(this).apply { text = "नमस्ते! मैं ZARVES हूँ। बोलने के लिए बटन दबाएँ।"; textSize = 20f; gravity = Gravity.CENTER }
        val button = Button(this).apply { text = "बोलें / Speak"; setOnClickListener { startListening() } }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(32, 32, 32, 32); addView(status); addView(button) }
        setContentView(layout)
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), requestAudio)
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { status.text = "इस फोन पर speech recognition उपलब्ध नहीं है।"; return }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { status.text = "सुन रहा हूँ…" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { status.text = "समझ रहा हूँ…" }
                override fun onError(error: Int) { status.text = "आवाज़ समझ नहीं आई। फिर कोशिश करें।" }
                override fun onResults(results: Bundle?) {
                    val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    status.text = if (words.isBlank()) "कुछ सुनाई नहीं दिया।" else "आपने कहा: $words\n\n${replyFor(words)}"
                    speak(status.text.toString().substringAfter("\n\n", "नमस्ते"))
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            }
            sr.startListening(intent)
        }
    }

    private fun replyFor(text: String): String {
        val q = text.lowercase(Locale.ROOT)
        return when {
            "नमस्ते" in q || "हेलो" in q || "hello" in q -> "नमस्ते! बताइए, मैं आपकी क्या मदद करूँ?"
            "समय" in q || "टाइम" in q -> "समय देखने के लिए फोन की घड़ी खोलें।"
            else -> "मैंने आपकी बात सुनी। अभी मैं शुरुआती संस्करण हूँ; मेरे फोन-कंट्रोल फीचर अभी तैयार नहीं हैं।"
        }
    }

    private fun speak(message: String) { tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "zarves_reply") }
    override fun onInit(statusCode: Int) { if (statusCode == TextToSpeech.SUCCESS) tts?.language = Locale("hi", "IN") }
    override fun onDestroy() { recognizer?.destroy(); tts?.shutdown(); super.onDestroy() }
}
