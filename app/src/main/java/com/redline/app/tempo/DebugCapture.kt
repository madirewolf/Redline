package com.redline.app.tempo

import android.content.Context
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Debug builds only: keeps the last set's raw audio in the app's cache as 16 kHz mono 16-bit
 * little-endian PCM, and logs each cue and the audio clock, so a misheard set can be replayed
 * through [CueDetector] off the phone. Nothing leaves the device unless it's pulled over adb.
 */
internal class DebugCapture private constructor(private val out: BufferedOutputStream) {
    private val bytes = ByteArray(4096)

    fun write(samples: ShortArray, count: Int) {
        var i = 0
        while (i < count) {
            val n = minOf(count - i, bytes.size / 2)
            for (k in 0 until n) {
                val s = samples[i + k].toInt()
                bytes[2 * k] = (s and 0xff).toByte()
                bytes[2 * k + 1] = (s shr 8).toByte()
            }
            runCatching { out.write(bytes, 0, n * 2) }
            i += n
        }
    }

    fun log(message: String) {
        Log.d(TAG, message)
    }

    fun close() {
        runCatching { out.close() }
    }

    companion object {
        private const val TAG = "VoiceCue"
        const val FILE_NAME = "voice-debug.pcm"

        fun open(context: Context): DebugCapture? = runCatching {
            DebugCapture(BufferedOutputStream(FileOutputStream(File(context.cacheDir, FILE_NAME))))
        }.getOrNull()
    }
}
