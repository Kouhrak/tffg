package com.glorton.engine

/** Sink for the original Snd class; the app layer decides what audio to play. */
interface SoundSink {
    fun play(name: String, loop: Boolean = false)
    fun stop(name: String)
    fun stopAll()
}

object NoopSoundSink : SoundSink {
    override fun play(name: String, loop: Boolean) = Unit
    override fun stop(name: String) = Unit
    override fun stopAll() = Unit
}
