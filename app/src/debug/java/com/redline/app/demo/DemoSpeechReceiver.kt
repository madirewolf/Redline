package com.redline.app.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.redline.app.voice.SpeechRecognizerManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Debug builds only. The emulator has no microphone input, so this lets adb
 * speak for it when recording a demo:
 *
 *     adb shell am broadcast -n com.redline.app/.demo.DemoSpeechReceiver \
 *         -a com.redline.app.DEMO_SPEECH --es text "barbell row 185 for 8 RPE 8"
 *
 * That plays the transcript right away. Add `--ez arm true` to hold it until
 * the next tap on a mic button instead, so a recording shows the tap and
 * Android never lights its microphone-in-use indicator.
 *
 * The transcript goes through SpeechRecognizerManager.simulate, so the
 * screen, the parser and the database see what a real recognition produces.
 */
class DemoSpeechReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun speechManager(): SpeechRecognizerManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("text")?.takeIf { it.isNotBlank() } ?: return
        val speech = EntryPointAccessors.fromApplication(context, Deps::class.java).speechManager()
        if (intent.getBooleanExtra("arm", false)) speech.armSimulation(text) else speech.simulate(text)
    }
}
