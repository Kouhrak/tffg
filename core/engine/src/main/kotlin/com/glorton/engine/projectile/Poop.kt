package com.glorton.engine.projectile

import com.glorton.engine.fighter.Fighter
import com.glorton.engine.fighter.FighterState
import com.glorton.engine.world.HitCandidate
import com.glorton.engine.world.World

/**
 * Port of class Poop (Poop.as): CoffeePlayer's special. Speed 7, life
 * 3000ms, damage 15, Throw(3, 45deg), "Fart" sound.
 */
class Poop(worldObj: World, sender: Fighter, dirSign: Float) : Projectile(worldObj, sender, dirSign) {

    override fun onInit() {
        lifeMs = 3000f
        xInc = 7f * dirSign
        yInc = 0f
    }

    override fun doMovement() {
        x += xInc
    }

    override fun hitFighter(candidate: HitCandidate) {
        val target = targetOf(candidate)
        if (target === sender || target.invincible || target.state == FighterState.KO) {
            return
        }
        sender.sound.play("Fart")
        target.damage(15f, sender)
        target.doThrow(3f, 45f * (if (xInc <= 0f) -1f else 1f), sender)
        destroy()
    }
}
