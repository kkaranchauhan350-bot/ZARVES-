package com.zarves.ai

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingManager
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Local speaker embedding enrollment and verification.
 * The model and enrolled embedding stay in app-private storage.
 */
class SpeakerVerifier(private val context: Context) {
    companion object {
        private const val PREFS = "zarves_voice_profile"
        private const val KEY_EMBEDDING = "owner_embedding"
        private const val SAMPLE_RATE = 16000
        private const val THRESHOLD = 0.60f
        private const val MODEL_NAME = "speaker.onnx"
        private const val MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx"
    }

    @Synchronized
    private fun modelPath(): String {
        val target = File(context.filesDir, MODEL_NAME)
        if (target.exists() && target.length() > 1_000_000L) return target.absolutePath

        // The CI build packages the official model in assets; copy it locally on first use.
        try {
            context.assets.open(MODEL_NAME).use { input ->
                target.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            if (target.length() > 1_000_000L) return target.absolutePath
            target.delete()
        } catch (_: java.io.IOException) {
            // Older builds may not contain the model; download it below.
        }

        val temp = File(context.filesDir, "$MODEL_NAME.part")
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 60000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Voice model download failed: HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                temp.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            if (temp.length() < 1_000_000L) {
                temp.delete()
                throw IllegalStateException("Voice model adhura download hua. Internet check karein.")
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            return target.absolutePath
        } finally {
            connection.disconnect()
        }
    }

    private fun record(seconds: Int): FloatArray {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        require(minBuffer > 0) { "Microphone is not available" }
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, SAMPLE_RATE * 2)
        )
        require(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone could not start" }
        val samples = ShortArray(SAMPLE_RATE * seconds)
        var offset = 0
        try {
            recorder.startRecording()
            while (offset < samples.size) {
                val count = recorder.read(samples, offset, samples.size - offset)
                if (count < 0) throw IllegalStateException("Microphone recording failed")
                if (count == 0) continue
                offset += count
            }
        } finally {
            try { recorder.stop() } catch (_: Exception) {}
            recorder.release()
        }
        return FloatArray(samples.size) { samples[it] / 32768.0f }
    }

    private fun embedding(audio: FloatArray): FloatArray {
        val extractor = SpeakerEmbeddingExtractor(
            config = SpeakerEmbeddingExtractorConfig(
                model = modelPath(), numThreads = 2, debug = false, provider = "cpu"
            )
        )
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
        require(vector.isNotEmpty()) { "Awaaz ka sample nahi mila. Dobara koshish karein." }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_EMBEDDING, vector.joinToString(",")).apply()
        return true
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
