package com.glorton.android.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.glorton.engine.SoundSink
import kotlin.random.Random

/**
 * Android implementation of the engine's SoundSink. Mirrors the original
 * Snd class (Snd.as) semantics:
 *
 *  - A logical name can map to several variants registered with AddSound;
 *    PlaySound picks one at random (e.g. "Punch" -> punch1/punch2/slap).
 *  - Looping is supported for stage music and the menu theme.
 *
 * Source assets live in `assets/sounds/` with the logical names from the
 * Snd.AddSound table of frame_42. The two sounds that JPEXS exported as
 * Flash-ADPCM FLV containers (Fart.wav, PeachRocket.wav) could not be
 * converted here; "Fart" falls back to Fart2 and "Rocket" is still missing
 * (it is only used by PeachPlayer, not yet ported).
 */
class AndroidSoundSink(context: Context) : SoundSink {

    private val appContext = context.applicationContext

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    /** Logical name -> loaded variant sound ids (Snd.Sounds arrays). */
    private val soundIds = mutableMapOf<String, MutableList<Int>>()

    /** Logical name -> looping MediaPlayer for music-length tracks. */
    private val musicPlayers = mutableMapOf<String, MediaPlayer>()

    private var currentMusicName: String? = null

    /** Loads every asset eagerly, like Snd.AddSound at boot (frame_42). */
    fun preload() {
        // Long tracks go through MediaPlayer; the rest through SoundPool.
        // Keys are the AS2 logical names (Snd.AddSound second argument).
        registerMusic("Rooftop", "sounds/rooftop.mp3")
        registerMusic("Mogadishu", "sounds/mogadishu.mp3")
        registerMusic("B52", "sounds/b52.mp3")
        registerMusic("Space", "sounds/space.mp3")
        registerMusic("MenuMusic", "sounds/menumusic.mp3")
        registerMusic("Star", "sounds/star.mp3")

        registerVariant("Punch", "sounds/punch1.wav")
        registerVariant("Punch", "sounds/punch2.wav")
        registerVariant("Punch", "sounds/slap.wav")
        registerVariant("Boom", "sounds/boom.mp3")
        registerVariant("Boom", "sounds/boom2.mp3")
        registerVariant("Fart", "sounds/fart2.mp3")
        registerVariant("Electric", "sounds/electric.mp3")
        registerVariant("Helicopter", "sounds/helicopter.mp3")
        registerVariant("HitGround", "sounds/hitground.mp3")
        registerVariant("JetEngine", "sounds/jetengine.mp3")
        registerVariant("Gun", "sounds/gun.mp3")
        registerVariant("Thrown", "sounds/thrown.mp3")
        registerVariant("Thunder", "sounds/thunder.mp3")
        registerVariant("WaterSplash", "sounds/watersplash.mp3")
        registerVariant("Headshot", "sounds/headshot.mp3")
        registerVariant("Woosh", "sounds/woosh.mp3")
        registerVariant("MineActivate", "sounds/mineactivate.mp3")
        registerVariant("Kamehameha", "sounds/kamehameha.mp3")
    }

    private fun registerVariant(logicalName: String, assetPath: String) {
        runCatching {
            val descriptor = appContext.assets.openFd(assetPath)
            val id = soundPool.load(descriptor, 1)
            descriptor.close()
            soundIds.getOrPut(logicalName) { mutableListOf() }.add(id)
        }
    }

    private fun registerMusic(logicalName: String, assetPath: String) {
        runCatching {
            val descriptor = appContext.assets.openFd(assetPath)
            val player = MediaPlayer()
            player.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            descriptor.close()
            player.isLooping = true
            player.prepare()
            musicPlayers[logicalName] = player
        }
    }

    override fun play(name: String, loop: Boolean) {
        val music = musicPlayers[name]
        if (music != null) {
            if (currentMusicName != name) {
                currentMusicName?.let { musicPlayers[it]?.pause() }
                musicPlayers[name]?.let {
                    it.seekTo(0)
                    it.start()
                }
                currentMusicName = name
            } else if (!music.isPlaying) {
                music.start()
            }
            return
        }

        val variants = soundIds[name]
        if (variants.isNullOrEmpty()) return
        val soundId = variants[Random.nextInt(variants.size)]
        soundPool.play(soundId, 1f, 1f, 1, if (loop) -1 else 0, 1f)
    }

    override fun stop(name: String) {
        musicPlayers[name]?.let {
            if (it.isPlaying) it.pause()
        }
        if (currentMusicName == name) currentMusicName = null
    }

    override fun stopAll() {
        musicPlayers.values.forEach { if (it.isPlaying) it.pause() }
        currentMusicName = null
        soundPool.autoPause()
    }

    /** Releases the SoundPool and every MediaPlayer; call from the ViewModel. */
    fun release() {
        soundPool.release()
        musicPlayers.values.forEach { it.release() }
        musicPlayers.clear()
    }
}
