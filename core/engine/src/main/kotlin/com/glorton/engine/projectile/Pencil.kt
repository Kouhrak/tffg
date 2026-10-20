package com.glorton.engine.projectile

import com.glorton.engine.fighter.Fighter
import com.glorton.engine.fighter.FighterState
import com.glorton.engine.world.HitCandidate
import com.glorton.engine.world.World

/**
 * Port of class Pencil (Pencil.as): AuberginePlayer's thrown pencil.
 * Speed 7, life 2500ms, damage 20, Throw(3.5, 50deg), spins +15deg/tick,
 * "Punch" sound.
 */
class Pencil(worldObj: World, sender: Fighter, dirSign: Float) : Projectile(worldObj, sender, dirSign) {

    override fun onInit() {
        lifeMs = 2500f
        xInc = 7f * dirSign
        yInc = 0f
    }

    override fun doMovement() {
        x += xInc
        rotationDeg += 15f
    }

    override fun hitFighter(candidate: HitCandidate) {
        val target = targetOf(candidate)
        if (target === sender || target.invincible || target.state == FighterState.KO) {
            return
        }
        sender.sound.play("Punch")
        target.damage(20f, sender)
        target.doThrow(3.5f, 50f * (if (xInc <= 0f) -1f else 1f), sender)
        destroy()
    }
}
