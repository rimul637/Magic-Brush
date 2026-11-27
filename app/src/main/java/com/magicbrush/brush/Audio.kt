package com.magicbrush.brush

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.SystemClock

class Audio(private val context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(5)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val sounds = intArrayOf(R.raw.fx_click, R.raw.fx_ink, R.raw.fx_collision, R.raw.fx_step,
        R.raw.fx_portal, R.raw.fx_win, R.raw.fx_fail, R.raw.fx_star, R.raw.fx_finale)
        .map { pool.load(context, it, 1) }
    private val last = LongArray(Cue.entries.size)
    private var track: MediaPlayer? = null
    private var gameTrack = false
    private var foreground = false
    private var music = true
    private var effects = true

    fun update(active: Boolean, enableMusic: Boolean, enableEffects: Boolean, inGame: Boolean) {
        foreground = active
        music = enableMusic
        effects = enableEffects
        if (gameTrack != inGame) {
            gameTrack = inGame
            track?.release()
            track = null
        }
        if (active && music) {
            if (track == null) {
                track = MediaPlayer.create(context, if (inGame) R.raw.music_play else R.raw.music_menu)?.apply {
                    isLooping = true
                    setVolume(.37f, .37f)
                }
            }
            track?.start()
        } else track?.pause()
        if (active && effects) pool.autoResume() else pool.autoPause()
    }

    fun play(cue: Cue) {
        if (!foreground || !effects) return
        val now = SystemClock.elapsedRealtime()
        val i = cue.ordinal
        if (now - last[i] < if (cue == Cue.STEP) 280 else 100) return
        last[i] = now
        val volume = if (cue == Cue.STEP) .2f else .62f
        pool.play(sounds[i], volume, volume, 1, 0, 1f)
    }

    fun release() {
        track?.release()
        pool.release()
    }
}
