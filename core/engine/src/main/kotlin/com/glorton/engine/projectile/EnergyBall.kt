package com.glorton.engine.projectile

import com.glorton.engine.fighter.Fighter
import com.glorton.engine.fighter.FighterState
import com.glorton.engine.world.HitCandidate
import com.glorton.engine.world.World
import kotlin.math.sin

/**
 * Port of class EnergyBall (EnergyBall.as): DefaultPlayer's electric special.
 * Speed 5 with a sine wobble on Y, life 2000ms, damage 7 + electrocution
 * (Paralized 100ms), bounces back at walls instead of dying, "Electric" sound.
 */
class EnergyBall(worldObj: World, sender: Fighter, dirSign: Float) : Projectile(worldObj, sender, dirSign) {

    override fun onInit() {
        lifeMs = 2000f
        xInc = 5f * dirSign
    }

    /** Port of EnergyBall.HitWall: reverses direction and jumps 15px instead of dying. */
    override fun hitWall(force: Boolean) {
        if (ageMs > lifeMs || force) {
            destroy()
        } else {
            xInc *= -1f
            x += if (xInc > 0f) 15f else -15f
        }
    }

    override fun hitFighter(candidate: HitCandidate) {
        val target = targetOf(candidate)
        if (target === sender || target.invincible || target.state == FighterState.KO) {
            return
        }
        sender.sound.play("Electric")
        target.state = FighterState.ELECTROCUTED
        target.damage(7f, sender)
        target.electrocuted = true
        target.paralyzedMs = 100f
        // AS2 quirk: Throw(2.5, 45 * dir, this) — the *ball* is the throw
        // source (its Weight was copied from the shooter at init).
        target.doThrow(2.5f, 45f * (if (xInc <= 0f) -1f else 1f), this)
        destroy()
    }

    override fun doMovement() {
        x += xInc
        y += sin((x * Math.PI / 180.0 * 10.0).toFloat()) * 2f
    }
}
