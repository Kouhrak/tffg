package com.glorton.engine.projectile

import com.glorton.engine.fighter.Fighter
import com.glorton.engine.fighter.FighterState
import com.glorton.engine.world.HitCandidate
import com.glorton.engine.world.World

/**
 * Port of class Bullet (Bullet.as): fast horizontal shot from Peach's gun.
 * Speed 20 (mirrored by the firing direction), life 3000ms, damage 10,
 * Throw(3, 45deg toward the firing side), "Headshot" sound.
 */
class Bullet(worldObj: World, sender: Fighter, dirSign: Float) : Projectile(worldObj, sender, dirSign) {

    override fun onInit() {
        lifeMs = 3000f
        xInc = 20f * dirSign
    }

    override fun doMovement() {
        x += xInc
    }

    override fun hitFighter(candidate: HitCandidate) {
        val target = targetOf(candidate)
        if (target === sender || target.invincible || target.state == FighterState.KO) {
            return
        }
        sender.sound.play("Headshot")
        target.damage(10f, sender)
        target.doThrow(3f, 45f * (if (xInc <= 0f) -1f else 1f), sender)
        destroy()
    }
}
