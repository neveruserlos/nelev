package com.nelev.dev.media

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

object SoundPlayer {
    private var player: MediaPlayer? = null
    fun play(ctx: Context) {
        try {
            player?.release(); player = null
            val id = ctx.resources.getIdentifier("sound", "raw", ctx.packageName); if (id == 0) return
            player = MediaPlayer.create(ctx, id)?.also { it.setOnCompletionListener { mp -> mp.release(); player = null }; it.start() }
        } catch (_: Exception) {}
    }
    fun release() { try { player?.release() } catch (_: Exception) {}; player = null }
}

object ClickPlayer {
    private var pool: SoundPool? = null
    private var soundId: Int = 0
    private var loaded = false
    fun init(ctx: Context) {
        if (pool != null) return
        pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).build()
        val id = ctx.resources.getIdentifier("click", "raw", ctx.packageName)
        if (id != 0) { pool!!.setOnLoadCompleteListener { _, _, status -> if (status == 0) loaded = true }; soundId = pool!!.load(ctx, id, 1) }
    }
    fun play(ctx: Context) { if (pool == null) init(ctx); if (loaded && soundId != 0) pool!!.play(soundId, 1f, 1f, 1, 0, 1f) }
    fun release() { pool?.release(); pool = null; loaded = false; soundId = 0 }
}
