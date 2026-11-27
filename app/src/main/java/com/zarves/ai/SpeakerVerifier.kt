package com.zarves.ai

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingManager

/**
 * Local speaker embedding enrollment and verification.
 * The enrolled embedding is stored only in app-private preferences.
 */
class SpeakerVerifier(private val context: Context) {
    companion object {
        private const val PREFS = "zarves_voice_profile"
        private const val KEY_EMBEDDING = "owner_embedding"
        private const val SAMPLE_RATE = 16000
        private const val THRESHOLD = 0.60f
    }

    private fun modelPath(): String {
        val target = java.io.File(context.filesDir, "speaker.onnx")
        if (!target.exists()) {
            context.assets.open("speaker.onnx").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return target.absolutePath
    }

    private fun record(seconds: Int): FloatArray {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        require(minBuffer > 0) { "Microphone is not available" }
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, SAMPLE_RATE * 2)
        )
        require(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone could not start" }
        val samples = ShortArray(SAMPLE_RATE * seconds)
        var offset = 0
        try {
            recorder.startRecording()
            while (offset < samples.size) {
                val count = recorder.read(samples, offset, samples.size - offset)
                if (count < 0) throw IllegalStateException("Microphone recording failed")
                offset += count
            }
        } finally {
            try { recorder.stop() } catch (_: Exception) {}
            recorder.release()
        }
        return FloatArray(samples.size) { samples[it] / 32768.0f }
    }

    private fun embedding(audio: FloatArray): FloatArray {
        val config = SpeakerEmbeddingExtractorConfig.builder()
            .setModel(modelPath())
            .setNumThreads(2)
            .setDebug(false)
            .build()
        val extractor = SpeakerEmbeddingExtractor(config)
        try {
            val stream = extractor.createStream()
            try {
                stream.acceptWaveform(audio, SAMPLE_RATE)
                stream.inputFinished()
                return extractor.compute(stream)
            } finally {
                stream.release()
            }
        } finally {
            extractor.release()
        }
    }

    fun enroll(): Boolean {
        val vector = embedding(record(6))
        val encoded = vector.joinToString(",")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_EMBEDDING, encoded).apply()
        return vector.isNotEmpty()
    }

    fun hasEnrollment(): Boolean =
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_EMBEDDING, null).isNullOrBlank()

    fun verify(): Boolean {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_EMBEDDING, null) ?: return false
        val enrolled = saved.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
        if (enrolled.isEmpty()) return false
        val test = embedding(record(4))
        val manager = SpeakerEmbeddingManager(enrolled.size)
        try {
            if (!manager.add("owner", arrayOf(enrolled))) return false
            return manager.verify("owner", test, THRESHOLD)
        } finally {
            manager.release()
        }
    }
}
