package com.glorton.engine.effects

import com.glorton.engine.GameConfig
import com.glorton.engine.SoundSink
import com.glorton.engine.fighter.Fighter
import com.glorton.engine.world.World

/**
 * Port of class Explosion (Explosion.as): a radial blast created by Rocket
 * (and later Mine/Grenade). Every fighter inside the radius takes damage and
 * throw exactly once, tracked by [influenced].
 *
 * Visuals of the original (BoomMatter particles, BoomWave ring, BoomStar
 * flash) are collapsed into one effect with the timeline durations read from
 * the exported sprites:
 *
 *  - Damage window: `BoomMatter1._currentframe < _totalframes` — the sprite
 *    has 8 frames; reaching the last frame takes 7 transitions at the SWF
 *    rate, so damage applies while [ageMs] < [BOOM_DAMAGE_MS].
 *  - Life: `BoomWave._currentframe == _totalframes` — 21 frames, 20
 *    transitions at 30fps, then the MovieClip is removed ([BOOM_LIFE_MS]).
 *
 * The AS2 constructor also calls `VCam.Shake(-500)` (a shake that starts
 * already half-decayed); the port applies it on the first tick, when the
 * engine clock is available.
 */
class Explosion(
    val worldObj: World,
    val sender: Fighter?,
    val x: Float,
    val y: Float,
    val size: Float,
    private val sound: SoundSink,
) {
    /** AS2: `SquareSize = Size * 10 * (Size * 10) + 400` (squared radius). */
    val squareSize: Float = size * 10f * (size * 10f) + 400f

    /** AS2 `Influenced` array: fighters already caught by this blast. */
    private val influenced = mutableSetOf<Fighter>()

    var ageMs: Float = 0f
        private set

    var active: Boolean = true
        private set

    private var firstTick = true

    fun tick(nowMs: Float) {
        if (!active) return

        if (firstTick) {
            firstTick = false
            // AS2 constructor: Snd.PlaySound("Boom") + _level0.VCam.Shake(-500).
            sound.play("Boom")
            worldObj.camera?.shake(offSetMs = -500f, nowMs = nowMs)
        }

        ageMs += GameConfig.TICK_MS

        // Damage window (BoomMatter timeline still playing in the AS2 version).
        if (ageMs < BOOM_DAMAGE_MS) {
            for (player in worldObj.players) {
                if (player in influenced) continue
                val dx = player.x - x
                val dy = player.y - y
                if (dx * dx + dy * dy <= squareSize) {
                    player.damage(size * 5f, sender, fromExplosion = true)
                    val dir = if (player.x <= x) -1f else 1f
                    player.doThrow(size * 2f, 45f * dir, sender, fromExplosion = true)
                    influenced += player
                }
            }
        }

        // Life end (BoomWave reached its last frame in the AS2 version).
        if (ageMs >= BOOM_LIFE_MS) {
            active = false
            worldObj.explosions.remove(this)
        }
    }

    companion object {
        /** BoomMatter sprite: 8 frames, damage while frames 1..7 = 7 transitions @30fps. */
        val BOOM_DAMAGE_MS: Float = 7f * GameConfig.MS_PER_ANIM_FRAME

        /** BoomWave sprite: 21 frames, removal at frame 21 = 20 transitions @30fps. */
        val BOOM_LIFE_MS: Float = 20f * GameConfig.MS_PER_ANIM_FRAME
    }
}
