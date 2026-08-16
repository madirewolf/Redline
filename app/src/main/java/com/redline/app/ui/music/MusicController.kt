package com.redline.app.ui.music

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.redline.app.data.local.entity.PrTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun play(track: PrTrack): Boolean {
        val uri = runCatching { Uri.parse(track.trackUri) }.getOrNull() ?: return false
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
